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
  public PrintController(PrintRuntime runtime) { this.runtime=runtime; }

  @GetMapping("/capabilities") public Object capabilities(HttpServletRequest r){return reply(()->runtime.capabilities(context(r)));}
  @GetMapping("/templates") public Object templates(HttpServletRequest r){return reply(()->runtime.templates(context(r)));}
  @GetMapping("/templates/{id}") public Object template(HttpServletRequest r,@PathVariable String id){return reply(()->runtime.template(context(r),id));}
  @PostMapping("/templates") public Object create(HttpServletRequest r,@RequestBody PrintRuntime.TemplateDraft d){return reply(()->runtime.saveTemplate(context(r),null,d));}
  @PutMapping("/templates/{id}") public Object save(HttpServletRequest r,@PathVariable String id,@RequestBody PrintRuntime.TemplateDraft d){return reply(()->runtime.saveTemplate(context(r),id,d));}
  @PostMapping("/templates/{id}/publish") public Object publish(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,Long> body){return reply(()->runtime.publish(context(r),id,body.getOrDefault("revision",-1L)));}
  @GetMapping("/templates/{id}/versions") public Object versions(HttpServletRequest r,@PathVariable String id){return reply(()->runtime.versions(context(r),id));}
  @GetMapping("/devices") public Object devices(HttpServletRequest r){return reply(()->runtime.devices(context(r)));}
  @PostMapping("/devices") public Object createDevice(HttpServletRequest r,@RequestBody PrintRuntime.DeviceDraft d){return reply(()->runtime.saveDevice(context(r),null,d));}
  @PutMapping("/devices/{id}") public Object saveDevice(HttpServletRequest r,@PathVariable String id,@RequestBody PrintRuntime.DeviceDraft d){return reply(()->runtime.saveDevice(context(r),id,d));}
  @GetMapping("/jobs") public Object jobs(HttpServletRequest r){return reply(()->runtime.jobs(context(r)));}
  @PostMapping("/jobs") public Object submit(HttpServletRequest r,@RequestBody PrintRuntime.JobDraft d){return reply(()->runtime.submit(context(r),d));}
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
