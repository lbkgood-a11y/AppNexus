package com.appnexus.platform.security;

import com.appnexus.platform.tenant.TaskManagement;
import com.appnexus.contracts.data.TransactionalBusinessEvents;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.context.annotation.Configuration;

/** 共享租约的 Outbox 调度器，权限事件与业务事件按类型独立分派。 */
@Configuration(proxyBeanMethods=false)
@EnableScheduling
public class AuthorityEventWorker {
    private final ReliableEvents events;
    private final com.appnexus.contracts.businessobject.BusinessObjects.ScheduledOperations operations;
    private final boolean enabled;
    private final Map<TransactionalBusinessEvents.Type, Map<String,TransactionalBusinessEvents.Consumer>> consumers;
    private final AtomicBoolean running=new AtomicBoolean();
    /**
     * @param events 共享租约及幂等端口
     * @param operations 统一业务对象的定时系统操作准入
     * @param enabled 默认 true；关闭后事件保留待消费，不影响权威同步撤权
     */
    public AuthorityEventWorker(ReliableEvents events,com.appnexus.contracts.businessobject.BusinessObjects.ScheduledOperations operations,
            boolean enabled){
        this(events,operations,enabled,List.of());
    }

    /**
     * @param events 共享事务 Outbox
     * @param operations 固定调度入口的统一权限
     * @param enabled 是否启用调度，默认启用
     * @param handlers 启动期配置的固定类型消费者，同类型内消费者标识必须唯一
     */
    @Autowired
    public AuthorityEventWorker(ReliableEvents events,com.appnexus.contracts.businessobject.BusinessObjects.ScheduledOperations operations,
            @Value("${app.events.worker.enabled:true}") boolean enabled,List<TransactionalBusinessEvents.Consumer> handlers){
        this.events=events;
        this.operations=operations;
        this.enabled=enabled;
        consumers=ReliableEvents.registrations(handlers);
    }
    /** 每 5 秒最多处理 20 条，租户事件必须保持数据库返回的边界；实例崩溃依赖 30 秒租约恢复。 */
    @Scheduled(fixedDelayString="${app.events.worker.delay:5000}")
    public void process(){
        if(!enabled||!running.compareAndSet(false,true))return;
        try{
            // 在领取或重置任何记录前，复核调度入口和统一声明的绑定。
            operations.require(TaskManagement.PROCESS_EVENTS,TaskManagement.EVENT_WORKER_AUTHORITY);
            for(int index=0;index<20;index++){
                var claimed=events.claim();if(claimed.isEmpty())break;var event=claimed.get();
                try{dispatch(event);}catch(SecurityException|IllegalArgumentException permanent){events.fail(event,true);}
                catch(RuntimeException transientFailure){events.fail(event,false);}
            }
        }finally{running.set(false);}
    }

    private void dispatch(ReliableEvents.Event event) {
        if (ReliableEvents.AUTHORITY_CONSUMER.equals(event.consumer())
                && ("AUTHORITY_INVALIDATED".equals(event.type()) || "INTEGRATION_AUTHORITY_CHANGED".equals(event.type()))) {
            events.consume(event,"authority-invalidation",event.tenant(),()->{
                // 权限版本已在业务事务推进，消费不能再次扩大或恢复授权。
                org.slf4j.LoggerFactory.getLogger(getClass()).info("authority invalidation consumed event={} tenant={} type={}",event.id(),event.tenant(),event.type());
            });
            return;
        }
        var type=TransactionalBusinessEvents.Type.valueOf(event.type());
        var handler=consumers.getOrDefault(type,Map.of()).get(event.consumer());
        if(handler==null || ReliableEvents.PLATFORM.equals(event.tenant())) {
            throw new SecurityException("业务事件缺少可信租户或固定消费者");
        }
        var delivery=new TransactionalBusinessEvents.Delivery(event.id(),event.tenant(),type,UUID.fromString(event.aggregate()));
        events.consume(event,handler.consumerId(),event.tenant(),()->handler.consume(delivery));
    }
}
