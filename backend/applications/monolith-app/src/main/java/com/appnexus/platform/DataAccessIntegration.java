package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 装配期验证声明、真实操作及执行器，并将代码目录投影到已有受控元数据表；不产生业务授权。 */
@Configuration(proxyBeanMethods=false)
public class DataAccessIntegration {
    /**
     * 已发布策略引用的字段不可静默移除；新能力只登记目录，不默认授予任何角色。
     * 系统投影无人工账号，审计账号保持 NULL；时间由数据库 UTC 生成。
     */
    @Bean public SmartInitializingSingleton validateDataAccess(DataAccessCatalog catalog,OperationRegistry operations,
            List<DataAccessDeclaration.Executor> executors,JdbcTemplate jdbc,PlatformTransactionManager manager){
        return ()->new TransactionTemplate(manager).executeWithoutResult(status->{
            var enforced=new HashSet<String>();executors.forEach(e->enforced.addAll(e.enforcedResources()));
            for(var scope:PermissionCatalog.ScopeType.values())for(var d:catalog.list(scope)){
                for(var resource:d.resources())if(!enforced.contains(resource.resource()))throw new IllegalStateException("DATA_ACCESS_EXECUTOR_MISSING:"+resource.resource());
                for(var op:d.operations()){
                    if(operations.all().stream().noneMatch(value->value.id().equals(op.operationId())&&value.objectType().equals(d.objectType())&&value.permissions().contains(op.permissionCode())))throw new IllegalStateException("DATA_ACCESS_OPERATION_REFERENCE_INVALID:"+op.operationId());
                    var ids=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM access_permission WHERE code=? AND scope_type=? AND enabled=TRUE",String.class,op.permissionCode(),scope.name());
                    if(ids.size()!=1)throw new IllegalStateException("DATA_ACCESS_PERMISSION_REFERENCE_INVALID:"+op.permissionCode());
                    for(var type:op.scopes())jdbc.update("INSERT IGNORE INTO access_permission_scope_type(permission_id,scope_type,resource_type) VALUES(UUID_TO_BIN(?),?,?)",ids.getFirst(),type.name(),d.resources().getFirst().resource());
                }
                for(var resource:d.resources())for(var row:jdbc.queryForList("SELECT DISTINCT field_code,display_mode FROM access_role_field_security WHERE resource_type=?",resource.resource()))
                    if(resource.fields().stream().noneMatch(f->f.field().equals(row.get("field_code"))&&f.modes().stream().anyMatch(mode->mode.name().equals(row.get("display_mode")))))throw new IllegalStateException("DATA_ACCESS_POLICY_MIGRATION_REQUIRED:"+resource.resource());
            }
        });
    }
}
