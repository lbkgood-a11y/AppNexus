package com.appnexus.platform.security;

import com.appnexus.platform.identity.*;
import jakarta.servlet.http.*;
import java.util.UUID;
import java.util.Locale;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

/** 当前账号个人中心；只操作认证主体本人的资料，不接受客户端指定账号 ID。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.ACCOUNT_SELF_SERVICE)
@RequestMapping("/bff/account")
public class AccountSettingsController {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final MultiFactorAuthentication mfa;
    public AccountSettingsController(AccountRepository accounts, PasswordEncoder encoder,MultiFactorAuthentication mfa) {
        this.accounts = accounts; this.encoder = encoder;this.mfa=mfa;
    }
    /** 公开展示资料，不返回密码哈希、Token 或内部授权记录。 */
    public record Profile(String id, String username, String displayName, String email,
            boolean emailVerified, String phoneNumber, boolean phoneVerified) {}
    /** 联系方式可缺省；邮箱最长 254 字符，手机号使用 E.164，修改后验证状态自动失效。 */
    public record ProfileChange(String displayName, String email, String phoneNumber) {}
    /** 当前密码用于重新认证；新密码长度为 12 至 128 字符，绝不记录请求正文。 */
    public record PasswordChange(String currentPassword, String newPassword) {}

    /** 查询当前账号的可展示资料。 */
    @GetMapping
    ResponseEntity<Profile> profile(Authentication auth) {
        var account = current(auth);
        var profile = accounts.findProfileById(account.id())
            .orElseThrow(() -> problem(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "登录已失效，请重新登录"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Profile(profile.id().toString(), profile.username(), profile.displayName(), profile.email(),
                profile.emailVerified(), profile.phoneNumber(), profile.phoneVerified()));
    }
    /** 修改显示名称，不修改登录标识或认证版本。 */
    @PatchMapping
    ResponseEntity<Void> update(@RequestBody ProfileChange input, Authentication auth) {
        String name = input.displayName() == null ? "" : input.displayName().strip();
        if (name.isEmpty() || name.length() > 100 || name.codePoints().anyMatch(Character::isISOControl))
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_DISPLAY_NAME", "显示名称须为 1 至 100 个字符，且不能包含控制字符");
        String email = normalizeEmail(input.email());
        String phone = normalizePhone(input.phoneNumber());
        try {
            if (!accounts.updateProfile(current(auth).id(), name, email, phone))
                throw problem(HttpStatus.CONFLICT, "ACCOUNT_CHANGED", "账号状态已变化，请重新登录");
        } catch (AccountContactConflictException conflict) {
            throw problem(HttpStatus.CONFLICT, "CONTACT_ALREADY_USED", "邮箱或手机号已被其他账号使用");
        }
        return ResponseEntity.noContent().build();
    }
    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) return null;
        String email = value.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "邮箱格式不正确");
        return email;
    }
    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String phone = value.strip();
        if (!phone.matches("^\\+[1-9][0-9]{7,14}$"))
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "手机号须使用 E.164 格式");
        return phone;
    }
    /** 校验当前密码后以版本条件原子更新；撤销全部旧授权，并注销当前会话。 */
    @PostMapping("/password")
    ResponseEntity<Void> password(@RequestBody PasswordChange input, Authentication auth,
            HttpServletRequest request, HttpServletResponse response) {
        var account = current(auth);
        MfaController.requireRecent(mfa,account.id(),request);
        if (input.currentPassword() == null || input.currentPassword().length() > 128 ||
                !encoder.matches(input.currentPassword(), account.passwordHash()))
            throw problem(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "当前密码不正确");
        String next = input.newPassword();
        if (next == null || next.length() < 12 || next.length() > 128 || next.isBlank() ||
                next.equals(input.currentPassword()) || next.equalsIgnoreCase(account.username()) ||
                java.util.Set.of("password1234", "123456789012", "qwerty123456").contains(next.toLowerCase(java.util.Locale.ROOT)))
            throw problem(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "新密码须为 12 至 128 个字符，不能与当前密码相同或使用常见弱密码");
        if (!accounts.changePassword(account.id(), account.version(), encoder.encode(next)))
            throw problem(HttpStatus.CONFLICT, "CREDENTIALS_CHANGED", "凭据已变化，请重新登录后重试");
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return ResponseEntity.noContent().build();
    }
    private AccountCredentials current(Authentication auth) {
        return accounts.findById(UUID.fromString(auth.getName())).filter(AccountCredentials::enabled)
            .orElseThrow(() -> problem(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "登录已失效，请重新登录"));
    }
    /** 使用标准 ProblemDetail 返回稳定业务码；不回显任何凭据或请求内容。 */
    static org.springframework.web.ErrorResponseException problem(HttpStatus status, String code, String detail) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase()); body.setProperty("code", code);
        return new org.springframework.web.ErrorResponseException(status, body, null);
    }
}
