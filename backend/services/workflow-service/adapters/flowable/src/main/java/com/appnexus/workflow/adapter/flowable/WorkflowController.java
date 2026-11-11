package com.appnexus.workflow.adapter.flowable;

import com.appnexus.contracts.workflow.*;
import com.appnexus.workflow.core.WorkflowModel;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 工作流租户API；可信上下文由宿主安全链写入，写请求要求CSRF及幂等UUID，不接受客户端租户声明。 */
@RestController
@RequestMapping("/api/workflow")
public final class WorkflowController {
  private final WorkflowRuntime runtime;

  public WorkflowController(WorkflowRuntime runtime) {
    this.runtime = runtime;
  }

  /** 草稿请求；revision首次-1，后续为读取版本。 */
  public record Draft(long revision, WorkflowModel model) {}

  /** 发布/停用请求；reason停用必填，发布时可为空。 */
  public record Version(long revision, String reason) {}

  /** 发起请求；只有业务身份与版本，条件值从业务服务重读。 */
  public record Start(
      String objectType,
      String objectId,
      long objectVersion,
      String organizationId,
      String purpose,
      String entryCapability) {}

  /** 任务动作；values限定能力声明的字段，敏感业务数据由业务适配器负责字段权限。 */
  public record TaskAction(String action, Map<String, String> values, String comment) {}

  /** 恢复请求；原因必填，不接受跳过节点或更改实例结果。 */
  public record Recovery(String actor, String reason) {}

  /** 样本预演只计算路径，不创建实例、不调用业务能力。 */
  public record Preview(WorkflowModel model, Map<String, String> sample) {}

  @PostMapping("/preview")
  public Object preview(HttpServletRequest r, @RequestBody Preview p) {
    return reply(
        () -> {
          var errors = runtime.validate(context(r), p.model());
          if (!errors.isEmpty()) throw new WorkflowFailure("PREVIEW_INVALID_MODEL", 400);
          return com.appnexus.workflow.core.WorkflowPreview.trace(p.model(), p.sample());
        });
  }

  @GetMapping("/capabilities")
  public Object capabilities(HttpServletRequest r) {
    return reply(() -> runtime.capabilities(context(r)));
  }

  @GetMapping("/businesses")
  public Object businesses(HttpServletRequest r) {
    return reply(() -> runtime.businesses(context(r)));
  }

  /** 面向业务配置的单据、字段、结果动作与租户内人员目录，不返回任意调用地址。 */
  @GetMapping("/configuration")
  public Object configuration(HttpServletRequest r) {
    return reply(() -> runtime.configuration(context(r)));
  }

  @GetMapping("/definitions")
  public Object definitions(HttpServletRequest r) {
    return reply(() -> runtime.definitions(context(r)));
  }

  @PostMapping("/validate")
  public Object validate(HttpServletRequest r, @RequestBody WorkflowModel m) {
    return reply(() -> runtime.validate(context(r), m));
  }

  @PutMapping("/definitions/{id}")
  public Object save(HttpServletRequest r, @PathVariable String id, @RequestBody Draft d) {
    return reply(() -> runtime.save(context(r), id, d.revision(), d.model()));
  }

  @PostMapping("/definitions/{id}/publish")
  public Object publish(HttpServletRequest r, @PathVariable String id, @RequestBody Version v) {
    return reply(() -> runtime.publish(context(r), id, v.revision()));
  }

  @PostMapping("/definitions/{id}/disable")
  public Object disable(HttpServletRequest r, @PathVariable String id, @RequestBody Version v) {
    return reply(() -> runtime.disable(context(r), id, v.revision(), v.reason()));
  }

  @GetMapping("/definitions/{id}/versions")
  public Object versions(HttpServletRequest r, @PathVariable String id) {
    return reply(() -> runtime.versions(context(r), id));
  }

  @PostMapping("/instances")
  public Object start(HttpServletRequest r, @RequestBody Start s) {
    return reply(
        () ->
            runtime.start(
                context(r),
                s.objectType(),
                s.objectId(),
                s.objectVersion(),
                s.organizationId(),
                s.purpose(),
                s.entryCapability()));
  }

  @GetMapping("/instances")
  public Object instances(HttpServletRequest r) {
    return reply(() -> runtime.instances(context(r)));
  }

  @PostMapping("/instances/{id}/withdraw")
  public Object withdraw(HttpServletRequest r, @PathVariable String id, @RequestBody Recovery a) {
    return reply(() -> runtime.withdraw(context(r), id, a.reason()));
  }

  @GetMapping("/instances/{id}")
  public Object detail(HttpServletRequest r, @PathVariable String id) {
    return reply(() -> runtime.detail(context(r), id));
  }

  @GetMapping("/tasks")
  public Object tasks(HttpServletRequest r, @RequestParam(defaultValue = "todo") String view) {
    return reply(() -> runtime.tasks(context(r), view));
  }

  @GetMapping("/tasks/{id}")
  public Object task(HttpServletRequest r, @PathVariable String id) {
    return reply(() -> runtime.task(context(r), id));
  }

  @PostMapping("/tasks/{id}/actions")
  public Object action(HttpServletRequest r, @PathVariable String id, @RequestBody TaskAction a) {
    return reply(
        () ->
            runtime.complete(
                context(r), id, a.action(), a.values(), a.comment() == null ? "" : a.comment()));
  }

  @PostMapping("/tasks/{id}/reassign")
  public Object reassign(HttpServletRequest r, @PathVariable String id, @RequestBody Recovery a) {
    return reply(() -> runtime.reassign(context(r), id, a.actor(), a.reason()));
  }

  @PostMapping("/jobs/{id}/retry")
  public Object retry(HttpServletRequest r, @PathVariable String id, @RequestBody Recovery a) {
    return reply(() -> runtime.retry(context(r), id, a.reason()));
  }

  private WorkflowContext context(HttpServletRequest r) {
    if (!(r.getAttribute(WorkflowContext.class.getName()) instanceof WorkflowContext c))
      throw new WorkflowFailure("TENANT_REQUIRED", 409);
    String id = r.getHeader("Idempotency-Key");
    if (!Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod())) {
      try {
        UUID.fromString(id);
      } catch (Exception e) {
        throw new WorkflowFailure("IDEMPOTENCY_KEY_REQUIRED", 400);
      }
    }
    return new WorkflowContext(
        c.tenantId(), c.actorId(), id == null ? UUID.randomUUID().toString() : id);
  }

  private Object reply(Supplier<Object> action) {
    try {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());
    } catch (WorkflowFailure e) {
      return problem(e.status(), e.code());
    } catch (SecurityException e) {
      return problem(403, "BUSINESS_ACCESS_DENIED");
    } catch (org.springframework.dao.DuplicateKeyException e) {
      return problem(409, "WORKFLOW_DUPLICATE");
    } catch (IllegalArgumentException | NullPointerException e) {
      return problem(400, "WORKFLOW_INPUT_INVALID");
    }
  }

  private ResponseEntity<?> problem(int status, String code) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(Map.of("status", status, "code", code));
  }
}
