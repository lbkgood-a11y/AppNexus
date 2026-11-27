package com.appnexus.masterdata.adapter.web.organization;
import com.appnexus.masterdata.adapter.web.organization.OrganizationDomainController;

import java.util.NoSuchElementException;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 将组织领域约束转换为稳定错误码，避免向客户端暴露数据库细节或内部异常文本。 */
@RestControllerAdvice(assignableTypes=OrganizationDomainController.class)
public class OrganizationDomainErrors {
 /** 组织行范围拒绝统一返回 403，不暴露不可见组织或内部计划细节。 */
 @ExceptionHandler(SecurityException.class) ResponseEntity<ProblemDetail> denied(){return response(HttpStatus.FORBIDDEN,"ORGANIZATION_DATA_ACCESS_DENIED","当前操作不在已授权的组织范围内");}
 @ExceptionHandler(NoSuchElementException.class) ResponseEntity<ProblemDetail> missing(){return response(HttpStatus.NOT_FOUND,"ORGANIZATION_OBJECT_NOT_FOUND","组织对象不存在或不属于当前租户");}
 @ExceptionHandler(DuplicateKeyException.class) ResponseEntity<ProblemDetail> duplicate(){return response(HttpStatus.CONFLICT,"ORGANIZATION_CODE_CONFLICT","编码或关系已经存在");}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ProblemDetail> integrity(){return response(HttpStatus.CONFLICT,"ORGANIZATION_REFERENCE_CONFLICT","组织对象仍被引用或关联状态已变化");}
 @ExceptionHandler(IllegalStateException.class) ResponseEntity<ProblemDetail> state(IllegalStateException error){String code="CONCURRENT_MODIFICATION".equals(error.getMessage())?"ORGANIZATION_CHANGED":"ORGANIZATION_STATE_CONFLICT";return response(HttpStatus.CONFLICT,code,"组织数据已变化或当前状态不允许该操作，请刷新后重试");}
 @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class}) ResponseEntity<ProblemDetail> invalid(){return response(HttpStatus.BAD_REQUEST,"INVALID_ORGANIZATION_INPUT","组织参数、层级、任职或负责人约束不符合要求");}
 /**
  * 直接返回受控 ProblemDetail；异常处理器内再次抛出不会重新进入同一解析链，会丢失业务状态。
  * @param status 对应业务约束的 HTTP 状态，不包含数据库实现细节
  * @param code 稳定业务码，用于前端国际化映射
  * @param detail 已审核的中文业务说明，不拼接原始异常文本
  * @return 与响应正文 status 一致且禁止缓存的错误响应
  */
 private ResponseEntity<ProblemDetail> response(HttpStatus status,String code,String detail){
  var body=ProblemDetail.forStatusAndDetail(status,detail);body.setProperty("code",code);
  return ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
 }
}
