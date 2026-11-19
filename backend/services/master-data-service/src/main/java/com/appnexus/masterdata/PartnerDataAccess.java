package com.appnexus.masterdata;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.appnexus.masterdata.MasterData.*;
import static com.appnexus.masterdata.BusinessPartnerDeclaration.*;

/** 客商统一声明的真实执行器；只使用构建内固定 SQL，原始明细只在已授权父记录的事务内读取。 */
@Component
public final class PartnerDataAccess implements DataAccessDeclaration.Executor, com.appnexus.masterdata.application.PartnerAccess {
    private final AccessPlanning planning;private final FieldProtection protection;private final DataAccessReferences references;private final JdbcTemplate jdbc;private final DataAccessCatalog catalog;
    public PartnerDataAccess(AccessPlanning planning,FieldProtection protection,DataAccessReferences references,JdbcTemplate jdbc,DataAccessCatalog catalog){this.planning=planning;this.protection=protection;this.references=references;this.jdbc=jdbc;this.catalog=catalog;}
    @Override public Set<String> enforcedResources(){return Set.of(ROOT,CONTACT,BANK);}
    private AccessAuthorization.Subject subject(VerifiedTenantContext c){return AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId());}
    /** 每次执行读取权威策略；无功能或无范围默认拒绝，不使用浏览器传入的权限计划。 */
    public AccessPlan plan(VerifiedTenantContext c,String action){try{return planning.plan(subject(c),ROOT+":"+action,ROOT);}catch(SecurityException e){throw denied();}}
    /** 固定组织列谓词；只动态生成绑定占位符数量，COUNT 与分页必须共用返回条件。 */
    public String predicate(AccessPlan plan,List<Object> args){
        if(plan.rows().type()==DataScopeAuthorization.ScopeType.ALL)return "";
        if(plan.rows().type()!=DataScopeAuthorization.ScopeType.ORGANIZATION||plan.rows().organizationIds().isEmpty())return " AND 1=0";
        plan.rows().organizationIds().stream().sorted().forEach(id->args.add(id.toString()));
        return " AND organization_id IN ("+String.join(",",Collections.nCopies(plan.rows().organizationIds().size(),"UUID_TO_BIN(?)"))+")";
    }
    /** 写入先锁定授权范围内父记录；无权与不存在统一冲突，不先泄露客商或明细内容。 */
    public String lock(VerifiedTenantContext c,String id,String action){
        var args=new ArrayList<Object>(List.of(c.tenantId().toString(),id));String condition=predicate(plan(c,action),args);
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(organization_id) organization_id FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)"+condition+" FOR UPDATE",args.toArray());
        if(rows.size()!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");return (String)rows.getFirst().get("organization_id");
    }
    /**
     * 新增和移动归属同时验证目标组织属于可信租户、启用且位于对应操作范围。
     * @param organization 可空只用于历史未知且未移动；新增必须显式指定组织
     */
    public void target(VerifiedTenantContext c,String organization,String action,boolean creating){
        if(organization==null){if(creating)throw new Rejection(400,"PARTNER_ORGANIZATION_REQUIRED");return;}
        if(organization.isBlank())throw new Rejection(400,"PARTNER_ORGANIZATION_REQUIRED");
        UUID id;try{id=UUID.fromString(organization);}catch(IllegalArgumentException e){throw new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
        var plan=plan(c,action);
        if(references.organizations(subject(c),ROOT+":"+action).stream().noneMatch(o->o.id().equals(id))||!(plan.rows().type()==DataScopeAuthorization.ScopeType.ALL||plan.rows().type()==DataScopeAuthorization.ScopeType.ORGANIZATION&&plan.rows().organizationIds().contains(id)))throw denied();
    }
    /** 字段限制与归属候选供页面展示；该元数据不替代保存时的后端检查。 */
    public Map<String,Object> metadata(VerifiedTenantContext c){
        var scope=plan(c,"read").rows();
        var options=references.organizations(subject(c),ROOT+":read").stream().filter(o->scope.type()==DataScopeAuthorization.ScopeType.ALL||scope.type()==DataScopeAuthorization.ScopeType.ORGANIZATION&&scope.organizationIds().contains(o.id())).toList();
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
        if(supplied==null)return null;var existing=id==null?List.<Detail>of():PartnerDetails.read(jdbc,c,id,bank);
        var known=new HashMap<String,Detail>();existing.forEach(row->known.put(row.id(),row));var retained=new HashSet<String>();var result=new ArrayList<DetailInput>();
        for(var row:supplied){if(row==null)throw new Rejection(400,"MASTER_DATA_INPUT_INVALID");var old=row.id()==null?null:known.get(row.id());if(row.id()!=null&&old==null)throw new Rejection(409,"MASTER_DATA_CHANGED");if(old!=null)retained.add(old.id());result.add(new DetailInput(row.id(),merge(row.values(),old==null?Map.of():old.values(),modes)));}
        if(!modes.isEmpty()&&existing.stream().anyMatch(row->!retained.contains(row.id())))throw new Rejection(403,"DATA_FIELD_WRITE_DENIED");
        return List.copyOf(result);
    }
    private static Rejection denied(){return new Rejection(403,"MASTER_DATA_DENIED");}
}
