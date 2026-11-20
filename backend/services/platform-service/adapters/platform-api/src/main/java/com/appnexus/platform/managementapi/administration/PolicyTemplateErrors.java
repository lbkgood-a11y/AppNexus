package com.appnexus.platform.managementapi.administration;
import com.appnexus.platform.authorization.PolicyTemplates;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 模板原业务拒绝的HTTP映射，不暴露授权快照或数据库细节。 */
@RestControllerAdvice(assignableTypes=PolicyTemplateController.class)
public final class PolicyTemplateErrors {
 /** @param error 固定业务拒绝 @return 原400与稳定错误码 */
 @ExceptionHandler(PolicyTemplates.Rejection.class)
 public ResponseEntity<ProblemDetail> reject(PolicyTemplates.Rejection error){var body=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"策略模板不存在、不属于当前域或参数无效");body.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());body.setProperty("code",error.getMessage());return ResponseEntity.badRequest().body(body);}
}
