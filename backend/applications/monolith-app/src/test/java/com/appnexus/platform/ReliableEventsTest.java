package com.appnexus.platform;

import com.appnexus.platform.security.ReliableEvents;
import com.appnexus.platform.security.AuthorityEventWorker;
import com.appnexus.platform.security.ScheduledObjectOperations;
import com.appnexus.platform.tenant.TaskManagement;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

/** 真实 MySQL 验证事务回滚、租约恢复、跨租户拒绝、消费幂等及失败重试分类。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ReliableEventsTest extends DatabaseTestSupport {
    @Autowired ReliableEvents events;
    @Autowired ScheduledObjectOperations scheduledOperations;
    @Autowired com.appnexus.platform.security.operation.OperationRegistry operationRegistry;
    @Autowired MockMvc mvc;
    @Autowired com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration administration;
    @Autowired JdbcTemplate jdbc;
    @Autowired List<com.appnexus.contracts.data.TransactionalBusinessEvents.Consumer> handlers;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.appnexus.platform.authorization.AuthorityEvents authorityEvents;
    private UUID publish(){return new TransactionTemplate(manager).execute(status->events.publish(UUID.randomUUID(),"AUTHORITY_INVALIDATED",UUID.randomUUID().toString()));}
    private ReliableEvents.Event claim(UUID id){
        // 测试仅隔离事件领取顺序，不修改租约机制；共享库中的其他事件先延后。
        jdbc.update("UPDATE platform_event_delivery SET available_at=CURRENT_TIMESTAMP(6)+INTERVAL 1 HOUR WHERE status='PENDING' AND event_id<>UUID_TO_BIN(?)",id.toString());
        return events.claim().orElseThrow();
    }
    /** 真实调度方法先复核 Provider，再按数据库租户与租约消费；拒绝时不领取。 */
    @Test void scheduledWorkerRequiresDeclarationBeforeClaiming() {
        UUID id = publish();
        jdbc.update("UPDATE platform_event_delivery SET available_at=CURRENT_TIMESTAMP(6)+INTERVAL 1 HOUR WHERE status='PENDING' AND event_id<>UUID_TO_BIN(?)", id.toString());
        var unregistered = new AuthorityEventWorker(events, new ScheduledObjectOperations(List.of()), true);
        assertThatThrownBy(unregistered::process).isInstanceOf(SecurityException.class)
                .hasMessage("SCHEDULED_OBJECT_OPERATION_DENIED:" + TaskManagement.PROCESS_EVENTS);
        assertThat(jdbc.queryForObject("SELECT attempts FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?)",
                Integer.class, id.toString())).isZero();

        var worker = new AuthorityEventWorker(events, scheduledOperations, true);
        worker.process();
        worker.process();
        assertThat(jdbc.queryForObject("SELECT status FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?)",
                String.class, id.toString())).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_event_consumption WHERE event_id=UUID_TO_BIN(?)",
                Integer.class, id.toString())).isEqualTo(1);
        assertThat(operationRegistry.all()).noneMatch(operation -> operation.id().equals(TaskManagement.PROCESS_EVENTS));
    }
    /** 平台任务目录和重试经真实会话与权限过滤器执行，越权与状态冲突均不改变任务。 */
    @Test void platformOperationsRespectPermissionAndManualState() throws Exception {
        administration.initializeAdministrator("admin-test");
        String administrator = jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'", String.class);
        UUID outsider = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Task test','unused')",
                outsider.toString(), "task-" + outsider);
        UUID eventId = publish();
        assertThat(events.fail(claim(eventId), true)).isTrue();
        String tasks = "/bff/platform/tasks";
        String retry = tasks + "/" + eventId + "/retry";

        mvc.perform(get("/bff/platform/business-objects").with(authenticated(administrator, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.descriptor.objectType == 'PlatformTask')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.descriptor.objectType == 'DictionaryItemReference')]").isNotEmpty());
        mvc.perform(get(tasks).with(authenticated(administrator, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + eventId + "')]").isNotEmpty());
        mvc.perform(get(tasks).with(authenticated(outsider.toString(), 0))).andExpect(status().isForbidden());
        mvc.perform(post(retry).with(authenticated(outsider.toString(), 0)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?)",
                String.class, eventId.toString())).isEqualTo("MANUAL");
        mvc.perform(post(retry).with(authenticated(administrator, 0)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post(retry).with(authenticated(administrator, 0)).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?)",
                String.class, eventId.toString())).isEqualTo("PENDING");
    }

    /** 真实注册、授权、CSRF、暂停领取、恢复领取和管理审计使用同一组 Spring/MySQL 入口。 */
    @Test void subscriptionStateControlsOnlyItsOwnDelivery() throws Exception {
        administration.initializeAdministrator("admin-test");
        String admin = jdbc.queryForObject(
            "SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'", String.class);
        String catalog = "/bff/platform/event-subscriptions";
        String path = catalog + "/INBOUND_RECEIPT_POSTED/inbound-batch-quality/state";
        String pause = """
            {"paused":true,"reason":"维护质检投影"}
            """;
        String resume = """
            {"paused":false,"reason":"恢复质检投影"}
            """;
        mvc.perform(get(catalog)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).contentType("application/json").content(pause).with(authenticated(admin, 0)))
            .andExpect(status().isForbidden());
        mvc.perform(put(catalog + "/AUTHORITY_INVALIDATED/authority-invalidation/state")
                .contentType("application/json").content(pause).with(authenticated(admin, 0)).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(put(path).contentType("application/json").content(pause).with(authenticated(admin, 0)).with(csrf()))
            .andExpect(status().isNoContent());
        var tenant = UUID.randomUUID();
        var tx = new TransactionTemplate(manager);
        var type = com.appnexus.contracts.data.TransactionalBusinessEvents.Type.INBOUND_RECEIPT_POSTED;
        UUID paused;
        try {
            mvc.perform(get(catalog).with(authenticated(admin, 0))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'INBOUND_RECEIPT_POSTED' && @.consumer == 'inbound-batch-quality' && @.paused == true)]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.type == 'INBOUND_QUALITY_DECIDED' && @.paused == false)]").isNotEmpty());
            paused = tx.execute(status -> events.publish(tenant,type,UUID.randomUUID()));
            var unrelated = claim(publish());
            assertThat(events.consume(unrelated,unrelated.consumer(),unrelated.tenant(),()->{})).isTrue();
            jdbc.update("UPDATE platform_event_delivery SET available_at=CURRENT_TIMESTAMP(6) WHERE event_id=UUID_TO_BIN(?)",
                paused.toString());
            var sibling = events.claim().orElseThrow();
            assertThat(sibling.id()).isEqualTo(paused);
            assertThat(sibling.consumer()).isEqualTo("test-second");
            assertThat(events.consume(sibling,sibling.consumer(),tenant,()->{})).isTrue();
            assertThat(jdbc.queryForObject("""
                SELECT status FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?) AND consumer_id='inbound-batch-quality'
                """,String.class,paused.toString())).isEqualTo("PENDING");
        } finally {
            mvc.perform(put(path).contentType("application/json").content(resume).with(authenticated(admin, 0)).with(csrf()))
                .andExpect(status().isNoContent());
        }

        var restored = claim(paused);
        assertThat(restored.consumer()).isEqualTo("inbound-batch-quality");
        assertThat(events.consume(restored,restored.consumer(),tenant,()->{})).isTrue();
        assertThat(jdbc.queryForList("""
            SELECT operation_reason FROM platform_administration_audit
            WHERE action IN ('EVENT_SUBSCRIPTION_PAUSED','EVENT_SUBSCRIPTION_RESUMED')
            """,String.class)).contains("维护质检投影","恢复质检投影");
    }

    @Test void rollbackCannotPublishAndOutsideTransactionIsRejected(){
        UUID id=UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(status->{events.publish(id,"AUTHORITY_INVALIDATED",id.toString());status.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_outbox WHERE tenant_id=UUID_TO_BIN(?)",Integer.class,id.toString())).isZero();
        assertThatThrownBy(()->events.publish(id,"AUTHORITY_INVALIDATED",id.toString())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    @Test void duplicateConsumptionAndForeignTenantCannotChangeState(){
        UUID id=publish();var event=claim(id);var count=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->events.consume(event,"authority-invalidation",UUID.randomUUID(),count::incrementAndGet)).isInstanceOf(SecurityException.class);
        assertThat(events.consume(event,"authority-invalidation",event.tenant(),count::incrementAndGet)).isTrue();
        assertThat(events.consume(event,"authority-invalidation",event.tenant(),count::incrementAndGet)).isFalse();
        assertThat(count.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?)",String.class,id.toString())).isEqualTo("DONE");
    }
    @Test void expiredLeaseIsRecoveredAndStaleWorkerCannotComplete(){
        UUID id=publish();var first=claim(id);
        jdbc.update("UPDATE platform_event_delivery SET lease_until=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE event_id=UUID_TO_BIN(?)",id.toString());
        var second=events.claim().orElseThrow();assertThat(second.id()).isEqualTo(id);assertThat(second.lease()).isNotEqualTo(first.lease());
        assertThat(events.consume(first,"authority-invalidation",first.tenant(),()->{})).isFalse();
        assertThat(events.consume(second,"authority-invalidation",second.tenant(),()->{})).isTrue();
    }
    @Test void failedEffectRollsBackReceiptAndCanBeRetried(){
        UUID id=publish();var event=claim(id);
        assertThatThrownBy(()->events.consume(event,"authority-invalidation",event.tenant(),()->{throw new IllegalStateException("test failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_event_consumption WHERE event_id=UUID_TO_BIN(?)",Integer.class,id.toString())).isZero();
        assertThat(events.fail(event,false)).isTrue();
        jdbc.update("UPDATE platform_event_delivery SET available_at=CURRENT_TIMESTAMP(6) WHERE event_id=UUID_TO_BIN(?)",id.toString());
        var retry=events.claim().orElseThrow();assertThat(retry.attempts()).isEqualTo(2);
        assertThat(events.fail(retry,true)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?)",String.class,id.toString())).isEqualTo("MANUAL");
    }
    @Test void exhaustedCrashLeaseMovesToManual(){
        UUID id=publish();claim(id);
        jdbc.update("UPDATE platform_event_delivery SET attempts=5,lease_until=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE event_id=UUID_TO_BIN(?)",id.toString());
        events.claim();
        assertThat(jdbc.queryForObject("SELECT status FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?)",String.class,id.toString())).isEqualTo("MANUAL");
        administration.initializeAdministrator("admin-test");
        UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        assertThat(events.retry(id,actor)).isTrue();
        assertThat(events.retry(id,actor)).isFalse();
    }
    /** 消费者宕机前并行领取只能一个实例拿到该事件，另一个不能共享同一租约。 */
    @Test void concurrentClaimDoesNotReturnSameEventTwice()throws Exception{
        UUID id=publish();jdbc.update("UPDATE platform_event_delivery SET available_at=CURRENT_TIMESTAMP(6)+INTERVAL 1 HOUR WHERE status='PENDING' AND event_id<>UUID_TO_BIN(?)",id.toString());
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

    /** 实际发布为两个订阅者创建独立投递，HTTP 人工恢复仅影响目标投递。 */
    @Test void multiConsumerRetryPreservesCompletedSibling() throws Exception {
        administration.initializeAdministrator("admin-test");
        String actor=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class);
        UUID tenant=UUID.randomUUID(), subject=UUID.randomUUID();
        UUID id=new TransactionTemplate(manager).execute(tx->events.publish(tenant,
                com.appnexus.contracts.data.TransactionalBusinessEvents.Type.INBOUND_RECEIPT_POSTED,subject));
        var first=claim(id);
        var second=events.claim().orElseThrow();
        assertThat(List.of(first.consumer(),second.consumer())).containsExactlyInAnyOrder("inbound-batch-quality","test-second");
        var done=first.consumer().equals("inbound-batch-quality")?first:second;
        var failed=done==first?second:first;
        assertThat(events.consume(done,done.consumer(),tenant,
                ()->jdbc.update("UPDATE platform_outbox SET updated_at=CURRENT_TIMESTAMP(6) WHERE id=UUID_TO_BIN(?)",id.toString()))).isTrue();
        assertThatThrownBy(()->events.consume(failed,failed.consumer(),tenant,()->{throw new IllegalArgumentException("test");}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(events.fail(failed,true)).isTrue();
        mvc.perform(get("/bff/platform/events").param("tenant",tenant.toString()).with(authenticated(actor,0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].manual").value(1));
        mvc.perform(get("/bff/platform/events/"+id).with(authenticated(actor,0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deliveries.length()").value(2));
        mvc.perform(post("/bff/platform/tasks/"+id+"/retry").with(authenticated(actor,0)).with(csrf()))
                .andExpect(status().isConflict());
        String retry="/bff/platform/event-deliveries/"+failed.deliveryId()+"/retry";
        mvc.perform(post(retry).with(authenticated(actor,0)).contentType("application/json")
                .content("{\"reason\":\"missing csrf\"}")).andExpect(status().isForbidden());
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> request=()->{
                gate.await();
                return mvc.perform(post(retry).with(authenticated(actor,0)).with(csrf())
                    .contentType("application/json").content("{\"reason\":\"verified\"}")).andReturn().getResponse().getStatus();
            };
            var left=pool.submit(request);
            var right=pool.submit(request);
            gate.countDown();
            assertThat(List.of(left.get(),right.get())).containsExactlyInAnyOrder(204,409);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM platform_event_delivery WHERE id=UUID_TO_BIN(?)",
                String.class,done.deliveryId().toString())).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT status FROM platform_event_delivery WHERE id=UUID_TO_BIN(?)",
                String.class,failed.deliveryId().toString())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_event_consumption WHERE event_id=UUID_TO_BIN(?)",
                Integer.class,id.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT operation_reason FROM platform_administration_audit WHERE target_id=UUID_TO_BIN(?)",
                String.class,failed.deliveryId().toString())).isEqualTo("verified");
        new AuthorityEventWorker(events,scheduledOperations,true,handlers).process();
        assertThat(jdbc.queryForMap("SELECT attempts,total_attempts,status FROM platform_event_delivery WHERE id=UUID_TO_BIN(?)",
                failed.deliveryId().toString())).containsEntry("attempts",1).containsEntry("total_attempts",2).containsEntry("status","DONE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_account WHERE id=UUID_TO_BIN(?)",
                Integer.class,subject.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_event_consumption WHERE event_id=UUID_TO_BIN(?)",
                Integer.class,id.toString())).isEqualTo(2);
    }

    /** 测试专用第二订阅者，生产注册表无虚构消费者。 */
    @org.springframework.boot.test.context.TestConfiguration
    static class SecondSubscriptionConfiguration {
        @org.springframework.context.annotation.Bean
        com.appnexus.contracts.data.TransactionalBusinessEvents.Consumer secondSubscription(JdbcTemplate jdbc) {
            return new com.appnexus.contracts.data.TransactionalBusinessEvents.Consumer() {
                public Set<com.appnexus.contracts.data.TransactionalBusinessEvents.Type> types() {
                    return Set.of(com.appnexus.contracts.data.TransactionalBusinessEvents.Type.INBOUND_RECEIPT_POSTED);
                }
                public String consumerId() { return "test-second"; }
                public void consume(com.appnexus.contracts.data.TransactionalBusinessEvents.Delivery delivery) {
                    jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Event test','unused')",
                        delivery.subjectId().toString(),"event-consumer-"+delivery.subjectId());
                }
            };
        }
    }
}
