package com.appnexus.platform;

import com.appnexus.platform.security.IntegrationClients;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

/** 真实 Token 端点验证服务主体、固定租户、权限版本、密钥轮换和接口面隔离。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class IntegrationClientTest extends DatabaseTestSupport {
    @Autowired IntegrationClients clients;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    private UUID actor;
    @BeforeEach void initialize(){administration.initializeAdministrator("admin-test");actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));}
    private UUID tenant(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Partner tenant')",id.toString());return id;}
    /** 专用服务 scope 只开放解析入口，Token 固定租户，不能访问人员配置或伪装任意账号。 */
    @Test void documentScopeOnlyAllowsResolution() throws Exception {
        var client=clients.create(actor,tenant(),"Document service",Set.of("document:resolve"));String bearer=token(client);
        mvc.perform(post("/api/documents/integration/resolutions").header("Authorization","Bearer "+bearer).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{\"typeCode\":\"Unknown\",\"actionCode\":\"submit\",\"objectId\":\""+UUID.randomUUID()+"\",\"basisVersion\":0,\"facts\":{}}"))
          .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents").header("Authorization","Bearer "+bearer)).andExpect(status().isUnauthorized());
        var other=clients.create(actor,tenant(),"Dictionary only",Set.of("dictionaries:read"));
        mvc.perform(post("/api/documents/integration/resolutions").header("Authorization","Bearer "+token(other)).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
    }
    private String token(IntegrationClients.Created client)throws Exception{
        String body=mvc.perform(post("/oauth2/token").with(httpBasic(client.client().id(),client.secret()))
            .contentType("application/x-www-form-urlencoded").param("grant_type","client_credentials").param("scope",String.join(" ",client.client().scopes())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refresh_token").doesNotExist()).andReturn().getResponse().getContentAsString();
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("access_token").asString();
    }
    /** 同编码的 A/B 字典数据不能通过列表或计数泄漏；响应只含公开业务字段。 */
    @Test void clientCredentialsReadOnlyBoundTenantAndCannotUseOtherApiSurfaces()throws Exception{
        UUID a=tenant(),b=tenant();dictionary(a,"ALPHA");dictionary(b,"BETA");
        var client=clients.create(actor,a,"Partner",Set.of("dictionaries:read"));String token=token(client);
        String response=mvc.perform(get("/v1/dictionaries/sample/items").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(header().exists("X-Trace-Id")).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].value").value("ALPHA")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("BETA","tenantId","createdBy","password","authorityVersion");
        mvc.perform(get("/api/tenants").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/bff/platform/users").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/dictionaries/sample/items").with(authenticated(actor.toString(),0))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_call_audit WHERE client_id=? AND status=200",Integer.class,client.client().id())).isEqualTo(1);
    }
    @Test void versionChangesAndDisabledTenantInvalidateExistingTokens()throws Exception{
        UUID tenant=tenant();var client=clients.create(actor,tenant,"Partner",Set.of("dictionaries:read"));String old=token(client);
        clients.update(actor,client.client().id(),0,false,Set.of("dictionaries:read"));
        mvc.perform(get("/v1/dictionaries/sample/items").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        mvc.perform(post("/oauth2/token").with(httpBasic(client.client().id(),client.secret())).contentType("application/x-www-form-urlencoded").param("grant_type","client_credentials"))
            .andExpect(status().isBadRequest());
        clients.update(actor,client.client().id(),1,true,Set.of("dictionaries:read"));String current=token(client);
        jdbc.update("UPDATE platform_tenant SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",tenant.toString());
        mvc.perform(get("/v1/dictionaries/sample/items").header("Authorization","Bearer "+current)).andExpect(status().isUnauthorized());
    }
    /** 打印代理只可访问专用领取/回执路径；固定租户来自 Token 策略，不能借 scope 进入人员管理 API。 */
    @Test void printAgentClientUsesOnlyDedicatedTenantBoundSurface()throws Exception{
        UUID tenant=tenant();
        var agent=clients.create(actor,tenant,"Print agent",Set.of("print-agent:operate"));
        String bearer=token(agent);
        mvc.perform(get("/api/printing/agent/identity").header("Authorization","Bearer "+bearer))
          .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(tenant.toString())).andExpect(jsonPath("$.tenantName").value("Partner tenant")).andExpect(jsonPath("$.clientId").value(agent.client().id()));
        mvc.perform(post("/api/printing/agent/claim").queryParam("site","default").queryParam("agent","test-agent")
                .header("Authorization","Bearer "+bearer).header("Idempotency-Key",UUID.randomUUID().toString()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/printing/templates").header("Authorization","Bearer "+bearer))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tenants").header("Authorization","Bearer "+bearer))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/printing/external/outputs").header("Authorization","Bearer "+bearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"sceneCode\":\"missing\",\"sourceId\":\"test\",\"data\":{},\"mode\":\"PREVIEW\",\"copies\":1}"))
            .andExpect(status().isForbidden());
        var external=clients.create(actor,tenant,"External print",Set.of("print:submit"));
        String externalToken=token(external);
        mvc.perform(post("/api/printing/external/outputs").header("Authorization","Bearer "+externalToken)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"sceneCode\":\"missing\",\"sourceId\":\"test\",\"data\":{},\"mode\":\"PREVIEW\",\"copies\":1}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRINT_SCENE_UNAVAILABLE"));
        mvc.perform(post("/api/printing/agent/claim").queryParam("site","default").queryParam("agent","test")
                .header("Authorization","Bearer "+externalToken).header("Idempotency-Key",UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
        var dictionary=clients.create(actor,tenant,"Dictionary client",Set.of("dictionaries:read"));
        mvc.perform(post("/api/printing/agent/claim").queryParam("site","default").queryParam("agent","test-agent")
                .header("Authorization","Bearer "+token(dictionary)).header("Idempotency-Key",UUID.randomUUID().toString()))
            .andExpect(status().isForbidden());
    }
    @Test void secretRotationAndAuthorityAreEnforced()throws Exception{
        UUID tenant=tenant();var client=clients.create(actor,tenant,"Partner",Set.of("dictionaries:read"));String old=token(client);
        var rotated=clients.rotate(actor,client.client().id(),0);
        assertThat(rotated.secret()).isNotEqualTo(client.secret());
        // 过渡期间两把密钥均能认证，旧 Token 仍因版本变化撤销；过期后只接受新密钥。
        token(client);token(rotated);
        jdbc.update("UPDATE integration_client SET previous_secret_until=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",client.client().id());
        mvc.perform(post("/oauth2/token").with(httpBasic(client.client().id(),client.secret())).contentType("application/x-www-form-urlencoded").param("grant_type","client_credentials"))
            .andExpect(status().isUnauthorized());
        token(rotated);
        mvc.perform(get("/v1/dictionaries/sample/items").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->clients.create(UUID.randomUUID(),tenant,"Denied",Set.of("dictionaries:read"))).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(()->clients.create(actor,tenant,"Invalid",Set.of("unknown:write"))).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(()->clients.rotate(actor,client.client().id(),0)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        mvc.perform(post("/bff/platform/tenants/"+tenant+"/clients").with(authenticated(actor.toString(),0)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    private void dictionary(UUID tenant,String value){
        String dictionary=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tenant_dictionary(id,tenant_id,code,name,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'sample','Sample',UUID_TO_BIN(?),UUID_TO_BIN(?))",dictionary,tenant.toString(),actor.toString(),actor.toString());
        jdbc.update("INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'item',?,'Name',UUID_TO_BIN(?),UUID_TO_BIN(?))",UUID.randomUUID().toString(),tenant.toString(),dictionary,value,actor.toString(),actor.toString());
    }
    /** 真实写入重复同键只产生一次业务引用；参数变化、跨租户、无 scope 均拒绝。 */
    @Test void externalReferenceWritesAreIdempotentAndTenantScoped()throws Exception{
        UUID tenant=tenant();dictionary(tenant,"ALPHA");
        var client=clients.create(actor,tenant,"Writer",Set.of("dictionaries:read","dictionary-references:write"));String bearer=token(client);
        String body="{\"dictionaryCode\":\"sample\",\"itemValue\":\"ALPHA\",\"referenceKey\":\"order-42\"}";
        String key="reference-request-"+UUID.randomUUID();
        var first=mvc.perform(post("/v1/dictionary-item-references").header("Authorization","Bearer "+bearer).header("Idempotency-Key",key).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse();
        String response=first.getContentAsString();
        mvc.perform(post("/v1/dictionary-item-references").header("Authorization","Bearer "+bearer).header("Idempotency-Key",key).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(content().json(response));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_dictionary_item_reference WHERE consumer=?",Integer.class,client.client().id())).isEqualTo(1);
        mvc.perform(post("/v1/dictionary-item-references").header("Authorization","Bearer "+bearer).header("Idempotency-Key",key).contentType("application/json").content(body.replace("order-42","order-43")))
            .andExpect(status().isConflict());
        var other=clients.create(actor,tenant(),"Other",Set.of("dictionary-references:write"));String otherBearer=token(other);
        mvc.perform(get(first.getHeader("Location")).header("Authorization","Bearer "+otherBearer)).andExpect(status().isNotFound());
        mvc.perform(post("/v1/dictionary-item-references").header("Authorization","Bearer "+otherBearer).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_reference_request WHERE client_id=?",Integer.class,other.client().id())).isZero();
    }
    /** 固定客户端行锁使同一幂等键并发请求返回相同结果，不能重复登记或覆盖参数。 */
    @Test void concurrentReferenceRetriesHaveOneBusinessEffect()throws Exception{
        UUID tenant=tenant();dictionary(tenant,"ALPHA");
        var client=clients.create(actor,tenant,"Writer",Set.of("dictionary-references:write"));String bearer=token(client),key="concurrent-key-"+UUID.randomUUID();
        var body="{\"dictionaryCode\":\"sample\",\"itemValue\":\"ALPHA\",\"referenceKey\":\"concurrent-order\"}";
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<String> call=()->{gate.await();return mvc.perform(post("/v1/dictionary-item-references").header("Authorization","Bearer "+bearer).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();};
            var first=pool.submit(call);var second=pool.submit(call);gate.countDown();assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_dictionary_item_reference WHERE consumer=?",Integer.class,client.client().id())).isEqualTo(1);
    }
}
