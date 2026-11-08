package com.appnexus.platform.security;

import java.util.NoSuchElementException;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 将组织领域约束转换为稳定错误码，避免向客户端暴露数据库细节或内部异常文本。 */
@RestControllerAdvice(assignableTypes=OrganizationDomainController.class)
public class OrganizationDomainErrors {
 @ExceptionHandler(NoSuchElementException.class) Object missing(){throw AccountSettingsController.problem(HttpStatus.NOT_FOUND,"ORGANIZATION_OBJECT_NOT_FOUND","组织对象不存在或不属于当前租户");}
 @ExceptionHandler(DuplicateKeyException.class) Object duplicate(){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"ORGANIZATION_CODE_CONFLICT","编码或关系已经存在");}
 @ExceptionHandler(DataIntegrityViolationException.class) Object integrity(){throw AccountSettingsController.problem(HttpStatus.CONFLICT,"ORGANIZATION_REFERENCE_CONFLICT","组织对象仍被引用或关联状态已变化");}
 @ExceptionHandler(IllegalStateException.class) Object state(IllegalStateException error){String code="CONCURRENT_MODIFICATION".equals(error.getMessage())?"ORGANIZATION_CHANGED":"ORGANIZATION_STATE_CONFLICT";throw AccountSettingsController.problem(HttpStatus.CONFLICT,code,"组织数据已变化或当前状态不允许该操作，请刷新后重试");}
 @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class}) Object invalid(){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_ORGANIZATION_INPUT","组织参数、层级、任职或负责人约束不符合要求");}
}
