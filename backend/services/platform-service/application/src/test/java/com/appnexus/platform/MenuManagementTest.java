package com.appnexus.platform;

import com.appnexus.platform.configuration.MenuManagement;
import com.appnexus.platform.persistence.platformscope.MyBatisMenuManagement;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.ConcurrentModificationException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实 MySQL 验证菜单层级、乐观锁、平台授权、CSRF 与审计事务。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class MenuManagementTest extends DatabaseTestSupport {
 @Autowired MyBatisMenuManagement menus;
 @Autowired MyBatisPlatformAdministration administration;
 @Autowired JdbcTemplate jdbc;
 @Autowired MockMvc mvc;
 UUID actor;
 @BeforeEach void initialize(){actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));administration.initializeAdministrator("admin-test");}
 private MenuManagement.Command command(UUID parent,MenuManagement.Type type,String code){return new MenuManagement.Command(parent,type,code,"菜单","Menu",type==MenuManagement.Type.BUTTON?null:"/"+code,null,"ri/menu-line",type==MenuManagement.Type.BUTTON?code+":read":null,10,true,true);}

 @Test void managesTreeWithOptimisticVersionAndAudit(){
  var root=menus.create(actor,command(null,MenuManagement.Type.DIRECTORY,"system"));
  var child=menus.create(actor,command(root.id(),MenuManagement.Type.MENU,"system.menu"));
  assertThat(menus.list(actor)).extracting(MenuManagement.Menu::id).contains(root.id(),child.id());
  assertThatThrownBy(()->menus.update(actor,root.id(),root.version(),command(child.id(),MenuManagement.Type.DIRECTORY,"system"))).isInstanceOf(IllegalArgumentException.class);
  var updated=menus.update(actor,child.id(),child.version(),command(root.id(),MenuManagement.Type.MENU,"system.menu"));
  assertThat(updated.version()).isEqualTo(1);
  assertThatThrownBy(()->menus.delete(actor,child.id(),0)).isInstanceOf(ConcurrentModificationException.class);
  menus.delete(actor,child.id(),updated.version());menus.delete(actor,root.id(),root.version());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_menu_audit WHERE menu_id=UUID_TO_BIN(?)",Integer.class,child.id().toString())).isEqualTo(3);
 }
 @Test void ordinaryAccountIsDeniedAndWritesRequireCsrf() throws Exception {
  UUID ordinary=UUID.randomUUID();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",ordinary.toString(),"menu-ordinary","Menu Ordinary","$argon2id$v=19$m=19456,t=2,p=1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
  assertThatThrownBy(()->menus.list(ordinary)).isInstanceOf(SecurityException.class);
  mvc.perform(get("/bff/platform/menus").with(authenticated(ordinary.toString(),0))).andExpect(status().isForbidden());
  mvc.perform(post("/bff/platform/menus").with(authenticated(actor.toString(),0)).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
  mvc.perform(post("/bff/platform/menus").with(authenticated(actor.toString(),0)).with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
 }
}
