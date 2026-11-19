package com.appnexus.masterdata;

import com.appnexus.masterdata.application.MasterDataRelations;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.appnexus.masterdata.MasterData.*;

/**
 * 主数据关系与查询 JDBC 适配器；保留在原内部协作者包，避免为迁包扩大地理和明细的公开能力。
 * 所有 SQL 固定白名单并绑定租户，调用方已授权；本类不提供独立 HTTP 入口。
 */
public final class JdbcMasterDataRelations implements MasterDataRelations {
  private final JdbcTemplate jdbc;
  private final BusinessValueCatalog dictionaries;
  private final PartnerDataAccess partner;
  /** @param jdbc 同事务 UTC 数据源 @param dictionaries 公开字典引用端口 @param partner 客商真实数据权限适配器，缺省拒绝客商读取 */
  public JdbcMasterDataRelations(JdbcTemplate jdbc,BusinessValueCatalog dictionaries,PartnerDataAccess partner){this.jdbc=jdbc;this.dictionaries=dictionaries;this.partner=partner;}
  private PartnerDataAccess partner(){if(partner==null)throw new Rejection(403,"MASTER_DATA_DENIED");return partner;}
  @Override public Object geographyCatalog(VerifiedTenantContext c){return new JdbcGeographyCatalog(jdbc).nodes(c.tenantId());}
  @Override public Object geographyTree(VerifiedTenantContext c,Kind kind,String parent,int page,int size){return new TenantGeography(jdbc,c.tenantId(),false).children(parent,page,size,kind==Kind.COUNTRY_REGION);}
  @Override public void prepareStorage(VerifiedTenantContext c,Kind kind,String id,Map<String,String> values){StorageHierarchy.prepare(jdbc,c,kind,id,values);}
  @Override public void storageStatus(VerifiedTenantContext c,Kind kind,String id,boolean enabled){StorageHierarchy.status(jdbc,c,kind,id,enabled);}
  @Override public void savePartnerDetails(VerifiedTenantContext c,String id,Input input){PartnerDetails.save(jdbc,c,id,input,dictionaries);}
  @Override public Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size,String id,String scope){
    if(id!=null)uuid(id);
    if(page<1||page>1000000||size<1||size>100||keyword==null||keyword.length()>200)throw invalid();
    var geography=kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION?new TenantGeography(jdbc,c.tenantId(),false):null;
    String where=" WHERE tenant_id=UUID_TO_BIN(?) AND (LOCATE(?,code)>0 OR LOCATE(?,name)>0)"+(enabled==null?"":" AND enabled=?");
    var args=new ArrayList<Object>(List.of(c.tenantId().toString(),keyword.trim(),keyword.trim()));
    // 跨国检索允许通过所属国别及标准别名定位，参数仍绑定且强制当前租户。
    if(kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION){
      String extra=kind==Kind.COUNTRY_REGION?"alpha3_code":"country_code";
      String extra2=kind==Kind.COUNTRY_REGION?"numeric_code":"parent_code";
      where=where.replace("OR LOCATE(?,name)>0)","OR LOCATE(?,name)>0 OR LOCATE(?,"+extra+")>0 OR LOCATE(?,"+extra2+")>0)");
      args.add(keyword.trim());args.add(keyword.trim());
      // 层级编码只是服务端派生的查询别名；仍以绑定的标准编码限定同一租户资料。
      if(!keyword.isBlank()){
        String query=keyword.trim().toLowerCase(Locale.ROOT);
        var aliases=geography.aliases(query,kind==Kind.SUBDIVISION);
        if(!aliases.isEmpty()){
          where=where.replace("OR LOCATE(?,"+extra2+")>0)","OR LOCATE(?,"+extra2+")>0 OR code IN ("+String.join(",",Collections.nCopies(aliases.size(),"?"))+"))");args.addAll(aliases);
        }
      }
    }
    if(enabled!=null)args.add(enabled);
    if((kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION)&&scope!=null&&!scope.isBlank()){
      if(!scope.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw invalid();
      where+=" AND parent_code=?";args.add(scope);
    }
    if(id!=null){where+=" AND id=UUID_TO_BIN(?)";args.add(id);}
    if((kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION)&&scope!=null&&!scope.isEmpty()){
      var codes=geography.codes(scope,kind==Kind.SUBDIVISION);
      if(codes.isEmpty())return new Page(List.of(),0);
      where+=" AND code IN ("+String.join(",",Collections.nCopies(codes.size(),"?"))+")";args.addAll(codes);
    }
    if(kind==Kind.PARTNER)where+=partner().predicate(partner().plan(c,"read"),args);
    var fieldModes=kind==Kind.PARTNER?partner().fieldModes(c):Map.<String,Map<String,com.appnexus.platform.authorization.FieldSecurity.Mode>>of();
    long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+kind.table()+where,Long.class,args.toArray());
    args.add(size);args.add((page-1)*size);
    // 客商列表仅返回原摘要字段，证件、资本与银行账户等扩展字段只在指定详情 ID 后读取。
    var listFields=kind==Kind.PARTNER&&id==null?List.of("partnerType","creditCode","contactName","phone","address"):kind.fields();
    var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,enabled,version,BIN_TO_UUID(created_by) audit_created_by,created_at,BIN_TO_UUID(updated_by) audit_updated_by,updated_at,"+(kind==Kind.PARTNER?"BIN_TO_UUID(organization_id) data_organization_id,":"")+String.join(",",listFields.stream().map(kind::column).toList())+" FROM "+kind.table()+where+" ORDER BY code,id LIMIT ? OFFSET ?",(r,n)->{
      var attributes=new LinkedHashMap<String,String>();for(String field:listFields)attributes.put(field,r.getString(kind.column(field)));
      if(kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION){
        attributes.put("parentCode",geography.parent(r.getString("code")));
        attributes.put("displayName",geography.name(r.getString("code")));
        attributes.put("hierarchyCode",geography.path(r.getString("code")));
        attributes.put("parentHierarchyCode",geography.path(geography.parent(r.getString("code"))));
        // 上级名称与父级编码从同一租户图解析，编辑回显不依赖左树已加载或当前选中的范围。
        attributes.put("parentName",geography.name(geography.parent(r.getString("code"))));
      }
      Row row=new Row(r.getString("id"),r.getString("code"),r.getString("name"),attributes,r.getBoolean("enabled"),r.getLong("version"),new Audit(r.getString("audit_created_by"),r.getTimestamp("created_at")==null?null:r.getTimestamp("created_at").toInstant().toString(),r.getString("audit_updated_by"),r.getTimestamp("updated_at")==null?null:r.getTimestamp("updated_at").toInstant().toString()));
      if(kind!=Kind.PARTNER)return row;
      if(id!=null)row=new Row(row.id(),row.code(),row.name(),row.attributes(),row.enabled(),row.version(),row.audit(),PartnerDetails.read(jdbc,c,row.id(),false),PartnerDetails.read(jdbc,c,row.id(),true));
      return partner().protect(c,row,r.getString("data_organization_id"),fieldModes);
    },args.toArray());
    return new Page(rows,total);
  }

  /** 写入事务内先锁定当前租户地理行；父链校验失败时整笔业务和审计均回滚。 */
  @Override public void prepareGeography(VerifiedTenantContext c,Kind kind,Input input,Map<String,String> values){
    if(kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION)new TenantGeography(jdbc,c.tenantId(),true).put(kind,input.code().trim(),input.name().trim(),values);
  }

  /** 跨国移动同步全部已建后代国别；同事务递增版本和审计，旧编辑草稿不得覆盖移动结果。 */
  @Override public void synchronizeGeography(VerifiedTenantContext c,Kind kind){
    if(kind!=Kind.COUNTRY_REGION&&kind!=Kind.SUBDIVISION)return;
    var graph=new TenantGeography(jdbc,c.tenantId(),false);
    var nodes=jdbc.queryForList("SELECT code,country_code FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?)",c.tenantId().toString());
    for(var node:nodes){String code=(String)node.get("code"),country=graph.country(code);if(!country.equals(node.get("country_code")))
      jdbc.update("UPDATE md_subdivision SET country_code=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND code=?",country,c.accountId().toString(),c.tenantId().toString(),code);}
  }

  private static void uuid(String id){try{if(!UUID.fromString(id).toString().equalsIgnoreCase(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
