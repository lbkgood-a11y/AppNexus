package com.appnexus.printing.adapter.jdbc;

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
  private final PrintRuntime runtime;
  private final PrintFlow flow;
  private final PrintOutputs outputs;
  public PrintController(PrintRuntime runtime,PrintFlow flow,PrintOutputs outputs) { this.runtime=runtime;this.flow=flow;this.outputs=outputs; }
  /** 本人单据只读预检，不生成解析、打印任务或设备副作用。 */
  @com.appnexus.contracts.operation.BusinessObjectType(code="PrintOutput",nameZh="打印输出",nameEn="Print Output",category=com.appnexus.contracts.operation.BusinessObjectType.Category.TRANSACTION,module="print")
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.inspect",objectType="PrintOutput",operationCode="inspect",pages={"print/index"},nameZh="打印输出：检查依赖",nameEn="Inspect printing dependencies",permissions={"print.job:submit"},policy="本人单据、当前租户场景及设备；不返回正文或创建任务")
  @GetMapping("/documents/{id}/preflight") public Object preflight(HttpServletRequest r,@PathVariable String id,@RequestParam String mode,@RequestParam(required=false) String deviceId){return reply(()->outputs.preflight(context(r),id,mode,deviceId));}
  @PostMapping("/external/outputs") public Object external(HttpServletRequest r,@RequestBody PrintOutputs.Input d){return reply(()->outputs.external(context(r),d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.create",objectType="PrintOutput",operationCode="create",pages={"print/index"},nameZh="打印输出：新增",nameEn="Create output",permissions={"print.job:submit"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/documents/{id}/outputs") public Object output(HttpServletRequest r,@PathVariable String id,@RequestBody PrintOutputs.DocumentInput d){return reply(()->outputs.document(context(r),id,d));}
  @GetMapping({"/external/outputs/{id}","/outputs/{id}"}) public Object outputStatus(HttpServletRequest r,@PathVariable String id){return reply(()->outputs.status(context(r),id));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.confirm",objectType="PrintOutput",operationCode="confirm",pages={"print/index"},nameZh="打印输出：确认输出",nameEn="Confirm output",permissions={"print.job:submit"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping({"/external/outputs/{id}/confirm","/outputs/{id}/confirm"}) public Object confirmOutput(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String> d){return reply(()->outputs.confirm(context(r),id,d.get("deviceId")));}
  /** 预览 PDF 使用认证请求返回，禁止把调用方 Token 放在预览 URL 中。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintOutput.preview",objectType="PrintOutput",operationCode="preview",pages={"print/index"},nameZh="打印输出：预演",nameEn="View print preview",permissions={"print.job:submit"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @GetMapping({"/external/outputs/{id}/pdf","/outputs/{id}/pdf"}) public Object previewPdf(HttpServletRequest r,@PathVariable String id){
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").body(outputs.pdf(context(r),id));}
    catch(PrintFailure e){return problem(e.status(),e.code());}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
  }

  /** 已认证代理自查租户与客户端，避免把其他租户客户端误认为本租户渲染能力。 */
  @GetMapping("/agent/identity") public Object agentIdentity(HttpServletRequest r){return reply(()->runtime.agentIdentity(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.BusinessObjectType(code="PrintDataContract",nameZh="打印数据契约",nameEn="Print Data Contract",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="print")
 @com.appnexus.contracts.operation.OperationBinding(id="PrintDataContract.list",objectType="PrintDataContract",operationCode="list",pages={"print/index"},nameZh="打印数据契约：查看列表",nameEn="View data contracts",permissions={"print.template:design"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @GetMapping("/design-contracts") public Object designContracts(HttpServletRequest r){return reply(()->flow.contracts(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDataContract.create",objectType="PrintDataContract",operationCode="create",pages={"print/index"},nameZh="打印数据契约：新增",nameEn="Create data contract",permissions={"print.template:design"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/design-contracts") public Object createContract(HttpServletRequest r,@RequestBody tools.jackson.databind.JsonNode d){return reply(()->flow.createContract(context(r),d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.BusinessObjectType(code="PrintScene",nameZh="打印场景",nameEn="Print Scene",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="print")
 @com.appnexus.contracts.operation.OperationBinding(id="PrintScene.list",objectType="PrintScene",operationCode="list",pages={"print/index"},nameZh="打印场景：查看列表",nameEn="View scenes",permissions={"print.job:submit"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_scene")
 @GetMapping("/scenes") public Object scenes(HttpServletRequest r){return reply(()->flow.scenes(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintScene.create",objectType="PrintScene",operationCode="create",pages={"print/index"},nameZh="打印场景：新增",nameEn="Create scene",permissions={"print.template:publish"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/scenes") public Object createScene(HttpServletRequest r,@RequestBody PrintFlow.Scene d){return reply(()->flow.saveScene(context(r),null,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintScene.update",objectType="PrintScene",operationCode="update",pages={"print/index"},nameZh="打印场景：修改",nameEn="Save scene",permissions={"print.template:publish"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PutMapping("/scenes/{id}") public Object saveScene(HttpServletRequest r,@PathVariable String id,@RequestBody PrintFlow.Scene d){return reply(()->flow.saveScene(context(r),id,d));}
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_document")
 @GetMapping("/documents") public Object documents(HttpServletRequest r){return reply(()->flow.documents(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.BusinessObjectType(code="PrintDocument",nameZh="打印单据",nameEn="Print Document",category=com.appnexus.contracts.operation.BusinessObjectType.Category.TRANSACTION,module="print")
 @com.appnexus.contracts.operation.OperationBinding(id="PrintDocument.create",objectType="PrintDocument",operationCode="create",pages={"print/index"},nameZh="打印单据：新增",nameEn="Create print document",permissions={"print.job:submit"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/documents") public Object createDocument(HttpServletRequest r,@RequestBody PrintFlow.Document d){return reply(()->flow.saveDocument(context(r),d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDocument.print",objectType="PrintDocument",operationCode="print",pages={"print/index"},nameZh="打印单据：打印",nameEn="Print document",permissions={"print.job:submit"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/documents/{id}/print") public Object printDocument(HttpServletRequest r,@PathVariable String id,@RequestBody PrintFlow.Output d){return reply(()->flow.print(context(r),id,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.BusinessObjectType(code="PrintDevice",nameZh="打印设备",nameEn="Print Device",category=com.appnexus.contracts.operation.BusinessObjectType.Category.MASTER_DATA,module="print")
 @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.discoverQueues",objectType="PrintDevice",operationCode="discoverQueues",pages={"print/index"},nameZh="打印设备：发现设备队列",nameEn="View client queues",permissions={"print.device:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @GetMapping("/client-queues") public Object queues(HttpServletRequest r){return reply(()->flow.queues(context(r)));}
  @PostMapping("/agent/queues") public Object reportQueues(HttpServletRequest r,@RequestBody PrintFlow.QueueReport d){return reply(()->{flow.report(context(r),d);return Map.of("accepted",true);});}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.BusinessObjectType(code="PrintTemplate",nameZh="打印模板",nameEn="Print Template",category=com.appnexus.contracts.operation.BusinessObjectType.Category.CONFIGURATION,module="print")
 @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.list",objectType="PrintTemplate",operationCode="list",pages={"print/index"},nameZh="打印模板：查看列表",nameEn="View templates",permissions={"print.template:read"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_template")
 @GetMapping("/templates") public Object templates(HttpServletRequest r){return reply(()->runtime.templates(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.read",objectType="PrintTemplate",operationCode="read",pages={"print/index"},nameZh="打印模板：查看",nameEn="View template",permissions={"print.template:read"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_template")
 @GetMapping("/templates/{id}") public Object template(HttpServletRequest r,@PathVariable String id){return reply(()->runtime.template(context(r),id));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.create",objectType="PrintTemplate",operationCode="create",pages={"print/index"},nameZh="打印模板：新增",nameEn="Create template",permissions={"print.template:design"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/templates") public Object create(HttpServletRequest r,@RequestBody PrintRuntime.TemplateDraft d){return reply(()->runtime.saveTemplate(context(r),null,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.update",objectType="PrintTemplate",operationCode="update",pages={"print/index"},nameZh="打印模板：修改",nameEn="Save template",permissions={"print.template:design"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PutMapping("/templates/{id}") public Object save(HttpServletRequest r,@PathVariable String id,@RequestBody PrintRuntime.TemplateDraft d){return reply(()->runtime.saveTemplate(context(r),id,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.publish",objectType="PrintTemplate",operationCode="publish",pages={"print/index"},nameZh="打印模板：发布",nameEn="Publish template",permissions={"print.template:publish"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/templates/{id}/publish") public Object publish(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Long> body){return reply(()->runtime.publish(context(r),id,body.getOrDefault("revision",-1L)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.versions",objectType="PrintTemplate",operationCode="versions",pages={"print/index"},nameZh="打印模板：查看版本",nameEn="View template versions",permissions={"print.template:read"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @GetMapping("/templates/{id}/versions") public Object versions(HttpServletRequest r,@PathVariable String id){return reply(()->runtime.versions(context(r),id));}
  /** 历史版本仅恢复草稿；必须显式传入当前 revision 防止覆盖其他设计人员的修改。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintTemplate.restore",objectType="PrintTemplate",operationCode="restore",pages={"print/index"},nameZh="打印模板：恢复历史版本",nameEn="Restore template version",permissions={"print.template:design"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/templates/{id}/restore") public Object restore(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Long> body){return reply(()->runtime.restoreVersion(context(r),id,Math.toIntExact(body.getOrDefault("version",0L)),body.getOrDefault("revision",-1L)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.list",objectType="PrintDevice",operationCode="list",pages={"print/index"},nameZh="打印设备：查看列表",nameEn="View devices",permissions={"print.device:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_device")
 @GetMapping("/devices") public Object devices(HttpServletRequest r){return reply(()->runtime.devices(context(r)));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.create",objectType="PrintDevice",operationCode="create",pages={"print/index"},nameZh="打印设备：新增",nameEn="Create device",permissions={"print.device:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/devices") public Object createDevice(HttpServletRequest r,@RequestBody PrintRuntime.DeviceDraft d){return reply(()->runtime.saveDevice(context(r),null,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintDevice.update",objectType="PrintDevice",operationCode="update",pages={"print/index"},nameZh="打印设备：修改",nameEn="Edit device",permissions={"print.device:manage"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PutMapping("/devices/{id}") public Object saveDevice(HttpServletRequest r,@PathVariable String id,@RequestBody PrintRuntime.DeviceDraft d){return reply(()->runtime.saveDevice(context(r),id,d));}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.BusinessObjectType(code="PrintJob",nameZh="打印任务",nameEn="Print Job",category=com.appnexus.contracts.operation.BusinessObjectType.Category.LEDGER,module="print")
 @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.list",objectType="PrintJob",operationCode="list",pages={"print/index"},nameZh="打印任务：查看列表",nameEn="View print jobs",permissions={"print.job:read"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @com.appnexus.contracts.operation.RecordAuditSource(table="print_job")
 @GetMapping("/jobs") public Object jobs(HttpServletRequest r){return reply(()->runtime.jobs(context(r)));}
  /** 人员不得通过任意 JSON 快照绕过契约；正式输出统一从已保存单据或业务适配入口提交。 */
  @PostMapping("/jobs") public Object submit(HttpServletRequest r,@RequestBody PrintRuntime.JobDraft d){return problem(400,"PRINT_USE_SCENE_REQUIRED");}
  /** 操作契约关联下方真实接口；仅供展示与一致性校验，主体、权限及业务约束仍由原用例执行。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.reprint",objectType="PrintJob",operationCode="reprint",pages={"print/index"},nameZh="重新打印",nameEn="Reprint",permissions={"print.job:reprint"},policy="原服务逐请求复核有效成员、功能权限、对象归属、版本与业务状态")
  @PostMapping("/jobs/{id}/reprint") public Object reprint(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String> body){return reply(()->runtime.reprint(context(r),id,body.get("reason")));}
  @PostMapping("/agent/claim") public Object claim(HttpServletRequest r,@RequestParam String site,@RequestParam String agent){
    try {
      var job=runtime.claim(context(r),site,agent);
      return job.<Object>map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value))
          .orElseGet(()->ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    } catch(PrintFailure e){return problem(e.status(),e.code());}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
    catch(IllegalArgumentException|NullPointerException e){return problem(400,"PRINT_INPUT_INVALID");}
  }
  @PostMapping("/agent/jobs/{id}/receipt") public Object receipt(HttpServletRequest r,@PathVariable String id,@RequestBody PrintRuntime.Receipt body){return reply(()->runtime.receipt(context(r),id,body));}

  /** PDF 上传在进入字节缓冲区前限制读取大小；不信任 Content-Length，兼容分块传输。 */
  @PostMapping(value="/agent/jobs/{id}/pdf",consumes="application/pdf")
  public Object uploadPdf(HttpServletRequest r,@PathVariable String id,@RequestParam String agent){
    return reply(()->{
      try{return runtime.uploadPdf(context(r),id,agent,r.getInputStream().readNBytes(10*1024*1024+1));}
      catch(java.io.IOException e){throw new PrintFailure("PRINT_PDF_INVALID",400);}
    });
  }

  /** 下载仅允许附件；禁用缓存及内容嗅探，文件名只使用已验证 UUID。 */
  @com.appnexus.contracts.operation.OperationBinding(id="PrintJob.downloadPdf",objectType="PrintJob",operationCode="downloadPdf",pages={"print/index"},nameZh="打印任务：下载 PDF",nameEn="Download print artifact",permissions={"print.job:read"},policy="原服务校验当前主体、对象授权和业务条件")
  @GetMapping("/jobs/{id}/pdf")
  public Object downloadPdf(HttpServletRequest r,@PathVariable String id){
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
        .header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename("print-"+UUID.fromString(id)+".pdf").build().toString())
        .body(runtime.downloadPdf(context(r),id));}
    catch(PrintFailure e){return problem(e.status(),e.code());}
    catch(IllegalArgumentException e){return problem(400,"PRINT_IDENTIFIER_INVALID");}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
  }

  private PrintContext context(HttpServletRequest request) {
    if(!(request.getAttribute(PrintContext.class.getName()) instanceof PrintContext c)) throw new PrintFailure("TENANT_REQUIRED",409);
    String requestId=request.getHeader("Idempotency-Key");
    if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) try{UUID.fromString(requestId);}catch(Exception e){throw new PrintFailure("IDEMPOTENCY_KEY_REQUIRED",400);}
    return new PrintContext(c.tenantId(),c.actorId(),requestId==null?UUID.randomUUID().toString():requestId);
  }

  private Object reply(Supplier<Object> action){
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());}
    catch(com.appnexus.contracts.data.DataContract.Violation e){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("status",400,"code",e.getMessage(),"field",e.path()));}
    catch(PrintFailure e){return problem(e.status(),e.code());}
    catch(org.springframework.dao.DuplicateKeyException e){return problem(409,"PRINT_DUPLICATE");}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
    catch(IllegalArgumentException|NullPointerException e){return problem(400,"PRINT_INPUT_INVALID");}
  }
  private ResponseEntity<?> problem(int status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("status",status,"code",code));}
}
