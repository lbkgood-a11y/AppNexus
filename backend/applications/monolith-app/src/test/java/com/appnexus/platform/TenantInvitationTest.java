package com.appnexus.platform;

import com.appnexus.platform.tenant.TenantInvitations;
import com.appnexus.platform.persistence.platformscope.MyBatisPlatformAdministration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** 邀请只由本人消费，过期/撤销/错账号拒绝，重新加入复用成员且不恢复授权。 */
@SpringBootTest
@ActiveProfiles("local")
class TenantInvitationTest extends DatabaseTestSupport {
    @Autowired TenantInvitations invitations;
    @Autowired MyBatisPlatformAdministration administration;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private UUID actor;
    @BeforeEach void initialize(){administration.initializeAdministrator("admin-test");actor=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_account WHERE username='admin-test'",String.class));}
    private UUID account(){UUID id=UUID.randomUUID();administration.createUser(actor,id,"invitation-"+id,"Invitation user",encoder.encode("invitation-password-2026"));return id;}
    private UUID tenant(){return administration.createTenant(actor,UUID.randomUUID(),"Invitation tenant",account()).id();}
    /** 原授权允许时可读写，字段隐藏后不可读或写，失败保持原邀请。 */
    @Test void inviteeFieldRuleBlocksDisclosureAndWrite(){
        UUID tenant=UUID.randomUUID(),target=account(),role=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'Invitation field test')",tenant.toString());
        var original=invitations.invite(actor,tenant,target).invitation();
        assertThat(invitations.list(actor,tenant)).singleElement()
            .extracting(TenantInvitations.View::accountId).isEqualTo(target);
        jdbc.update("INSERT INTO access_role(id,scope_type,role_type,code,name,status) VALUES(UUID_TO_BIN(?),'PLATFORM','CUSTOM',?,'Invitation field test','ENABLED')",role.toString(),"invitation_"+role.toString().replace("-",""));
        jdbc.update("INSERT INTO access_account_role(account_id,role_id,scope_type,assigned_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PLATFORM',UUID_TO_BIN(?))",actor.toString(),role.toString(),actor.toString());
        jdbc.update("INSERT INTO access_role_field_security(role_id,resource_type,field_code,display_mode) VALUES(UUID_TO_BIN(?),'tenant.invitation','accountId','HIDDEN')",role.toString());
        try{
            assertThat(invitations.list(actor,tenant)).singleElement()
                .satisfies(row->{assertThat(row.id()).isEqualTo(original.id());assertThat(row.accountId()).isNull();});
            assertThatThrownBy(()->invitations.invite(actor,tenant,target))
                .isInstanceOfSatisfying(TenantInvitations.Rejection.class,error->{
                    assertThat(error.status()).isEqualTo(403);
                    assertThat(error).hasMessage("DATA_FIELD_WRITE_DENIED");
                });
            assertThat(invitations.list(actor,tenant)).singleElement()
                .extracting(TenantInvitations.View::status).isEqualTo("PENDING");
        }finally{
            jdbc.update("DELETE FROM access_role_field_security WHERE role_id=UUID_TO_BIN(?)",role.toString());
        }
        assertThat(invitations.list(actor,tenant)).singleElement()
            .extracting(TenantInvitations.View::accountId).isEqualTo(target);
    }
    @Test void onlyInvitedAccountCanAcceptAndRejoinKeepsIdentityWithoutRoles(){
        UUID tenant=tenant(),target=account(),foreign=account();var invitation=invitations.invite(actor,tenant,target);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",Integer.class,tenant.toString(),target.toString())).isZero();
        assertThatThrownBy(()->invitations.accept(foreign,invitation.invitation().id(),invitation.token())).isInstanceOfSatisfying(com.appnexus.platform.tenant.TenantInvitations.Rejection.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error).hasMessage("INVITATION_INVALID");
        });
        var accepted=invitations.accept(target,invitation.invitation().id(),invitation.token());
        assertThatThrownBy(()->invitations.accept(target,invitation.invitation().id(),invitation.token())).isInstanceOfSatisfying(com.appnexus.platform.tenant.TenantInvitations.Rejection.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error).hasMessage("INVITATION_INVALID");
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_member_role WHERE member_id=UUID_TO_BIN(?)",Integer.class,accepted.memberId().toString())).isZero();
        administration.removeMember(actor,tenant,accepted.memberId());
        var rejoin=invitations.invite(actor,tenant,target);assertThat(invitations.accept(target,rejoin.invitation().id(),rejoin.token()).memberId()).isEqualTo(accepted.memberId());
    }
    @Test void expiredRevokedAndReplacedInvitationsCannotActivate(){
        UUID tenant=tenant(),target=account();var first=invitations.invite(actor,tenant,target);var second=invitations.invite(actor,tenant,target);
        assertThatThrownBy(()->invitations.accept(target,first.invitation().id(),first.token())).isInstanceOfSatisfying(com.appnexus.platform.tenant.TenantInvitations.Rejection.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error).hasMessage("INVITATION_INVALID");
        });
        invitations.revoke(actor,tenant,second.invitation().id());
        assertThatThrownBy(()->invitations.accept(target,second.invitation().id(),second.token())).isInstanceOfSatisfying(com.appnexus.platform.tenant.TenantInvitations.Rejection.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error).hasMessage("INVITATION_INVALID");
        });
        var expired=invitations.invite(actor,tenant,target);jdbc.update("UPDATE tenant_invitation SET expires_at=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=UUID_TO_BIN(?)",expired.invitation().id().toString());
        assertThatThrownBy(()->invitations.accept(target,expired.invitation().id(),expired.token())).isInstanceOfSatisfying(com.appnexus.platform.tenant.TenantInvitations.Rejection.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error).hasMessage("INVITATION_INVALID");
        });
        assertThatThrownBy(()->invitations.invite(target,tenant,account())).isInstanceOfSatisfying(com.appnexus.platform.tenant.TenantInvitations.Rejection.class, error -> {
            assertThat(error.status()).isEqualTo(403);
            assertThat(error).hasMessage("PLATFORM_DENIED");
        });
    }
}
