package com.appnexus.platform;

import com.appnexus.platform.identity.MultiFactorAuthentication;
import com.appnexus.platform.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 平台管理默认强制 MFA；与旧功能容器回归分离，避免修改共享管理员认证因子。 */
class PlatformMfaFilterTest {
    private final MultiFactorAuthentication mfa=mock(MultiFactorAuthentication.class);
    private final UUID account=UUID.randomUUID();
    @BeforeEach void authenticate(){
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account.toString(),null,List.of()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private MockHttpServletResponse execute(boolean enabled,Instant verified)throws Exception{
        var request=new MockHttpServletRequest("POST","/bff/platform/users");
        if(verified!=null)request.getSession().setAttribute(MfaController.VERIFIED_AT,verified);
        var response=new MockHttpServletResponse();
        new PlatformMfaFilter(enabled,mfa).doFilter(request,response,(req,res)->response.setStatus(204));return response;
    }
    @Test void enrollmentAndRecentFactorAreBothRequired()throws Exception{
        assertThat(execute(true,null).getContentAsString()).contains("MFA_ENROLLMENT_REQUIRED");
        when(mfa.enabled(account)).thenReturn(true);
        assertThat(execute(true,null).getContentAsString()).contains("MFA_STEP_UP_REQUIRED");
        assertThat(execute(true,Instant.now().minusSeconds(301)).getStatus()).isEqualTo(403);
        assertThat(execute(true,Instant.now().plusSeconds(60)).getStatus()).isEqualTo(403);
        assertThat(execute(true,Instant.now().minusSeconds(1)).getStatus()).isEqualTo(204);
    }
    @Test void customRoleSubjectsCannotBypassPolicyAndMigrationIsExplicit()throws Exception{
        assertThat(execute(true,null).getStatus()).isEqualTo(403);
        assertThat(execute(false,null).getStatus()).isEqualTo(204);
    }
}
