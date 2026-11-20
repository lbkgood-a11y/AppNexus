package com.appnexus.platform.appapi.administration;
import com.appnexus.platform.tenant.TenantInvitations;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 邀请稳定拒绝HTTP映射，不能泄漏凭证、摘要或账号联系方式。 */
@RestControllerAdvice
public final class InvitationErrors {
 /** @param error 原邀请拒绝 @return 原状态、中文说明与稳定业务码 */
 @ExceptionHandler(TenantInvitations.Rejection.class)
 public ResponseEntity<ProblemDetail> reject(TenantInvitations.Rejection error){var status=HttpStatus.valueOf(error.status());String text=switch(error.getMessage()){case "PLATFORM_DENIED"->"无平台管理权限";case "MEMBERSHIP_EXISTS"->"账号已有成员身份";default->"邀请不存在、已失效或不属于当前账号";};var body=ProblemDetail.forStatusAndDetail(status,text);body.setTitle(status.getReasonPhrase());body.setProperty("code",error.getMessage());return ResponseEntity.status(status).body(body);}
}
