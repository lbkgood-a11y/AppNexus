package com.appnexus.platform.appapi.operation;
import com.appnexus.contracts.operation.ObjectDiagnostics;
import com.appnexus.contracts.operation.EndpointPolicy;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 当前登录主体对象诊断，不接收目标账号；未接入领域明确返回未检查，不推测对象存在性。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.ACCOUNT_SELF_SERVICE)
public final class ObjectDiagnosticsController {
 private final List<ObjectDiagnostics> providers;
 public ObjectDiagnosticsController(List<ObjectDiagnostics> providers){this.providers=List.copyOf(providers);var ids=new HashSet<String>();for(var provider:providers)for(var id:provider.operations())if(!ids.add(id))throw new IllegalStateException("重复对象诊断提供者："+id);}
 /**
  * 查询具体记录，业务提供者必须先复核读取/归属；业务异常交由现有统一异常处理。
  * @param operationId 代码登记的操作，不支持时不探测对象
  * @param objectId 记录标识，不作为权限凭据
  * @param request 认证链写入的当前可信租户及成员
  * @return 不缓存的分层诊断，不是后续执行凭据
  */
 @com.appnexus.contracts.operation.OperationBinding(id="BusinessObject.inspectOperation", reference=true, eligibility=com.appnexus.contracts.operation.OperationBinding.Eligibility.OBJECT)
 @GetMapping("/api/operations/{operationId}/objects/{objectId}/diagnostics")
 public ResponseEntity<List<ObjectDiagnostics.Check>> inspect(@PathVariable String operationId,@PathVariable String objectId,HttpServletRequest request){
  if(!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c))return ResponseEntity.status(409).build();
  var provider=providers.stream().filter(value->value.operations().contains(operationId)).findFirst();
  try{
   var checks=provider.isEmpty()?List.of(new ObjectDiagnostics.Check("OBJECT","NOT_CHECKED","NO_OBJECT_DIAGNOSTIC_PROVIDER")):provider.get().inspect(new ObjectDiagnostics.Context(c.accountId().toString(),c.tenantId().toString(),c.memberId().toString(),UUID.randomUUID().toString()),operationId,objectId);
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(checks);
  }catch(SecurityException denied){throw problem(404,"OBJECT_NOT_ACCESSIBLE","记录不存在或无权查看");}
  catch(com.appnexus.contracts.workflow.WorkflowRejection denied){throw problem(denied.status()==403?404:denied.status(),denied.status()==403?"OBJECT_NOT_ACCESSIBLE":denied.code(),"对象检查未通过");}
  catch(com.appnexus.contracts.businessobject.BusinessObjects.Rejection denied){throw problem(denied.status()==403?404:denied.status(),denied.status()==403?"OBJECT_NOT_ACCESSIBLE":denied.getMessage(),"对象配置检查未通过");}
  catch(com.appnexus.contracts.printing.PrintRejection denied){throw problem(denied.status()==403?404:denied.status(),denied.status()==403?"OBJECT_NOT_ACCESSIBLE":denied.code(),"打印依赖检查未通过");}
  catch(org.springframework.dao.DataAccessException unavailable){throw problem(503,"OBJECT_DIAGNOSTIC_UNAVAILABLE","对象检查服务暂不可用");}
  catch(IllegalArgumentException invalid){throw problem(400,"INVALID_OBJECT_DIAGNOSTIC_INPUT","对象标识或检查参数无效");}
 }
 /** 拒绝码仅包含稳定协议标识；不回显对象原文、SQL 或基础设施异常。 */
 private org.springframework.web.ErrorResponseException problem(int status,String code,String message){var http=HttpStatus.valueOf(status);var detail=ProblemDetail.forStatusAndDetail(http,message);detail.setProperty("code",code);return new org.springframework.web.ErrorResponseException(http,detail,null);}

}
