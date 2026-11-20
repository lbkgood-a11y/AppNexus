package com.appnexus.platform.appapi.configuration;
import com.appnexus.platform.configuration.TenantParameters;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 参数业务拒绝到原HTTP契约的映射，不回显请求值、数据库或安全配置。 */
@RestControllerAdvice(assignableTypes=TenantParameterController.class)
public final class TenantParameterErrors {
 /** @param rejected 原稳定业务拒绝 @return 原状态、中文说明及稳定code */
 @ExceptionHandler(TenantParameters.Rejection.class)
 public ResponseEntity<ProblemDetail> reject(TenantParameters.Rejection rejected){String detail=switch(rejected.getMessage()){case "PARAMETER_MANAGEMENT_DENIED"->"无租户参数管理权限";case "TENANT_UNAVAILABLE"->"租户不可访问";case "PARAMETER_CHANGED"->"参数已被其他管理员修改，请重新读取";default->"参数未登记或取值无效";};var body=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(rejected.status()),detail);body.setTitle(HttpStatus.valueOf(rejected.status()).getReasonPhrase());body.setProperty("code",rejected.getMessage());return ResponseEntity.status(rejected.status()).body(body);}
}
