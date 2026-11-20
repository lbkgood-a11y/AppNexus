package com.appnexus.platform.managementapi.administration;
import com.appnexus.platform.audit.AuditQueries;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 审计公开用例业务拒绝映射，保持原状态与稳定码，不暴露技术异常。 */
@RestControllerAdvice(assignableTypes=AuditQueryController.class)
public final class AuditQueryErrors {
 /** @param rejected 原审计拒绝 @return 原HTTP错误契约 */
 @ExceptionHandler(AuditQueries.Rejection.class)
 public ResponseEntity<ProblemDetail> reject(AuditQueries.Rejection rejected){var status=HttpStatus.valueOf(rejected.status());var body=ProblemDetail.forStatusAndDetail(status,rejected.status()==400?"审计分页参数不正确":"无审计查询权限");body.setTitle(status.getReasonPhrase());body.setProperty("code",rejected.getMessage());return ResponseEntity.status(status).body(body);}
}
