package com.appnexus.platform.security;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * 共享数据库事务事件及基础任务租约；只保存标识，不保存敏感 payload，不引入消息队列。
 * 发布必须参与已有业务事务；消费去重和副作用必须通过 consume 的同事务回调完成。
 */
@Service
public class ReliableEvents {
    /** 零 UUID 为平台级事件边界，不允许租户业务使用其绕过隔离。 */
    public static final UUID PLATFORM=new UUID(0,0);
    private static final Set<String> TYPES=Set.of("AUTHORITY_INVALIDATED","INTEGRATION_AUTHORITY_CHANGED");
    private final JdbcTemplate jdbc;
    public ReliableEvents(JdbcTemplate jdbc){this.jdbc=jdbc;}
    /** 租约令牌必须与领取时一致；租户和聚合均由数据库记录返回。 */
    public record Event(UUID id,UUID tenant,String type,String aggregate,int attempts,String lease){}
    /**
     * 写业务事件；外部无事务调用直接拒绝，回滚不留下可消费事件。
     * @param tenant 可信租户 UUID；平台事件必须显式使用 PLATFORM
     * @param type 已登记事件类型，仅权限失效和集成授权变化
     * @param aggregate 不可变聚合标识，1–100 位安全标识字符，禁止敏感原值
     * @return 新事件 UUID，事务提交前不可消费
     */
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID publish(UUID tenant,String type,String aggregate){
        if(tenant==null||!TYPES.contains(type)||aggregate==null||!aggregate.matches("[A-Za-z0-9._:-]{1,100}"))throw new IllegalArgumentException("Invalid event metadata");
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO platform_outbox(id,tenant_id,event_type,aggregate_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",id.toString(),tenant.toString(),type,aggregate);return id;
    }
    /**
     * 多实例 SKIP LOCKED 领取单条事件，30 秒租约；超过 5 次转人工，不静默丢弃。
     * @return 无可领取事件时为空；调用方不能自行替换租户或 lease
     */
    @Transactional
    public Optional<Event> claim(){
        jdbc.update("UPDATE platform_outbox SET status='MANUAL',failure_code='ATTEMPTS_EXHAUSTED',lease_token=NULL,lease_until=NULL WHERE attempts>=5 AND (status='PENDING' OR (status='PROCESSING' AND lease_until<=CURRENT_TIMESTAMP(6)))");
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(tenant_id) tenant,event_type,aggregate_id,attempts FROM platform_outbox WHERE attempts<5 AND ((status='PENDING' AND available_at<=CURRENT_TIMESTAMP(6)) OR (status='PROCESSING' AND lease_until<=CURRENT_TIMESTAMP(6))) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();String lease=UUID.randomUUID().toString();
        String id=(String)row.get("id");jdbc.update("UPDATE platform_outbox SET status='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=CURRENT_TIMESTAMP(6)+INTERVAL 30 SECOND WHERE id=UUID_TO_BIN(?)",lease,id);
        return Optional.of(new Event(UUID.fromString(id),UUID.fromString((String)row.get("tenant")),(String)row.get("event_type"),(String)row.get("aggregate_id"),((Number)row.get("attempts")).intValue()+1,lease));
    }
    /**
     * 消费幂等及副作用原子提交；副作用异常时整体回滚，不能提前记为已消费。
     * @param event 本次有效租约，不接受伪造租户
     * @param consumer 固定消费者标识，1–100 位安全字符
     * @param expectedTenant 消费者明确接受的租户，必须与数据库事件一致
     * @param effect 同库事务副作用；不得发外部消息、HTTP 或执行不可回滚操作
     * @return 首次执行为 true，已消费或租约失效为 false
     */
    @Transactional
    public boolean consume(Event event,String consumer,UUID expectedTenant,Runnable effect){
        if(!consumer.matches("[A-Za-z0-9._:-]{1,100}")||!event.tenant().equals(expectedTenant))throw new SecurityException("Event tenant mismatch");
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(tenant_id) tenant FROM platform_outbox WHERE id=UUID_TO_BIN(?) AND status='PROCESSING' AND lease_token=? AND lease_until>CURRENT_TIMESTAMP(6) FOR UPDATE",event.id().toString(),event.lease());
        if(rows.isEmpty())return false;
        if(!expectedTenant.toString().equals(rows.getFirst().get("tenant")))throw new SecurityException("Event tenant mismatch");
        int inserted=jdbc.update("INSERT IGNORE INTO platform_event_consumption(tenant_id,event_id,consumer) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?)",expectedTenant.toString(),event.id().toString(),consumer);
        if(inserted==1)effect.run();
        jdbc.update("UPDATE platform_outbox SET status='DONE',lease_token=NULL,lease_until=NULL,failure_code=NULL WHERE id=UUID_TO_BIN(?) AND lease_token=?",event.id().toString(),event.lease());
        return inserted==1;
    }
    /**
     * 分类失败；永久错误立即转人工，临时故障按次数退避并最多领取五次。
     * @param event 当前租约，旧 Worker 不能改写新租约
     * @param permanent true 为不可自动恢复的业务/安全错误
     * @return 成功更新当前租约时为 true
     */
    @Transactional
    public boolean fail(Event event,boolean permanent){
        boolean manual=permanent||event.attempts()>=5;
        return jdbc.update("UPDATE platform_outbox SET status=?,failure_code=?,available_at=TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP(6)),lease_token=NULL,lease_until=NULL WHERE id=UUID_TO_BIN(?) AND lease_token=? AND status='PROCESSING'",
            manual?"MANUAL":"PENDING",permanent?"PERMANENT":"TRANSIENT",Math.min(300,event.attempts()*10),event.id().toString(),event.lease())==1;
    }
    /**
     * 平台运维只读任务摘要，不返回敏感 payload。
     * @param limit 1–100 条，默认入口使用 50，按最新创建时间排序
     * @return 状态及租户边界，用于人工处理定位
     */
    public List<Task> tasks(int limit){
        if(limit<1||limit>100)throw new IllegalArgumentException("Invalid task limit");
        return jdbc.query("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(tenant_id) tenant,event_type,status,attempts,failure_code FROM platform_outbox ORDER BY created_at DESC,id DESC LIMIT ?",
            (row,index)->new Task(UUID.fromString(row.getString("id")),UUID.fromString(row.getString("tenant")),row.getString("event_type"),row.getString("status"),row.getInt("attempts"),row.getString("failure_code")),limit);
    }
    /** 任务元数据摘要，不暴露租约令牌或业务原值。 */
    public record Task(UUID id,UUID tenant,String type,String status,int attempts,String failureCode){}
    /**
     * 明确人工重试，只允许 MANUAL，推进为新一轮处理；保留历史消费幂等记录。
     * @param id 要处理的事件 UUID，必须由受权平台操作入口调用
     * @return 当前确为人工状态且已恢复时为 true
     */
    @Transactional
    public boolean retry(UUID id){return jdbc.update("UPDATE platform_outbox SET status='PENDING',attempts=0,available_at=CURRENT_TIMESTAMP(6),failure_code=NULL,lease_token=NULL,lease_until=NULL WHERE id=UUID_TO_BIN(?) AND status='MANUAL'",id.toString())==1;}
}
