package com.appnexus.platform;

import com.appnexus.platform.security.EndpointOperation;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** 枚举所有正式平台端点，验证平台身份不能绕过稳定操作码，并检查目录登记与管理域。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class AllOperationAuthorizationTest extends DatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RequestMappingHandlerMapping requestMappingHandlerMapping;
    @Test void everyPlatformOperationDeniesAdministratorWithoutItsPermission()throws Exception{
        String account=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Operation test','unused')",account,"operation-"+account);
        jdbc.update("INSERT INTO platform_administrator(account_id) VALUES(UUID_TO_BIN(?))",account);
        int checked=0;
        for(var entry:requestMappingHandlerMapping.getHandlerMethods().entrySet()){
            var operation=entry.getValue().getMethodAnnotation(EndpointOperation.class);
            if(operation==null)continue;
            for(String path:entry.getKey().getPatternValues())if(path.startsWith("/bff/platform/")){
                String required=operation.platformPermission().isBlank()?operation.value():operation.platformPermission();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=? AND enabled=TRUE AND scope_type='PLATFORM'",Integer.class,required)).as(path+" -> "+required).isEqualTo(1);
                String resolved=path.replace("{capability}","READ").replaceAll("\\{[^}]+}",UUID.randomUUID().toString());
                for(var method:entry.getKey().getMethodsCondition().getMethods()){
                    var response=mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method.name()),resolved)
                        .with(authenticated(account,0)).with(csrf()).contentType("application/json").content("{}"))
                        .andReturn().getResponse();
                    assertThat(response.getStatus()).as(method+" "+path+" needs "+required+": "+response.getContentAsString()).isEqualTo(403);
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(50);
    }
    /** 能力快照必须使用同一权威查询，撤销后不能继续对前端返回旧操作。 */
    @Test void capabilitiesReflectAuthorityImmediately()throws Exception{
        String account=UUID.randomUUID().toString(),role=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Capabilities test','unused')",account,"capability-"+account);
        jdbc.update("INSERT INTO access_role(id,scope_type,role_type,code,name,status) VALUES(UUID_TO_BIN(?),'PLATFORM','CUSTOM',?,'Capabilities role','ENABLED')",role,"cap_"+role.replace("-",""));
        jdbc.update("INSERT INTO access_account_role(account_id,role_id,scope_type,assigned_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PLATFORM',UUID_TO_BIN(?))",account,role,account);
        jdbc.update("INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT UUID_TO_BIN(?),id,'PLATFORM',UUID_TO_BIN(?) FROM access_permission WHERE code='identity.account:revoke_sessions'",role,account);
        var granted=mvc.perform(get("/bff/operation-capabilities").with(authenticated(account,0))).andReturn().getResponse();
        assertThat(granted.getStatus()).isEqualTo(200);assertThat(granted.getContentAsString()).contains("identity.account:revoke_sessions").doesNotContain("identity.account:reset_password");
        jdbc.update("DELETE FROM access_role_permission WHERE role_id=UUID_TO_BIN(?)",role);
        var revoked=mvc.perform(get("/bff/operation-capabilities").with(authenticated(account,0))).andReturn().getResponse();
        assertThat(revoked.getStatus()).isEqualTo(200);assertThat(revoked.getContentAsString()).doesNotContain("identity.account:revoke_sessions");
    }
    /** 当前有效成员没有业务操作时，所有登记租户端点逐一拒绝，不能以成员资格代替功能权限。 */
    @Test void everyTenantBusinessOperationDeniesMemberWithoutPermission()throws Exception{
        String account=UUID.randomUUID().toString(),tenant=UUID.randomUUID().toString(),member=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Tenant operations','unused')",account,"tenant-operation-"+account);
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Operation tenant')",tenant);
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",member,tenant,account);
        var selected=mvc.perform(post("/bff/tenants/current").with(authenticated(account,0)).with(csrf()).contentType("application/json").content("{\"tenantId\":\""+tenant+"\"}"))
            .andReturn().getResponse();
        assertThat(selected.getStatus()).isEqualTo(200);var cookie=selected.getCookie("JSESSIONID");int checked=0;
        for(var entry:requestMappingHandlerMapping.getHandlerMethods().entrySet()){
            var operation=entry.getValue().getMethodAnnotation(EndpointOperation.class);if(operation==null)continue;
            for(String path:entry.getKey().getPatternValues())if(path.startsWith("/api/")&&!path.equals("/api/organization/capabilities")){
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE code=? AND enabled=TRUE AND scope_type='TENANT'",Integer.class,operation.value())).as(path).isEqualTo(1);
                String resolved=path.replace("{capability}","READ").replaceAll("\\{[^}]+}",UUID.randomUUID().toString());
                for(var method:entry.getKey().getMethodsCondition().getMethods()){
                    var response=mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method.name()),resolved).cookie(cookie).with(csrf()).contentType("application/json").content("{}"))
                        .andReturn().getResponse();
                    assertThat(response.getStatus()).as(method+" "+path+": "+response.getContentAsString()).isEqualTo(403);checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(35);
    }
    /** 拆分后的强制下线不隐式授予账号编辑或重置密码，撤权后下一请求立即拒绝。 */
    @Test void narrowAccountOperationDoesNotGrantOtherSensitiveActions()throws Exception{
        String actor=UUID.randomUUID().toString(),target=UUID.randomUUID().toString(),role=UUID.randomUUID().toString();
        for(String id:List.of(actor,target))jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Narrow operation','unused')",id,"narrow-"+id);
        jdbc.update("INSERT INTO platform_administrator(account_id) VALUES(UUID_TO_BIN(?))",actor);
        jdbc.update("INSERT INTO access_role(id,scope_type,role_type,code,name,status) VALUES(UUID_TO_BIN(?),'PLATFORM','CUSTOM',?,'Narrow role','ENABLED')",role,"narrow_"+role.replace("-",""));
        jdbc.update("INSERT INTO access_account_role(account_id,role_id,scope_type,assigned_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PLATFORM',UUID_TO_BIN(?))",actor,role,actor);
        jdbc.update("INSERT INTO access_role_permission(role_id,permission_id,scope_type,granted_by) SELECT UUID_TO_BIN(?),id,'PLATFORM',UUID_TO_BIN(?) FROM access_permission WHERE code='identity.account:revoke_sessions'",role,actor);
        var session=authenticated(actor,0);
        var allowed=mvc.perform(post("/bff/platform/users/"+target+"/revoke-sessions").with(session).with(csrf())).andReturn().getResponse();
        assertThat(allowed.getStatus()).isEqualTo(200);
        assertThat(mvc.perform(post("/bff/platform/users/"+target+"/password-recovery").with(session).with(csrf())).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(put("/bff/platform/users/"+target).with(session).with(csrf()).contentType("application/json").content("{}" )).andReturn().getResponse().getStatus()).isEqualTo(403);
        jdbc.update("DELETE FROM access_role_permission WHERE role_id=UUID_TO_BIN(?)",role);
        assertThat(mvc.perform(post("/bff/platform/users/"+target+"/revoke-sessions").with(session).with(csrf())).andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
