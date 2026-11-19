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
}
