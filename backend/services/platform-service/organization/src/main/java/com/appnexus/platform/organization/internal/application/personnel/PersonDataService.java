package com.appnexus.platform.organization.internal.application.personnel;
import com.appnexus.platform.organization.internal.domain.personnel.PersonnelSchema;
import com.appnexus.platform.organization.PersonData;
import com.appnexus.platform.organization.port.personnel.PersonRepository;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.platform.organization.internal.domain.personnel.PersonnelSchema;
import com.appnexus.platform.configuration.BusinessValueCatalog;

import static com.appnexus.platform.organization.PersonData.*;

/**
 * 租户公共档案执行器；所有 SQL 强制带租户，所有调用重新鉴权，不缓存授权结果。
 * 仅表名和列名取自构建内白名单；业务值全部使用绑定参数，禁止任意 SQL 或动态对象执行。
 */
public final class PersonDataService implements com.appnexus.platform.organization.PersonManagement {
  private final BusinessValueCatalog dictionaries;
  private final PersonRepository mapper;
  private final AccessAuthorization access;
  private final com.appnexus.platform.organization.port.personnel.PersonnelTransaction transaction;
  /** 人员或商品完整用例依赖装配；所有输出参与原业务安全与事务约束。
  * @param mapper 固定人员仓储，可信租户及审计由存储适配器强制
  * @param access 权威功能授权公开端口
  * @param transaction 同数据源REQUIRED完整用例事务，失败整笔回滚
  * @param dictionaries 字典及引用公开端口，不读取其他上下文私表
  */
 public PersonDataService(PersonRepository mapper,AccessAuthorization access,com.appnexus.platform.organization.port.personnel.PersonnelTransaction transaction,BusinessValueCatalog dictionaries) {
    this.dictionaries=dictionaries;this.mapper=mapper;this.access=access;this.transaction=transaction;
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
    require(c,kind,"read");
    if(page<1||page>1000000||size<1||size>100||keyword==null||keyword.length()>200)throw invalid();
    long total=mapper.count(c.tenantId().toString(),keyword.trim(),enabled);
    var rows=mapper.list(c.tenantId().toString(),keyword.trim(),enabled,size,(page-1)*size).stream().map(row->{
      var attributes=new LinkedHashMap<String,String>();for(String field:List.of("englishName","phone","email","employmentStatus"))attributes.put(field,(String)row.get(field));
      return new Row((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),attributes,Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("version")).longValue(),new Audit((String)row.get("audit_created_by"),utc(row.get("created_at")),(String)row.get("audit_updated_by"),utc(row.get("updated_at"))));
    }).toList();
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
      return transaction.required(()->{
        mapper.create(c.tenantId().toString(),id,input,values,c.accountId().toString());
        dictionaryReferences(c,id,values,Map.of());
        return Map.of("id",id);
      });
    } catch(com.appnexus.platform.organization.port.personnel.PersonnelTransaction.DuplicateRecord e){throw new Rejection(409,"MASTER_DATA_CODE_CONFLICT");}
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
    transaction.run(()->{
      var previous=previousValues(c,id);
      int count=mapper.update(c.tenantId().toString(),id,input,values);
      if(count!=1)throw new Rejection(409,"MASTER_DATA_CHANGED");
      dictionaryReferences(c,id,values,previous);
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
    transaction.run(()->{
      if(mapper.status(c.tenantId().toString(),id,input.enabled(),input.version())!=1)
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
    if(kind==Kind.PERSON&&!values.get("email").isEmpty()&&(values.get("email").length()>200||!values.get("email").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw invalid();
    if(kind==Kind.PERSON){
      if(values.get("employmentStatus").isEmpty())values.put("employmentStatus","ACTIVE");
      PersonnelSchema.validateProfile(values);
    }
    return values;
  }

  /** 旧值只从已授权父对象读取，供停用项保持不变时放行；事务中仍需父版本成功。 */
  private Map<String,String> previousValues(VerifiedTenantContext c,String id){
    var rows=mapper.previous(c.tenantId().toString(),id);
    return rows.isEmpty()?Map.of():rows.getFirst();
  }
  /** 字典校验与引用在业务写事务内执行；未知新值拒绝，已有停用值可保留。 */
  private void dictionaryReferences(VerifiedTenantContext c,String id,Map<String,String> values,Map<String,String> previous){
    if(!dictionaries.reference(c.tenantId(),c.accountId(),"Person",id,BusinessValueCatalog.PERSON,values,previous))throw invalid();
  }

  /** 更新人员档案的最后修改主体；时间由数据库生成，避免客户端时钟漂移。 */
  private void audit(VerifiedTenantContext c,Kind kind,String id){mapper.audit(c.tenantId().toString(),id,c.accountId().toString());}
  private static void uuid(String id){try{if(!UUID.fromString(id).toString().equalsIgnoreCase(id))throw invalid();}catch(IllegalArgumentException|NullPointerException e){throw invalid();}}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
 private static String utc(Object value){return value==null?null:((java.time.Instant)value).toString();}
}
