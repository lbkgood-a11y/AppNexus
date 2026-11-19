package com.appnexus.platform.managementapi.businessobject;

import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.workflow.WorkflowContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 管理 HTTP 适配；平台入口不要求选租户，租户入口只接受宿主验证上下文，Cookie 写请求保留 CSRF。 */
@RestController
public final class BusinessObjectController {
  private final com.appnexus.platform.businessobject.BusinessObjectManagement service;
  public BusinessObjectController(com.appnexus.platform.businessobject.BusinessObjectManagement service) {this.service=service;}
  /** 目录激活参数；revision 为读取的非负版本，enabled 显式提交。 */
  public record State(long revision, boolean enabled) {}
  /** 草稿参数；revision 首次为 -1，后续为读取值，bindings 必须非空。 */
  public record Draft(long revision,List<Binding> bindings) {}
  /** 发布请求；revision 为草稿读取版本，避免覆盖其他管理人员修改。 */
  public record Publish(long revision) {}
  /** 入口控制参数；首次版本 -1，原因必填且最多 200 字，禁用只影响新命令。 */
  public record PageControl(long revision,String operationId,boolean enabled,String reason) {}
  /** 已接入操作只读目录，不向平台开放任意关系编辑。 */
  @com.appnexus.contracts.operation.BusinessObjectType(code="BusinessObject",nameZh="业务对象配置",nameEn="Business Object",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="configuration")
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.relationships",objectType="BusinessObject",operationCode="relationships",pages={"platform/business-objects"},nameZh="业务对象配置：查看入口关联",nameEn="View page relationships",permissions={"business.object.catalog:manage"},policy="原服务校验当前主体、对象授权和业务条件")
  @GetMapping("/bff/platform/business-objects/{type}/page-relationships")
  public Object relationships(HttpServletRequest r,@PathVariable String type){return reply(()->service.pageRelationships(context(r,true),type));}
  /** 独立启停入口，需平台权限并记录原因及版本。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.control",objectType="BusinessObject",operationCode="control",pages={"platform/business-objects"},nameZh="业务对象配置：管控入口",nameEn="Control page entry",permissions={"business.object.catalog:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PutMapping("/bff/platform/business-objects/{type}/page-controls")
  public Object control(HttpServletRequest r,@PathVariable String type,@RequestBody PageControl input){return reply(()->{service.controlPage(context(r,true),type,input.revision(),input.operationId(),input.enabled(),input.reason());return service.pageRelationships(context(r,true),type);});}
  /** 远程业务只提交非敏感可信依据；身份和租户由 OAuth 认证链固定，不接受 body 覆盖。 */
  public record ResolveInput(String objectType,String operationCode,String objectId,long basisVersion,Map<String,String> facts) {}
  /** 专用服务入口；普通浏览器会话不能伪装业务提供器提交任意依据。 */
  @PostMapping("/api/business-objects/integration/resolutions")
  public Object resolve(HttpServletRequest r,@RequestBody ResolveInput input){return reply(()->{
    if(!(r.getAttribute(Context.class.getName()) instanceof Context c))throw new SecurityException();
    String key=r.getHeader("Idempotency-Key");UUID.fromString(key);
    return service.resolveOperation(new Context(c.tenantId(),c.actorId(),key),input.objectType(),input.operationCode(),input.objectId(),input.basisVersion(),input.facts());
  });}
  /** 平台业务对象目录，包含未激活代码声明。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.catalog",objectType="BusinessObject",operationCode="catalog",pages={"platform/business-objects"},nameZh="业务对象配置：查看目录",nameEn="View document registry",permissions={"business.object.catalog:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @com.appnexus.contracts.operation.RecordAuditSource(table="business_object_type",key="object_type",id="descriptor.objectType",binaryKey=false,tenant=false)
 @GetMapping("/bff/platform/business-objects")
  public Object catalog(HttpServletRequest r) {return reply(()->service.catalog(context(r,true),true));}
  /** 平台统一查看工作流及打印投影与驱动协议，不重复定义服务专属业务契约。 */
  @GetMapping("/bff/platform/interaction-contracts")
  public Object contracts(HttpServletRequest r){return reply(()->{service.catalog(context(r,true),true);return service.contracts();});}
  /** 激活或停用系统目录，不影响已经运行的结果回写。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.state",objectType="BusinessObject",operationCode="state",pages={"platform/business-objects"},nameZh="业务对象配置：启停类型",nameEn="Change document state",permissions={"business.object.catalog:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PutMapping("/bff/platform/business-objects/{type}/state")
  public Object state(HttpServletRequest r,@PathVariable String type,@RequestBody State input) {return reply(()->{service.activate(context(r,true),type,input.revision(),input.enabled());return Map.of();});}
  /** 类型的租户授权清单。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.grants",objectType="BusinessObject",operationCode="grants",pages={"platform/business-objects"},nameZh="业务对象配置：查看租户授权",nameEn="View document grants",permissions={"business.object.catalog:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @GetMapping("/bff/platform/business-objects/{type}/grants")
  public Object grants(HttpServletRequest r,@PathVariable String type) {return reply(()->service.grants(context(r,true),type));}
  /** 授权现有租户；租户存在性由平台公开目录在管理端选取，服务侧仍校验 UUID。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.grant",objectType="BusinessObject",operationCode="grant",pages={"platform/business-objects"},nameZh="业务对象配置：授予操作权限",nameEn="Grant document access",permissions={"business.object.catalog:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PutMapping("/bff/platform/business-objects/{type}/grants/{tenant}")
  public Object grant(HttpServletRequest r,@PathVariable String type,@PathVariable String tenant,@RequestBody State input) {return reply(()->{service.grant(context(r,true),type,tenant,input.enabled());return Map.of();});}
  /** 租户管理角色可见的已授权业务对象。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.tenantCatalog",objectType="BusinessObject",operationCode="tenantCatalog",pages={"business-objects/index"},nameZh="业务对象配置：查看已开放对象",nameEn="View document workbench",permissions={"business.object.configuration:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @com.appnexus.contracts.operation.RecordAuditSource(table="business_object_type",key="object_type",id="descriptor.objectType",binaryKey=false,tenant=false)
 @GetMapping("/api/business-objects")
  public Object tenantCatalog(HttpServletRequest r) {return reply(()->service.catalog(context(r,false),false));}
  /** 静态配置及瞬时渲染依赖检查，不能代替执行时校验。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.readiness",objectType="BusinessObject",operationCode="readiness",pages={"business-objects/index"},nameZh="业务对象配置：检查配置就绪",nameEn="Check readiness",permissions={"business.object.configuration:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @GetMapping("/api/business-objects/{type}/readiness")
  public Object readiness(HttpServletRequest r,@PathVariable String type){return reply(()->service.readiness(context(r,false),type));}
  /** 打印行为只选择同租户兼容场景，不读取业务数据。 */
  @GetMapping("/api/business-objects/{type}/print-scenes")
  public Object scenes(HttpServletRequest r,@PathVariable String type,@RequestParam String operationCode){return reply(()->service.scenes(context(r,false),type,operationCode));}
  /** 配置草稿和发布版本。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.configuration",objectType="BusinessObject",operationCode="configuration",pages={"business-objects/index"},nameZh="业务对象配置：查看配置",nameEn="View configuration",permissions={"business.object.configuration:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @GetMapping("/api/business-objects/{type}/configuration")
  public Object configuration(HttpServletRequest r,@PathVariable String type) {return reply(()->service.configuration(context(r,false),type));}
  /**
   * 仅返回当前行为审批契约兼容的发布流程。
   * @param operationCode 已登记行为编码，必填；服务端不推断默认行为
   */
  @GetMapping("/api/business-objects/{type}/workflows")
  public Object options(HttpServletRequest r,@PathVariable String type,@RequestParam String operationCode) {return reply(()->service.options(context(r,false),type,operationCode));}
  /** 保存草稿不直接生效。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.save",objectType="BusinessObject",operationCode="save",pages={"business-objects/index"},nameZh="业务对象配置：保存",nameEn="Save document configuration",permissions={"business.object.configuration:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PutMapping("/api/business-objects/{type}/configuration")
  public Object save(HttpServletRequest r,@PathVariable String type,@RequestBody Draft input) {return reply(()->{service.save(context(r,false),type,input.revision(),input.bindings());return Map.of();});}
  /** 完整校验后原子发布。 */
  @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.publish",objectType="BusinessObject",operationCode="publish",pages={"business-objects/index"},nameZh="业务对象配置：发布",nameEn="Publish document configuration",permissions={"business.object.configuration:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/api/business-objects/{type}/configuration:publish")
  public Object publish(HttpServletRequest r,@PathVariable String type,@RequestBody Publish input) {return reply(()->{service.publish(context(r,false),type,input.revision());return Map.of();});}

  private Context context(HttpServletRequest r,boolean global) {
    if(global) {if(r.getUserPrincipal()==null)throw new SecurityException();return new Context(null,r.getUserPrincipal().getName(),UUID.randomUUID().toString());}
    if(!(r.getAttribute(WorkflowContext.class.getName()) instanceof WorkflowContext c))throw new SecurityException();
    return new Context(c.tenantId(),c.actorId(),c.requestId());
  }
  private Object reply(Supplier<Object> action) {
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}
    catch(Rejection e){return problem(e.status(),e.getMessage());}
    catch(com.appnexus.contracts.printing.PrintRejection e){return problem(e.status(),e.code());}
    catch(SecurityException e){return problem(403,"BUSINESS_OBJECT_ACCESS_DENIED");}
    catch(IllegalArgumentException|NullPointerException e){return problem(400,"BUSINESS_OBJECT_INPUT_INVALID");}
  }
  private Object problem(int status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("status",status,"code",code));}
}
