package com.appnexus.document;

import static com.appnexus.contracts.document.Documents.*;
import com.appnexus.contracts.workflow.WorkflowContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 管理 HTTP 适配；平台入口不要求选租户，租户入口只接受宿主验证上下文，Cookie 写请求保留 CSRF。 */
@RestController
public final class DocumentController {
  private final DocumentService service;
  public DocumentController(DocumentService service) {this.service=service;}
  /** 目录激活参数；revision 为读取的非负版本，enabled 显式提交。 */
  public record State(long revision, boolean enabled) {}
  /** 草稿参数；revision 首次为 -1，后续为读取值，bindings 必须非空。 */
  public record Draft(long revision,List<Binding> bindings) {}
  /** 发布请求；revision 为草稿读取版本，避免覆盖其他管理人员修改。 */
  public record Publish(long revision) {}
  /** 平台关系保存参数；全量关系，revision 首次 -1；空集合显式关闭页面关联。 */
  public record Relationships(long revision,List<PageRelationship> relationships) {}
  /** 平台编辑候选及已保存关系，管理权限不授予业务原文访问。 */
  @GetMapping("/bff/platform/documents/{type}/page-relationships")
  public Object relationships(HttpServletRequest r,@PathVariable String type){return reply(()->service.pageRelationships(context(r,true),type));}
  /** 保存后即时影响新页面命令；既有流程结果交付独立继续。 */
  @PutMapping("/bff/platform/documents/{type}/page-relationships")
  public Object saveRelationships(HttpServletRequest r,@PathVariable String type,@RequestBody Relationships input){return reply(()->{service.savePageRelationships(context(r,true),type,input.revision(),input.relationships());return service.pageRelationships(context(r,true),type);});}
  /** 远程业务只提交非敏感可信依据；身份和租户由 OAuth 认证链固定，不接受 body 覆盖。 */
  public record ResolveInput(String typeCode,String actionCode,String objectId,long basisVersion,Map<String,String> facts) {}
  /** 专用服务入口；普通浏览器会话不能伪装业务提供器提交任意依据。 */
  @PostMapping("/api/documents/integration/resolutions")
  public Object resolve(HttpServletRequest r,@RequestBody ResolveInput input){return reply(()->{
    if(!(r.getAttribute(Context.class.getName()) instanceof Context c))throw new SecurityException();
    String key=r.getHeader("Idempotency-Key");UUID.fromString(key);
    return service.resolveAction(new Context(c.tenantId(),c.actorId(),key),input.typeCode(),input.actionCode(),input.objectId(),input.basisVersion(),input.facts());
  });}
  /** 平台单据目录，包含未激活代码声明。 */
  @GetMapping("/bff/platform/documents")
  public Object catalog(HttpServletRequest r) {return reply(()->service.catalog(context(r,true),true));}
  /** 平台统一查看工作流及打印投影与驱动协议，不重复定义服务专属业务契约。 */
  @GetMapping("/bff/platform/interaction-contracts")
  public Object contracts(HttpServletRequest r){return reply(()->{service.catalog(context(r,true),true);return service.contracts();});}
  /** 激活或停用系统目录，不影响已经运行的结果回写。 */
  @PutMapping("/bff/platform/documents/{type}/state")
  public Object state(HttpServletRequest r,@PathVariable String type,@RequestBody State input) {return reply(()->{service.activate(context(r,true),type,input.revision(),input.enabled());return Map.of();});}
  /** 类型的租户授权清单。 */
  @GetMapping("/bff/platform/documents/{type}/grants")
  public Object grants(HttpServletRequest r,@PathVariable String type) {return reply(()->service.grants(context(r,true),type));}
  /** 授权现有租户；租户存在性由平台公开目录在管理端选取，服务侧仍校验 UUID。 */
  @PutMapping("/bff/platform/documents/{type}/grants/{tenant}")
  public Object grant(HttpServletRequest r,@PathVariable String type,@PathVariable String tenant,@RequestBody State input) {return reply(()->{service.grant(context(r,true),type,tenant,input.enabled());return Map.of();});}
  /** 租户管理角色可见的已授权单据。 */
  @GetMapping("/api/documents")
  public Object tenantCatalog(HttpServletRequest r) {return reply(()->service.catalog(context(r,false),false));}
  /** 静态配置及瞬时渲染依赖检查，不能代替执行时校验。 */
  @GetMapping("/api/documents/{type}/readiness")
  public Object readiness(HttpServletRequest r,@PathVariable String type){return reply(()->service.readiness(context(r,false),type));}
  /** 打印行为只选择同租户兼容场景，不读取业务数据。 */
  @GetMapping("/api/documents/{type}/print-scenes")
  public Object scenes(HttpServletRequest r,@PathVariable String type,@RequestParam String actionCode){return reply(()->service.scenes(context(r,false),type,actionCode));}
  /** 配置草稿和发布版本。 */
  @GetMapping("/api/documents/{type}/configuration")
  public Object configuration(HttpServletRequest r,@PathVariable String type) {return reply(()->service.configuration(context(r,false),type));}
  /**
   * 仅返回当前行为审批契约兼容的发布流程。
   * @param actionCode 已登记行为编码，必填；服务端不推断默认行为
   */
  @GetMapping("/api/documents/{type}/workflows")
  public Object options(HttpServletRequest r,@PathVariable String type,@RequestParam String actionCode) {return reply(()->service.options(context(r,false),type,actionCode));}
  /** 保存草稿不直接生效。 */
  @PutMapping("/api/documents/{type}/configuration")
  public Object save(HttpServletRequest r,@PathVariable String type,@RequestBody Draft input) {return reply(()->{service.save(context(r,false),type,input.revision(),input.bindings());return Map.of();});}
  /** 完整校验后原子发布。 */
  @PostMapping("/api/documents/{type}/configuration:publish")
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
    catch(SecurityException e){return problem(403,"DOCUMENT_ACCESS_DENIED");}
    catch(IllegalArgumentException|NullPointerException e){return problem(400,"DOCUMENT_INPUT_INVALID");}
  }
  private Object problem(int status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("status",status,"code",code));}
}
