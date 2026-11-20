package com.appnexus.platform.appapi.organization;
import com.appnexus.platform.organization.PersonData;


import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 仅处理主数据稳定拒绝码；不吞掉数据库故障，也不回显业务原文。 */
@RestControllerAdvice(assignableTypes=PersonController.class)
public final class PersonnelErrors {
  /**
   * @return 标准 ProblemDetail 和 no-store，不以 HTTP 200 包装失败 */
  @ExceptionHandler(PersonData.Rejection.class)
  public ResponseEntity<ProblemDetail> reject(PersonData.Rejection error){
    var body=ProblemDetail.forStatus(error.status);body.setProperty("code",error.getMessage());
    return ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(body);
  }
}
