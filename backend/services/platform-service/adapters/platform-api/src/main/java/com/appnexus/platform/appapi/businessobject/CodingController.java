package com.appnexus.platform.appapi.businessobject;
import com.appnexus.contracts.businessobject.BusinessObjects.*;

import com.appnexus.platform.businessobject.CodingManagement;

import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.contracts.workflow.WorkflowContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 编码 HTTP 适配；配置与普通业务协助使用不同授权路径，所有写入沿用宿主会话和 CSRF。 */
@RestController
public final class CodingController {
  private final CodingManagement service;
  public CodingController(CodingManagement service){this.service=service;}
  /** 规则独立目录，读取与追加版本均由统一规则权限准入。 */
  @OperationBinding(id="CodingRule.list",reference=true)
  @GetMapping("/api/coding-rules")
  public Object rules(HttpServletRequest r){return reply(()->service.rules(context(r)));}
  /** 追加固定规则版本，不允许原地修改已存在版本。 */
  @OperationBinding(id="CodingRule.save",reference=true)
  @PutMapping("/api/coding-rules/{key}")
  public Object saveRule(HttpServletRequest r,@PathVariable String key,@RequestBody CodingManagement.RuleInput input){return reply(()->{service.saveRule(context(r),key,input);return Map.of();});}
  /** 绑定候选复用对象配置授权，不能据此修改规则。 */
  @OperationBinding(id="CodingEncoder.ruleOptions",reference=true)
  @GetMapping("/api/business-objects/{type}/coding-rules")
  public Object ruleOptions(HttpServletRequest r,@PathVariable String type){return reply(()->service.ruleOptions(context(r),type));}
  /** 配置列表只对租户配置管理人员开放，原操作声明由 Provider 唯一维护。 */
  @OperationBinding(id="CodingEncoder.list",reference=true)
  @GetMapping("/api/business-objects/{type}/encoders")
  public Object list(HttpServletRequest r,@PathVariable String type){return reply(()->service.configurations(context(r),type));}
  /** 保存只接受规则和版本，不接受客户端租户及审计信息。 */
  @OperationBinding(id="CodingEncoder.save",reference=true)
  @PutMapping("/api/business-objects/{type}/encoders/{key}")
  public Object save(HttpServletRequest r,@PathVariable String type,@PathVariable String key,@RequestBody com.appnexus.platform.businessobject.CodingManagement.Input input){return reply(()->{service.save(context(r),type,key,input);return Map.of();});}
  /** 动态协助读取由字段声明派生目标对象操作权限；不能要求普通用户具备编码器管理权。 */
  @com.appnexus.contracts.operation.OperationBinding(id="CodingEncoder.fieldOptions", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @GetMapping("/api/business-objects/{type}/coding-options")
  public Object options(HttpServletRequest r,@PathVariable String type,@RequestParam String field){return reply(()->service.options(context(r),type,field));}
  /**
   * 发号为显式写操作，不能通过 GET 或打开页面触发。
   * @param r Idempotency-Key 为本次操作 UUID；重试必须显式复用，服务端始终重新鉴权
   */
  @com.appnexus.contracts.operation.OperationBinding(id="CodingEncoder.issue", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @PostMapping("/api/business-objects/{type}/encoders/{key}/issue")
  public Object issue(HttpServletRequest r,@PathVariable String type,@PathVariable String key){return reply(()->{
    var c=context(r);String request=r.getHeader("Idempotency-Key");UUID.fromString(request);
    return Map.of("code",service.issue(new Context(c.tenantId(),c.actorId(),request),type,key));
  });}
  private Context context(HttpServletRequest r){if(!(r.getAttribute(WorkflowContext.class.getName()) instanceof WorkflowContext c))throw new SecurityException();return new Context(c.tenantId(),c.actorId(),c.requestId());}
  private Object reply(Supplier<Object> action){
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}
    catch(Rejection e){return problem(e.status(),e.getMessage());}
    catch(SecurityException e){return problem(403,"CODING_ACCESS_DENIED");}
    catch(IllegalArgumentException|NullPointerException|java.time.DateTimeException e){return problem(400,"CODING_INPUT_INVALID");}
  }
  private Object problem(int status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("status",status,"code",code));}
}
