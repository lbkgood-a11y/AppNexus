package com.appnexus.platform.security;

import com.appnexus.platform.identity.PasswordRecovery;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** 登录前密码找回；保持 CSRF 和共享限流，响应不区分账号不存在、恢复码错误或凭证过期。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PUBLIC_LOGIN)
@RequestMapping("/login/recovery")
public class PasswordRecoveryController {
    private final PasswordRecovery recovery;
    private final PasswordEncoder encoder;
    public PasswordRecoveryController(PasswordRecovery recovery,PasswordEncoder encoder){this.recovery=recovery;this.encoder=encoder;}
    /** 恢复码只接受高熵一次性恢复码，不接收六位 TOTP 来代替遗失的密码。 */
    public record Request(String username,String recoveryCode){@Override public String toString(){return "Request[redacted]";}}
    /** 一次性重置凭证仅在有权恢复时返回，不放 URL、日志或持久浏览器存储。 */
    public record Ticket(String token){@Override public String toString(){return "Ticket[redacted]";}}
    /** 新密码及凭证只在单次请求内存中存在。 */
    public record Completion(String username,String token,String newPassword){@Override public String toString(){return "Completion[redacted]";}}
    /** 正确恢复码被原子消费后签发 10 分钟有效凭证；失败文案统一。 */
    @PostMapping
    ResponseEntity<Ticket> request(@RequestBody Request input){
        try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Ticket(recovery.recover(input.username(),input.recoveryCode())));}
        catch(IllegalArgumentException failure){throw invalid();}
        catch(IllegalStateException unavailable){throw AccountSettingsController.problem(HttpStatus.SERVICE_UNAVAILABLE,"MFA_UNAVAILABLE","恢复服务暂不可用");}
    }
    /** 单次凭证改密并撤销全部旧认证；MFA 因子保持启用，不能通过找回降级认证要求。 */
    @PostMapping("/completion")
    ResponseEntity<Void> complete(@RequestBody Completion input){
        String next=input.newPassword();
        if(next==null||next.length()<12||next.length()>128||next.isBlank()||next.equalsIgnoreCase(input.username())||
                java.util.Set.of("password1234","123456789012","qwerty123456").contains(next.toLowerCase(java.util.Locale.ROOT)))
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"PASSWORD_POLICY","新密码须为 12 至 128 个字符，不能使用常见弱密码或账号名");
        if(!recovery.complete(input.username(),input.token(),encoder.encode(next)))throw invalid();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private org.springframework.web.ErrorResponseException invalid(){return AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"PASSWORD_RECOVERY_INVALID","恢复信息不正确或已失效");}
}
