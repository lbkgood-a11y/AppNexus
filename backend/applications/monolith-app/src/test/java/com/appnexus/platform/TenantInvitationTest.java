package com.appnexus.platform;

import com.appnexus.platform.security.TenantInvitations;
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
    @Test void onlyInvitedAccountCanAcceptAndRejoinKeepsIdentityWithoutRoles(){
        UUID tenant=tenant(),target=account(),foreign=account();var invitation=invitations.invite(actor,tenant,target);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_member WHERE tenant_id=UUID_TO_BIN(?) AND account_id=UUID_TO_BIN(?)",Integer.class,tenant.toString(),target.toString())).isZero();
        assertThatThrownBy(()->invitations.accept(foreign,invitation.invitation().id(),invitation.token())).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        var accepted=invitations.accept(target,invitation.invitation().id(),invitation.token());
        assertThatThrownBy(()->invitations.accept(target,invitation.invitation().id(),invitation.token())).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_member_role WHERE member_id=UUID_TO_BIN(?)",Integer.class,accepted.memberId().toString())).isZero();
        administration.removeMember(actor,tenant,accepted.memberId());
        var rejoin=invitations.invite(actor,tenant,target);assertThat(invitations.accept(target,rejoin.invitation().id(),rejoin.token()).memberId()).isEqualTo(accepted.memberId());
    }
    @Test void expiredRevokedAndReplacedInvitationsCannotActivate(){
        UUID tenant=tenant(),target=account();var first=invitations.invite(actor,tenant,target);var second=invitations.invite(actor,tenant,target);
        assertThatThrownBy(()->invitations.accept(target,first.invitation().id(),first.token())).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        invitations.revoke(actor,tenant,second.invitation().id());
        assertThatThrownBy(()->invitations.accept(target,second.invitation().id(),second.token())).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        var expired=invitations.invite(actor,tenant,target);jdbc.update("UPDATE tenant_invitation SET expires_at=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=UUID_TO_BIN(?)",expired.invitation().id().toString());
        assertThatThrownBy(()->invitations.accept(target,expired.invitation().id(),expired.token())).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(()->invitations.invite(target,tenant,account())).isInstanceOf(org.springframework.web.ErrorResponseException.class);
    }
}
