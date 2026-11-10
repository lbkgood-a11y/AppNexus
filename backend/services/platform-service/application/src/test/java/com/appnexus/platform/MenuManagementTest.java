package com.appnexus.platform;

import com.appnexus.platform.configuration.MenuManagement;
import com.appnexus.platform.configuration.NavigationQuery;
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
 @Autowired NavigationQuery navigation;
 @Autowired com.appnexus.platform.authorization.OrganizationGrantManagement organizationGrants;
 UUID actor;
 @BeforeEach void initialize(){actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));administration.initializeAdministrator("admin-test");}
 private MenuManagement.Command command(UUID parent,MenuManagement.Type type,String code){return new MenuManagement.Command(parent,type,code,"菜单","Menu",null,null,"ri/menu-line",null,10,true,true,MenuManagement.Scope.PLATFORM,type==MenuManagement.Type.EXTERNAL?"https://example.com/track":null);}


 @Test void managesTreeWithOptimisticVersionAndAudit(){
  var root=menus.create(actor,command(null,MenuManagement.Type.DIRECTORY,"system"));
  var child=menus.create(actor,command(root.id(),MenuManagement.Type.EXTERNAL,"system.menu"));
  assertThat(menus.list(actor)).extracting(MenuManagement.Menu::id).contains(root.id(),child.id());
  assertThatThrownBy(()->menus.update(actor,root.id(),root.version(),command(child.id(),MenuManagement.Type.DIRECTORY,"system"))).isInstanceOf(IllegalArgumentException.class);
  var updated=menus.update(actor,child.id(),child.version(),command(root.id(),MenuManagement.Type.EXTERNAL,"system.menu"));
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

 /** 外链只允许受控浏览器跳转，危险协议、凭据、反斜杠及伪造组件均拒绝。 */
 @Test void rejectsUnsafeExternalAddressesAndUnregisteredPages(){
  for(String address:java.util.List.of("javascript:alert(1)","//example.com","https://user:password@example.com","https://example.com\\evil","https://example.com\n")) {
   assertThatThrownBy(()->menus.create(actor,external(null,"unsafe"+UUID.randomUUID(),address,MenuManagement.Scope.AUTHENTICATED,1,true)))
    .isInstanceOf(IllegalArgumentException.class);
  }
  assertThatThrownBy(()->menus.create(actor,new MenuManagement.Command(null,MenuManagement.Type.MENU,"unknown.page","页面","Page","/anything","anything",null,null,0,true,true)))
   .isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->menus.create(actor,new MenuManagement.Command(null,MenuManagement.Type.MENU,"changed.permission","页面","Page","/platform/users","platform/users",null,null,0,true,true)))
   .isInstanceOf(IllegalArgumentException.class);
 }

 /** 同值排序按 code 稳定，隐藏页面仍可注册；停用祖先会同时取消其后代访问资格。 */
 @Test void navigationFiltersAncestorsAndUsesStableOrder(){
  var root=menus.create(actor,command(null,MenuManagement.Type.DIRECTORY,"navigation-test"));
  var last=menus.create(actor,external(root.id(),"navigation-test.z","https://example.com/z",MenuManagement.Scope.PLATFORM,7,true));
  var first=menus.create(actor,external(root.id(),"navigation-test.a","https://example.com/a",MenuManagement.Scope.PLATFORM,7,false));
  var entries=navigation.current(actor,null,null,0).items().stream().filter(item->root.id().equals(item.parentId())).toList();
  assertThat(entries).extracting(MenuManagement.Menu::id).containsExactly(first.id(),last.id());
  assertThat(entries.getFirst().visible()).isFalse();
  menus.update(actor,root.id(),root.version(),new MenuManagement.Command(null,MenuManagement.Type.DIRECTORY,root.code(),"菜单","Menu",null,null,null,null,10,true,false));
  assertThat(navigation.current(actor,null,null,0).items()).extracting(MenuManagement.Menu::id).doesNotContain(root.id(),first.id(),last.id());
 }

 /** 普通账号仅获登录公共外链，不泄露平台目录；本人导航无需菜单管理权限。 */
 @Test void currentNavigationIsPersonalAndDoesNotRequireMenuAdministration() throws Exception {
  UUID ordinary=UUID.randomUUID();
  jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",ordinary.toString(),"navigation-"+ordinary,"Navigation","not-a-login-password");
  var link=menus.create(actor,external(null,"public-"+ordinary,"https://example.com/help",MenuManagement.Scope.AUTHENTICATED,4,true));
  assertThat(navigation.current(ordinary,null,null,0).items()).extracting(MenuManagement.Menu::id).containsExactly(link.id());
  mvc.perform(get("/bff/navigation").with(authenticated(ordinary.toString(),0))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get("/bff/platform/menus/pages").with(authenticated(ordinary.toString(),0))).andExpect(status().isForbidden());
  mvc.perform(get("/bff/platform/authorization-navigation").with(authenticated(ordinary.toString(),0))).andExpect(status().isForbidden());
  mvc.perform(get("/bff/platform/authorization-navigation").with(authenticated(actor.toString(),0))).andExpect(status().isOk());
  mvc.perform(get("/bff/navigation")).andExpect(status().isUnauthorized());
  menus.delete(actor,link.id(),link.version());
 }

 /** 授权视图按域提供完整菜单，不取本人可见菜单；页面资源映射必须明确登记。 */
 @Test void authorizationCatalogPreservesPageResourceRelationships(){
  var platform=navigation.authorizationCatalog(MenuManagement.Scope.PLATFORM);
  assertThat(platform).anySatisfy(item->{assertThat(item.resources()).contains("configuration.menu");assertThat(item.permission()).isEqualTo("configuration.menu:read");});
  assertThat(platform).noneSatisfy(item->assertThat(item.resources()).contains("organization.unit"));
  assertThat(navigation.authorizationCatalog(MenuManagement.Scope.TENANT)).anySatisfy(item->assertThat(item.resources()).contains("organization.unit"));
 }

 /** 租户上下文决定入口域，权限撤销后的下一次快照立即移除组织页面。 */
 @Test void tenantNavigationFollowsRealPermissionDecisions(){
  UUID owner=UUID.randomUUID(),account=UUID.randomUUID();
  for(UUID id:java.util.List.of(owner,account))jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,?,?)",id.toString(),"navigation-"+id,"Navigation","not-a-login-password");
  var tenant=administration.createTenant(actor,UUID.randomUUID(),"Navigation tenant",owner);
  administration.addMember(actor,tenant.id(),account);
  UUID member=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.id().toString(),account.toString()));
  UUID ownerMember=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",String.class,tenant.id().toString(),owner.toString()));
  assertThat(navigation.current(account,tenant.id(),member,1).items()).isEmpty();
  organizationGrants.grant(tenant.id(),member,ownerMember,com.appnexus.platform.authorization.OrganizationCapability.READ,null);
  assertThat(navigation.current(account,tenant.id(),member,1).items()).extracting(MenuManagement.Menu::component).contains("organization/index").doesNotContain("roles/index");
  organizationGrants.revoke(tenant.id(),member,ownerMember,com.appnexus.platform.authorization.OrganizationCapability.READ);
  assertThat(navigation.current(account,tenant.id(),member,1).items()).isEmpty();
 }

 private MenuManagement.Command external(UUID parent,String code,String address,MenuManagement.Scope scope,int sort,boolean visible){
  return new MenuManagement.Command(parent,MenuManagement.Type.EXTERNAL,code,"外部入口","External entry",null,null,null,null,sort,visible,true,scope,address);
 }
 /** 同一资源不同页面只登记实际操作，菜单未实现的发布/独立启停不再可授予。 */
 @Test void pageOperationsMatchActualActions(){
  var nodes=navigation.authorizationCatalog(MenuManagement.Scope.PLATFORM);
  assertThat(nodes.stream().filter(item->item.titleZh().equals("平台角色")).findFirst().orElseThrow().operations())
   .contains("access.role:create","access.role:delete").doesNotContain("access.role:grant");
  assertThat(nodes.stream().filter(item->item.titleZh().equals("授权中心")).findFirst().orElseThrow().operations())
   .contains("access.role:read","access.role:grant").doesNotContain("access.role:create","access.role:delete","access.role:enable");
  assertThat(nodes.stream().filter(item->item.permission()!=null&&item.permission().equals("configuration.menu:read")).findFirst().orElseThrow().operations())
   .containsExactly("configuration.menu:read","configuration.menu:create","configuration.menu:update","configuration.menu:delete");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE enabled=TRUE AND code IN ('configuration.menu:enable','configuration.menu:publish')",Integer.class)).isZero();
  for(var page:com.appnexus.platform.configuration.NavigationPages.all())for(String operation:com.appnexus.platform.configuration.NavigationPages.operations(page.key()))
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_permission WHERE enabled=TRUE AND code=?",Integer.class,operation)).as(page.key()+" -> "+operation).isEqualTo(1);
 }
}
