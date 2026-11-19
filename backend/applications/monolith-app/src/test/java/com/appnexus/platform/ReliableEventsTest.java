package com.appnexus.platform;

import com.appnexus.platform.security.ReliableEvents;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** 真实 MySQL 验证事务回滚、租约恢复、跨租户拒绝、消费幂等及失败重试分类。 */
@SpringBootTest
@ActiveProfiles("local")
class ReliableEventsTest extends DatabaseTestSupport {
    @Autowired ReliableEvents events;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.appnexus.platform.authorization.AuthorityEvents authorityEvents;
    private UUID publish(){return new TransactionTemplate(manager).execute(status->events.publish(UUID.randomUUID(),"AUTHORITY_INVALIDATED",UUID.randomUUID().toString()));}
    private ReliableEvents.Event claim(UUID id){
        // 测试仅隔离事件领取顺序，不修改租约机制；共享库中的其他事件先延后。
        jdbc.update("UPDATE platform_outbox SET available_at=CURRENT_TIMESTAMP(6)+INTERVAL 1 HOUR WHERE status='PENDING' AND id<>UUID_TO_BIN(?)",id.toString());
        return events.claim().orElseThrow();
    }
    @Test void rollbackCannotPublishAndOutsideTransactionIsRejected(){
        UUID id=UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(status->{events.publish(id,"AUTHORITY_INVALIDATED",id.toString());status.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_outbox WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,id.toString())).isZero();
        assertThatThrownBy(()->events.publish(id,"AUTHORITY_INVALIDATED",id.toString())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    @Test void duplicateConsumptionAndForeignTenantCannotChangeState(){
        UUID id=publish();var event=claim(id);var count=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->events.consume(event,"consumer",UUID.randomUUID(),count::incrementAndGet)).isInstanceOf(SecurityException.class);
        assertThat(events.consume(event,"consumer",event.tenant(),count::incrementAndGet)).isTrue();
        assertThat(events.consume(event,"consumer",event.tenant(),count::incrementAndGet)).isFalse();
        assertThat(count.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_outbox WHERE id=UUID_TO_BIN(?)",String.class,id.toString())).isEqualTo("DONE");
    }
    @Test void expiredLeaseIsRecoveredAndStaleWorkerCannotComplete(){
        UUID id=publish();var first=claim(id);
        jdbc.update("UPDATE platform_outbox SET lease_until=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=UUID_TO_BIN(?)",id.toString());
        var second=events.claim().orElseThrow();assertThat(second.id()).isEqualTo(id);assertThat(second.lease()).isNotEqualTo(first.lease());
        assertThat(events.consume(first,"consumer",first.tenant(),()->{})).isFalse();
        assertThat(events.consume(second,"consumer",second.tenant(),()->{})).isTrue();
    }
    @Test void failedEffectRollsBackReceiptAndCanBeRetried(){
        UUID id=publish();var event=claim(id);
        assertThatThrownBy(()->events.consume(event,"consumer",event.tenant(),()->{throw new IllegalStateException("test failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_event_consumption WHERE event_id=UUID_TO_BIN(?)",Integer.class,id.toString())).isZero();
        assertThat(events.fail(event,false)).isTrue();
        jdbc.update("UPDATE platform_outbox SET available_at=CURRENT_TIMESTAMP(6) WHERE id=UUID_TO_BIN(?)",id.toString());
        var retry=events.claim().orElseThrow();assertThat(retry.attempts()).isEqualTo(2);
        assertThat(events.fail(retry,true)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM platform_outbox WHERE id=UUID_TO_BIN(?)",String.class,id.toString())).isEqualTo("MANUAL");
    }
    @Test void exhaustedCrashLeaseMovesToManual(){
        UUID id=publish();claim(id);
        jdbc.update("UPDATE platform_outbox SET attempts=5,lease_until=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=UUID_TO_BIN(?)",id.toString());
        events.claim();
        assertThat(jdbc.queryForObject("SELECT status FROM platform_outbox WHERE id=UUID_TO_BIN(?)",String.class,id.toString())).isEqualTo("MANUAL");
        assertThat(events.retry(id)).isTrue();assertThat(events.retry(id)).isFalse();
    }
    /** 消费者宕机前并行领取只能一个实例拿到该事件，另一个不能共享同一租约。 */
    @Test void concurrentClaimDoesNotReturnSameEventTwice()throws Exception{
        UUID id=publish();jdbc.update("UPDATE platform_outbox SET available_at=CURRENT_TIMESTAMP(6)+INTERVAL 1 HOUR WHERE status='PENDING' AND id<>UUID_TO_BIN(?)",id.toString());
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var gate=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{gate.await();return events.claim();});var second=pool.submit(()->{gate.await();return events.claim();});
            gate.countDown();var values=List.of(first.get(),second.get());
            assertThat(values.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        }
    }
    /** 权威事务事件端口与授权变更共事务，回滚后没有可消费事件。 */
    @Test void authorityEventsPublishOnlyCommittedChanges(){
        UUID account=UUID.randomUUID();jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Event test','unused')",account.toString(),"event-"+account);
        new TransactionTemplate(manager).executeWithoutResult(status->{authorityEvents.invalidated(null,account);status.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_outbox WHERE aggregate_id=?",Integer.class,account.toString())).isZero();
        new TransactionTemplate(manager).executeWithoutResult(status->authorityEvents.invalidated(null,account));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_outbox WHERE aggregate_id=? AND event_type='AUTHORITY_INVALIDATED'",Integer.class,account.toString())).isEqualTo(1);
    }
}
