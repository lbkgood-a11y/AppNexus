package com.appnexus.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.session.*;
import org.springframework.web.bind.annotation.*;

/** 本人在线会话管理；原始 Cookie 会话 ID 永不返回浏览器 JavaScript。 */
@RestController
@RequestMapping("/bff/account/sessions")
public class AccountSessionsController {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    private final com.appnexus.platform.audit.SecurityLogRepository logs;
    public AccountSessionsController(FindByIndexNameSessionRepository<? extends Session> sessions,
            com.appnexus.platform.audit.SecurityLogRepository logs) {
        this.logs = logs;
        this.sessions = sessions;
    }
    /**
     * 不透明管理标识仅用于本人会话撤销，不可用作登录凭据。
     * @param handle 会话 ID 的 SHA-256 摘要，不返回原始会话 ID
     * @param createdAt 会话创建时间点，UTC
     * @param lastAccessedAt 最近一次服务端访问时间，包含会话探测请求
     * @param current 是否与当前请求使用的 Cookie 会话相同
     */
    public record SessionView(String handle, Instant createdAt, Instant lastAccessedAt, boolean current) {}

    /** 通过主体索引查询本人未过期会话，不扫描其他账号；不展示推断的设备或位置。 */
    @GetMapping
    ResponseEntity<List<SessionView>> list(Authentication auth, HttpServletRequest request) {
        String currentId = request.getSession().getId();
        Long version = (Long) request.getSession().getAttribute(CredentialSession.VERSION);
        var result = sessions.findByPrincipalName(auth.getName()).values().stream()
            .filter(session -> !session.isExpired() &&
                Objects.equals(version, session.getAttribute(CredentialSession.VERSION)))
            .map(session -> new SessionView(handle(session.getId()), session.getCreationTime(),
                session.getLastAccessedTime(), currentId.equals(session.getId())))
            .sorted(Comparator.comparing(SessionView::current).reversed()
                .thenComparing(SessionView::lastAccessedAt, Comparator.reverseOrder()))
            .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * 撤销本人指定的其他会话；目标不存在或属于其他账号时均返回 404，避免泄露存在性。
     * @param handle 不透明管理标识，固定 64 位十六进制字符串
     * @param auth 当前认证主体
     * @param request 用于保护当前会话；退出当前会话应使用已有注销入口
     */
    @DeleteMapping("/{handle}")
    ResponseEntity<Void> revoke(@PathVariable String handle, Authentication auth, HttpServletRequest request) {
        if (!handle.matches("[a-f0-9]{64}")) throw AccountSettingsController.problem(
            HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或已失效");
        var target = sessions.findByPrincipalName(auth.getName()).values().stream()
            .filter(session -> !session.isExpired() && handle(session.getId()).equals(handle)).findFirst()
            .orElseThrow(() -> AccountSettingsController.problem(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在或已失效"));
        if (target.getId().equals(request.getSession().getId()))
            throw AccountSettingsController.problem(HttpStatus.CONFLICT, "CURRENT_SESSION", "请使用退出登录结束当前会话");
        sessions.deleteById(target.getId());
        return ResponseEntity.noContent().build();
    }
    /**
     * 撤销查询快照中本人的其他会话；保留当前会话，不影响之后建立的新登录。
     * Redis 与 MySQL 不构成分布式事务：失败时返回错误，允许重新查询后幂等重试。
     * @param auth 服务端认证主体，不接受客户端指定账号
     * @param request 取得必须保留的当前会话
     * @return 完成删除的会话数量；不返回会话凭据或目标标识
     */
    @PostMapping("/revoke-others")
    ResponseEntity<RevocationResult> revokeOthers(Authentication auth, HttpServletRequest request) {
        String current = request.getSession().getId();
        var targets = sessions.findByPrincipalName(auth.getName()).values().stream()
            .filter(session -> !session.isExpired() && !session.getId().equals(current))
            .map(Session::getId).toList();
        for (String id : targets) sessions.deleteById(id);
        if (!targets.isEmpty()) logs.append(UUID.fromString(auth.getName()),
            com.appnexus.platform.audit.SecurityLog.Action.OTHER_SESSIONS_REVOKED);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RevocationResult(targets.size()));
    }
    /** 已完成删除调用的快照条数；并发自然过期时不代表精确在线人数变化。 */
    public record RevocationResult(int revoked) {}
    /** 摘要只用于管理定位，授权仍由服务端主体索引限定，不能仅凭摘要撤销其他账号。 */
    private String handle(String id) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
