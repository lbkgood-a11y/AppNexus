package com.appnexus.masterdata.adapter.web.organization;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
/** 主数据响应包含联系方式等租户资料，统一禁止浏览器及中间缓存存储。 */
@ControllerAdvice(assignableTypes=PersonController.class)
public final class PersonnelCachePolicy implements ResponseBodyAdvice<Object> {
  /**
   * @return 主数据 Controller 的所有响应都应用缓存策略 */
  public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return true;}
  /**
   * @return 原始业务响应，仅增加 no-store，不改写状态码或正文 */
  public Object beforeBodyWrite(Object body,MethodParameter method,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){response.getHeaders().setCacheControl(CacheControl.noStore());return body;}
}
