package com.appnexus.platform;

import com.appnexus.platform.identity.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** 真实 MySQL/Valkey 验证 MFA 绑定、登录强制验证、恢复码防重放和旧会话撤销。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class MfaTest extends DatabaseTestSupport {
    @Autowired MultiFactorAuthentication mfa;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired PasswordRecovery recovery;
    private UUID account(){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",
            id.toString(),"mfa-"+id,"MFA test",encoder.encode("mfa-test-password-2026"));return id;
    }
    /** Base32 只为从公开绑定响应连接标准认证器向量，不读取或绕过密钥加密。 */
    private byte[] decode(String input){
        byte[] output=new byte[input.length()*5/8];int buffer=0,bits=0,index=0;
        for(char character:input.toCharArray()){
            buffer=(buffer<<5)|"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character);bits+=5;
            if(bits>=8){bits-=8;output[index++]=(byte)(buffer>>>bits);}
        }return output;
    }
    private List<String> activate(UUID account){
        String secret=mfa.begin(account);
        assertThat(mfa.enabled(account)).isFalse();
        byte[] encrypted=jdbc.queryForObject("SELECT encrypted_secret FROM identity_account_mfa WHERE account_id=UUID_TO_BIN(?)",byte[].class,account.toString());
        assertThat(encrypted).isNotEqualTo(decode(secret));
        return mfa.activate(account,Totp.code(decode(secret),Instant.now().getEpochSecond()/30));
    }
    @Test void activationRevokesOldSessionAndRecoveryCannotReplay() throws Exception {
        UUID account=account();var old=authenticated(account.toString(),0);
        var recovery=activate(account);
        assertThat(mfa.enabled(account)).isTrue();assertThat(recovery).hasSize(10).doesNotHaveDuplicates();
        mvc.perform(get("/bff/session").with(old)).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->mfa.begin(account)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mfa.verify(account,recovery.getFirst())).isTrue();
        assertThat(mfa.verify(account,recovery.getFirst())).isFalse();
        assertThat(mfa.disable(account,"0000000")).isFalse();
        assertThat(mfa.disable(account,recovery.get(1))).isTrue();
        assertThat(mfa.enabled(account)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_mfa_recovery WHERE account_id=UUID_TO_BIN(?)",Integer.class,account.toString())).isZero();
    }
    @Test void passwordAloneCannotEstablishMfaAccountSession() throws Exception {
        UUID account=account();var recovery=activate(account);
        mvc.perform(post("/login").with(csrf()).param("username","mfa-"+account).param("password","mfa-test-password-2026"))
            .andExpect(redirectedUrl("http://localhost:8848/#/login?credentials=1&error=1"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_security_log WHERE account_id=UUID_TO_BIN(?) AND action='MFA_FAILED'",Integer.class,account.toString())).isEqualTo(1);
        mvc.perform(post("/login").with(csrf()).param("username","mfa-"+account).param("password","mfa-test-password-2026").param("mfaCode",recovery.getFirst()))
            .andExpect(redirectedUrl("/oauth2/authorization/appnexus"));
        assertThat(mfa.verify(account,recovery.getFirst())).isFalse();
    }
    @Test void selfServiceRequiresPasswordAndCsrf() throws Exception {
        UUID account=account();
        mvc.perform(post("/bff/account/mfa/enrollment").with(authenticated(account.toString(),0)).contentType("application/json").content("{\"password\":\"mfa-test-password-2026\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/bff/account/mfa/enrollment").with(authenticated(account.toString(),0)).with(csrf()).contentType("application/json").content("{\"password\":\"wrong\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));
        mvc.perform(post("/bff/account/mfa/enrollment").with(authenticated(account.toString(),0)).with(csrf()).contentType("application/json").content("{\"password\":\"mfa-test-password-2026\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.secret").isString());
    }
    /** 同一恢复码并发提交只能成功一次；数据库锁而非单实例内存保证此性质。 */
    @Test void concurrentRecoveryConsumptionHasOnlyOneWinner() throws Exception {
        UUID account=account();String code=activate(account).getFirst();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var gate=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{gate.await();return mfa.verify(account,code);});
            var second=pool.submit(()->{gate.await();return mfa.verify(account,code);});
            gate.countDown();assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(true,false);
        }
    }
    /** 绑定后旧因子时间缺失的会话不能改密；step-up 轮换 Cookie 后方可执行敏感操作。 */
    @Test void enrolledAccountMustStepUpBeforeChangingPassword() throws Exception {
        UUID account=account();var codes=activate(account);
        var session=authenticated(account.toString(),1);
        mvc.perform(post("/bff/account/password").with(session).with(csrf()).contentType("application/json")
            .content("{\"currentPassword\":\"mfa-test-password-2026\",\"newPassword\":\"another-test-password-2026\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MFA_STEP_UP_REQUIRED"));
        mvc.perform(post("/bff/account/mfa/step-up").with(session).with(csrf()).contentType("application/json")
            .content("{\"password\":\"mfa-test-password-2026\",\"code\":\""+codes.getFirst()+"\"}"))
            .andExpect(status().isOk());
    }
    /** 恢复码找回只重置密码，不能把已启用账号降级为仅密码认证。 */
    @Test void passwordRecoveryConsumesRecoveryCodeAndPreservesMfa(){
        UUID account=account();var codes=activate(account);
        String ticket=recovery.recover("mfa-"+account,codes.getFirst());
        assertThat(mfa.verify(account,codes.getFirst())).isFalse();
        assertThat(recovery.complete("mfa-"+account,ticket,encoder.encode("recovered-password-2026"))).isTrue();
        assertThat(mfa.enabled(account)).isTrue();
        assertThatThrownBy(()->recovery.recover("mfa-"+account,codes.getFirst())).isInstanceOf(IllegalArgumentException.class);
    }
    /** 真实 HTTP 激活保留本次认证会话，防止恢复码响应后被后台会话探测立即清除。 */
    @Test void activationRotatesAndKeepsProvenCurrentSession() throws Exception {
        UUID account=account();String secret=mfa.begin(account);
        String code=Totp.code(decode(secret),Instant.now().getEpochSecond()/30);
        var response=mvc.perform(post("/bff/account/mfa/activation").with(authenticated(account.toString(),0)).with(csrf())
            .contentType("application/json").content("{\"password\":\"mfa-test-password-2026\",\"code\":\""+code+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.codes.length()").value(10)).andReturn().getResponse();
        var cookie=response.getCookie("JSESSIONID");assertThat(cookie).isNotNull();
        mvc.perform(get("/bff/account/mfa").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.steppedUp").value(true));
    }

    /** 十个代管命令经过真实平台认证和 MySQL，敏感操作仅在近期 MFA 后执行。 */
    @Test void platformCommandsKeepAdministrationAndRecentMfaBoundaries() throws Exception {
        UUID bootstrapAdmin=UUID.fromString(jdbc.queryForObject(
            "SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        administration.initializeAdministrator("admin-test");
        UUID admin=account();
        jdbc.update("INSERT INTO platform_administrator(account_id) VALUES(UUID_TO_BIN(?))",admin.toString());
        jdbc.update("""
            INSERT INTO access_account_role(account_id,role_id,scope_type,assigned_by)
            SELECT UUID_TO_BIN(?),role_id,scope_type,UUID_TO_BIN(?) FROM access_account_role
             WHERE account_id=UUID_TO_BIN(?) AND scope_type='PLATFORM'
            """,admin.toString(),bootstrapAdmin.toString(),bootstrapAdmin.toString());
        var adminSession=authenticated(admin.toString(),0);
        String username="typed-account-"+UUID.randomUUID();
        var created=mvc.perform(platformCommand("Account","create",null,Map.of(
                "username",username,"displayName","命令账号","password","isolated-platform-command-2026"))
            .with(adminSession).with(csrf())).andExpect(status().isOk()).andReturn().getResponse();
        var json=new tools.jackson.databind.ObjectMapper();
        String target=json.readTree(created.getContentAsString()).path("output").path("result").path("id").asText();
        UUID targetId=UUID.fromString(target);
        mvc.perform(platformCommand("Account","list",null,Map.of("query",username)).with(adminSession).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.items[0].id").value(target));
        mvc.perform(platformCommand("Account","update",target,Map.of("displayName","命令账号更新"))
                .with(adminSession).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.displayName").value("命令账号更新"));
        mvc.perform(platformCommand("Account","setEnabled",target,Map.of("enabled",false)).with(adminSession))
            .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT enabled FROM identity_account WHERE id=UUID_TO_BIN(?)",Boolean.class,target)).isTrue();
        mvc.perform(platformCommand("Account","setEnabled",target,Map.of("enabled",false)).with(adminSession).with(csrf()))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT enabled FROM identity_account WHERE id=UUID_TO_BIN(?)",Boolean.class,target)).isFalse();
        mvc.perform(platformCommand("Account","setEnabled",target,Map.of("enabled",true)).with(adminSession).with(csrf()))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT enabled FROM identity_account WHERE id=UUID_TO_BIN(?)",Boolean.class,target)).isTrue();

        UUID tenant=UUID.randomUUID(),otherTenant=UUID.randomUUID();
        for(var id:List.of(tenant,otherTenant))
            jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'平台代管隔离验收')",id.toString());
        var memberTarget=Map.of("targetTenantId",tenant.toString());
        mvc.perform(platformCommand("TenantMember","createManaged",null,
                Map.of("targetTenantId",tenant.toString(),"accountId",target))
            .with(adminSession).with(csrf())).andExpect(status().isOk());
        String member=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",
                String.class,tenant.toString(),target);
        mvc.perform(platformCommand("TenantMember","listManaged",null,memberTarget).with(adminSession).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.items[0].memberId").value(member));
        mvc.perform(platformCommand("TenantMember","setEnabledManaged",member,
                Map.of("targetTenantId",otherTenant.toString(),"enabled",false)).with(adminSession).with(csrf()))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT enabled FROM tenant_member WHERE id=UUID_TO_BIN(?)",Boolean.class,member)).isTrue();
        mvc.perform(platformCommand("TenantMember","setEnabledManaged",member,
                Map.of("targetTenantId",tenant.toString(),"enabled",false)).with(adminSession).with(csrf()))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT enabled FROM tenant_member WHERE id=UUID_TO_BIN(?)",Boolean.class,member)).isFalse();
        mvc.perform(platformCommand("TenantMember","removeManaged",member,memberTarget).with(adminSession).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(get("/bff/platform/tenants/"+tenant+"/members").with(adminSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].removed").value(true));

        UUID powerless=account();
        jdbc.update("INSERT INTO platform_administrator(account_id) VALUES(UUID_TO_BIN(?))",powerless.toString());
        mvc.perform(platformCommand("Account","list",null,Map.of()).with(authenticated(powerless.toString(),0)).with(csrf()))
            .andExpect(status().isForbidden());
        var restrictedRole=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO access_role(id,scope_type,role_type,code,name,status) VALUES(UUID_TO_BIN(?),'PLATFORM','CUSTOM',?,'平台命令只读','ENABLED')",
            restrictedRole,"typed_ro_"+restrictedRole.replace("-",""));
        jdbc.update("INSERT INTO access_account_role(account_id,role_id,scope_type,assigned_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PLATFORM',UUID_TO_BIN(?))",
            powerless.toString(),restrictedRole,admin.toString());
        jdbc.update("INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT UUID_TO_BIN(?),id,'PLATFORM',UUID_TO_BIN(?) FROM access_permission WHERE code='identity.account:read'",
            restrictedRole,admin.toString());
        var readOnly=authenticated(powerless.toString(),0);
        mvc.perform(platformCommand("Account","list",null,Map.of("query",username)).with(readOnly).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.output.result.items[0].id").value(target));
        mvc.perform(platformCommand("Account","setEnabled",target,Map.of("enabled",false)).with(readOnly).with(csrf()))
            .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT enabled FROM identity_account WHERE id=UUID_TO_BIN(?)",Boolean.class,target)).isTrue();
        mvc.perform(platformCommand("BusinessPartner","list",null,Map.of()).with(adminSession).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_UNAVAILABLE"));
        mvc.perform(platformCommand("Account","mfaStatus",null,Map.of()).with(adminSession).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_UNAVAILABLE"));
        mvc.perform(platformCommand("Account","list",null,Map.of("actorId",powerless.toString())).with(adminSession).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_OBJECT_COMMAND_INVALID"));

        var codes=activate(admin);
        var beforeStepUp=authenticated(admin.toString(),1);
        long version=credentialVersion(targetId);
        var expired=authenticated(admin.toString(),1);
        org.springframework.test.web.servlet.request.RequestPostProcessor expiredStepUp=request->{
            var prepared=expired.postProcessRequest(request);
            var cookie=prepared.getCookies()[0].getValue();
            var key=new String(Base64.getDecoder().decode(cookie),java.nio.charset.StandardCharsets.UTF_8);
            var session=sessionRepository.findById(key);
            session.setAttribute(com.appnexus.platform.security.MfaController.VERIFIED_AT,Instant.now().minusSeconds(301));
            sessionRepository.save(session);
            return prepared;
        };
        for(var authentication:List.of(beforeStepUp,expiredStepUp))
            for(String operation:List.of("revokeSessions","issueRecovery")){
                mvc.perform(platformCommand("Account",operation,target,Map.of()).with(authentication).with(csrf()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MFA_STEP_UP_REQUIRED"));
            }
        assertThat(credentialVersion(targetId)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_password_recovery WHERE account_id=UUID_TO_BIN(?)",
            Integer.class,target)).isZero();
        mvc.perform(post("/bff/platform/users/"+target+"/revoke-sessions").with(beforeStepUp).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MFA_STEP_UP_REQUIRED"));
        var stepped=mvc.perform(post("/bff/account/mfa/step-up").with(beforeStepUp).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                    "password","mfa-test-password-2026","code",codes.getFirst()))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("JSESSIONID");
        assertThat(stepped).isNotNull();
        mvc.perform(platformCommand("Account","revokeSessions",target,Map.of()).cookie(stepped).with(csrf()))
            .andExpect(status().isOk());
        assertThat(credentialVersion(targetId)).isGreaterThan(version);
        mvc.perform(platformCommand("Account","issueRecovery",target,Map.of()).cookie(stepped).with(csrf()))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.output.result.token").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_password_recovery WHERE account_id=UUID_TO_BIN(?)",
            Integer.class,target)).isEqualTo(1);
    }

    @Autowired com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration administration;

    private long credentialVersion(UUID account){
        return jdbc.queryForObject("SELECT version FROM identity_account WHERE id=UUID_TO_BIN(?)",Long.class,account.toString());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder platformCommand(
            String object,String operation,String id,Map<String,?> command){
        var body=new LinkedHashMap<String,Object>();
        body.put("objectType",object);body.put("operationCode",operation);body.put("operationId",object+"."+operation);
        body.put("objectId",id);body.put("basisVersion",0);body.put("command",command);
        return post("/bff/platform/business-object-executions").header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(new tools.jackson.databind.ObjectMapper().writeValueAsString(body));
    }

}
