package com.appnexus.platform.persistence.organization;
import com.appnexus.platform.organization.*;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.TenantSqlInterceptor;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.*;

/** 组织单元仓储适配器；租户 UUID 只取自请求级服务端上下文。 */
@Repository
public class MyBatisOrganizationUnitRepository implements OrganizationUnitRepository {
    private final OrganizationUnitMapper mapper;
    private final OrganizationAuthorizationMapper grants;
    private final OrganizationStructure structure;
    private final AccessPlanning planning;
    public MyBatisOrganizationUnitRepository(OrganizationUnitMapper mapper,OrganizationAuthorizationMapper grants,OrganizationStructure structure,AccessPlanning planning){this.mapper=mapper;this.grants=grants;this.structure=structure;this.planning=planning;}
    public List<OrganizationUnit> findAll(){
        var context=context();byte[] tenant=tenantBytes(context);var scope=scope(context,"organization.unit:read");
        var rows=switch(scope.type()){
            case ALL -> mapper.findAll(tenant);
            case ORGANIZATION -> mapper.findExact(tenant,ids(scope));
            case ORGANIZATION_SUBTREE -> mapper.findSubtree(tenant,ids(scope));
            default -> java.util.List.<java.util.Map<String,Object>>of();
        };
        return rows.stream()
            .map(r -> new OrganizationUnit(UUID.fromString((String)r.get("id")),(String)r.get("name"))).toList();
    }
    public void create(OrganizationUnit unit){var context=context();var scope=scope(context,"organization.unit:create");if(scope.type()!=DataScopeAuthorization.ScopeType.ALL)throw new SecurityException("Organization create scope required");if(structure.types().stream().noneMatch(type->type.code().equals("BUSINESS")&&type.enabled()))structure.createType("BUSINESS","业务组织");if(mapper.create(tenantBytes(context),unit.id().toString(),unit.name())!=1)throw new IllegalStateException("Default organization type unavailable");}
    public boolean rename(UUID id,String name){var context=context();var scope=scope(context,"organization.unit:update");byte[] tenant=tenantBytes(context);return switch(scope.type()){case ALL->mapper.rename(name,tenant,id.toString())==1;case ORGANIZATION->mapper.renameExact(name,tenant,id.toString(),ids(scope))==1;case ORGANIZATION_SUBTREE->mapper.renameSubtree(name,tenant,id.toString(),ids(scope))==1;default->false;};}
    /** 与成员归属写入共用租户锁；存在归属时先解除，禁止留下悬空关联。 */
    @org.springframework.transaction.annotation.Transactional
    public boolean delete(UUID id){
        var context=context();var scope=scope(context,"organization.unit:delete");byte[] bytes=tenantBytes(context);String tenant=context.tenantId().toString();
        boolean allowed=switch(scope.type()){case ALL->true;case ORGANIZATION,ORGANIZATION_SUBTREE->!ids(scope).isEmpty();default->false;};if(!allowed)return false;
        grants.lockTenant(tenant);
        if(grants.organizationAssigned(tenant,id.toString()))throw new IllegalStateException("Organization has members");
        return switch(scope.type()){case ALL->mapper.delete(bytes,id.toString())==1;case ORGANIZATION->mapper.deleteExact(bytes,id.toString(),ids(scope))==1;case ORGANIZATION_SUBTREE->mapper.deleteSubtree(bytes,id.toString(),ids(scope))==1;default->false;};
    }
    private VerifiedTenantContext context(){
        var attrs=RequestContextHolder.getRequestAttributes();
        if(!(attrs instanceof ServletRequestAttributes servlet) ||
            !(servlet.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new IllegalStateException("Verified tenant context required");
        return context;
    }
    /** 每个 CRUD 操作生成新的权威计划；SQL 只消费该计划行范围，不复用前一次读取权限。 */
    private DataScopeAuthorization.DataScope scope(VerifiedTenantContext context,String code){var subject=AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId());return planning.plan(subject,code,"organization.unit").rows();}
    private java.util.List<String> ids(DataScopeAuthorization.DataScope scope){return scope.organizationIds().stream().map(UUID::toString).sorted().toList();}
    private byte[] tenantBytes(VerifiedTenantContext context){
        return java.nio.ByteBuffer.allocate(16).putLong(context.tenantId().getMostSignificantBits())
            .putLong(context.tenantId().getLeastSignificantBits()).array();
    }
}
