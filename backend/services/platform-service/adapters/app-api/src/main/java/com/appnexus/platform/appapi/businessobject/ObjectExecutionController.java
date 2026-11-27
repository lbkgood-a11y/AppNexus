package com.appnexus.platform.appapi.businessobject;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.workflow.WorkflowContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 对象能力统一HTTP执行入口；功能、记录、字段、入口关联及版本均在公共执行器复核。 */
@RestController
public final class ObjectExecutionController {
  private final BusinessObjectExecution execution;
  /** @param execution 真实对象公共用例，无默认放行或专业服务直调 */
  public ObjectExecutionController(BusinessObjectExecution execution){this.execution=execution;}
  /**
   * 执行已登记记录能力或类型化业务命令，浏览器不能指定模板或流程。
   * @param request 宿主可信会话及UUID幂等键
   * @param input 唯一对象操作、依据版本及适用的类型化命令参数
   * @return no-store能力结果；错误交由全局异常契约处理
   */
  @PostMapping("/api/business-object-executions")
  public ResponseEntity<?> execute(HttpServletRequest request,@RequestBody BusinessObjectExecution.Request input){
    try{return executeTrusted(request,input);}
    catch(BusinessObjects.Rejection error){return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(java.util.Map.of("status",error.status(),"code",error.getMessage()));}
    catch(com.appnexus.contracts.data.DataContract.Violation error){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(java.util.Map.of("status",400,"code",error.getMessage(),"field",error.path()));}
    catch(SecurityException error){return ResponseEntity.status(403).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(java.util.Map.of("status",403,"code","BUSINESS_OBJECT_ACCESS_DENIED"));}
  }
  private ResponseEntity<BusinessObjectExecution.Result> executeTrusted(HttpServletRequest request,BusinessObjectExecution.Request input){
    if(!(request.getAttribute(WorkflowContext.class.getName()) instanceof WorkflowContext c))throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_ACCESS_DENIED");
    String key=request.getHeader("Idempotency-Key");
    try{UUID.fromString(key);}catch(RuntimeException error){throw new BusinessObjects.Rejection(400,"BUSINESS_OBJECT_REQUEST_KEY_REQUIRED");}
    var context=new BusinessObjects.Context(c.tenantId(),c.actorId(),key,c.personId(),c.organizationId());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(execution.execute(context,input));
  }
}
