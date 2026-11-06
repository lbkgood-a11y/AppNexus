package com.appnexus.platform.security;

import com.appnexus.platform.identity.*;
import jakarta.servlet.http.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

/** 当前账号个人中心；只操作认证主体本人的资料，不接受客户端指定账号 ID。 */
@RestController
@RequestMapping("/bff/account")
public class AccountSettingsController {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    public AccountSettingsController(AccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts; this.encoder = encoder;
    }
    /** 公开展示资料，不返回密码哈希、Token 或内部授权记录。 */
    public record Profile(String id, String username, String displayName) {}
    /** 显示名称长度为 1 至 100，首尾空白在服务端移除。 */
    public record ProfileChange(String displayName) {}
    /** 当前密码用于重新认证；新密码长度为 12 至 128 字符，绝不记录请求正文。 */
    public record PasswordChange(String currentPassword, String newPassword) {}

    /** 查询当前账号的可展示资料。 */
    @GetMapping
    ResponseEntity<Profile> profile(Authentication auth) {
        var account = current(auth);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Profile(account.id().toString(), account.username(), account.displayName()));
    }
    /** 修改显示名称，不修改登录标识或认证版本。 */
    @PatchMapping
    ResponseEntity<Void> update(@RequestBody ProfileChange input, Authentication auth) {
        String name = input.displayName() == null ? "" : input.displayName().strip();
        if (name.isEmpty() || name.length() > 100 || name.codePoints().anyMatch(Character::isISOControl))
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_DISPLAY_NAME", "显示名称须为 1 至 100 个字符，且不能包含控制字符");
        if (!accounts.updateDisplayName(current(auth).id(), name))
            throw problem(HttpStatus.CONFLICT, "ACCOUNT_CHANGED", "账号状态已变化，请重新登录");
        return ResponseEntity.noContent().build();
    }
    /** 校验当前密码后以版本条件原子更新；撤销全部旧授权，并注销当前会话。 */
    @PostMapping("/password")
    ResponseEntity<Void> password(@RequestBody PasswordChange input, Authentication auth,
            HttpServletRequest request, HttpServletResponse response) {
        var account = current(auth);
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
