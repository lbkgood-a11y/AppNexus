package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前不使用权限缓存；在单个权威读取事务中解析决策，数据库故障不能回退旧计划放行。 */
@Service
public class TransactionalAccessPlanning implements AccessPlanning {
    private final AccessAuthorization authorization;
    private final DataScopeAuthorization scopes;
    public TransactionalAccessPlanning(AccessAuthorization authorization,DataScopeAuthorization scopes){this.authorization=authorization;this.scopes=scopes;}
    /** 组织资源当前没有已声明敏感字段，因此字段限制为空；不能宣称全资源字段操作权限已接通。 */
    @Transactional(readOnly=true)
    public AccessPlan plan(AccessAuthorization.Subject subject,String code,String resource){
        if(!"organization.unit".equals(resource)||subject==null||subject.tenantId()==null||subject.memberId()==null||
                code==null||!code.startsWith(resource+":")||!authorization.hasPermission(subject,code))throw new SecurityException("Access plan denied");
        return new AccessPlan(subject,code,resource,scopes.resolveDataScope(subject,code,resource),Map.of(),Instant.now());
    }
}
