package com.appnexus.platform.persistence.eventing;

import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.persistence.mybatis.GuardedScope;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** Outbox 事实、投递租约及原子消费回执的固定 SQL。调用方负责事务和平台准入。 */
@Mapper
@PlatformScoped(tables={"SKIP","platform_outbox","platform_event_delivery","platform_event_delivery_attempt","platform_event_consumption"})
public interface OutboxMapper {
    String LEGACY = """
        CASE o.event_type
          WHEN 'AUTHORITY_INVALIDATED' THEN 'authority-invalidation'
          WHEN 'INTEGRATION_AUTHORITY_CHANGED' THEN 'authority-invalidation'
          WHEN 'INBOUND_RECEIPT_POSTED' THEN 'inbound-batch-quality'
          WHEN 'INBOUND_QUALITY_DECIDED' THEN 'inbound-batch-quality'
        END
        """;

    /** 在发布事务内追加事件事实，参数为服务端生成的 UUID、可信租户及受控事件引用。 */
    @GuardedScope(tenant="tenant",serviceOperation="system.platform.events:publish",serviceKeys={"id"})
    @Insert("INSERT INTO platform_outbox(id,tenant_id,event_type,aggregate_id,updated_at) VALUES(UUID_TO_BIN(#{id}),UUID_TO_BIN(#{tenant}),#{type},#{aggregate},CURRENT_TIMESTAMP(6))")
    int append(@Param("id") String id,@Param("tenant") String tenant,@Param("type") String type,@Param("aggregate") String aggregate);

    /** 在同一个发布事务内为代码声明的消费者创建投递。 */
    @GuardedScope(tenant="tenant",serviceOperation="system.platform.events:publish",serviceKeys={"event"})
    @Insert("INSERT INTO platform_event_delivery(id,event_id,tenant_id,consumer_id) VALUES(UUID_TO_BIN(UUID()),UUID_TO_BIN(#{event}),UUID_TO_BIN(#{tenant}),#{consumer})")
    int appendDelivery(@Param("event") String event,@Param("tenant") String tenant,@Param("consumer") String consumer);

    /** 逐条锁定到期投递，包含耗尽预算的崩溃恢复记录。 */
    @GuardedScope(tenant="scope",serviceOperation="system.platform.task:process",serviceKeys={"key"})
    @Select("""
        SELECT BIN_TO_UUID(d.id) delivery,BIN_TO_UUID(d.event_id) id,BIN_TO_UUID(d.tenant_id) tenant,
               o.event_type,o.aggregate_id,d.consumer_id,d.attempts,d.total_attempts,d.status
        FROM platform_event_delivery d STRAIGHT_JOIN platform_outbox o ON o.id=d.event_id AND o.tenant_id=d.tenant_id
        WHERE UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000 AND #{key}='queue'
          AND d.claim_at<=CURRENT_TIMESTAMP(6)
        ORDER BY d.claim_at,d.id LIMIT 1 FOR UPDATE OF d SKIP LOCKED
        """)
    List<Map<String,Object>> claimable(@Param("scope") String scope,@Param("key") String key);

    /** 持有投递行锁时分配 30 秒租约并推进累计领取序号。 */
    @GuardedScope(tenant="scope",serviceOperation="system.platform.task:process",serviceKeys={"id"})
    @Update("""
        UPDATE platform_event_delivery SET status='PROCESSING',attempts=attempts+1,total_attempts=total_attempts+1,
          lease_token=#{lease},lease_until=CURRENT_TIMESTAMP(6)+INTERVAL 30 SECOND,
          updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE id=UUID_TO_BIN(#{id}) AND UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000
        """)
    int claim(@Param("scope") String scope,@Param("id") String id,@Param("lease") String lease);

    /** 在领取事务内记录当前累计序号。 */
    @GuardedScope(tenant="scope",serviceOperation="system.platform.task:process",serviceKeys={"id"})
    @Insert("INSERT INTO platform_event_delivery_attempt(delivery_id,attempt_no,outcome) SELECT UUID_TO_BIN(#{id}),#{sequence},'PROCESSING' WHERE UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000")
    int startAttempt(@Param("scope") String scope,@Param("id") String id,@Param("sequence") int sequence);

    /** 更新仍在途的执行记录，回填前缺失的历史尝试不补造。 */
    @GuardedScope(tenant="scope",serviceOperation="system.platform.task:process",serviceKeys={"id"})
    @Update("""
        UPDATE platform_event_delivery_attempt SET outcome=#{outcome},failure_code=#{code},updated_at=CURRENT_TIMESTAMP(6)
        WHERE delivery_id=UUID_TO_BIN(#{id}) AND UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000 AND attempt_no=#{sequence} AND outcome='PROCESSING'
        """)
    int finishAttempt(@Param("scope") String scope,@Param("id") String id,@Param("sequence") int sequence,@Param("outcome") String outcome,@Param("code") String code);

    /** 消费前核对有效租约与持久投递身份，并持续持锁到业务事务结束。 */
    @GuardedScope(tenant="scope",serviceOperation="system.platform.task:process",serviceKeys={"id"})
    @Select("""
        SELECT BIN_TO_UUID(tenant_id) tenant,BIN_TO_UUID(event_id) event_id,consumer_id
        FROM platform_event_delivery WHERE id=UUID_TO_BIN(#{id}) AND UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000 AND status='PROCESSING'
          AND lease_token=#{lease} AND total_attempts=#{sequence} AND lease_until>CURRENT_TIMESTAMP(6) FOR UPDATE
        """)
    List<Map<String,Object>> lockLease(@Param("scope") String scope,@Param("id") String id,@Param("lease") String lease,@Param("sequence") int sequence);

    /** 唯一键竞争决定是否首次消费，写入与业务副作用共享事务。 */
    @GuardedScope(tenant="tenant",serviceOperation="system.platform.task:process",serviceKeys={"event"})
    @Insert("INSERT IGNORE INTO platform_event_consumption(tenant_id,event_id,consumer) VALUES(UUID_TO_BIN(#{tenant}),UUID_TO_BIN(#{event}),#{consumer})")
    int receipt(@Param("tenant") String tenant,@Param("event") String event,@Param("consumer") String consumer);

    /** 只完成本次租约的投递，过期 Worker 无法改写新租约。 */
    @GuardedScope(tenant="scope",serviceOperation="system.platform.task:process",serviceKeys={"id"})
    @Update("""
        UPDATE platform_event_delivery SET status=#{state},failure_code=#{code},
          available_at=TIMESTAMPADD(SECOND,#{delay},CURRENT_TIMESTAMP(6)),lease_token=NULL,lease_until=NULL,
          updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)
        WHERE id=UUID_TO_BIN(#{id}) AND UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000 AND total_attempts=#{sequence} AND lease_token=#{lease} AND status='PROCESSING'
        """)
    int finish(@Param("scope") String scope,@Param("id") String id,@Param("sequence") int sequence,@Param("lease") String lease,
               @Param("state") String state,@Param("code") String code,@Param("delay") int delay);

    /** 持有行锁且预算耗尽时转人工。 */
    @GuardedScope(tenant="scope",serviceOperation="system.platform.task:process",serviceKeys={"id"})
    @Update("""
        UPDATE platform_event_delivery SET status='MANUAL',failure_code='ATTEMPTS_EXHAUSTED',
          lease_token=NULL,lease_until=NULL,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE id=UUID_TO_BIN(#{id}) AND UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000
        """)
    int exhaust(@Param("scope") String scope,@Param("id") String id);

    /** 旧接口仍按事件 UUID 定位固定的原消费者。 */
    @GuardedScope(tenant="scope",serviceOperation="platform.task:retry",serviceKeys={"actor","id"})
    @Select("SELECT BIN_TO_UUID(d.id) FROM platform_event_delivery d JOIN platform_outbox o ON o.id=d.event_id AND o.tenant_id=d.tenant_id WHERE UUID_TO_BIN(#{actor}) IS NOT NULL AND UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000 AND o.id=UUID_TO_BIN(#{id}) AND d.consumer_id=(" + LEGACY + ")")
    String legacyDelivery(@Param("scope") String scope,@Param("actor") String actor,@Param("id") String id);

    /** 原接口摘要只投影固定旧消费者。 */
    @GuardedScope(platformActor="actor",platformPermission="platform.task:read")
    @Select("""
        SELECT BIN_TO_UUID(o.id) id,BIN_TO_UUID(o.tenant_id) tenant,o.event_type,d.status,d.attempts,d.failure_code
        FROM platform_outbox o JOIN platform_event_delivery d ON d.event_id=o.id AND d.tenant_id=o.tenant_id
        WHERE UUID_TO_BIN(#{actor}) IS NOT NULL AND d.consumer_id=(
        """ + LEGACY + ") ORDER BY o.created_at DESC,o.id DESC LIMIT #{limit}")
    List<Map<String,Object>> summaries(@Param("actor") String actor,@Param("limit") int limit);

    /** 单投递状态条件更新。迁移对账记录须先核实副作用。 */
    @GuardedScope(tenant="scope",serviceOperation="platform.task:retry",serviceKeys={"actor","id"})
    @Update("""
        UPDATE platform_event_delivery SET status='PENDING',attempts=0,available_at=CURRENT_TIMESTAMP(6),
          failure_code=NULL,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
        WHERE id=UUID_TO_BIN(#{id})  AND UUID_TO_BIN(#{scope})=0x00000000000000000000000000000000 AND status='MANUAL'
          AND (failure_code IS NULL OR failure_code NOT IN ('LEGACY_RECEIPT_MISSING','LEGACY_UNMAPPED'))
        """)
    int retry(@Param("scope") String scope,@Param("id") String id,@Param("actor") String actor);

    String EVENT_SUMMARY = """
        SELECT BIN_TO_UUID(o.id) id,BIN_TO_UUID(o.tenant_id) tenant,o.event_type type,
          o.aggregate_id subjectReference,o.event_revision revision,o.created_at createdAt,BIN_TO_UUID(o.created_by) createdBy,
          o.updated_at updatedAt,BIN_TO_UUID(o.updated_by) updatedBy,
          (SELECT COUNT(*) FROM platform_event_delivery d WHERE d.event_id=o.id AND d.tenant_id=o.tenant_id AND d.status='PENDING') pending,
          (SELECT COUNT(*) FROM platform_event_delivery d WHERE d.event_id=o.id AND d.tenant_id=o.tenant_id AND d.status='PROCESSING') processing,
          (SELECT COUNT(*) FROM platform_event_delivery d WHERE d.event_id=o.id AND d.tenant_id=o.tenant_id AND d.status='DONE') done,
          (SELECT COUNT(*) FROM platform_event_delivery d WHERE d.event_id=o.id AND d.tenant_id=o.tenant_id AND d.status='MANUAL') manual
        FROM platform_outbox o
        """;
    String EVENT_FILTER = """
        WHERE (#{tenant} IS NULL OR o.tenant_id=UUID_TO_BIN(#{tenant}))
          AND (#{type} IS NULL OR o.event_type=#{type})
          AND (#{from} IS NULL OR o.created_at>=#{from})
          AND (#{until} IS NULL OR o.created_at<#{until})
          AND (#{state} IS NULL OR EXISTS (SELECT 1 FROM platform_event_delivery d
                WHERE d.event_id=o.id AND d.tenant_id=o.tenant_id AND d.status=#{state}))
        """;

    /** 平台分页查询，固定排序和 20 条页容量，所有条件绑定参数。 */
    @GuardedScope(platformActor="actor",platformPermission="platform.task:read")
    @Select(EVENT_SUMMARY+EVENT_FILTER+" AND UUID_TO_BIN(#{actor}) IS NOT NULL"+" ORDER BY o.created_at DESC,o.id DESC LIMIT 20 OFFSET #{offset}")
    List<Map<String,Object>> events(@Param("actor") String actor,@Param("tenant") String tenant,@Param("type") String type,
        @Param("state") String state,@Param("from") java.time.Instant from,@Param("until") java.time.Instant until,
        @Param("offset") int offset);

    /** 与分页读取共用筛选，返回匹配的事件数量。 */
    @GuardedScope(platformActor="actor",platformPermission="platform.task:read")
    @Select("SELECT COUNT(*) FROM platform_outbox o "+EVENT_FILTER+" AND UUID_TO_BIN(#{actor}) IS NOT NULL")
    long eventCount(@Param("actor") String actor,@Param("tenant") String tenant,@Param("type") String type,@Param("state") String state,
        @Param("from") java.time.Instant from,@Param("until") java.time.Instant until);

    /** 平台事件详情只读取摘要及稳定业务引用。 */
    @GuardedScope(platformActor="actor",platformPermission="platform.task:read")
    @Select(EVENT_SUMMARY+" WHERE UUID_TO_BIN(#{actor}) IS NOT NULL AND o.id=UUID_TO_BIN(#{id})")
    Map<String,Object> event(@Param("actor") String actor,@Param("id") String id);

    /** 事件所属投递，不返回租约令牌。 */
    @GuardedScope(platformActor="actor",platformPermission="platform.task:read")
    @Select("""
        SELECT BIN_TO_UUID(id) id,consumer_id consumer,status,attempts,total_attempts totalAttempts,
          available_at availableAt,lease_until leaseUntil,failure_code failureCode,
          BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt
        FROM platform_event_delivery WHERE UUID_TO_BIN(#{actor}) IS NOT NULL AND event_id=UUID_TO_BIN(#{id}) ORDER BY consumer_id
        """)
    List<Map<String,Object>> deliveries(@Param("actor") String actor,@Param("id") String id);

    /** 事件最近 200 次执行记录，投递累计次数可用于识别更早的执行。 */
    @GuardedScope(platformActor="actor",platformPermission="platform.task:read")
    @Select("""
        SELECT BIN_TO_UUID(a.delivery_id) deliveryId,a.attempt_no attemptNo,a.outcome,a.failure_code failureCode,
               a.created_at createdAt,a.updated_at updatedAt
        FROM platform_event_delivery_attempt a JOIN platform_event_delivery d ON a.delivery_id=d.id
        WHERE UUID_TO_BIN(#{actor}) IS NOT NULL AND d.event_id=UUID_TO_BIN(#{id}) ORDER BY a.created_at DESC,a.delivery_id,a.attempt_no DESC LIMIT 200
        """)
    List<Map<String,Object>> attempts(@Param("actor") String actor,@Param("id") String id);
}
