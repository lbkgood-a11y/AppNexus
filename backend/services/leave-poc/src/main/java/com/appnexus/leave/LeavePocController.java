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

  /** 冻结并发起；没有唯一启用流程时回滚单据提交状态。 */
  @PostMapping("/{id}/submit")
  public Object submit(HttpServletRequest r, @PathVariable String id) {
    return reply(() -> service.submit(context(r), id));
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
