package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.security.PolicyTemplates;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** 策略模板复用正式授权用例，不能跨管理域套用或跳过目标版本、字段和范围校验。 */
@SpringBootTest
@ActiveProfiles("local")
class PolicyTemplateTest extends DatabaseTestSupport {
    @Autowired PolicyTemplates templates;
    @Autowired RoleManagement roles;
    @Autowired RolePermissionManagement permissions;
    @Autowired PermissionCatalog catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired MyBatisPlatformAdministration administration;
    private UUID actor;
    @BeforeEach void initialize(){administration.initializeAdministrator("admin-test");actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));}
    private RoleManagement.Role role(RoleManagement.Context context){return roles.create(context,new RoleManagement.Create("template_"+UUID.randomUUID().toString().replace("-",""),"Template role",null,100));}
    @Test void snapshotAppliesThroughVersionedAuthorizationAndRetainsSource(){
        var context=new RoleManagement.Context(actor,null,null);var source=role(context);var target=role(context);
        UUID read=catalog.list(PermissionCatalog.ScopeType.PLATFORM,false).stream().filter(value->value.code().equals("tenant:read")).findFirst().orElseThrow().id();
        permissions.replaceAuthorization(context,source.id(),0,Set.of(read),Set.of(),Set.of(),"Prepare template");
        var template=templates.save(context,source.id(),"Read template");
        var result=templates.apply(context,target.id(),template.id(),0,"Apply reviewed template");
        assertThat(result.permissionIds()).containsExactly(read);
        assertThat(permissions.getAuthorization(context,source.id()).permissionIds()).containsExactly(read);
        assertThatThrownBy(()->templates.apply(context,target.id(),template.id(),0,"Stale retry")).isInstanceOf(RoleManagementException.class);
        assertThat(templates.list(context,target.id())).extracting(PolicyTemplates.View::id).contains(template.id());
    }
    @Test void ordinaryActorAndForeignTemplateAreRejected(){
        var context=new RoleManagement.Context(actor,null,null);var source=role(context);var target=role(context);
        var template=templates.save(context,source.id(),"Owned template");
        assertThatThrownBy(()->templates.list(new RoleManagement.Context(UUID.randomUUID(),null,null),target.id())).isInstanceOf(SecurityException.class);
        jdbc.update("UPDATE access_policy_template SET owner_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",UUID.randomUUID().toString(),template.id().toString());
        assertThatThrownBy(()->templates.apply(context,target.id(),template.id(),0,"Cross owner")).isInstanceOf(org.springframework.web.ErrorResponseException.class);
    }
}
