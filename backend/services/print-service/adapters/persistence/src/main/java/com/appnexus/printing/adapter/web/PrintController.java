package com.appnexus.printing.adapter.web;
import com.appnexus.printing.api.*;
import com.appnexus.contracts.printing.PrintRejection;

import com.appnexus.contracts.printing.PrintContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 打印租户 API；身份和租户只接受宿主安全链属性，写请求必须携带幂等 UUID。 */
@RestController
@RequestMapping("/api/printing")
public final class PrintController {
  private final PrintRuntimeManagement runtime;
  private final PrintSceneManagement flow;
  private final PrintOutputManagement outputs;
  public PrintController(PrintRuntimeManagement runtime,PrintSceneManagement flow,PrintOutputManagement outputs) { this.runtime=runtime;this.flow=flow;this.outputs=outputs; }
  /** 本人单据只读预检，不生成解析、打印任务或设备副作用。 */

  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.inspect", reference=true)
  @GetMapping("/documents/{id}/preflight") public Object preflight(HttpServletRequest r,@PathVariable String id,@RequestParam String mode,@RequestParam(required=false) String deviceId){return reply(()->outputs.preflight(context(r),id,mode,deviceId));}
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.externalSubmit", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @PostMapping("/external/outputs") public Object external(HttpServletRequest r,@RequestBody com.appnexus.printing.api.PrintData.ExternalOutputRequest d){return reply(()->outputs.external(context(r),d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.create", reference=true)
  @PostMapping("/documents/{id}/outputs") public Object output(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.printing.api.PrintData.DocumentOutputRequest d){return reply(()->outputs.document(context(r),id,d));}
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.readStatus", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @GetMapping({"/external/outputs/{id}","/outputs/{id}"}) public Object outputStatus(HttpServletRequest r,@PathVariable String id){return reply(()->outputs.status(context(r),id));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.confirm", reference=true)
  @PostMapping({"/external/outputs/{id}/confirm","/outputs/{id}/confirm"}) public Object confirmOutput(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String> d){return reply(()->outputs.confirm(context(r),id,d.get("deviceId")));}
  /** 预览 PDF 使用认证请求返回，禁止把调用方 Token 放在预览 URL 中。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.preview", reference=true)
  @GetMapping({"/external/outputs/{id}/pdf","/outputs/{id}/pdf"}) public Object previewPdf(HttpServletRequest r,@PathVariable String id){
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").body(outputs.pdf(context(r),id));}
    catch(PrintRejection e){return problem(e.status(),e.code());}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
  }

  /** 已认证代理自查租户与客户端，避免把其他租户客户端误认为本租户渲染能力。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.agentIdentity", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @GetMapping("/agent/identity") public Object agentIdentity(HttpServletRequest r){return reply(()->runtime.agentIdentity(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="PrintDataContract.list", reference=true)
  @GetMapping("/design-contracts") public Object designContracts(HttpServletRequest r){return reply(()->flow.contracts(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDataContract.create", reference=true)
  @PostMapping("/design-contracts") public Object createContract(HttpServletRequest r,@RequestBody tools.jackson.databind.JsonNode d){return reply(()->flow.createContract(context(r),d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="PrintScene.list", reference=true)
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_scene")
 @GetMapping("/scenes") public Object scenes(HttpServletRequest r){return reply(()->flow.scenes(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintScene.create", reference=true)
  @PostMapping("/scenes") public Object createScene(HttpServletRequest r,@RequestBody com.appnexus.printing.api.PrintData.SceneRequest d){return reply(()->flow.saveScene(context(r),null,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintScene.update", reference=true)
  @PutMapping("/scenes/{id}") public Object saveScene(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.printing.api.PrintData.SceneRequest d){return reply(()->flow.saveScene(context(r),id,d));}
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_document")
 @com.appnexus.contracts.operation.OperationBinding(id="PrintDocument.list", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
 @GetMapping("/documents") public Object documents(HttpServletRequest r){return reply(()->flow.documents(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="PrintDocument.create", reference=true)
  @PostMapping("/documents") public Object createDocument(HttpServletRequest r,@RequestBody com.appnexus.printing.api.PrintData.DocumentRequest d){return reply(()->flow.saveDocument(context(r),d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDocument.print", reference=true)
  @PostMapping("/documents/{id}/print") public Object printDocument(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.printing.api.PrintData.DocumentPrintRequest d){return reply(()->flow.print(context(r),id,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.discoverQueues", reference=true)
  @GetMapping("/client-queues") public Object queues(HttpServletRequest r){return reply(()->flow.queues(context(r)));}
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.reportQueues", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @PostMapping("/agent/queues") public Object reportQueues(HttpServletRequest r,@RequestBody com.appnexus.printing.api.PrintData.QueueReport d){return reply(()->{flow.report(context(r),d);return Map.of("accepted",true);});}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.list", reference=true)
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_template")
 @GetMapping("/templates") public Object templates(HttpServletRequest r){return reply(()->runtime.templates(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.read", reference=true)
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_template")
 @GetMapping("/templates/{id}") public Object template(HttpServletRequest r,@PathVariable String id){return reply(()->runtime.template(context(r),id));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.create", reference=true)
  @PostMapping("/templates") public Object create(HttpServletRequest r,@RequestBody com.appnexus.printing.api.PrintData.TemplateDraft d){return reply(()->runtime.saveTemplate(context(r),null,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.update", reference=true)
  @PutMapping("/templates/{id}") public Object save(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.printing.api.PrintData.TemplateDraft d){return reply(()->runtime.saveTemplate(context(r),id,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.publish", reference=true)
  @PostMapping("/templates/{id}/publish") public Object publish(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Long> body){return reply(()->runtime.publish(context(r),id,body.getOrDefault("revision",-1L)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.versions", reference=true)
  @GetMapping("/templates/{id}/versions") public Object versions(HttpServletRequest r,@PathVariable String id){return reply(()->runtime.versions(context(r),id));}
  /** 历史版本仅恢复草稿；必须显式传入当前 revision 防止覆盖其他设计人员的修改。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.restore", reference=true)
  @PostMapping("/templates/{id}/restore") public Object restore(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Long> body){return reply(()->runtime.restoreVersion(context(r),id,Math.toIntExact(body.getOrDefault("version",0L)),body.getOrDefault("revision",-1L)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.list", reference=true)
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_device")
 @GetMapping("/devices") public Object devices(HttpServletRequest r){return reply(()->runtime.devices(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.create", reference=true)
  @PostMapping("/devices") public Object createDevice(HttpServletRequest r,@RequestBody com.appnexus.printing.api.PrintData.DeviceDraft d){return reply(()->runtime.saveDevice(context(r),null,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.update", reference=true)
  @PutMapping("/devices/{id}") public Object saveDevice(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.printing.api.PrintData.DeviceDraft d){return reply(()->runtime.saveDevice(context(r),id,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */

 @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.list", reference=true)
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_job")
 @GetMapping("/jobs") public Object jobs(HttpServletRequest r){return reply(()->runtime.jobs(context(r)));}
  /** 人员不得通过任意 JSON 快照绕过契约；正式输出统一从已保存单据或业务适配入口提交。 */
  @PostMapping("/jobs") public Object submit(HttpServletRequest r,@RequestBody com.appnexus.printing.api.PrintData.JobDraft d){return problem(400,"PRINT_USE_SCENE_REQUIRED");}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.reprint", reference=true)
  @PostMapping("/jobs/{id}/reprint") public Object reprint(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String> body){return reply(()->runtime.reprint(context(r),id,body.get("reason")));}
  @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.claim", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @PostMapping("/agent/claim") public Object claim(HttpServletRequest r,@RequestParam String site,@RequestParam String agent){
    try {
      var job=runtime.claim(context(r),site,agent);
      return job.<Object>map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value))
          .orElseGet(()->ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    } catch(PrintRejection e){return problem(e.status(),e.code());}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
    catch(IllegalArgumentException|NullPointerException e){return problem(400,"PRINT_INPUT_INVALID");}
  }
  @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.receipt", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @PostMapping("/agent/jobs/{id}/receipt") public Object receipt(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.printing.api.PrintData.Receipt body){return reply(()->runtime.receipt(context(r),id,body));}

  /** PDF 上传在进入字节缓冲区前限制读取大小；不信任 Content-Length，兼容分块传输。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.uploadPdf", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
  @PostMapping(value="/agent/jobs/{id}/pdf",consumes="application/pdf")
  public Object uploadPdf(HttpServletRequest r,@PathVariable String id,@RequestParam String agent){
    return reply(()->{
      try{return runtime.uploadPdf(context(r),id,agent,r.getInputStream().readNBytes(10*1024*1024+1));}
      catch(java.io.IOException e){throw new PrintRejection("PRINT_PDF_INVALID",400);}
    });
  }

  /** 下载仅允许附件；禁用缓存及内容嗅探，文件名只使用已验证 UUID。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.downloadPdf", reference=true)
  @GetMapping("/jobs/{id}/pdf")
  public Object downloadPdf(HttpServletRequest r,@PathVariable String id){
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
        .header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename("print-"+UUID.fromString(id)+".pdf").build().toString())
        .body(runtime.downloadPdf(context(r),id));}
    catch(PrintRejection e){return problem(e.status(),e.code());}
    catch(IllegalArgumentException e){return problem(400,"PRINT_IDENTIFIER_INVALID");}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
  }

  private PrintContext context(HttpServletRequest request) {
    if(!(request.getAttribute(PrintContext.class.getName()) instanceof PrintContext c)) throw new PrintRejection("TENANT_REQUIRED",409);
    String requestId=request.getHeader("Idempotency-Key");
    if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) try{UUID.fromString(requestId);}catch(Exception e){throw new PrintRejection("IDEMPOTENCY_KEY_REQUIRED",400);}
    return new PrintContext(c.tenantId(),c.actorId(),requestId==null?UUID.randomUUID().toString():requestId);
  }

  private Object reply(Supplier<Object> action){
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}
    catch(com.appnexus.contracts.data.DataContract.Violation e){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("status",400,"code",e.getMessage(),"field",e.path()));}
    catch(PrintRejection e){return problem(e.status(),e.code());}
    catch(org.springframework.dao.DuplicateKeyException e){return problem(409,"PRINT_DUPLICATE");}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
    catch(IllegalArgumentException|NullPointerException e){return problem(400,"PRINT_INPUT_INVALID");}
  }
  private ResponseEntity<?> problem(int status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("status",status,"code",code));}
}
