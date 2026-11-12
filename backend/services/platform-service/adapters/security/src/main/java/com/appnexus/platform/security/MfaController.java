package com.appnexus.platform.security;

import com.appnexus.platform.identity.*;
import com.appnexus.platform.audit.*;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

/** 本人 MFA 管理与敏感操作重新认证；全部写操作沿用会话 CSRF 和共享限流。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.ACCOUNT_SELF_SERVICE)
@RequestMapping("/bff/account/mfa")
public class MfaController {
    /** 服务端第二因子验证时间；只由密码登录或 step-up 成功写入，客户端不能指定。 */
    public static final String VERIFIED_AT="appnexus.mfaVerifiedAt";
    private final MultiFactorAuthentication mfa;
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final SecurityLogRepository logs;
    /** 平台写入强制策略，默认 true；只用于展示实际部署策略，不授予个人权限。 */
    @org.springframework.beans.factory.annotation.Value("${app.mfa.require-platform:true}")
    private boolean platformRequired=true;
    public MfaController(MultiFactorAuthentication mfa,AccountRepository accounts,PasswordEncoder encoder,SecurityLogRepository logs){
        this.mfa=mfa;this.accounts=accounts;this.encoder=encoder;this.logs=logs;
    }
    /** 状态描述本人绑定、实际平台强制策略及已实现方式；不返回密钥、恢复码或其他账号资料。 */
    public record Status(boolean available,boolean enabled,boolean steppedUp,boolean platformRequired,List<String> methods){}
    /** 密码及一次性验证码只在请求内存使用；禁止自动 record.toString 泄露凭据。 */
    public record Challenge(String password,String code){@Override public String toString(){return "Challenge[redacted]";}}
    /** 待确认 Base32 密钥，只在本次建立绑定时返回，不缓存。 */
    public record Enrollment(String secret){@Override public String toString(){return "Enrollment[redacted]";}}
    /** 恢复码仅在成功激活时显示一次，之后无法查询原文。 */
    public record Recovery(List<String> codes){@Override public String toString(){return "Recovery[redacted]";}}
    @GetMapping
    ResponseEntity<Status> status(Authentication auth,HttpServletRequest request){
        Object verified=request.getSession().getAttribute(VERIFIED_AT);
        boolean recent=verified instanceof Instant time&&!time.isAfter(Instant.now())&&time.plusSeconds(300).isAfter(Instant.now());
        return noStore(new Status(mfa.available(),mfa.enabled(id(auth)),recent,platformRequired,List.of("TOTP","RECOVERY_CODE")));
    }
    /** 当前密码复核后建立 10 分钟待确认绑定，已有有效因子不可覆盖。 */
    @PostMapping("/enrollment")
    ResponseEntity<Enrollment> enroll(Authentication auth,@RequestBody Challenge input){
        password(auth,input);return execute(()->new Enrollment(mfa.begin(id(auth))));
    }
    /** 激活并撤销旧认证；当前已证明密码与第二因子的会话轮换后保留，供一次性保存恢复码。 */
    @PostMapping("/activation")
    ResponseEntity<Recovery> activate(Authentication auth,@RequestBody Challenge input,HttpServletRequest request,HttpServletResponse response){
        password(auth,input);
        long previous=((Number)request.getSession().getAttribute(CredentialSession.VERSION)).longValue();
        var result=execute(()->new Recovery(mfa.activate(id(auth),input.code())));
        request.changeSessionId();
        // 只能升级到本次因子变更产生的版本；并发管理员撤销或改密后不能读取最新值恢复已撤销会话。
        request.getSession().setAttribute(CredentialSession.VERSION,previous+1);
        request.getSession().setAttribute(VERIFIED_AT,Instant.now());return result;
    }
    /** 当前密码和第二因子共同通过后轮换会话 ID，重新认证窗口固定为 5 分钟。 */
    @PostMapping("/step-up")
    ResponseEntity<Void> stepUp(Authentication auth,@RequestBody Challenge input,HttpServletRequest request){
        password(auth,input);
        execute(()->{if(!mfa.verify(id(auth),input.code()))throw new IllegalArgumentException();return null;});
        logs.append(id(auth),SecurityLog.Action.MFA_STEP_UP);
        request.changeSessionId();request.getSession().setAttribute(VERIFIED_AT,Instant.now());return noStore(null);
    }
    /** 关闭需复核密码及第二因子，成功后注销当前会话和全部旧授权。 */
    @PostMapping("/disable")
    ResponseEntity<Void> disable(Authentication auth,@RequestBody Challenge input,HttpServletRequest request,HttpServletResponse response){
        password(auth,input);
        execute(()->{if(!mfa.disable(id(auth),input.code()))throw new IllegalArgumentException();return null;});
        new SecurityContextLogoutHandler().logout(request,response,auth);return noStore(null);
    }
    private UUID id(Authentication auth){return UUID.fromString(auth.getName());}
    /**
     * 对已启用 MFA 的账号强制检查近期认证；未绑定账号仍可进入受控绑定流程。
     * @param mfa 第二因子权威端口，不信任前端按钮状态
     * @param account 当前操作者，不是目标账号
     * @param request 只读取服务端会话，窗口为 300 秒，不接受客户端时间
     * @throws org.springframework.web.ErrorResponseException 缺少有效近期因子时返回 403
     */
    public static void requireRecent(MultiFactorAuthentication mfa,UUID account,HttpServletRequest request){
        if(!mfa.enabled(account))return;
        Object value=request.getSession().getAttribute(VERIFIED_AT);
        Instant now=Instant.now();
        if(!(value instanceof Instant time)||time.isAfter(now)||!time.plusSeconds(300).isAfter(now))
            throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"MFA_STEP_UP_REQUIRED","请先在账号安全中完成重新认证");
    }
    private void password(Authentication auth,Challenge input){
        var account=accounts.findById(id(auth)).filter(AccountCredentials::enabled).orElseThrow(()->invalid());
        if(input.password()==null||input.password().length()>128||!encoder.matches(input.password(),account.passwordHash()))throw invalid();
    }
    private org.springframework.web.ErrorResponseException invalid(){return AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"MFA_CHALLENGE_INVALID","认证信息不正确或已失效");}
    private <T> ResponseEntity<T> execute(java.util.function.Supplier<T> action){
        if(!mfa.available())throw AccountSettingsController.problem(HttpStatus.SERVICE_UNAVAILABLE,"MFA_UNAVAILABLE","第二因子服务暂不可用");
        try{return noStore(action.get());}catch(IllegalArgumentException invalid){throw invalid();}
    }
    private <T> ResponseEntity<T> noStore(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
