package com.appnexus.platform.businessobject.internal.application.coding;

import com.appnexus.platform.businessobject.port.*;
import com.appnexus.platform.businessobject.port.CodingStore;
import com.appnexus.platform.businessobject.internal.domain.coding.CodingRules;
import com.appnexus.contracts.businessobject.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.BusinessCoding.*;
import java.time.*;
import java.util.*;

/** 编码应用用例；协调公开授权、领域规则与持久化端口，不依赖 JDBC 或 Spring 事务。 */
public final class CodingService implements com.appnexus.platform.businessobject.CodingManagement {
  private final CodingStore store;
  private final BusinessObjectPlatform platform;
  private final com.appnexus.platform.businessobject.port.CodingObjectAccess objects;
  private final Map<String,Descriptor> types=new HashMap<>();

  /**
   * @param store 同事务存储端口，必须执行租户隔离、数据库锁和审计
   * @param platform 可信主体和权限公开端口，每次执行重新校验
   * @param providers 构建内唯一对象声明，重复类型拒绝启动
   * @param objects 对象配置和页面操作准入；不得由客户端目录替代
   */
  public CodingService(CodingStore store,BusinessObjectPlatform platform,List<Provider> providers,com.appnexus.platform.businessobject.port.CodingObjectAccess objects){
    this.store=store;this.platform=platform;this.objects=objects;
    providers.forEach(p->{if(types.putIfAbsent(p.descriptor().objectType(),p.descriptor())!=null)throw new IllegalArgumentException("CODING_OBJECT_DUPLICATE");});
  }

  /** 原配置读取授权通过后才访问租户存储。 */
  public List<Map<String,Object>> configurations(Context c,String type){manage(c,type);return store.configurations(c,type);}
  /** 候选读取复核目标字段的业务操作，不要求普通用户具备配置管理权。 */
  public List<Map<String,Object>> options(Context c,String type,String field){allowed(c,type,field);return store.options(c,type,field);}
  /** 保存时锁定配置并验证版本；已发号的字段和规则冻结，失败不推进审计。 */
  public void save(Context c,String type,String key,Input input){
    key(key);Objects.requireNonNull(input);var field=field(type,input.field());CodingRules.validate(input.rule(),field.maxLength());
    if(input.name()==null||input.name().isBlank()||input.name().trim().length()>100||input.revision() < -1)fail(400,"CODING_INPUT_INVALID");
    store.atomic(()->{
      manage(c,type);var prior=store.lock(c,type,key);
      if(prior==null){if(input.revision()!=-1)fail(409,"CODING_VERSION_CONFLICT");}
      else{
        if(prior.revision()!=input.revision())fail(409,"CODING_VERSION_CONFLICT");
        if((!input.field().equals(prior.field())||!input.rule().equals(prior.rule()))&&store.hasIssues(c,type,key))fail(409,"CODING_RULE_FROZEN");
      }
      store.save(c,type,key,input.field(),input.name().trim(),input.enabled(),input.rule(),prior==null);return null;
    },"CODING_VERSION_CONFLICT");
  }
  /** 发号锁内重新授权、校验启用并处理重放；流水与回执原子提交，号码不回收。 */
  @Override public String issue(Context c,String type,String key){
    key(key);UUID.fromString(c.requestId());
    return store.atomic(()->{
      var encoder=store.lock(c,type,key);if(encoder==null)throw new Rejection(403,"CODING_UNAVAILABLE");
      allowed(c,type,encoder.field());if(!encoder.enabled())fail(409,"CODING_DISABLED");
      var prior=store.issued(c);
      if(prior!=null){if(!type.equals(prior.objectType())||!key.equals(prior.encoderKey()))fail(409,"CODING_IDEMPOTENCY_CONFLICT");return prior.code();}
      var rule=encoder.rule();CodingRules.validate(rule,field(type,encoder.field()).maxLength());
      var date=store.now().atZone(ZoneId.of(rule.zoneId())).toLocalDate();String period=CodingRules.period(rule,date);
      long next=store.sequence(c,type,key,period)+1;String code=CodingRules.render(rule,date,next);
      store.record(c,type,key,encoder.field(),period,next,code);return code;
    },"CODING_CODE_OR_REQUEST_CONFLICT");
  }
  private CodingField field(String type,String field){var d=types.get(type);if(d==null)throw new Rejection(403,"CODING_UNAVAILABLE");return d.codingFields().stream().filter(f->f.field().equals(field)).findFirst().orElseThrow(()->new Rejection(403,"CODING_UNAVAILABLE"));}
  private void manage(Context c,String type){identity(c);if(!types.containsKey(type)||!platform.tenantAdministrator(c))fail(403,"CODING_ACCESS_DENIED");
    // 复用对象配置的租户授权门禁，不能通过猜测对象类型绕过目录授权。
    objects.requireConfiguration(c,type);}
  private void allowed(Context c,String type,String field){
    identity(c);var f=field(type,field);var operation=types.get(type).operations().stream().filter(o->o.operationCode().equals(f.operationCode())).findFirst().orElseThrow();
    if(!platform.active(c)||!platform.codingAllowed(c,operation.operationId(),operation.permissionCode()))fail(403,"CODING_ACCESS_DENIED");
    objects.requireOperation(c,type,operation.operationCode(),operation.operationId());
  }
  private void identity(Context c){UUID.fromString(c.tenantId());UUID.fromString(c.actorId());}
  private void key(String key){if(key==null||!key.matches("[A-Za-z][A-Za-z0-9_]{0,39}"))fail(400,"CODING_INPUT_INVALID");}
  private static void fail(int status,String code){throw new Rejection(status,code);}
}
