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
public class UnifiedFieldProtectionTest extends DatabaseTestSupport {
    @Autowired FieldCatalog catalog;
    @Autowired FieldProtection protection;
    @Autowired RoleManagement roles;
    @Autowired RolePermissionManagement permissions;
    @Autowired SubjectRoleManagement subjects;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    /** 仅声明业务字段语义；目录发现、策略校验与响应投影均不引用此测试类型。 */
    @ProtectedResource(value="test.profile",scope=PermissionCatalog.ScopeType.PLATFORM)
    public record Fixture(
        @ProtectedField(nameZh="联系邮箱",nameEn="Contact email",kind=FieldSecurity.Kind.EMAIL) String contact,
        @ProtectedField(nameZh="姓名",nameEn="Name",kind=FieldSecurity.Kind.PERSON_NAME) String legalName,
        @ProtectedField(nameZh="证件号码",nameEn="Identity number",kind=FieldSecurity.Kind.IDENTIFIER) String identityNo,
        String publicLabel) {}

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
