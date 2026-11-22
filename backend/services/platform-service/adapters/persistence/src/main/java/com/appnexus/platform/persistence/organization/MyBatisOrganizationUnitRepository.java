package com.appnexus.platform.persistence.organization;
import com.appnexus.platform.organization.port.OrganizationUnitRepository;
import com.appnexus.platform.organization.*;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.*;

/** 组织单元仓储适配器；租户 UUID 只取自请求级服务端上下文。 */
@Repository
public class MyBatisOrganizationUnitRepository implements OrganizationUnitRepository, DataAccessDeclaration.Executor {
    /** 本仓储真实消费统一权限计划，覆盖组织单元读写。 */
    @Override public Set<String> enforcedResources(){return Set.of("organization.unit");}
    private final OrganizationUnitMapper mapper;
    private final OrganizationAuthorizationMapper grants;
    private final OrganizationStructure structure;
    private final AccessPlanning planning;
    private final RowAccessControl rowAccess;
    private final ScopedWriteGate writes;
    public MyBatisOrganizationUnitRepository(OrganizationUnitMapper mapper,OrganizationAuthorizationMapper grants,OrganizationStructure structure,AccessPlanning planning,RowAccessControl rowAccess,ScopedWriteGate writes){this.mapper=mapper;this.grants=grants;this.structure=structure;this.planning=planning;this.rowAccess=rowAccess;this.writes=writes;}
    private static final RowAccessControl.Column ID_COLUMN=new RowAccessControl.Column("id",DataGrantRules.ValueType.UUID);
    private static final RowAccessControl.Mapping READ_MAPPING=new RowAccessControl.Mapping("organization.unit",ID_COLUMN,ID_COLUMN,Map.of());
    /** 平面兼容入口与标准查询共用公共 SQL 门禁。 */
    public List<OrganizationUnit> findAll(){
        var context=context();
        byte[] tenant=tenantBytes(context);
        var plan=plan(context,"organization.unit:read");
        // 组织及下级策略已由权威计划服务在当前租户内展开。
        var rows=mapper.findScoped(tenant,compiledRead(plan));
        return rows.stream()
            .map(r -> new OrganizationUnit(UUID.fromString((String)r.get("id")),(String)r.get("name"))).toList();
    }

    /** 组织本身即归属；将公共组织投影送入统一 SQL 编译器，保留原计划作为拦截器凭据。 */
    private RowAccessControl.Predicate compiledRead(AccessPlan plan){
        var range=rowAccess.organizationRange(plan,"organizationId");
        var scope=range.all()?new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ALL,Set.of()):
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ORGANIZATION,
                range.ids().stream().map(UUID::fromString).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        var projected=new AccessPlan(plan.subject(),plan.permissionCode(),plan.resource(),scope,plan.fields(),plan.evaluatedAt());
        var predicate=rowAccess.predicate(projected,READ_MAPPING);
        return new RowAccessControl.Predicate(predicate.sql(),predicate.parameters(),plan);
    }
    /** 兼容平面入口只能创建根，全部范围规则与组织领域入口一致。 */
    @org.springframework.transaction.annotation.Transactional
    public void create(OrganizationUnit unit){
        var context=context();
        var plan=plan(context,"organization.unit:create");
        if(!plan.tenantWide())throw new SecurityException("Organization create scope required");
        if(structure.types().stream().noneMatch(type->type.code().equals("BUSINESS")&&type.enabled()))structure.createType("BUSINESS","业务组织");
        writes.authorize(plan,unit.id());
        if(mapper.create(tenantBytes(context),unit.id().toString(),unit.name())!=1)
            throw new IllegalStateException("Default organization type unavailable");
    }
    /** 修改权限及范围在同一事务解析，SQL 使用可信租户和目标 ID 强制限制。 */
    @org.springframework.transaction.annotation.Transactional
    public boolean rename(UUID id,String name){
        var context=context();byte[] tenant=tenantBytes(context);
        var plan=plan(context,"organization.unit:update");
        var range=rowAccess.organizationRange(plan,"organizationId");
        if(!range.all()&&!range.ids().contains(id.toString()))return false;
        writes.authorize(plan,id);
        return range.all()?mapper.rename(name,tenant,id.toString())==1:
            mapper.renameExact(name,tenant,id.toString(),range.ids())==1;
    }
    /** 与成员归属写入共用租户锁；存在归属时先解除，禁止留下悬空关联。 */
    @org.springframework.transaction.annotation.Transactional
    public boolean delete(UUID id){
        var context=context();byte[] bytes=tenantBytes(context);String tenant=context.tenantId().toString();
        grants.lockTenant(tenant);
        var plan=plan(context,"organization.unit:delete");
        var range=rowAccess.organizationRange(plan,"organizationId");
        // 先校验目标，避免通过归属冲突探测范围外组织。
        if(!range.all()&&!range.ids().contains(id.toString()))return false;
        if(grants.organizationAssigned(tenant,id.toString()))throw new IllegalStateException("Organization has members");
        writes.authorize(plan,id);
        return range.all()?mapper.delete(bytes,id.toString())==1:mapper.deleteExact(bytes,id.toString(),range.ids())==1;
    }
    private VerifiedTenantContext context(){
        var attrs=RequestContextHolder.getRequestAttributes();
        if(!(attrs instanceof ServletRequestAttributes servlet) ||
            !(servlet.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new IllegalStateException("Verified tenant context required");
        return context;
    }
    /** 每个 CRUD 操作消费新计划，组织及下级由计划层统一展开。 */
    private AccessPlan plan(VerifiedTenantContext context,String code){
        var subject=new AccessAuthorization.Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        return planning.plan(subject,code,"organization.unit");
    }
    private byte[] tenantBytes(VerifiedTenantContext context){
        return java.nio.ByteBuffer.allocate(16).putLong(context.tenantId().getMostSignificantBits())
            .putLong(context.tenantId().getLeastSignificantBits()).array();
    }
}
