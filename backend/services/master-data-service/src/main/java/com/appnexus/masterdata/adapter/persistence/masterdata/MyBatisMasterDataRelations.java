package com.appnexus.masterdata.adapter.persistence.masterdata;
import com.appnexus.masterdata.adapter.persistence.masterdata.PartnerDataAccess;
import com.appnexus.masterdata.adapter.persistence.masterdata.TenantGeography;
import com.appnexus.masterdata.adapter.persistence.masterdata.StorageHierarchy;
import com.appnexus.masterdata.adapter.persistence.masterdata.PartnerDetails;
import com.appnexus.masterdata.adapter.persistence.masterdata.MyBatisGeographyCatalog;
import com.appnexus.masterdata.adapter.persistence.StorageHierarchyMapper;
import com.appnexus.masterdata.adapter.persistence.PartnerDetailMapper;
import com.appnexus.masterdata.adapter.persistence.MasterReadMapper;
import com.appnexus.masterdata.adapter.persistence.GeographyMapper;

import com.appnexus.masterdata.application.MasterDataRelations;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;

import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/**
 * 主数据关系与查询 MyBatis 适配器；保留在原内部协作者包，避免为迁包扩大地理和明细的公开能力。
 * 所有 SQL 固定白名单并绑定租户，调用方已授权；本类不提供独立 HTTP 入口。
 */
public final class MyBatisMasterDataRelations implements MasterDataRelations {
  private final MasterReadMapper mapper;private final GeographyMapper geographyMapper;private final StorageHierarchyMapper storageMapper;private final PartnerDetailMapper detailsMapper;
  private final BusinessValueCatalog dictionaries;
  private final PartnerDataAccess partner;
  private static final String WAREHOUSE_RESOURCE=Kind.WAREHOUSE.resource();
  private final StandardCrudScope warehouse;
  private final StandardCrudScope warehouseJoin;
  private final ScopedWriteGate writes;

  /** 装配原主数据读写关系；仓库及其下级仅以固定列映射接入平台范围服务。 */
  public MyBatisMasterDataRelations(MasterReadMapper mapper,GeographyMapper geographyMapper,
      StorageHierarchyMapper storageMapper,PartnerDetailMapper detailsMapper,BusinessValueCatalog dictionaries,
      PartnerDataAccess partner,AccessPlanning planning,RowAccessControl rowAccess,DataAccessReferences references,
      ScopedWriteGate writes){
    this.mapper=mapper;this.geographyMapper=geographyMapper;this.storageMapper=storageMapper;
    this.detailsMapper=detailsMapper;this.dictionaries=dictionaries;this.partner=partner;this.writes=writes;
    this.warehouse=new StandardCrudScope(planning,rowAccess,references,warehouseMapping(""));
    this.warehouseJoin=new StandardCrudScope(planning,rowAccess,references,warehouseMapping("w."));
  }

  static RowAccessControl.Mapping warehouseMapping(String prefix){
    var id=new RowAccessControl.Column(prefix+"id",DataGrantRules.ValueType.UUID);
    var organization=new RowAccessControl.Column(prefix+"organization_id",DataGrantRules.ValueType.UUID);
    return new RowAccessControl.Mapping(WAREHOUSE_RESOURCE,id,organization,Map.of(
        "organizationId",organization,
        "warehouseType",new RowAccessControl.Column(prefix+"warehouse_type",DataGrantRules.ValueType.TEXT),
        "operatingMode",new RowAccessControl.Column(prefix+"operating_mode",DataGrantRules.ValueType.TEXT)));
  }
  private PartnerDataAccess partner(){if(partner==null)throw new Rejection(403,"MASTER_DATA_DENIED");return partner;}
  @Override public Object geographyCatalog(VerifiedTenantContext c){return MyBatisGeographyCatalog.nodes(geographyMapper,c.tenantId());}
  @Override public Object geographyTree(VerifiedTenantContext c,Kind kind,String parent,int page,int size){return new TenantGeography(geographyMapper,c.tenantId(),false).children(parent,page,size,kind==Kind.COUNTRY_REGION);}
  @Override public void prepareStorage(VerifiedTenantContext c,Kind kind,String id,Map<String,String> values,boolean creating){
    if(creating&&(kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION)){
      var roots=storageMapper.rootForParent(c.tenantId().toString(),kind.name(),values.get("parentCode"));
      if(roots.size()!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");
      var parentPlan=lockStorage(c,Kind.WAREHOUSE,roots.getFirst(),"update");
      StorageHierarchy.prepare(storageMapper,c,kind,null,values);
      writes.authorizeChild(parentPlan,kind.resource(),"create",UUID.fromString(id),List.of(values.get("parentCode")));
      return;
    }
    StorageHierarchy.prepare(storageMapper,c,kind,creating?null:id,values);
  }
  @Override public void requireStorageWrite(VerifiedTenantContext c,Kind kind,String id,String action){
    if(!storage(kind))return;
    var plan=lockStorage(c,kind,id,kind==Kind.WAREHOUSE?action:"update");
    UUID record=UUID.fromString(id);
    if(kind==Kind.WAREHOUSE)writes.authorize(plan,record);
    else writes.authorizeChild(plan,kind.resource(),action,record,List.of());
  }

  /** 未取得仓库或下级档案的授权行锁统一拒绝；版本冲突由后续业务写入判断。 */
  private AccessPlan lockStorage(VerifiedTenantContext c,Kind kind,String id,String action){
    var plan=warehousePlan(c,action);
    var query=new MasterReadMapper.Query(c.tenantId().toString(),kind,"",null,id,null,List.of(),null,warehousePredicate(plan,kind),1,0);
    if(mapper.lock(query).size()!=1)throw denied();
    return plan;
  }
  /** 库区库位沿用根仓库的读或维护范围，同时由应用层独立检查自身功能权限。 */
  private AccessPlan warehousePlan(VerifiedTenantContext c,String action){
    var subject=new AccessAuthorization.Subject(c.accountId(),c.tenantId(),c.memberId(),c.personId(),c.organizationId());
    try{return warehouse.plan(subject,action);}
    catch(SecurityException failure){throw denied();}
  }

  private RowAccessControl.Predicate warehousePredicate(AccessPlan plan,Kind kind){
    return (kind==Kind.WAREHOUSE?warehouse:warehouseJoin).predicate(plan);
  }

  /** 新建许可绑定确切目标，编辑已有记录还必须先通过原记录锁。缺省编辑保留归属。 */
  @Override public void warehouseTarget(VerifiedTenantContext c,String id,String organization,boolean creating){
    if(!creating&&organization==null)return;
    var plan=warehousePlan(c,creating?"create":"update");
    UUID record=UUID.fromString(id);
    if(organization==null){
      // 兼容既有显式 ALL 策略创建无归属仓库，其他范围必须提供可验证的目标。
      if(plan.rows().type()!=DataScopeAuthorization.ScopeType.ALL)throw denied();
      writes.authorize(plan,record);
      return;
    }
    uuid(organization);
    UUID target=UUID.fromString(organization);
    if(!warehouse.allowsTarget(plan,creating?null:record,target))throw denied();
    writes.authorize(plan,record,target);
  }

  @Override public Object warehouseOrganizations(VerifiedTenantContext c,String action){
    return warehouse.organizations(warehousePlan(c,action),null);
  }
  private static boolean storage(Kind kind){return kind==Kind.WAREHOUSE||kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION;}
  private static Rejection denied(){return new Rejection(403,"MASTER_DATA_DENIED");}
  @Override public void storageStatus(VerifiedTenantContext c,Kind kind,String id,boolean enabled){StorageHierarchy.status(storageMapper,c,kind,id,enabled);}
  @Override public void savePartnerDetails(VerifiedTenantContext c,String id,Input input){PartnerDetails.save(detailsMapper,c,id,input,dictionaries);}
  @Override public Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size,String id,String scope){
    if(id!=null)uuid(id);
    if(page<1||page>1000000||size<1||size>100||keyword==null||keyword.length()>200)throw invalid();
    var geography=kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION?new TenantGeography(geographyMapper,c.tenantId(),false):null;
    String parent=null;if((kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION)&&scope!=null&&!scope.isBlank()){if(!scope.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();parent=scope;}
    var aliases=geography!=null&&!keyword.isBlank()?geography.aliases(keyword.trim().toLowerCase(Locale.ROOT),kind==Kind.SUBDIVISION):List.<String>of();
    var codes=geography!=null&&scope!=null&&!scope.isEmpty()?geography.codes(scope,kind==Kind.SUBDIVISION):null;
    var query=new MasterReadMapper.Query(c.tenantId().toString(),kind,keyword.trim(),enabled,id,parent,aliases,codes,kind==Kind.PARTNER?partner().compiled(partner().plan(c,"read")):storage(kind)?warehousePredicate(warehousePlan(c,"read"),kind):null,size,(page-1)*size);
    var fieldModes=kind==Kind.PARTNER?partner().fieldModes(c):Map.<String,Map<String,com.appnexus.platform.authorization.FieldSecurity.Mode>>of();
    long total=mapper.count(query);
    var listFields=kind==Kind.PARTNER&&id==null?List.of("partnerType","creditCode","contactName","phone","address"):kind.fields();
    var rows=mapper.list(query).stream().map(raw->{
      var attributes=new LinkedHashMap<String,String>();for(String field:listFields)attributes.put(field,(String)raw.get(kind.column(field)));
      String code=(String)raw.get("code");if(geography!=null){attributes.put("parentCode",geography.parent(code));attributes.put("displayName",geography.name(code));attributes.put("hierarchyCode",geography.path(code));attributes.put("parentHierarchyCode",geography.path(geography.parent(code)));attributes.put("parentName",geography.name(geography.parent(code)));}
      Row row=new Row((String)raw.get("id"),code,(String)raw.get("name"),attributes,Boolean.TRUE.equals(raw.get("enabled")),((Number)raw.get("version")).longValue(),new Audit((String)raw.get("audit_created_by"),utc(raw.get("created_at")),(String)raw.get("audit_updated_by"),utc(raw.get("updated_at"))));
      if(kind==Kind.WAREHOUSE)return new Row(row.id(),row.code(),row.name(),row.attributes(),row.enabled(),row.version(),row.audit(),
          row.contacts(),row.bankAccounts(),(String)raw.get("data_organization_id"),Map.of());
      if(kind!=Kind.PARTNER)return row;
      if(id!=null)row=new Row(row.id(),row.code(),row.name(),row.attributes(),row.enabled(),row.version(),row.audit(),PartnerDetails.read(detailsMapper,c,row.id(),false,query.access()),PartnerDetails.read(detailsMapper,c,row.id(),true,query.access()));
      return partner().protect(c,row,(String)raw.get("data_organization_id"),fieldModes);
    }).toList();
    return new Page(rows,total);
  }

  /** 写入事务内先锁定当前租户地理行；父链校验失败时整笔业务和审计均回滚。 */
  @Override public void prepareGeography(VerifiedTenantContext c,Kind kind,Input input,Map<String,String> values){
    if(kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION)new TenantGeography(geographyMapper,c.tenantId(),true).put(kind,input.code().trim(),input.name().trim(),values);
  }

  /** 跨国移动同步全部已建后代国别；同事务递增版本和审计，旧编辑草稿不得覆盖移动结果。 */
  @Override public void synchronizeGeography(VerifiedTenantContext c,Kind kind){
    if(kind!=Kind.COUNTRY_REGION&&kind!=Kind.SUBDIVISION)return;
    var graph=new TenantGeography(geographyMapper,c.tenantId(),false);
    var nodes=geographyMapper.subdivisions(c.tenantId().toString());
    for(var node:nodes){String code=(String)node.get("code"),country=graph.country(code);if(!country.equals(node.get("country_code")))
      geographyMapper.country(c.tenantId().toString(),code,country,c.accountId().toString());}
  }

  private static void uuid(String id){try{if(!UUID.fromString(id).toString().equalsIgnoreCase(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
 private static String utc(Object value){return value==null?null:((java.sql.Timestamp)value).toInstant().toString();}
}
