package com.appnexus.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 正式过滤器、Cookie、CSRF、迁移及工作流桥接集成，测试账号和基础设施均隔离。 */
@SpringBootTest(
    properties = {"app.workflow.engine.enabled=true", "app.workflow.worker.enabled=false"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class WorkflowSecurityTest extends DatabaseTestSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Autowired
  com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;

  /** 菜单拆分不扩大任务工作台的管理权限，设计器仍以read为独立入口门槛。 */
  @Test void designerHasIndependentRegisteredPageAndOperationBoundary(){
    var page=com.appnexus.platform.configuration.NavigationPages.find("workflow/designer");
    org.assertj.core.api.Assertions.assertThat(page.path()).isEqualTo("/workflow/designer");
    org.assertj.core.api.Assertions.assertThat(page.permission()).isEqualTo("workflow:read");
    org.assertj.core.api.Assertions.assertThat(com.appnexus.platform.configuration.NavigationPages.operations("workflow/index")).doesNotContain("workflow:edit","workflow:publish");
    org.assertj.core.api.Assertions.assertThat(com.appnexus.platform.configuration.NavigationPages.operations("workflow/designer")).containsExactly("workflow:read","workflow:edit","workflow:publish");
    org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT permission_code FROM platform_menu WHERE component_key='workflow/designer'",String.class)).isEqualTo("workflow:read");
  }
  @Test
  void tenantAdminCanManageDraftsWhileMemberCannotAndCsrfIsRequired() throws Exception {
    String account = UUID.randomUUID().toString(),
        tenant = UUID.randomUUID().toString(),
        member = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO identity_account(id,username,display_name,password_hash)"
            + " VALUES(UUID_TO_BIN(?),?,'workflow-test','unused')",
        account,
        "workflow-" + account);
    jdbc.update(
        "INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'workflow-test')", tenant);
    jdbc.update(
        "INSERT INTO tenant_member(id,tenant_id,account_id)"
            + " VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
        member,
        tenant,
        account);
    var selected =
        mvc.perform(
                post("/bff/tenants/current")
                    .with(authenticated(account, 0))
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"tenantId\":\"" + tenant + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    var cookie = selected.getResponse().getCookie("JSESSIONID");
    mvc.perform(get("/api/workflow/definitions").cookie(cookie)).andExpect(status().isForbidden());
    mvc.perform(get("/api/workflow/tasks").cookie(cookie)).andExpect(status().isOk());
    bootstrap.initialize(UUID.fromString(tenant), UUID.fromString(member));
    mvc.perform(get("/api/workflow/definitions").cookie(cookie)).andExpect(status().isOk());
    String body =
        "{\"revision\":-1,\"model\":{\"name\":\"Draft\",\"objectType\":\"Document\",\"purpose\":\"Review\",\"mode\":\"APPROVAL\",\"entryCapability\":\"none\",\"nodes\":[],\"edges\":[],\"matches\":[],\"terminalActions\":{}}}";
    mvc.perform(
            put("/api/workflow/definitions/draft")
                .cookie(cookie)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/workflow/definitions/draft")
                .cookie(cookie)
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    String key = UUID.randomUUID().toString();
    for (int attempt = 0; attempt < 2; attempt++)
      mvc.perform(
              put("/api/workflow/definitions/draft")
                  .cookie(cookie)
                  .with(csrf())
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.revision").value(0));
    mvc.perform(
            post("/api/workflow/definitions/draft/publish")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"revision\":0}"))
        .andExpect(status().isBadRequest());
    jdbc.update("UPDATE tenant_member SET enabled=FALSE WHERE id=UUID_TO_BIN(?)", member);
    mvc.perform(get("/api/workflow/tasks").cookie(cookie)).andExpect(status().isConflict());
  }
}
