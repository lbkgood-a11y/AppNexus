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
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;

import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/**
 * 主数据关系与查询 JDBC 适配器；保留在原内部协作者包，避免为迁包扩大地理和明细的公开能力。
 * 所有 SQL 固定白名单并绑定租户，调用方已授权；本类不提供独立 HTTP 入口。
 */
public final class MyBatisMasterDataRelations implements MasterDataRelations {
  private final MasterReadMapper mapper;private final GeographyMapper geographyMapper;private final StorageHierarchyMapper storageMapper;private final PartnerDetailMapper detailsMapper;
  private final BusinessValueCatalog dictionaries;
  private final PartnerDataAccess partner;
  /** @param jdbc 同事务 UTC 数据源 @param dictionaries 公开字典引用端口 @param partner 客商真实数据权限适配器，缺省拒绝客商读取 */
  public MyBatisMasterDataRelations(MasterReadMapper mapper,GeographyMapper geographyMapper,StorageHierarchyMapper storageMapper,PartnerDetailMapper detailsMapper,BusinessValueCatalog dictionaries,PartnerDataAccess partner){this.mapper=mapper;this.geographyMapper=geographyMapper;this.storageMapper=storageMapper;this.detailsMapper=detailsMapper;this.dictionaries=dictionaries;this.partner=partner;}
  private PartnerDataAccess partner(){if(partner==null)throw new Rejection(403,"MASTER_DATA_DENIED");return partner;}
  @Override public Object geographyCatalog(VerifiedTenantContext c){return new MyBatisGeographyCatalog(geographyMapper).nodes(c.tenantId());}
  @Override public Object geographyTree(VerifiedTenantContext c,Kind kind,String parent,int page,int size){return new TenantGeography(geographyMapper,c.tenantId(),false).children(parent,page,size,kind==Kind.COUNTRY_REGION);}
  @Override public void prepareStorage(VerifiedTenantContext c,Kind kind,String id,Map<String,String> values){StorageHierarchy.prepare(storageMapper,c,kind,id,values);}
  @Override public void storageStatus(VerifiedTenantContext c,Kind kind,String id,boolean enabled){StorageHierarchy.status(storageMapper,c,kind,id,enabled);}
  @Override public void savePartnerDetails(VerifiedTenantContext c,String id,Input input){PartnerDetails.save(detailsMapper,c,id,input,dictionaries);}
  @Override public Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size,String id,String scope){
    if(id!=null)uuid(id);
    if(page<1||page>1000000||size<1||size>100||keyword==null||keyword.length()>200)throw invalid();
    var geography=kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION?new TenantGeography(geographyMapper,c.tenantId(),false):null;
    String parent=null;if((kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION)&&scope!=null&&!scope.isBlank()){if(!scope.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();parent=scope;}
    var aliases=geography!=null&&!keyword.isBlank()?geography.aliases(keyword.trim().toLowerCase(Locale.ROOT),kind==Kind.SUBDIVISION):List.<String>of();
    var codes=geography!=null&&scope!=null&&!scope.isEmpty()?geography.codes(scope,kind==Kind.SUBDIVISION):null;
    var query=new MasterReadMapper.Query(c.tenantId().toString(),kind,keyword.trim(),enabled,id,parent,aliases,codes,kind==Kind.PARTNER?partner().compiled(partner().plan(c,"read")):null,size,(page-1)*size);
    var fieldModes=kind==Kind.PARTNER?partner().fieldModes(c):Map.<String,Map<String,com.appnexus.platform.authorization.FieldSecurity.Mode>>of();
    long total=mapper.count(query);
    var listFields=kind==Kind.PARTNER&&id==null?List.of("partnerType","creditCode","contactName","phone","address"):kind.fields();
    var rows=mapper.list(query).stream().map(raw->{
      var attributes=new LinkedHashMap<String,String>();for(String field:listFields)attributes.put(field,(String)raw.get(kind.column(field)));
      String code=(String)raw.get("code");if(geography!=null){attributes.put("parentCode",geography.parent(code));attributes.put("displayName",geography.name(code));attributes.put("hierarchyCode",geography.path(code));attributes.put("parentHierarchyCode",geography.path(geography.parent(code)));attributes.put("parentName",geography.name(geography.parent(code)));}
      Row row=new Row((String)raw.get("id"),code,(String)raw.get("name"),attributes,Boolean.TRUE.equals(raw.get("enabled")),((Number)raw.get("version")).longValue(),new Audit((String)raw.get("audit_created_by"),utc(raw.get("created_at")),(String)raw.get("audit_updated_by"),utc(raw.get("updated_at"))));
      if(kind!=Kind.PARTNER)return row;
      if(id!=null)row=new Row(row.id(),row.code(),row.name(),row.attributes(),row.enabled(),row.version(),row.audit(),PartnerDetails.read(detailsMapper,c,row.id(),false),PartnerDetails.read(detailsMapper,c,row.id(),true));
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
