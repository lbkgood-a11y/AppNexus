package com.appnexus.platform.security;

import com.appnexus.contracts.data.TransactionalBusinessEvents;
import com.appnexus.contracts.businessobject.BusinessObjects.ScheduledOperations;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.ScopedWriteGate;
import com.appnexus.platform.tenant.TaskManagement;
import com.appnexus.platform.persistence.eventing.OutboxMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 同库事件发布及投递事务。消费者副作用须加入消费事务，外部调用不具备此原子性。 */
@Service
public class ReliableEvents implements TransactionalBusinessEvents {
    /** 平台事件使用零 UUID，租户事件必须绑定实际租户。 */
    public static final UUID PLATFORM=new UUID(0,0);
    static final String AUTHORITY_CONSUMER="authority-invalidation";
    private static final Set<String> AUTHORITY_TYPES=Set.of("AUTHORITY_INVALIDATED","INTEGRATION_AUTHORITY_CHANGED");
    private static final String WORKER_SCOPE=PLATFORM.toString();
    private final OutboxMapper mapper;
    private final ScopedWriteGate gate;
    private final ScheduledOperations scheduled;
    private final AccessAuthorization access;
    private final Map<Type,Map<String,Consumer>> subscriptions;

    /**
     * @param mapper 与业务事务共用数据源的固定映射
     * @param handlers 启动期声明的消费者，同类型下标识必须唯一且视为持久契约
     */
    public ReliableEvents(OutboxMapper mapper,List<Consumer> handlers,ScopedWriteGate gate,
                          ScheduledOperations scheduled,AccessAuthorization access) {
        this.mapper=mapper;
        this.gate=gate;
        this.scheduled=scheduled;
        this.access=access;
        subscriptions=registrations(handlers);
    }

    static Map<Type,Map<String,Consumer>> registrations(List<Consumer> handlers) {
        var registrations=new EnumMap<Type,Map<String,Consumer>>(Type.class);
        for(var handler:handlers) {
            String id=handler.consumerId();
            if(id==null || !id.matches("[a-z0-9][a-z0-9._:-]{0,99}"))
                throw new IllegalArgumentException("无效的 Outbox 消费者标识");
            for(var type:handler.types()) {
                if(registrations.computeIfAbsent(type,key->new TreeMap<>()).putIfAbsent(id,handler)!=null)
                    throw new IllegalStateException("重复的 Outbox 订阅 "+type+"/"+id);
            }
        }
        registrations.replaceAll((type,byId)->Collections.unmodifiableMap(byId));
        return Collections.unmodifiableMap(registrations);
    }

    /** 已领取的投递以及数据库返回的事件引用，累计序号和租约共同标记本次执行。 */
    public record Event(UUID id,UUID tenant,String type,String aggregate,int attempts,String lease,
                        UUID deliveryId,String consumer,int sequence) {}

    /**
     * @param tenant 可信租户或显式平台零 UUID
     * @param type 已登记的权限事件类型
     * @param aggregate 聚合引用，1–100 个安全字符
     * @return 原事务提交后可见的事件 UUID
     */
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID publish(UUID tenant,String type,String aggregate) {
        if(tenant==null || !AUTHORITY_TYPES.contains(type) || aggregate==null || !aggregate.matches("[A-Za-z0-9._:-]{1,100}"))
            throw new IllegalArgumentException("无效的事件引用");
        return append(tenant,type,aggregate,Set.of(AUTHORITY_CONSUMER));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID publish(UUID tenant,Type type,UUID subjectId) {
        if(tenant==null || PLATFORM.equals(tenant) || type==null || subjectId==null)
            throw new IllegalArgumentException("业务事件必须绑定实际租户和记录");
        var consumers=subscriptions.getOrDefault(type,Map.of()).keySet();
        if(consumers.isEmpty()) throw new IllegalStateException("业务事件缺少订阅 "+type);
        return append(tenant,type.name(),subjectId.toString(),consumers);
    }

    private UUID append(UUID tenant,String type,String aggregate,Set<String> consumers) {
        UUID id=UUID.randomUUID();
        // 发布端口为受控业务事务内部调用，授权同一事务内锁定事件 ID 和可信租户。
        gate.authorizeServiceOperation(tenant,"system.platform.events:publish",List.of(id.toString()),()->true);
        requireOne(mapper.append(id.toString(),tenant.toString(),type,aggregate));
        for(String consumer:consumers) requireOne(mapper.appendDelivery(id.toString(),tenant.toString(),consumer));
        return id;
    }

    /** @return 单条投递及 30 秒租约，领取与尝试历史同事务提交 */
    @Transactional
    public Optional<Event> claim() {
        authorizeWorker(PLATFORM,"queue");
        for(int skipped=0;skipped<20;skipped++) {
            var rows=mapper.claimable(WORKER_SCOPE,"queue");
            if(rows.isEmpty()) return Optional.empty();
            var row=rows.getFirst();
            var delivery=(String)row.get("delivery");
            authorizeWorker(PLATFORM,delivery);
            int attempts=((Number)row.get("attempts")).intValue();
            int sequence=((Number)row.get("total_attempts")).intValue();
            if("PROCESSING".equals(row.get("status"))) mapper.finishAttempt(WORKER_SCOPE,delivery,sequence,"LEASE_EXPIRED","LEASE_EXPIRED");
            if(attempts>=5) {
                requireOne(mapper.exhaust(WORKER_SCOPE,delivery));
                continue;
            }
            String lease=UUID.randomUUID().toString();
            requireOne(mapper.claim(WORKER_SCOPE,delivery,lease));
            requireOne(mapper.startAttempt(WORKER_SCOPE,delivery,sequence+1));
            return Optional.of(new Event(UUID.fromString((String)row.get("id")),UUID.fromString((String)row.get("tenant")),
                (String)row.get("event_type"),(String)row.get("aggregate_id"),attempts+1,lease,
                UUID.fromString(delivery),(String)row.get("consumer_id"),sequence+1));
        }
        return Optional.empty();
    }

    /**
     * @param event 当前有效投递租约
     * @param consumer 固定消费者标识，须与持久投递一致
     * @param expectedTenant 可信期望租户，须与数据库一致
     * @param effect 同库可回滚业务副作用
     * @return 首次执行且确认成功；重复回执或租约失效返回 false
     */
    @Transactional
    public boolean consume(Event event,String consumer,UUID expectedTenant,Runnable effect) {
        if(!event.tenant().equals(expectedTenant) || !event.consumer().equals(consumer))
            throw new SecurityException("投递身份不符");
        var delivery=event.deliveryId().toString();
        authorizeWorker(PLATFORM,delivery);
        var rows=mapper.lockLease(WORKER_SCOPE,delivery,event.lease(),event.sequence());
        if(rows.isEmpty()) return false;
        var row=rows.getFirst();
        if(!expectedTenant.toString().equals(row.get("tenant")) || !event.id().toString().equals(row.get("event_id"))
                || !consumer.equals(row.get("consumer_id"))) throw new SecurityException("投递身份不符");
        authorizeWorker(expectedTenant,event.id().toString());
        boolean first=mapper.receipt(expectedTenant.toString(),event.id().toString(),consumer)==1;
        if(first) effect.run();
        requireOne(mapper.finish(WORKER_SCOPE,delivery,event.sequence(),event.lease(),"DONE",null,0));
        requireOne(mapper.finishAttempt(WORKER_SCOPE,delivery,event.sequence(),first?"DONE":"ALREADY_CONSUMED",null));
        return first;
    }

    /** 在消费事务回滚后记录故障，只推进该租约对应投递，最多五次自动领取。 */
    @Transactional
    public boolean fail(Event event,boolean permanent) {
        var delivery=event.deliveryId().toString();
        authorizeWorker(PLATFORM,delivery);
        String code=permanent?"PERMANENT":"TRANSIENT";
        String state=permanent || event.attempts()>=5?"MANUAL":"PENDING";
        if(mapper.finish(WORKER_SCOPE,delivery,event.sequence(),event.lease(),state,code,event.attempts()*10)!=1) return false;
        requireOne(mapper.finishAttempt(WORKER_SCOPE,delivery,event.sequence(),"FAILED",code));
        return true;
    }

    /** @return 原任务 API 的兼容投递摘要 */
    public List<Task> tasks(UUID actor,int limit) {
        if(limit<1 || limit>100) throw new IllegalArgumentException("无效的摘要数量");
        return mapper.summaries(actor.toString(),limit).stream().map(row->new Task(UUID.fromString((String)row.get("id")),
            UUID.fromString((String)row.get("tenant")),(String)row.get("event_type"),(String)row.get("status"),
            ((Number)row.get("attempts")).intValue(),(String)row.get("failure_code"))).toList();
    }

    /** 原任务接口摘要，ID 为事件 UUID。 */
    public record Task(UUID id,UUID tenant,String type,String status,int attempts,String failureCode) {}

    /** 使用固定历史映射恢复原消费者，授权和审计由任务管理用例负责。 */
    @Transactional
    public boolean retry(UUID id,UUID actor) {
        authorizeRetry(actor,id.toString());
        String delivery=mapper.legacyDelivery(WORKER_SCOPE,actor.toString(),id.toString());
        return delivery!=null && retryDelivery(UUID.fromString(delivery),actor);
    }

    /** 在外层管理审计事务内验证权限并执行单投递恢复。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean retryDelivery(UUID id,UUID actor) {
        authorizeRetry(actor,id.toString());
        return mapper.retry(WORKER_SCOPE,id.toString(),actor.toString())==1;
    }

    private void authorizeRetry(UUID actor,String id) {
        gate.authorizeServiceOperation(PLATFORM,"platform.task:retry",List.of(actor.toString(),id),
            ()->access.hasPermission(AccessAuthorization.Subject.platform(actor),"platform.task:retry"));
    }

    private void authorizeWorker(UUID tenant,String key) {
        scheduled.require(TaskManagement.PROCESS_EVENTS,TaskManagement.EVENT_WORKER_AUTHORITY);
        gate.authorizeServiceOperation(tenant,TaskManagement.EVENT_WORKER_AUTHORITY,List.of(key),()->true);
    }

    private static void requireOne(int affected) {
        if(affected!=1) throw new IllegalStateException("事件存储状态不一致");
    }
}
