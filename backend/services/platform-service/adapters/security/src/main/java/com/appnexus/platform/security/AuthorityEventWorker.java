package com.appnexus.platform.security;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.context.annotation.Configuration;

/** 权限失效事件基础 Worker；当前鉴权逐次查权威库，消费记录用于可靠传播依据，不伪装缓存已实现。 */
@Configuration(proxyBeanMethods=false)
@EnableScheduling
public class AuthorityEventWorker {
    private final ReliableEvents events;
    private final boolean enabled;
    private final AtomicBoolean running=new AtomicBoolean();
    /**
     * @param events 共享租约及幂等端口
     * @param enabled 默认 true；关闭后事件保留待消费，不影响权威同步撤权
     */
    public AuthorityEventWorker(ReliableEvents events,@Value("${app.events.worker.enabled:true}") boolean enabled){this.events=events;this.enabled=enabled;}
    /** 每 5 秒最多处理 20 条，租户事件必须保持数据库返回的边界；实例崩溃依赖 30 秒租约恢复。 */
    @Scheduled(fixedDelayString="${app.events.worker.delay:5000}")
    public void process(){
        if(!enabled||!running.compareAndSet(false,true))return;
        try{
            for(int index=0;index<20;index++){
                var claimed=events.claim();if(claimed.isEmpty())break;var event=claimed.get();
                try{events.consume(event,"authority-invalidation",event.tenant(),()->{
                    // 权限版本已在业务事务推进；当前无缓存，消费不能再次扩大或恢复授权。
                    org.slf4j.LoggerFactory.getLogger(getClass()).info("authority invalidation consumed event={} tenant={} type={}",event.id(),event.tenant(),event.type());
                });}catch(SecurityException permanent){events.fail(event,true);}
                catch(RuntimeException transientFailure){events.fail(event,false);}
            }
        }finally{running.set(false);}
    }
}
