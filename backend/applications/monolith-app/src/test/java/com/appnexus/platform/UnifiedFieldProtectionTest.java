package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import com.appnexus.platform.security.FieldProtectionResponseAdvice;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实数据库验证中央策略对任意已声明资源自动执行，测试资源不注册接口专用脱敏器。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@org.springframework.context.annotation.Import(UnifiedFieldProtectionTest.Declaration.class)
public class UnifiedFieldProtectionTest extends DatabaseTestSupport {
    @Autowired FieldCatalog catalog;
    @Autowired FieldProtection protection;
    @Autowired RoleManagement roles;
    @Autowired RolePermissionManagement permissions;
    @Autowired SubjectRoleManagement subjects;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    /** 响应仅包含业务数据，不再重复声明资源、算法或字段能力。 */
    public record Fixture(String contact,String legalName,String identityNo,String publicLabel) {}

    /** 测试字段也必须通过统一业务对象Provider进入目录，防止旧扫描路径回归。 */
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class Declaration {
        @org.springframework.context.annotation.Bean
        com.appnexus.contracts.businessobject.BusinessObjects.Provider fixtureObject(){
            return ()->new com.appnexus.contracts.businessobject.BusinessObjects.Descriptor("FieldFixture","字段测试","Field fixture","test",1,
                com.appnexus.contracts.businessobject.BusinessObjects.Category.SYSTEM,List.of(),List.of(),
                new com.appnexus.contracts.businessobject.BusinessObjects.DataAccess("PLATFORM",List.of(
                    new com.appnexus.contracts.businessobject.BusinessObjects.DataResource("test.profile","","测试资料","Test profile",List.of(
                        field("contact","联系邮箱","Contact email","EMAIL"),field("legalName","姓名","Name","PERSON_NAME"),field("identityNo","证件号码","Identity number","IDENTIFIER")),Fixture.class.getName())),Map.of()));
        }
        private static com.appnexus.contracts.businessobject.BusinessObjects.DataField field(String key,String zh,String en,String kind){return new com.appnexus.contracts.businessobject.BusinessObjects.DataField(key,zh,en,kind,Set.of("MASKED","HIDDEN"));}
    }

    /** 普通分页响应没有保护注解；管线必须递归处理集合内的敏感资源。 */
    public record Envelope(List<Fixture> items,long total) {}

    /** 独立 MockMvc 夹具没有权限逻辑；安全判定由真实策略与统一 Advice 完成。 */
    @RestController
    public static class FixtureController {
        @GetMapping("/test/field-projection") public Envelope response(){return new Envelope(List.of(new Fixture("alice@example.test","张小明","TEST12345678","公开说明")),1);}
    }

    @Test void declarationIsDiscoverableAndCentralRulesProtectNestedHttpResponse() throws Exception {
        UUID actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));
        administration.initializeAdministrator("admin-test");
        UUID target=UUID.randomUUID();administration.createUser(actor,target,"unified-"+target,"Unified target",encoder.encode("test-password-2026"));
        var context=new RoleManagement.Context(actor,null,null);
        assertThat(catalog.list(PermissionCatalog.ScopeType.PLATFORM)).filteredOn(item->item.resource().equals("test.profile")).extracting(FieldSecurity.Descriptor::field).containsExactlyInAnyOrder("contact","legalName","identityNo");
        var role=roles.create(context,new RoleManagement.Create("unified_"+UUID.randomUUID().toString().replace("-",""),"集中脱敏",null,100));
        var policies=Set.of(new FieldSecurity.Policy("test.profile","contact",FieldSecurity.Mode.MASKED),new FieldSecurity.Policy("test.profile","legalName",FieldSecurity.Mode.HIDDEN),new FieldSecurity.Policy("test.profile","identityNo",FieldSecurity.Mode.MASKED));
        permissions.replaceAuthorization(context,role.id(),role.version(),Set.of(),Set.of(),policies,"统一配置多个字段");
        var assigned=subjects.accountRoles(actor,target);subjects.replaceAccountRoles(actor,target,assigned.authorityVersion(),Set.of(role.id()),"应用字段策略");
        // 生产DTO也由同一Provider驱动，验证迁移后历史键仍生效，错误管理域不能读取原文。
        var productionPolicies=new HashSet<>(policies);
        productionPolicies.add(new FieldSecurity.Policy("identity.account","email",FieldSecurity.Mode.MASKED));
        productionPolicies.add(new FieldSecurity.Policy("identity.member","displayName",FieldSecurity.Mode.HIDDEN));
        var current=permissions.getAuthorization(context,role.id());
        permissions.replaceAuthorization(context,role.id(),current.roleVersion(),Set.of(),Set.of(),productionPolicies,"验证历史生产字段策略");
        var user=new com.appnexus.platform.tenant.PlatformAdministration.User(target,"fixture","公开名","alice@example.test",false,null,false,true);
        assertThat(protection.protect(AccessAuthorization.Subject.platform(target),user).email()).isEqualTo("a***@example.test");
        var member=new com.appnexus.platform.tenant.PlatformAdministration.Member(UUID.randomUUID(),target,"fixture","成员原文",true,false);
        assertThat(protection.protect(AccessAuthorization.Subject.platform(target),member).displayName()).isNull();
        assertThatThrownBy(()->protection.protect(AccessAuthorization.Subject.tenant(target,UUID.randomUUID(),UUID.randomUUID()),user)).isInstanceOf(SecurityException.class);
        var original=new Envelope(List.of(new Fixture("alice@example.test","张小明","TEST12345678","公开说明")),1);
        var masked=protection.protect(AccessAuthorization.Subject.platform(target),original);
        assertThat(masked.items().getFirst().contact()).isEqualTo("a***@example.test");
        assertThat(masked.items().getFirst().legalName()).isNull();
        assertThat(masked.items().getFirst().identityNo()).isEqualTo("****5678");
        assertThat(original.items().getFirst().legalName()).isEqualTo("张小明");
        var mvc=MockMvcBuilders.standaloneSetup(new FixtureController()).setControllerAdvice(new FieldProtectionResponseAdvice(protection,new MockHttpServletRequest())).build();
        var security=SecurityContextHolder.createEmptyContext();security.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(target.toString(),null,List.of()));SecurityContextHolder.setContext(security);
        try {
            mvc.perform(get("/test/field-projection")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].contact").value("a***@example.test")).andExpect(jsonPath("$.items[0].legalName").doesNotExist()).andExpect(jsonPath("$.items[0].identityNo").value("****5678")).andExpect(jsonPath("$.items[0].publicLabel").value("公开说明"));
        } finally {SecurityContextHolder.clearContext();}
        assertThatThrownBy(()->protection.protect(null,original)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->protection.protect(AccessAuthorization.Subject.tenant(target,UUID.randomUUID(),UUID.randomUUID()),original)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->permissions.replaceAuthorization(context,role.id(),permissions.getAuthorization(context,role.id()).roleVersion(),Set.of(),Set.of(),Set.of(new FieldSecurity.Policy("test.profile","notDeclared",FieldSecurity.Mode.MASKED)),"未声明字段不可配置")).isInstanceOf(IllegalArgumentException.class);
    }
}
