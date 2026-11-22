package com.appnexus.platform.persistence;

import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.DataScopeAuthorization;
import com.appnexus.platform.authorization.ScopedWriteGate;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 授权写入许可仅随真实数据库事务存在；挂起外部事务时不能用于新的事务。 */
@Component
public final class MyBatisScopedWriteGate implements ScopedWriteGate {
    private record Grant(UUID tenant,UUID id,String permission,Object target) {}

    @Override public void authorize(AccessPlan plan,UUID id,UUID target){
        authorize(plan,id,plan.permissionCode(),target);
    }

    @Override public void authorizeChild(AccessPlan parent,String resource,String action,UUID id,List<String> parentKeys){
        if(resource.equals(parent.resource())||("create".equals(action)&&parentKeys.isEmpty()))throw denied();
        authorize(parent,id,resource+":"+action,List.copyOf(parentKeys));
    }

    @Override public void authorizeOperation(AccessPlan plan){
        if(!plan.tenantWide())throw denied();
        add(plan,new Grant(plan.subject().tenantId(),null,plan.permissionCode(),null));
    }

    @Override public void authorizeCatalogPublication(AccessAuthorization authorization,AccessAuthorization.Subject subject,String permission){
        if(subject.tenantId()==null||subject.memberId()==null||!authorization.hasPermission(subject,permission))throw denied();
        add(new Grant(subject.tenantId(),null,permission,CatalogPublication.INSTANCE));
    }

    @Override public void requireCatalogPublication(UUID tenant,String permission){
        var grants=grants();
        if(grants==null||!grants.contains(new Grant(tenant,null,permission,CatalogPublication.INSTANCE)))throw denied();
    }

    @Override public void authorizeServiceOperation(UUID tenant,String permission,List<String> keys,java.util.function.BooleanSupplier allowed){
        if(tenant==null||keys.isEmpty()||keys.stream().anyMatch(key->key==null||key.isBlank())||!allowed.getAsBoolean())throw denied();
        add(new Grant(tenant,null,permission,new ServiceBinding(keys)));
    }

    @Override public void requireServiceOperation(UUID tenant,String permission,List<String> keys){
        var grants=grants();
        if(grants==null||!grants.contains(new Grant(tenant,null,permission,new ServiceBinding(keys))))throw denied();
    }

    private record ServiceBinding(List<String> keys){
        private ServiceBinding{keys=List.copyOf(keys);}
    }

    private enum CatalogPublication { INSTANCE }

    @Override public void requireOperation(UUID tenant,String[] permissions){
        var grants=grants();
        if(grants==null||grants.stream().noneMatch(grant->tenant.equals(grant.tenant())&&grant.id()==null&&grant.target()==null
            &&Arrays.asList(permissions).contains(grant.permission())))throw denied();
    }

    private void authorize(AccessPlan plan,UUID id,String permission,Object target){
        if(id==null)throw denied();
        add(plan,new Grant(plan.subject().tenantId(),id,permission,target));
    }

    private void add(AccessPlan plan,Grant grant){
        if(plan.rows().type()==DataScopeAuthorization.ScopeType.DENY)throw denied();
        add(grant);
    }

    private void add(Grant grant){
        if(!TransactionSynchronizationManager.isActualTransactionActive()
            ||!TransactionSynchronizationManager.isSynchronizationActive()
            ||TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw denied();
        var grants=grants();
        if(grants==null){
            var current=new HashSet<Grant>();
            TransactionSynchronizationManager.bindResource(this,current);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void suspend(){TransactionSynchronizationManager.unbindResourceIfPossible(MyBatisScopedWriteGate.this);}
                @Override public void resume(){TransactionSynchronizationManager.bindResource(MyBatisScopedWriteGate.this,current);}
                @Override public void afterCompletion(int status){
                    TransactionSynchronizationManager.unbindResourceIfPossible(MyBatisScopedWriteGate.this);
                    current.clear();
                }
            });
            grants=current;
        }
        grants.add(grant);
    }

    @Override public void require(UUID tenant,UUID id,String resource,String[] actions,UUID target,boolean bindTarget){
        requireGrant(tenant,id,resource,actions,target,bindTarget);
    }

    @Override public void requireParentKey(UUID tenant,UUID id,String resource,String[] actions,List<String> parentKeys){
        if(parentKeys.isEmpty()||parentKeys.stream().anyMatch(String::isBlank))throw denied();
        requireGrant(tenant,id,resource,actions,parentKeys,true);
    }

    private void requireGrant(UUID tenant,UUID id,String resource,String[] actions,Object target,boolean bindTarget){
        var grants=grants();
        if(grants==null||grants.stream().noneMatch(grant->tenant.equals(grant.tenant())&&id.equals(grant.id())
            &&(!bindTarget||Objects.equals(grant.target(),target))
            &&Arrays.stream(actions).anyMatch(action->grant.permission().equals(resource+":"+action))))throw denied();
    }

    @SuppressWarnings("unchecked")
    private Set<Grant> grants(){return (Set<Grant>)TransactionSynchronizationManager.getResource(this);}
    private static SecurityException denied(){return new SecurityException("SCOPED_WRITE_PLAN_REQUIRED");}
}
