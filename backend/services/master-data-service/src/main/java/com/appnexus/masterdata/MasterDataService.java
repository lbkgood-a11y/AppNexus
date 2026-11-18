package com.appnexus.masterdata;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.appnexus.masterdata.MasterData.*;

/**
 * 租户公共档案执行器；所有 SQL 强制带租户，所有调用重新鉴权，不缓存授权结果。
 * 仅表名和列名取自构建内白名单；业务值全部使用绑定参数，禁止任意 SQL 或动态对象执行。
 */
public final class MasterDataService {
  private final BusinessValueCatalog dictionaries;
  private final JdbcTemplate jdbc;
  private final AccessAuthorization access;
  private final TransactionTemplate transaction;
  /**
   * @param jdbc 宿主数据源
   * @param access 公开授权端口
   * @param dictionaries 字典公开端口；不回退前端硬编码选项
   * @param manager 宿主事务管理器 */
  public MasterDataService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager,BusinessValueCatalog dictionaries) {
    this.dictionaries=dictionaries;this.jdbc=jdbc;this.access=access;this.transaction=new TransactionTemplate(manager);
  }

  /** 只读当前客商或商品字典；由原对象读取权限复核，不授予字典维护能力。 */
  public Object dictionaryOptions(VerifiedTenantContext c,Kind kind){require(c,kind,"read");return dictionaries.options(c.tenantId(),BusinessValueCatalog.PARTNER);}

  /** 仓库读取权限仅允许查询本租户分类，不授予字典维护权限。 */
  public Object warehouseOptions(VerifiedTenantContext c){require(c,Kind.WAREHOUSE,"read");return dictionaries.options(c.tenantId(),BusinessValueCatalog.WAREHOUSE);}

  /** 业务读取权下的地理摘要，避免给客商、仓库操作员额外授予地理维护权限。 */
  public Object geographyCatalog(VerifiedTenantContext c,Kind kind){require(c,kind,"read");return new JdbcGeographyCatalog(jdbc).nodes(c.tenantId());}

  /**
   * 标准目录仍复核原对象读取权，分页约束由标准目录统一校验。
   * @param c 可信租户上下文
   * @param kind 国别或行政区划类型
   * @param keyword 编码或名称子串，空串不筛选
   * @param page 页码，从 1 开始，默认 1
   * @param size 每页条数，1–100，默认 20
   * @return 当前页及匹配总数
   * @throws MasterData.Rejection 无权或分页参数非法
   */
  public Object geographyOptions(VerifiedTenantContext c,Kind kind,String keyword,int page,int size){require(c,kind,"read");return GeographyStandards.page(kind==Kind.SUBDIVISION,keyword,page,size);}

  /**
   * 地理维护树沿用当前对象读取权，标准导航不返回其他租户业务正文。
   * @param c 可信租户上下文
   * @param kind 当前地理维护对象
   * @param parent 标准或当前租户自定义父级编码，空串获取七洲
   * @param page 当前层页码，从 1 开始，默认 1
   * @param size 每页条数，1–100，默认 20
   * @return 当前层节点及总数
   */
  public Object geographyTree(VerifiedTenantContext c,Kind kind,String parent,int page,int size){
    require(c,kind,"read");return new TenantGeography(jdbc,c.tenantId(),false).children(parent,page,size,kind==Kind.COUNTRY_REGION);
  }

  /**
   * 分页读取当前租户同类档案，不开放跨租户或免权限目录。
   *
   * @param c 可信请求上下文，不能从请求体构造
   *
   * @param kind 固定档案类型
   *
   * @param keyword 编码或名称的字面包含查询，缺省空，最长 200 字符；百分号不作通配符
   *
   * @param enabled 缺省 null 查询全部，true/false 分别查询启用/停用
   *
   * @param page 从 1 开始，最大 1000000；缺省 1
   *
   * @param size 每页条数，1–100；缺省 20
   *
   * @throws Rejection 无查看权限返回 403，分页或关键词不合法返回 400
   * @return 授权范围内的分页及真实总数
   */
  public Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size) {
    return list(c,kind,keyword,enabled,page,size,null);
  }
  /**
   * 客商详情复用查询操作；只有指定 id 才读取敏感扩展资料及关联明细，不增加免权限读取入口。
   * @param c 可信租户及认证账号，禁止由客户端构造
   * @param kind 固定主数据类型，客商返回关联明细，其他类型仅返回原档案
   * @param keyword 编码或名称字面查询，缺省空，最多 200 字符
   * @param enabled 启停筛选，null 为全部；与 id 同时传入仍参与筛选
   * @param page 页码，1–1000000，缺省 1
   * @param size 每页 1–100 条，缺省 20；详情页面使用 1
   * @param id 可空，详情传合法 UUID；不存在或跨租户返回空列表，不泄露对象存在性
   * @return 原读取权限内的真实分页记录，审计只读
   * @throws Rejection 无权为 403，非法参数为 400
   */
  public Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size,String id) {
    return list(c,kind,keyword,enabled,page,size,id,"");
  }
  /**
   * 按左树范围分页读取，范围只缩小原租户与权限边界。
   * @param c 可信租户主体
   * @param kind 固定对象类型
   * @param keyword 关键词，最多 200 字符，默认空串
   * @param enabled 启停筛选，null 不限
   * @param page 页码，1–1000000，默认 1
   * @param size 每页条数，1–100，默认 20
   * @param id 可选详情 UUID，缺省不限制
   * @param scope 地理对象为洲、国家或区划编码；库区/库位为直属上级编码（最多64字符），空串不限制
   * @return 授权范围内当前页与总数，包含只读层级编码
   */
  public Page list(VerifiedTenantContext c,Kind kind,String keyword,Boolean enabled,int page,int size,String id,String scope) {
    require(c,kind,"read");
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
    long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+kind.table()+where,Long.class,args.toArray());
    args.add(size);args.add((page-1)*size);
    // 客商列表仅返回原摘要字段，证件、资本与银行账户等扩展字段只在指定详情 ID 后读取。
    var listFields=kind==Kind.PARTNER&&id==null?List.of("partnerType","creditCode","contactName","phone","address"):kind.fields;
    var rows=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,enabled,version,BIN_TO_UUID(created_by) audit_created_by,created_at,BIN_TO_UUID(updated_by) audit_updated_by,updated_at,"+String.join(",",listFields.stream().map(kind::column).toList())+" FROM "+kind.table()+where+" ORDER BY code,id LIMIT ? OFFSET ?",(r,n)->{
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
      return kind==Kind.PARTNER&&id!=null?new Row(row.id(),row.code(),row.name(),row.attributes(),row.enabled(),row.version(),row.audit(),PartnerDetails.read(jdbc,c,row.id(),false),PartnerDetails.read(jdbc,c,row.id(),true)):row;
    },args.toArray());
    return new Page(rows,total);
  }

  /**
   * 创建启用档案；数据库唯一约束防止并发创建同一编码。重复提交不会创建重复档案，也不自动重放。
   *
   * @param c 可信主体
   * @param kind 固定类型
   * @param input 字段契约
   *
   * @throws Rejection 无新增权限返回 403，字段非法返回 400，编码冲突返回 409
   * @return 新建档案标识；编码冲突返回 409，输入错误返回 400
   */
  public Map<String,String> create(VerifiedTenantContext c,Kind kind,Input input) {
    require(c,kind,"create");var values=validate(kind,input);String id=UUID.randomUUID().toString();
    try {
      return transaction.execute(status->{
        StorageHierarchy.prepare(jdbc,c,kind,null,values);
        prepareGeography(c,kind,input,values);
        var args=new ArrayList<Object>(List.of(id,c.tenantId().toString(),input.code().trim(),input.name().trim()));args.addAll(values.values());
        jdbc.update("INSERT INTO "+kind.table()+"(id,tenant_id,code,name,"+String.join(",",kind.fields.stream().map(kind::column).toList())+") VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,"+String.join(",",Collections.nCopies(kind.fields.size(),"?"))+")",args.toArray());
        // 审计主体来自可信会话；四个字段均在同一事务内写入，系统任务可通过 NULL 表达未知主体。
        jdbc.update("UPDATE "+kind.table()+" SET created_by=UUID_TO_BIN(?),created_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.accountId().toString(),c.accountId().toString(),c.tenantId().toString(),id);
        if(kind==Kind.PARTNER)PartnerDetails.save(jdbc,c,id,input,dictionaries);
        if(kind==Kind.PARTNER||kind==Kind.WAREHOUSE)dictionaryReferences(c,kind,id,values,Map.of());
        synchronizeGeography(c,kind);
        return Map.of("id",id);
      });
    } catch(DuplicateKeyException e){throw new Rejection(409,"MASTER_DATA_CODE_CONFLICT");}
  }

  /**
   * 修改原档案；编码、启停与租户不可经此接口更改。无权 ID 和不存在 ID 均不暴露其他租户事实。
   *
   * @param c 可信主体
   * @param kind 固定类型
   * @param id 当前租户档案 UUID
   * @param input 含当前版本的字段输入
   * @throws Rejection 无编辑权限返回 403，输入非法返回 400，版本变化或对象不可更新返回 409
   */
  public void update(VerifiedTenantContext c,Kind kind,String id,Input input) {
    require(c,kind,"update");uuid(id);var values=validate(kind,input);
    transaction.executeWithoutResult(status->{
      StorageHierarchy.prepare(jdbc,c,kind,id,values);
      prepareGeography(c,kind,input,values);
      var previous=(kind==Kind.PARTNER||kind==Kind.WAREHOUSE)?previousValues(c,kind,id):Map.<String,String>of();
      var args=new ArrayList<Object>(List.of(input.name().trim()));args.addAll(values.values());
      args.add(c.tenantId().toString());args.add(id);args.add(input.version());args.add(input.code().trim());
      int count=jdbc.update("UPDATE "+kind.table()+" SET name=?,"+String.join(",",kind.fields.stream().map(f->kind.column(f)+"=?").toList())+",version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=? AND code=?",args.toArray());
      if(count!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");
      if(kind==Kind.PARTNER)PartnerDetails.save(jdbc,c,id,input,dictionaries);
      if(kind==Kind.PARTNER||kind==Kind.WAREHOUSE)dictionaryReferences(c,kind,id,values,previous);
      synchronizeGeography(c,kind);
      audit(c,kind,id);
    });
  }

  /**
   * 启停独立鉴权并比较版本；不直接删除档案，也不将停用解释为撤销历史业务。
   *
   * @param c 可信主体
   * @param kind 固定类型
   * @param id 当前租户档案 UUID
   * @param input 显式启停和当前版本
   * @throws Rejection 无启停权限返回 403，输入非法返回 400，版本变化或对象不可更新返回 409
   */
  public void setStatus(VerifiedTenantContext c,Kind kind,String id,Status input) {
    require(c,kind,"set_status");uuid(id);
    if(input==null||input.enabled()==null||input.version()<0)throw invalid();
    transaction.executeWithoutResult(status->{
      StorageHierarchy.status(jdbc,c,kind,id,input.enabled());
      if(jdbc.update("UPDATE "+kind.table()+" SET enabled=?,version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",input.enabled(),c.tenantId().toString(),id,input.version())!=1)
        throw new Rejection(409,"MASTER_DATA_CHANGED");
      audit(c,kind,id);
    });
  }

  /** 权限每次经公开端口复核；未知权限、无效成员和缺少上下文一律拒绝。 */
  private void require(VerifiedTenantContext c,Kind kind,String action) {
    if(c==null||kind==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),kind.resource()+":"+action))
      throw new Rejection(403,"MASTER_DATA_DENIED");
  }
  /** 严格白名单与长度校验，避免把任意 JSON 当作已实现的业务扩展能力。 */
  static LinkedHashMap<String,String> validate(Kind kind,Input input) {
    if(input==null||input.code()==null||!input.code().trim().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||input.name()==null||input.name().isBlank()||input.name().trim().length()>200||input.version()<0)throw invalid();
    var attributes=input.attributes()==null?Map.<String,String>of():input.attributes();
    if(!kind.fields.containsAll(attributes.keySet()))throw invalid();
    var values=new LinkedHashMap<String,String>();
    for(String field:kind.fields){String value=Objects.toString(attributes.get(field),"").trim();if(value.length()>500||value.chars().anyMatch(Character::isISOControl))throw invalid();values.put(field,value);}
    if(kind==Kind.PARTNER&&!PartnerDetails.validTypes(values.get("partnerType")))throw invalid();
    if(kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION){
      // 标准目录仅提供缺省值；显式选择上级允许租户调整层级，不再把自定义码伪装成标准码。
      var standard=GeographyStandards.entries(kind==Kind.SUBDIVISION).stream().filter(e->e.code().equals(input.code().trim())).findFirst();
      if(kind==Kind.COUNTRY_REGION&&standard.isPresent())for(String key:List.of("alpha3Code","numericCode")){
        String supplied=values.get(key);if(!supplied.isEmpty()&&!supplied.equals(standard.get().attributes().get(key)))throw invalid();
      }
      standard.ifPresent(e->e.attributes().forEach((key,value)->{if(values.getOrDefault(key,"").isEmpty())values.put(key,value);}));
      if(kind==Kind.COUNTRY_REGION&&(!values.get("alpha3Code").matches("|[A-Z]{3}")||!values.get("numericCode").matches("|[0-9]{3}")))throw invalid();
    }
    if(kind==Kind.WAREHOUSE)WarehouseProfile.validate(values);
    StorageHierarchy.validate(kind,values);
    if(kind==Kind.PARTNER)PartnerDetails.validate(input,values);
    else if(input.contacts()!=null||input.bankAccounts()!=null)throw invalid();
    return values;
  }

  /** 写入事务内先锁定当前租户地理行；父链校验失败时整笔业务和审计均回滚。 */
  private void prepareGeography(VerifiedTenantContext c,Kind kind,Input input,Map<String,String> values){
    if(kind==Kind.COUNTRY_REGION||kind==Kind.SUBDIVISION)new TenantGeography(jdbc,c.tenantId(),true).put(kind,input.code().trim(),input.name().trim(),values);
  }

  /** 跨国移动同步全部已建后代国别；同事务递增版本和审计，旧编辑草稿不得覆盖移动结果。 */
  private void synchronizeGeography(VerifiedTenantContext c,Kind kind){
    if(kind!=Kind.COUNTRY_REGION&&kind!=Kind.SUBDIVISION)return;
    var graph=new TenantGeography(jdbc,c.tenantId(),false);
    var nodes=jdbc.queryForList("SELECT code,country_code FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?)",c.tenantId().toString());
    for(var node:nodes){String code=(String)node.get("code"),country=graph.country(code);if(!country.equals(node.get("country_code")))
      jdbc.update("UPDATE md_subdivision SET country_code=?,version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND code=?",country,c.accountId().toString(),c.tenantId().toString(),code);}
  }

  /** 旧值只从已授权父对象读取，供停用项保持不变时放行；事务中仍需父版本成功。 */
  private Map<String,String> previousValues(VerifiedTenantContext c,Kind kind,String id){
    var fields=kind.fields;var rows=jdbc.query("SELECT "+String.join(",",fields.stream().map(kind::column).toList())+" FROM "+kind.table()+" WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",(r,n)->{var result=new HashMap<String,String>();for(String field:fields)result.put(field,r.getString(kind.column(field)));return result;},c.tenantId().toString(),id);
    return rows.isEmpty()?Map.of():rows.getFirst();
  }
  /** 字典校验与引用在业务写事务内执行；未知新值拒绝，已有停用值可保留。 */
  private void dictionaryReferences(VerifiedTenantContext c,Kind kind,String id,Map<String,String> values,Map<String,String> previous){
    if(kind==Kind.WAREHOUSE){if(!dictionaries.reference(c.tenantId(),c.accountId(),"Warehouse",id,BusinessValueCatalog.WAREHOUSE,values,previous))throw invalid();return;}
    var normal=new HashMap<>(values);normal.remove("partnerType");
    if(!dictionaries.reference(c.tenantId(),c.accountId(),"BusinessPartner",id,BusinessValueCatalog.PARTNER,normal,previous))throw invalid();
    String before=previous.getOrDefault("partnerType","");var prior=Arrays.asList((before.equals("BOTH")?"CUSTOMER,SUPPLIER":before).split(","));
    for(String role:(values.get("partnerType").equals("BOTH")?"CUSTOMER,SUPPLIER":values.get("partnerType")).split(","))if(!dictionaries.reference(c.tenantId(),c.accountId(),"BusinessPartner",id,Map.of("partnerType","partner_type"),Map.of("partnerType",role),prior.contains(role)?Map.of("partnerType",role):Map.of()))throw invalid();
  }

  /** 统一更新固定主数据的最后修改主体；时间由数据库生成，避免客户端时钟漂移。 */
  private void audit(VerifiedTenantContext c,Kind kind,String id){jdbc.update("UPDATE "+kind.table()+" SET updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.accountId().toString(),c.tenantId().toString(),id);}
  private static void uuid(String id){try{if(!UUID.fromString(id).toString().equalsIgnoreCase(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
