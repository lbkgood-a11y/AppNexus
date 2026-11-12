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
  @PostMapping("/external/outputs") public Object external(HttpServletRequest r,@RequestBody PrintOutputs.Input d){return reply(()->outputs.external(context(r),d));}
  @PostMapping("/documents/{id}/outputs") public Object output(HttpServletRequest r,@PathVariable String id,@RequestBody PrintOutputs.DocumentInput d){return reply(()->outputs.document(context(r),id,d));}
  @GetMapping({"/external/outputs/{id}","/outputs/{id}"}) public Object outputStatus(HttpServletRequest r,@PathVariable String id){return reply(()->outputs.status(context(r),id));}
  @PostMapping({"/external/outputs/{id}/confirm","/outputs/{id}/confirm"}) public Object confirmOutput(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String> d){return reply(()->outputs.confirm(context(r),id,d.get("deviceId")));}
  /** 预览 PDF 使用认证请求返回，禁止把调用方 Token 放在预览 URL 中。 */
  @GetMapping({"/external/outputs/{id}/pdf","/outputs/{id}/pdf"}) public Object previewPdf(HttpServletRequest r,@PathVariable String id){
    try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").body(outputs.pdf(context(r),id));}
    catch(PrintFailure e){return problem(e.status(),e.code());}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
  }

  @GetMapping("/capabilities") public Object capabilities(HttpServletRequest r){return reply(()->runtime.capabilities(context(r)));}
  @GetMapping("/design-contracts") public Object designContracts(HttpServletRequest r){return reply(()->flow.contracts(context(r)));}
  @PostMapping("/design-contracts") public Object createContract(HttpServletRequest r,@RequestBody tools.jackson.databind.JsonNode d){return reply(()->flow.createContract(context(r),d));}
  @GetMapping("/scenes") public Object scenes(HttpServletRequest r){return reply(()->flow.scenes(context(r)));}
  @PostMapping("/scenes") public Object createScene(HttpServletRequest r,@RequestBody PrintFlow.Scene d){return reply(()->flow.saveScene(context(r),null,d));}
  @PutMapping("/scenes/{id}") public Object saveScene(HttpServletRequest r,@PathVariable String id,@RequestBody PrintFlow.Scene d){return reply(()->flow.saveScene(context(r),id,d));}
  @GetMapping("/documents") public Object documents(HttpServletRequest r){return reply(()->flow.documents(context(r)));}
  @PostMapping("/documents") public Object createDocument(HttpServletRequest r,@RequestBody PrintFlow.Document d){return reply(()->flow.saveDocument(context(r),d));}
  @PostMapping("/documents/{id}/print") public Object printDocument(HttpServletRequest r,@PathVariable String id,@RequestBody PrintFlow.Output d){return reply(()->flow.print(context(r),id,d));}
  @GetMapping("/client-queues") public Object queues(HttpServletRequest r){return reply(()->flow.queues(context(r)));}
  @PostMapping("/agent/queues") public Object reportQueues(HttpServletRequest r,@RequestBody PrintFlow.QueueReport d){return reply(()->{flow.report(context(r),d);return Map.of("accepted",true);});}
  @GetMapping("/templates") public Object templates(HttpServletRequest r){return reply(()->runtime.templates(context(r)));}
  @GetMapping("/templates/{id}") public Object template(HttpServletRequest r,@PathVariable String id){return reply(()->runtime.template(context(r),id));}
  @PostMapping("/templates") public Object create(HttpServletRequest r,@RequestBody PrintRuntime.TemplateDraft d){return reply(()->runtime.saveTemplate(context(r),null,d));}
  @PutMapping("/templates/{id}") public Object save(HttpServletRequest r,@PathVariable String id,@RequestBody PrintRuntime.TemplateDraft d){return reply(()->runtime.saveTemplate(context(r),id,d));}
  @PostMapping("/templates/{id}/publish") public Object publish(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Long> body){return reply(()->runtime.publish(context(r),id,body.getOrDefault("revision",-1L)));}
  @GetMapping("/templates/{id}/versions") public Object versions(HttpServletRequest r,@PathVariable String id){return reply(()->runtime.versions(context(r),id));}
  /** 历史版本仅恢复草稿；必须显式传入当前 revision 防止覆盖其他设计人员的修改。 */
  @PostMapping("/templates/{id}/restore") public Object restore(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Long> body){return reply(()->runtime.restoreVersion(context(r),id,Math.toIntExact(body.getOrDefault("version",0L)),body.getOrDefault("revision",-1L)));}
  @GetMapping("/devices") public Object devices(HttpServletRequest r){return reply(()->runtime.devices(context(r)));}
  @PostMapping("/devices") public Object createDevice(HttpServletRequest r,@RequestBody PrintRuntime.DeviceDraft d){return reply(()->runtime.saveDevice(context(r),null,d));}
  @PutMapping("/devices/{id}") public Object saveDevice(HttpServletRequest r,@PathVariable String id,@RequestBody PrintRuntime.DeviceDraft d){return reply(()->runtime.saveDevice(context(r),id,d));}
  @GetMapping("/jobs") public Object jobs(HttpServletRequest r){return reply(()->runtime.jobs(context(r)));}
  /** 人员不得通过任意 JSON 快照绕过契约；正式输出统一从已保存单据或业务适配入口提交。 */
  @PostMapping("/jobs") public Object submit(HttpServletRequest r,@RequestBody PrintRuntime.JobDraft d){return problem(400,"PRINT_USE_SCENE_REQUIRED");}
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
    catch(PrintFailure e){return problem(e.status(),e.code());}
    catch(org.springframework.dao.DuplicateKeyException e){return problem(409,"PRINT_DUPLICATE");}
    catch(org.springframework.dao.DataAccessException e){return problem(503,"PRINT_STORAGE_UNAVAILABLE");}
    catch(IllegalArgumentException|NullPointerException e){return problem(400,"PRINT_INPUT_INVALID");}
  }
  private ResponseEntity<?> problem(int status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("status",status,"code",code));}
}
