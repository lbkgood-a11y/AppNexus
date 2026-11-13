package com.appnexus.leave;

import com.appnexus.contracts.workflow.WorkflowContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 请假 POC 租户 API；写请求要求 CSRF（宿主安全链）及 UUID，不接受客户端身份或终态。 */
@RestController
@RequestMapping("/api/leave-poc")
public final class LeavePocController {
  private final LeavePocService service;

  /** @param service 请假业务服务，负责权限、冻结依据和结果交付幂等 */
  public LeavePocController(LeavePocService service) { this.service = service; }

  /** 本人最近 100 张单据；无数据返回空列表。 */
  @GetMapping
  public Object list(HttpServletRequest r) { return reply(() -> service.list(context(r))); }

  /** 申请人有效任职组织目录；仅返回名称及 ID，不能声明任意组织归属。 */
  @GetMapping("/organizations")
  public Object organizations(HttpServletRequest r) { return reply(() -> service.organizations(context(r))); }

  /** 页面操作契约与实际业务处理共用声明；展示不是授权，提交仍由服务端复核。 */
  @GetMapping("/actions")
  public Object actions(HttpServletRequest r) {return reply(()->service.actions(context(r)));}
  /** 本人单据可执行性，不能用于枚举其他人的对象。 */
  @GetMapping("/{id}/availability") public Object availability(HttpServletRequest r,@PathVariable String id){return reply(()->service.availability(context(r),id));}
  /** 本人生命周期及专业服务关联摘要，不泄漏打印快照。 */
  @GetMapping("/{id}/timeline") public Object timeline(HttpServletRequest r,@PathVariable String id){return reply(()->service.timeline(context(r),id));}
  /** 业务供数打印只接受操作标识，不允许浏览器替换数据或模板；统一预览需另行确认。 */
  @PostMapping("/{id}/print")
  public Object print(HttpServletRequest r,@PathVariable String id,@RequestBody SubmitOperation input){return reply(()->service.print(context(r),id,input.operationKey()));}

  /** 创建草稿，未知或无主管组织拒绝，不隐式创建组织或人员。 */
  @PostMapping
  public Object create(HttpServletRequest r, @RequestBody LeavePocService.Input input) {
    return reply(() -> service.create(context(r), input));
  }

  /** 单据原文；taskId 仅是授权查询参数，不能替代可信身份。 */
  @GetMapping("/{id}")
  public Object read(HttpServletRequest r, @PathVariable String id,
      @RequestParam(required = false) String taskId) {
    return reply(() -> service.read(context(r), id, taskId));
  }

  /** 页面提交意图只接受稳定操作标识，不能指定策略、流程目标或业务终态。 */
  public record SubmitOperation(String operationKey) {}
  /** 页面按登记操作调用业务提交；旧客户端无请求体时兼容原固定入口，传入其他操作必须拒绝。 */
  @PostMapping("/{id}/submit")
  public Object submit(HttpServletRequest r, @PathVariable String id,@RequestBody(required=false) SubmitOperation input) {
    return reply(() -> {
      if(input!=null&&!LeavePocService.SUBMIT_ACTION.operationKey().equals(input.operationKey()))
        throw new com.appnexus.contracts.document.Documents.Rejection(400,"DOCUMENT_PAGE_OPERATION_INVALID");
      return service.submit(context(r), id);
    });
  }

  /** 修改参数包含期望依据版本；审批中不可修改，成功后需重新提交新轮次。 */
  public record Edit(long basisVersion,LeavePocService.Input input) {}
  /** 本人修改草稿、驳回或撤回单据。 */
  @PutMapping("/{id}")
  public Object update(HttpServletRequest r,@PathVariable String id,@RequestBody Edit edit) {
    return reply(()->service.update(context(r),id,edit.basisVersion(),edit.input()));
  }
  /** 本人流程关联历史。 */
  @GetMapping("/{id}/cycles")
  public Object cycles(HttpServletRequest r,@PathVariable String id) {return reply(()->service.cycles(context(r),id));}

  private WorkflowContext context(HttpServletRequest r) {
    if (!(r.getAttribute(WorkflowContext.class.getName()) instanceof WorkflowContext c))
      throw new SecurityException("TENANT_REQUIRED");
    String key = r.getHeader("Idempotency-Key");
    if (!r.getMethod().equals("GET")) UUID.fromString(key);
    return new WorkflowContext(c.tenantId(), c.actorId(), key == null ? c.requestId() : key);
  }

  private Object reply(Supplier<Object> action) {
    try {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());
    } catch (com.appnexus.contracts.printing.PrintRejection e) {
      return problem(e.status(),e.code());
    } catch (com.appnexus.contracts.document.Documents.Rejection e) {
      return problem(e.status(),e.getMessage());
    } catch (com.appnexus.contracts.workflow.WorkflowRejection e) {
      return problem(e.status(), e.code());
    } catch (SecurityException e) {
      return problem(403, "LEAVE_ACCESS_DENIED");
    } catch (IllegalArgumentException | NullPointerException e) {
      return problem(400, "LEAVE_INPUT_INVALID");
    }
  }

  private Object problem(int status, String code) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("status", status, "code", code));
  }
}
