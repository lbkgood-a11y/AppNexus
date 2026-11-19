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
    private final DataAccessCatalog catalog;
    private final FieldProtection fields;
    public TransactionalAccessPlanning(AccessAuthorization authorization,DataScopeAuthorization scopes,DataAccessCatalog catalog,FieldProtection fields){this.authorization=authorization;this.scopes=scopes;this.catalog=catalog;this.fields=fields;}
    /** 只有声明并接通的资源可生成计划；行范围与字段限制在同一权威事务中读取，不缓存故障回退。 */
    @Transactional(readOnly=true)
    public AccessPlan plan(AccessAuthorization.Subject subject,String code,String resource){
        if(!catalog.declared(code,resource)||subject==null||subject.tenantId()==null||subject.memberId()==null||
                code==null||!code.startsWith(resource+":")||!authorization.hasPermission(subject,code))throw new SecurityException("Access plan denied");
        return new AccessPlan(subject,code,resource,scopes.resolveDataScope(subject,code,resource),fields.restrictions(subject,resource),Instant.now());
    }
}
