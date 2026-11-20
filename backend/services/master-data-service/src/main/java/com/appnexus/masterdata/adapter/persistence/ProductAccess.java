package com.appnexus.masterdata.adapter.persistence;

import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;

/** 商品唯一数据权限执行适配器；计划、组织集合和主体事实均来自平台公共模型，不查询角色或权限私表。 */
@org.springframework.stereotype.Component
public final class ProductAccess implements DataAccessDeclaration.Executor, com.appnexus.masterdata.application.product.ProductAuthorization {
    private static final String RESOURCE="masterdata.product";
    private final AccessPlanning planning;
    private final RowAccessControl rows;
    private final DataAccessReferences references;
    private final ProductAccessMapper mapper;
    /** @param planning 本次权威权限计划 @param rows 公共范围执行能力 @param references 本租户启用组织公开端口 @param mapper 固定商品父行查询及锁映射 */
    public ProductAccess(AccessPlanning planning,RowAccessControl rows,DataAccessReferences references,ProductAccessMapper mapper){this.planning=planning;this.rows=rows;this.references=references;this.mapper=mapper;}
    @Override public Set<String> enforcedResources(){return Set.of(RESOURCE);}
    /** @param c 服务端可信主体 @param action 原稳定权限动作 @return 当前操作的权威计划；缺功能/声明/可信上下文拒绝 */
    public AccessPlan plan(VerifiedTenantContext c,String action){
        try{if(c==null)throw new SecurityException("Context required");return planning.plan(subject(c),RESOURCE+":"+action,RESOURCE);}
        catch(SecurityException error){throw denied();}
    }
    /** @param c 可信主体 @param action 真实操作动作 @return 公共执行器解析结果，不接受客户端计划 */
    public RowAccessControl.OrganizationRange range(VerifiedTenantContext c,String action){return rows.organizationRange(plan(c,action));}
    /**
     * 在读取正文前复核父记录；写入调用方必须包裹原聚合事务，不能先查全记录再在Java过滤。
     * @param c 可信主体
     * @param id 目标商品UUID
     * @param action 当前真实操作动作，不借用read替代修改权
     * @param lock 写用例是否锁定已授权原记录
     */
    public void authorize(VerifiedTenantContext c,String id,String action,boolean lock){
        if(mapper.authorized(tenantBytes(c),id,range(c,action),lock).size()!=1)throw denied();
    }
    /**
     * 复核新建或移动目标；原记录的修改范围由authorize独立验证。
     * @param c 可信主体
     * @param organization 目标组织UUID；修改缺省null保留，新增必须明确提供，空串不可清除
     * @param action create或update，不能混用读取范围
     * @param creating true表示新增，必须有明确归属
     */
    public void target(VerifiedTenantContext c,String organization,String action,boolean creating){
        if(organization==null){if(creating)throw new MasterData.Rejection(400,"PRODUCT_ORGANIZATION_REQUIRED");return;}
        UUID id;try{id=UUID.fromString(organization);if(!id.toString().equals(organization))throw new IllegalArgumentException();}
        catch(IllegalArgumentException error){throw new MasterData.Rejection(400,"PRODUCT_ORGANIZATION_REQUIRED");}
        var plan=plan(c,action);
        if(!rows.allowsOrganization(plan,id)||references.organizations(subject(c),RESOURCE+":"+action).stream().noneMatch(value->value.id().equals(id)))throw denied();
    }
    /** @param c 可信主体 @param action create/update对应的独立准入 @return 本租户启用且在操作范围内的组织候选，不默认选取首项 */
    public List<DataAccessReferences.Organization> organizations(VerifiedTenantContext c,String action){
        var plan=plan(c,action);return references.organizations(subject(c),RESOURCE+":"+action).stream().filter(value->rows.allowsOrganization(plan,value.id())).toList();
    }
    /** @param c 可信主体 @param action 旧商品对账/暂存操作 @throws MasterData.Rejection 无法确定归属的存量维护仅允许明确ALL，不推断组织 */
    public void requireAll(VerifiedTenantContext c,String action){if(!range(c,action).all())throw denied();}
    private static AccessAuthorization.Subject subject(VerifiedTenantContext c){return AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId());}
    private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    private static MasterData.Rejection denied(){return new MasterData.Rejection(403,"MASTER_DATA_DENIED");}
}
