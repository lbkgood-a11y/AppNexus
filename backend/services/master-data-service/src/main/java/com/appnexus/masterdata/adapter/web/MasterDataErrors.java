package com.appnexus.masterdata.adapter.web;
import com.appnexus.masterdata.api.masterdata.MasterData;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 仅处理主数据稳定拒绝码；不吞掉数据库故障，也不回显业务原文。 */
@RestControllerAdvice(basePackages="com.appnexus.masterdata.adapter.web")
public final class MasterDataErrors {
  /**
   * @return 标准 ProblemDetail 和 no-store，不以 HTTP 200 包装失败 */
  @ExceptionHandler(MasterData.Rejection.class)
  public ResponseEntity<ProblemDetail> reject(MasterData.Rejection error){
    var body=ProblemDetail.forStatus(error.status);body.setProperty("code",error.getMessage());
    return ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(body);
  }
}
