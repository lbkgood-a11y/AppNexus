package com.appnexus.masterdata.application.masterdata;
import com.appnexus.masterdata.domain.geography.GeographyStandards;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.FieldProtection;
import com.appnexus.platform.authorization.FieldSecurity;
import com.appnexus.platform.authorization.DataAccessCatalog;
import com.appnexus.platform.authorization.PermissionCatalog;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/**
 * 主数据应用用例；协调授权、领域校验、聚合关系及输出端口，不依赖 JDBC 或 Spring 事务。
 * 租户隔离、固定 SQL 和数据库 UTC 审计由输出适配器执行；同一聚合事务覆盖全部变更。
 */
public final class MasterDataService implements com.appnexus.masterdata.api.masterdata.MasterDataManagement {
  private final BusinessValueCatalog dictionaries;
  private final com.appnexus.masterdata.application.masterdata.MasterDataRepository records;
  private final com.appnexus.masterdata.application.MasterDataRelations relations;
  private final com.appnexus.masterdata.application.RecordTransaction transaction;
  private final com.appnexus.masterdata.application.PartnerAccess partner;
  private final AccessAuthorization access;
  private final FieldProtection fieldProtection;
  private final List<FieldSecurity.Descriptor> warehouseProtectedFields;
  /**
   * @param access 可信功能权限公开端口，每次调用重新检查
   * @param dictionaries 字典选项及引用公开端口，不读取其他模块表
   * @param records 固定记录输出端口，必须实现租户、CAS 和审计
   * @param relations 查询与聚合关系输出端口，不能独立提交明细
   * @param transaction 同一数据源的用例事务端口
   * @param partner 客商行与字段准入端口，缺省拒绝客商能力
   */
  public MasterDataService(AccessAuthorization access,BusinessValueCatalog dictionaries,com.appnexus.masterdata.application.masterdata.MasterDataRepository records,com.appnexus.masterdata.application.MasterDataRelations relations,com.appnexus.masterdata.application.RecordTransaction transaction,com.appnexus.masterdata.application.PartnerAccess partner){
    this(access,dictionaries,records,relations,transaction,partner,null,null);
  }

  /**
   * 装配仓库字段保护；字段来自同一 Provider 的租户目录。
   * @param fieldProtection 平台实时字段执行器；仅独立测试装配可与目录一起缺省
   * @param catalog 已装配的统一字段目录
   */
  public MasterDataService(AccessAuthorization access,BusinessValueCatalog dictionaries,MasterDataRepository records,
      com.appnexus.masterdata.application.MasterDataRelations relations,
      com.appnexus.masterdata.application.RecordTransaction transaction,com.appnexus.masterdata.application.PartnerAccess partner,
      FieldProtection fieldProtection,DataAccessCatalog catalog){
    this.access=access;this.dictionaries=dictionaries;this.records=records;this.relations=relations;this.transaction=transaction;this.partner=partner;
    this.fieldProtection=fieldProtection;
    this.warehouseProtectedFields=catalog==null?List.of():catalog.fields(PermissionCatalog.ScopeType.TENANT).stream()
        .filter(field->field.resource().equals(Kind.WAREHOUSE.resource()))
        .toList();
  }
  private com.appnexus.masterdata.application.PartnerAccess partner(){if(partner==null)throw new Rejection(403,"MASTER_DATA_DENIED");return partner;}
  /** 当前客商读取权下返回归属候选与字段限制，不读取账号资料。 */
  public Object partnerAccess(VerifiedTenantContext c,String action){
    if(!Set.of("read","create","update").contains(action))throw invalid();
    require(c,Kind.PARTNER,"read");
    if(!"read".equals(action))require(c,Kind.PARTNER,action);
    return transaction.execute(()->partner().metadata(c,action),null);
  }

  /** 只读当前客商或商品字典；由原对象读取权限复核，不授予字典维护能力。 */
  public Object dictionaryOptions(VerifiedTenantContext c,Kind kind){require(c,kind,"read");return dictionaries.options(c.tenantId(),BusinessValueCatalog.PARTNER);}

  /** 登录选仓使用独立权限和行范围，禁止借用仓库档案读取权。 */
  @Override public List<LoginWarehouse> loginWarehouses(VerifiedTenantContext context, UUID warehouseId){
    require(context,Kind.WAREHOUSE,"select_login");
    return transaction.execute(()->relations.loginWarehouses(context,warehouseId),null);
  }

  /** 仓库读取权限仅允许查询本租户分类，不授予字典维护权限。 */
  @Override public Object warehouseOrganizations(VerifiedTenantContext c,String action){
    if(!Set.of("create","update").contains(action))throw invalid();
    require(c,Kind.WAREHOUSE,action);
    return relations.warehouseOrganizations(c,action);
  }

  public Object warehouseOptions(VerifiedTenantContext c){require(c,Kind.WAREHOUSE,"read");return dictionaries.options(c.tenantId(),BusinessValueCatalog.WAREHOUSE);}

  /** 获取表单预加载所需的实时限制；权限不足或执行器未装配时拒绝。 */
  @Override public Map<String,FieldSecurity.Mode> warehouseFieldRestrictions(VerifiedTenantContext c){
    require(c,Kind.WAREHOUSE,"read");
    if(fieldProtection==null)throw new Rejection(403,"MASTER_DATA_DENIED");
    return fieldProtection.restrictions(warehouseSubject(c),Kind.WAREHOUSE.resource());
  }

  /** 业务读取权下的地理摘要，避免给客商、仓库操作员额外授予地理维护权限。 */
  public Object geographyCatalog(VerifiedTenantContext c,Kind kind){require(c,kind,"read");return relations.geographyCatalog(c);}

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
    require(c,kind,"read");return relations.geographyTree(c,kind,parent,page,size);
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
    if(kind==Kind.WAREHOUSE&&fieldProtection!=null)return transaction.execute(()->{
      var pageResult=relations.list(c,kind,keyword,enabled,page,size,id,scope);
      var subject=warehouseSubject(c);
      var modes=fieldProtection.restrictions(subject,kind.resource());
      return new Page(pageResult.rows().stream().map(row->protectWarehouse(row,modes)).toList(),pageResult.total());
    },null);
    if(kind==Kind.PARTNER||kind==Kind.WAREHOUSE||kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION)
      return transaction.execute(()->relations.list(c,kind,keyword,enabled,page,size,id,scope),null);
    return relations.list(c,kind,keyword,enabled,page,size,id,scope);
  }

  /** {@inheritDoc} */
  @Override
  public StoragePosition resolveStoragePosition(VerifiedTenantContext c, StoragePosition expected) {
    require(c, Kind.WAREHOUSE, "read");
    require(c, Kind.STORAGE_AREA, "read");
    require(c, Kind.STORAGE_LOCATION, "read");
    if (expected == null || expected.warehouseId() == null || expected.areaId() == null
        || expected.locationId() == null || expected.warehouseVersion() < 0
        || expected.areaVersion() < 0 || expected.locationVersion() < 0) throw invalid();

    return transaction.execute(() -> {
      var warehouse = enabledPositionRow(c, Kind.WAREHOUSE, expected.warehouseId());
      var area = enabledPositionRow(c, Kind.STORAGE_AREA, expected.areaId());
      var location = enabledPositionRow(c, Kind.STORAGE_LOCATION, expected.locationId());
      if (!warehouse.code().equals(area.attributes().get("parentCode"))
          || !area.code().equals(location.attributes().get("parentCode"))) throw invalid();

      var current = new StoragePosition(warehouse.id(), area.id(), location.id(),
          warehouse.version(), area.version(), location.version());
      if (!current.equals(expected)) throw new Rejection(409, "MASTER_DATA_CHANGED");
      return current;
    }, null);
  }

  private Row enabledPositionRow(VerifiedTenantContext c, Kind kind, String id) {
    var rows = list(c, kind, "", true, 1, 1, id).rows();
    if (rows.size() != 1) throw invalid();
    return rows.getFirst();
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
    require(c,kind,"create");if(input==null)throw invalid();String id=UUID.randomUUID().toString();
    return transaction.execute(()->{
        var effective=kind==Kind.PARTNER?partner().prepare(c,null,input,Map.of()):warehouseInput(c,kind,input,Map.of());
        var values=validate(kind,effective);
        if(kind==Kind.PARTNER)partner().target(c,id,effective.organizationId(),"create",true);
        if(kind==Kind.WAREHOUSE)relations.warehouseTarget(c,id,input.organizationId(),true);
        relations.prepareStorage(c,kind,id,values,true);
        relations.prepareGeography(c,kind,input,values);
        records.create(c,kind,id,input,values);
        if(kind==Kind.PARTNER){
          records.organization(c,id,effective.organizationId());
          relations.savePartnerDetails(c,id,effective);
        }
        if(kind==Kind.PARTNER||kind==Kind.WAREHOUSE)dictionaryReferences(c,kind,id,values,Map.of());
        relations.synchronizeGeography(c,kind);
        return Map.of("id",id);
      },"MASTER_DATA_CODE_CONFLICT");
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
    require(c,kind,"update");uuid(id);if(input==null)throw invalid();
    transaction.execute(()->{
      relations.requireStorageWrite(c,kind,id,"update");
      String oldOrganization=kind==Kind.PARTNER?partner().lock(c,id,"update"):null;
      var previous=(kind==Kind.PARTNER||kind==Kind.WAREHOUSE)?previousValues(c,kind,id):Map.<String,String>of();
      var effective=kind==Kind.PARTNER?partner().prepare(c,id,input,previous):warehouseInput(c,kind,input,previous);
      var values=validate(kind,effective);
      String organization=input.organizationId()==null?oldOrganization:input.organizationId();
      if(kind==Kind.PARTNER)partner().target(c,id,organization,"update",false);
      if(kind==Kind.WAREHOUSE)relations.warehouseTarget(c,id,input.organizationId(),false);
      relations.prepareStorage(c,kind,id,values,false);
      relations.prepareGeography(c,kind,input,values);
      records.update(c,kind,id,input,values);
      if(kind==Kind.PARTNER){
        records.organization(c,id,organization);
        relations.savePartnerDetails(c,id,effective);
      }
      if(kind==Kind.PARTNER||kind==Kind.WAREHOUSE)dictionaryReferences(c,kind,id,values,previous);
      relations.synchronizeGeography(c,kind);
      audit(c,kind,id);return null;
    },null);
  }

  private AccessAuthorization.Subject warehouseSubject(VerifiedTenantContext c){
    return AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId());
  }

  /** 同页只读一次限制，隐藏字段从响应移除，遮盖使用平台算法。 */
  private Row protectWarehouse(Row row,Map<String,FieldSecurity.Mode> modes){
    var values=new LinkedHashMap<>(row.attributes());
    for(var field:warehouseProtectedFields){
      var mode=modes.get(field.field());
      if(mode==null)continue;
      if(!field.modes().contains(mode))throw new SecurityException("DATA_FIELD_MODE_UNSUPPORTED");
      if(mode==FieldSecurity.Mode.HIDDEN)values.remove(field.field());
      else if(values.containsKey(field.field()))
        values.put(field.field(),FieldSecurity.display(field.kind(),values.get(field.field()),mode));
    }
    return new Row(row.id(),row.code(),row.name(),Collections.unmodifiableMap(values),row.enabled(),row.version(),
        row.audit(),row.contacts(),row.bankAccounts(),row.organizationId(),Map.of(Kind.WAREHOUSE.resource(),modes));
  }

  /** 限制字段仅可省略，拒绝任何显式提交，避免利用写入结果猜测隐藏原值。 */
  private Input warehouseInput(VerifiedTenantContext c,Kind kind,Input input,Map<String,String> previous){
    if(kind!=Kind.WAREHOUSE||fieldProtection==null)return input;
    var modes=fieldProtection.restrictions(warehouseSubject(c),kind.resource());
    var supplied=input.attributes()==null?Map.<String,String>of():input.attributes();
    var values=new LinkedHashMap<>(supplied);
    for(var field:modes.keySet()){
      if(supplied.containsKey(field))
        throw new Rejection(403,"DATA_FIELD_WRITE_DENIED");
      if(previous.containsKey(field))values.put(field,previous.get(field));else values.remove(field);
    }
    return new Input(input.code(),input.name(),values,input.version(),input.contacts(),input.bankAccounts(),input.organizationId());
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
    transaction.execute(()->{
      if(kind==Kind.PARTNER)partner().lock(c,id,"set_status");
      relations.requireStorageWrite(c,kind,id,"set_status");
      relations.storageStatus(c,kind,id,input.enabled());
      records.status(c,kind,id,input);
      audit(c,kind,id);return null;
    },null);
  }

  /** 权限每次经公开端口复核；未知权限、无效成员和缺少上下文一律拒绝。 */
  private void require(VerifiedTenantContext c,Kind kind,String action) {
    if(c==null||kind==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),kind.resource()+":"+action))
      throw new Rejection(403,"MASTER_DATA_DENIED");
  }
  /** 输入校验委托纯领域规则；UTC 自然日由应用提供，不接受客户端指定校验日期。 */
  static LinkedHashMap<String,String> validate(Kind kind,Input input){return com.appnexus.masterdata.domain.MasterDataRules.validate(kind,input,java.time.LocalDate.now(java.time.ZoneOffset.UTC));}

  /** 旧值只从已授权父对象读取，供停用项保持不变时放行；事务中仍需父版本成功。 */
  private Map<String,String> previousValues(VerifiedTenantContext c,Kind kind,String id){
    return records.previous(c,kind,id);
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
  private void audit(VerifiedTenantContext c,Kind kind,String id){records.audit(c,kind,id);}
  private static void uuid(String id){try{if(!UUID.fromString(id).toString().equalsIgnoreCase(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
}
