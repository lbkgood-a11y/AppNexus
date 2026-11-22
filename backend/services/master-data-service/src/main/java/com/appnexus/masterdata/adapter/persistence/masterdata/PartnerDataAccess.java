package com.appnexus.masterdata.adapter.persistence.masterdata;
import com.appnexus.masterdata.adapter.persistence.masterdata.PartnerDetails;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.adapter.persistence.MasterReadMapper;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;

import org.springframework.stereotype.Component;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;
import static com.appnexus.masterdata.adapter.integration.partner.BusinessPartnerDeclaration.*;

/** 客商统一声明的真实执行器；只使用构建内固定 SQL，原始明细只在已授权父记录的事务内读取。 */
@Component
public final class PartnerDataAccess implements DataAccessDeclaration.Executor, com.appnexus.masterdata.application.PartnerAccess {
    /** 固定业务列映射；条件目录不接收 SQL，全部组合算法由平台公共执行层负责。 */
    private static final RowAccessControl.Mapping MAPPING=new RowAccessControl.Mapping(ROOT,
        new RowAccessControl.Column("id",DataGrantRules.ValueType.UUID),new RowAccessControl.Column("organization_id",DataGrantRules.ValueType.UUID),
        Map.of("code",new RowAccessControl.Column("code",DataGrantRules.ValueType.TEXT),"name",new RowAccessControl.Column("name",DataGrantRules.ValueType.TEXT),
            "organizationId",new RowAccessControl.Column("organization_id",DataGrantRules.ValueType.UUID),
            "partnerType",new RowAccessControl.Column("partner_type",DataGrantRules.ValueType.TEXT),
            "createdBy",new RowAccessControl.Column("created_by",DataGrantRules.ValueType.UUID)));
    private final com.appnexus.masterdata.adapter.persistence.PartnerDetailMapper detailMapper;private final StandardCrudScope scope;private final ScopedWriteGate writes;private final FieldProtection protection;private final DataAccessReferences references;private final MasterReadMapper mapper;private final DataAccessCatalog catalog;private final RowAccessControl rows;
    /**
     * @param planning 当前主体、操作及资源的权威权限计划入口
     * @param protection 字段展示与写入的强制限制入口
     * @param references 可信租户内组织等授权引用目录
     * @param mapper 与客商用例事务共用的数据库访问器
     * @param catalog 统一业务对象派生的数据权限目录
     * @param rows 平台公共行范围执行能力；必须由装配注入，不能缺省绕过数据范围
     */
    public PartnerDataAccess(AccessPlanning planning,FieldProtection protection,DataAccessReferences references,MasterReadMapper mapper,DataAccessCatalog catalog,RowAccessControl rows,com.appnexus.masterdata.adapter.persistence.PartnerDetailMapper detailMapper,ScopedWriteGate writes){this.detailMapper=detailMapper;this.scope=new StandardCrudScope(planning,rows,references,MAPPING);this.writes=writes;this.protection=protection;this.references=references;this.mapper=mapper;this.catalog=catalog;this.rows=Objects.requireNonNull(rows);}
    @Override public Set<String> enforcedResources(){return Set.of(ROOT,CONTACT,BANK);}
    private AccessAuthorization.Subject subject(VerifiedTenantContext c){return new AccessAuthorization.Subject(c.accountId(),c.tenantId(),c.memberId(),c.personId(),c.organizationId());}
    /** 每次执行读取权威策略；无功能或无范围默认拒绝，不使用浏览器传入的权限计划。 */
    public AccessPlan plan(VerifiedTenantContext c,String action){try{return scope.plan(subject(c),action);}catch(SecurityException e){throw denied();}}
    /** 通过公共执行器编译固定业务映射，COUNT、分页、详情及原写入锁定共用条件。 */
    public String predicate(AccessPlan plan,List<Object> args){
        var predicate=rows.predicate(plan,MAPPING);
        args.addAll(predicate.parameters());return predicate.sql();
    }
    /** 只返回由统一执行器产生的固定映射谓词，供MyBatisProvider绑定参数；不接受客户端SQL。 */
    public RowAccessControl.Predicate compiled(AccessPlan plan){return scope.predicate(plan);}
    /** 写入先锁定授权范围内的父记录；组合范围外或缺少范围返回 403，不额外探查目标是否存在。 */
    public String lock(VerifiedTenantContext c,String id,String action){
        var plan=plan(c,action);
        var query=new MasterReadMapper.Query(c.tenantId().toString(),MasterData.Kind.PARTNER,"",null,id,null,List.of(),null,compiled(plan),1,0);
        var rows=mapper.lock(query);
        if(rows.size()!=1){
            if(plan.rows().type()==DataScopeAuthorization.ScopeType.RULES||plan.rows().type()==DataScopeAuthorization.ScopeType.DENY)
                throw denied();
            throw new Rejection(409,"MASTER_DATA_CHANGED");
        }
        writes.authorize(plan,UUID.fromString(id));
        return (String)rows.getFirst().get("organization_id");
    }
    /**
     * 新增和移动归属同时验证目标组织属于可信租户、启用且位于对应操作范围。
     * @param partnerId 已锁定或服务器新分配的客商 UUID
     * @param organization 可空只用于历史未知且未移动；新增必须显式指定组织
     */
    public void target(VerifiedTenantContext c,String partnerId,String organization,String action,boolean creating){
        if(organization==null){if(creating)throw new Rejection(400,"PARTNER_ORGANIZATION_REQUIRED");return;}
        if(organization.isBlank())throw new Rejection(400,"PARTNER_ORGANIZATION_REQUIRED");
        UUID id;try{id=UUID.fromString(organization);}catch(IllegalArgumentException e){throw new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
        var plan=plan(c,action);
        if(!scope.allowsTarget(plan,creating?null:UUID.fromString(partnerId),id))throw denied();
        writes.authorize(plan,UUID.fromString(partnerId),id);
    }
    /** 字段限制与归属候选供页面展示；该元数据不替代保存时的后端检查。 */
    public Map<String,Object> metadata(VerifiedTenantContext c,String action){
        var scope=plan(c,action);
        // 查询规则可能含非组织维度，保留原只读候选。写入仅公开可用组织，提交时另按客商 ID 校验。
        var options=references.organizations(subject(c),ROOT+":"+action);
        if("read".equals(action))options=options.stream().filter(o->rows.allowsOrganization(scope,o.id())).toList();
        else options=this.scope.organizations(scope,null);
        return Map.of("organizations",options,"fieldRestrictions",restrictions(c));
    }
    private Map<String,Map<String,FieldSecurity.Mode>> restrictions(VerifiedTenantContext c){var result=new LinkedHashMap<String,Map<String,FieldSecurity.Mode>>();for(String resource:enforcedResources())result.put(resource,protection.restrictions(subject(c),resource));return Map.copyOf(result);}
    /** 统一声明决定算法，隐藏字段从属性映射移除；原文不进入响应和日志。 */
    private Map<String,String> project(String resource,Map<String,String> values,Map<String,FieldSecurity.Mode> modes){
        var result=new LinkedHashMap<>(values);
        for(var descriptor:catalog.fields(PermissionCatalog.ScopeType.TENANT))if(descriptor.resource().equals(resource)&&modes.containsKey(descriptor.field())){
            if(modes.get(descriptor.field())==FieldSecurity.Mode.HIDDEN)result.remove(descriptor.field());
            else result.put(descriptor.field(),FieldSecurity.display(descriptor.kind(),result.get(descriptor.field()),modes.get(descriptor.field())));
        }
        return Collections.unmodifiableMap(result);
    }
    /** 主档与子资源共享行权限，字段分别使用自身资源目录；不修改原 DTO。 */
    public Row protect(VerifiedTenantContext c,Row row,String organization,Map<String,Map<String,FieldSecurity.Mode>> modes){
        return new Row(row.id(),row.code(),row.name(),project(ROOT,row.attributes(),modes.get(ROOT)),row.enabled(),row.version(),row.audit(),
            row.contacts().stream().map(d->new Detail(d.id(),project(CONTACT,d.values(),modes.get(CONTACT)),d.audit())).toList(),
            row.bankAccounts().stream().map(d->new Detail(d.id(),project(BANK,d.values(),modes.get(BANK)),d.audit())).toList(),organization,modes);
    }
    /** 同次查询只读取一次字段限制，避免每条数据单独执行权限查询。 */
    public Map<String,Map<String,FieldSecurity.Mode>> fieldModes(VerifiedTenantContext c){return restrictions(c);}
    /**
     * 受限字段缺省保留，主动提交变化一律拒绝；脱敏值不作原文比对后写回。
     * @param previous 已锁定原记录的原始属性；新建时为空
     */
    public Input prepare(VerifiedTenantContext c,String id,Input input,Map<String,String> previous){
        var modes=restrictions(c);var attrs=merge(input.attributes(),previous,modes.get(ROOT));
        return new Input(input.code(),input.name(),attrs,input.version(),details(c,id,input.contacts(),false,modes.get(CONTACT)),details(c,id,input.bankAccounts(),true,modes.get(BANK)),input.organizationId());
    }
    private Map<String,String> merge(Map<String,String> supplied,Map<String,String> previous,Map<String,FieldSecurity.Mode> modes){
        var result=new LinkedHashMap<>(previous);if(supplied!=null)result.putAll(supplied);
        for(String field:modes.keySet()){
            if(supplied!=null&&supplied.containsKey(field)&&!Objects.equals(supplied.get(field),previous.get(field)))throw new Rejection(403,"DATA_FIELD_WRITE_DENIED");
            if(previous.containsKey(field))result.put(field,previous.get(field));else result.remove(field);
        }
        return result;
    }
    /** 限制字段所在明细禁止通过整行删除绕过写保护；null 集合明确保留全部明细。 */
    private List<DetailInput> details(VerifiedTenantContext c,String id,List<DetailInput> supplied,boolean bank,Map<String,FieldSecurity.Mode> modes){
        if(supplied==null)return null;var existing=id==null?List.<Detail>of():PartnerDetails.read(detailMapper,c,id,bank,null);
        var known=new HashMap<String,Detail>();existing.forEach(row->known.put(row.id(),row));var retained=new HashSet<String>();var result=new ArrayList<DetailInput>();
        for(var row:supplied){if(row==null)throw new Rejection(400,"MASTER_DATA_INPUT_INVALID");var old=row.id()==null?null:known.get(row.id());if(row.id()!=null&&old==null)throw new Rejection(409,"MASTER_DATA_CHANGED");if(old!=null)retained.add(old.id());result.add(new DetailInput(row.id(),merge(row.values(),old==null?Map.of():old.values(),modes)));}
        if(!modes.isEmpty()&&existing.stream().anyMatch(row->!retained.contains(row.id())))throw new Rejection(403,"DATA_FIELD_WRITE_DENIED");
        return List.copyOf(result);
    }
    private static Rejection denied(){return new Rejection(403,"MASTER_DATA_DENIED");}
}
