package com.appnexus.client.document;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

/** 回环 HTTP 验证认证头与失败不重试；仅使用合成凭据。 */
class DocumentClientTest {
  HttpServer server;String base;AtomicInteger calls=new AtomicInteger();
  @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+server.getAddress().getPort();}
  @AfterEach void close(){server.stop(0);}
  @Test void authenticatedErrorsAreSafeAndNeverRetried(){
    String key=UUID.randomUUID().toString();
    server.createContext("/api/documents/integration/resolutions",exchange->{calls.incrementAndGet();assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-only");assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo(key);String body=new String(exchange.getRequestBody().readAllBytes());assertThat(body).contains("Order","confirm").doesNotContain("tenantId");byte[] reply="{\"code\":\"DOCUMENT_FLOW_NOT_BOUND\"}".getBytes();exchange.sendResponseHeaders(409,reply.length);exchange.getResponseBody().write(reply);exchange.close();});server.start();
    var client=new DocumentClient(URI.create(base),()->"test-only",Duration.ofSeconds(3));
    assertThatThrownBy(()->client.resolve(key,"Order","confirm",UUID.randomUUID().toString(),0,Map.of())).hasMessage("DOCUMENT_FLOW_NOT_BOUND");assertThat(calls.get()).isEqualTo(1);
  }
  @Test void rejectsRedirectAndNeverFollows(){
    server.createContext("/api/documents/integration/resolutions",exchange->{calls.incrementAndGet();exchange.getResponseHeaders().add("Location",base+"/other");exchange.sendResponseHeaders(302,-1);exchange.close();});server.start();
    var client=new DocumentClient(URI.create(base),()->"test-only",Duration.ofSeconds(3));
    assertThatThrownBy(()->client.resolve(UUID.randomUUID().toString(),"Order","confirm",UUID.randomUUID().toString(),0,Map.of())).isInstanceOf(DocumentClient.RemoteFailure.class);assertThat(calls.get()).isEqualTo(1);
  }
  @Test void refusesUnsafeDestinations(){
    assertThatThrownBy(()->new DocumentClient(URI.create("http://example.com"),()->"test",Duration.ofSeconds(3))).hasMessage("SDK_BASE_INVALID");
    assertThatThrownBy(()->new DocumentClient(URI.create("https://user:password@example.com"),()->"test",Duration.ofSeconds(3))).hasMessage("SDK_BASE_INVALID");
  }
  @Test void successfulResolutionAndPrintPreserveContract() {
    server.createContext("/api/documents/integration/resolutions",exchange->{byte[] reply="{\"id\":\"resolution\",\"tenantId\":\"tenant\",\"typeCode\":\"Order\",\"objectId\":\"object\",\"basisVersion\":0,\"configurationVersion\":1,\"definitionVersion\":0,\"facts\":{},\"actionCode\":\"print\",\"service\":\"PRINT\",\"sceneCode\":\"order-scene\",\"dataContract\":\"order.v1\"}".getBytes();exchange.sendResponseHeaders(200,reply.length);exchange.getResponseBody().write(reply);exchange.close();});
    server.createContext("/api/printing/external/outputs",exchange->{String body=new String(exchange.getRequestBody().readAllBytes());assertThat(body).contains("order-scene","order.v1","PREVIEW").doesNotContain("tenantId");byte[] reply="{\"id\":\"output\",\"state\":\"READY\"}".getBytes();exchange.sendResponseHeaders(200,reply.length);exchange.getResponseBody().write(reply);exchange.close();});server.start();
    var client=new DocumentClient(URI.create(base),()->"test-only",Duration.ofSeconds(3));var result=client.resolve(UUID.randomUUID().toString(),"Order","print",UUID.randomUUID().toString(),0,Map.of());
    assertThat(client.printResolved(UUID.randomUUID().toString(),result,Map.of("number","TEST"),"PREVIEW",null,1).get("id")).isEqualTo("output");
  }
}
