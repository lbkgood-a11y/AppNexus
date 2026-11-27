package com.appnexus.masterdata.adapter.web.organization;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 为组织管理入口保留与原 HTTP 适配器一致的业务错误码和 Problem Details。 */
final class OrganizationHttpProblems {
    private OrganizationHttpProblems() {}

    static ErrorResponseException problem(HttpStatus status, String code, String detail) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setProperty("code", code);
        return new ErrorResponseException(status, body, null);
    }
}
