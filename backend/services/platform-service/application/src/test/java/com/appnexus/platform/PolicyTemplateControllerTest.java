package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** 模板 HTTP 契约保留 CSRF 和权威管理边界，不因模板保存接口绕过角色权限校验。 */
@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
class PolicyTemplateControllerTest extends DatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired RoleManagement roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MyBatisPlatformAdministration administration;
    @Test void platformSaveRequiresCsrfAndListsOnlyAuthorizedTemplates()throws Exception{
        administration.initializeAdministrator("admin-test");UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        var role=roles.create(new RoleManagement.Context(actor,null,null),new RoleManagement.Create("template_http_"+UUID.randomUUID().toString().replace("-",""),"Template HTTP",null,100));
        String path="/bff/platform/roles/"+role.id()+"/policy-templates";
        mvc.perform(post(path).with(authenticated(actor.toString(),0)).contentType("application/json").content("{\"name\":\"Snapshot\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path).with(authenticated(actor.toString(),0)).with(csrf()).contentType("application/json").content("{\"name\":\"Snapshot\"}")).andExpect(status().isOk());
        mvc.perform(get(path).with(authenticated(actor.toString(),0))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        UUID ordinary=UUID.randomUUID();jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'Template user','unused')",ordinary.toString(),"template-ordinary-"+ordinary);
        mvc.perform(get(path).with(authenticated(ordinary.toString(),0))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTHORIZATION_MANAGEMENT_DENIED"));
    }
}
