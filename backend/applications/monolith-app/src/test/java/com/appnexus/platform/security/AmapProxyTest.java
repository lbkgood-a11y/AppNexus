package com.appnexus.platform.security;
import com.appnexus.platform.appapi.configuration.AmapProxyController;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.net.http.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 固定上游、防伪造 Key、服务端密钥注入、响应泄漏与缺少配置的回归，不调用真实高德。 */
class AmapProxyTest {
 @TempDir Path directory;
 private final String key="1".repeat(32),secret="2".repeat(32);
 private TenantParameters parameters=mock(TenantParameters.class);
 private HttpClient client=mock(HttpClient.class);
 private VerifiedTenantContext context=new VerifiedTenantContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1);
 private MockHttpServletRequest request(){
  var request=new MockHttpServletRequest("GET","/api/maps/_AMapService/v3/vectormap");
  request.setAttribute(VerifiedTenantContext.class.getName(),context);request.addParameter("key",key);request.addParameter("jscode","client-forged");
  when(parameters.list(context)).thenReturn(List.of(new TenantParameters.Parameter("map.amap-key",key,"","",0)));
  return request;
 }
 @Test void fixedRoutesRejectTraversalAndExternalTargets(){
  assertThat(AmapProxyController.allowedPath("/v3/vectormap")).isTrue();
  for(String path:List.of("//evil.test/v3/map","/v3/../secret","/v3/%2e%2e/secret","/v3/map?url=evil"))assertThat(AmapProxyController.allowedPath(path)).isFalse();
  assertThat(AmapProxyController.origin("/v3/vectormap")).isEqualTo("https://fmap01.amap.com");
  assertThat(AmapProxyController.origin("/v4/map/styles")).isEqualTo("https://webapi.amap.com");
 }
 @Test void missingSecretAndForeignKeyNeverCallUpstream(){
  var request=request();var proxy=new AmapProxyController(parameters,directory.toString(),client);
  assertThatThrownBy(()->proxy.proxy(request)).isInstanceOf(org.springframework.web.ErrorResponseException.class).satisfies(e->assertThat(((org.springframework.web.ErrorResponseException)e).getStatusCode().value()).isEqualTo(503));
  request.setParameter("key","3".repeat(32));
  assertThatThrownBy(()->proxy.proxy(request)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
  verifyNoInteractions(client);
 }
 @SuppressWarnings("unchecked")
 @Test void injectsSecretWithoutForwardingClientCredentialsAndRejectsLeaks() throws Exception {
  Files.writeString(directory.resolve(key),secret);
  var request=request();request.addHeader("Cookie","private-session");
  HttpResponse<InputStream> response=mock(HttpResponse.class);
  when(response.statusCode()).thenReturn(200);when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type",List.of("application/octet-stream")),(a,b)->true));
  when(response.body()).thenReturn(new ByteArrayInputStream(new byte[]{1,2,3}));
  when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(invocation->{
   HttpRequest outbound=invocation.getArgument(0);assertThat(outbound.uri().getHost()).isEqualTo("fmap01.amap.com");
   assertThat(outbound.uri().getQuery()).contains("jscode="+secret).doesNotContain("client-forged");assertThat(outbound.headers().map()).isEmpty();return response;
  });
  var proxy=new AmapProxyController(parameters,directory.toString(),client);
  assertThat(proxy.proxy(request).getBody()).containsExactly(1,2,3);
  request.addParameter("callback","mapCallback");
  when(response.body()).thenReturn(new ByteArrayInputStream("mapCallback({});".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  assertThat(proxy.proxy(request).getHeaders().getContentType().toString()).startsWith("application/javascript");
  request.setParameter("callback","bad();alert");
  assertThatThrownBy(()->proxy.proxy(request)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
  request.removeParameter("callback");
  when(response.body()).thenReturn(new ByteArrayInputStream(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  assertThatThrownBy(()->proxy.proxy(request)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
 }
}
