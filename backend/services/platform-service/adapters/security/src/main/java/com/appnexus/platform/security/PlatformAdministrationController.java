package com.appnexus.platform.security;

import com.appnexus.platform.tenant.PlatformAdministration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** 不依赖租户上下文的平台管理入口；每个目录/写操作在用例内校验平台权限，Cookie 写操作保留 CSRF。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
@RequestMapping("/bff/platform")
public class PlatformAdministrationController {
    private final PlatformAdministration service;
    private final PasswordEncoder encoder;
    private final com.appnexus.platform.identity.MultiFactorAuthentication mfa;
    private final com.appnexus.platform.identity.PasswordRecovery recovery;
    public PlatformAdministrationController(PlatformAdministration service,PasswordEncoder encoder,
            com.appnexus.platform.identity.MultiFactorAuthentication mfa,com.appnexus.platform.identity.PasswordRecovery recovery) {
        this.service=service;this.encoder=encoder;this.mfa=mfa;this.recovery=recovery;
    }
    /** 展示能力不代表授权，后续操作仍复核权威源。 */
    public record Access(boolean administrator) {}
    /** 初始密码仅在请求内处理，响应和审计不包含明文或哈希。 */
    public record NewUser(String username,String displayName,String email,String phoneNumber,String password) {
        @Override public String toString() { return "NewUser[redacted]"; }
    }
    /** 账号登录名不在资料编辑中变更，避免影响审计识别和现有登录流程。 */
    public record UpdateUser(String displayName,String email,String phoneNumber) {}
    /** 请求 UUID 用于重复提交幂等；首位管理员必须是已有有效账号。 */
    public record NewTenant(UUID requestId,String name,UUID administratorAccountId) {}
    /** 租户标识与状态不在名称编辑请求中变更。 */
    public record UpdateTenant(String name) {}
    /** 状态显式提交，停用与恢复需用户确认。 */
    public record State(Boolean enabled) {}
    /** 账号关联意图，不接受客户端 memberId 或业务能力字段。 */
    public record NewMember(UUID accountId) {}
    @EndpointOperation("tenant:read") @GetMapping("/access")
    ResponseEntity<Access> access(Authentication auth) { return ok(() -> new Access(service.isAdministrator(actor(auth)))); }
    /** 页码默认 1，每页默认 20、范围 1–100；查询词最长 100 字符。 */
    @EndpointOperation("identity.account:read") @GetMapping("/users")
    ResponseEntity<PlatformAdministration.Page<PlatformAdministration.User>> users(Authentication auth,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String query) {
        return ok(() -> service.users(actor(auth),page,size,query));
    }
    /** 登录名为 1–100 位 ASCII 标识，名称 1–100 字符，密码 12–128 字符；不允许普通账号借创建操作自提权。 */
    @EndpointOperation("identity.account:create") @PostMapping("/users")
    ResponseEntity<PlatformAdministration.User> user(Authentication auth,@RequestBody NewUser input) {
        return ok(() -> {
            if(!service.isAdministrator(actor(auth))) throw new SecurityException();
            if(input.password()==null || input.password().length()<12 || input.password().length()>128) throw new IllegalArgumentException();
            return service.createUser(actor(auth),com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate(),input.username(),input.displayName(),input.email(),input.phoneNumber(),encoder.encode(input.password()));
        });
    }
    /** 联系方式修改后为未验证；验证流程与登录识别符开通不属于此管理接口。 */
    @EndpointOperation("identity.account:update") @PutMapping("/users/{id}")
    ResponseEntity<PlatformAdministration.User> updateUser(Authentication auth,@PathVariable UUID id,@RequestBody UpdateUser input) {
        return ok(() -> service.updateUser(actor(auth),id,input.displayName(),input.email(),input.phoneNumber()));
    }
    @EndpointOperation("identity.account:update") @PutMapping("/users/{id}/state")
    ResponseEntity<Void> userState(Authentication auth,@PathVariable UUID id,@RequestBody State state) {
        return ok(() -> { service.setUserEnabled(actor(auth),id,enabled(state)); return null; });
    }
    /** 平台管理员强制下线目标账号；保留 CSRF，凭据版本与 OAuth 撤销在用例内原子完成。 */
    @EndpointOperation("identity.account:update") @PostMapping("/users/{id}/revoke-sessions")
    ResponseEntity<Void> revokeSessions(Authentication auth,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request) {
        MfaController.requireRecent(mfa,actor(auth),request);
        return ok(() -> { service.revokeUserSessions(actor(auth),id);return null; });
    }
    /** 管理员受权签发单次重置凭证，禁止重置平台管理员或本人；交付渠道由运维核实本人身份。 */
    @EndpointOperation("identity.account:update") @PostMapping("/users/{id}/password-recovery")
    ResponseEntity<PasswordRecoveryController.Ticket> passwordRecovery(Authentication auth,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request){
        MfaController.requireRecent(mfa,actor(auth),request);
        return ok(()->new PasswordRecoveryController.Ticket(recovery.issue(actor(auth),id)));
    }
    /** 平台租户目录分页，不读取租户内业务数据。 */
    @EndpointOperation("tenant:read") @GetMapping("/tenants")
    ResponseEntity<PlatformAdministration.Page<PlatformAdministration.Tenant>> tenants(Authentication auth,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String query) {
        return ok(() -> service.tenants(actor(auth),page,size,query));
    }
    /** 创建租户、管理员成员、组织权限和审计同事务；不将平台操作者自动加入租户。 */
    @EndpointOperation("tenant:create") @PostMapping("/tenants")
    ResponseEntity<PlatformAdministration.Tenant> tenant(Authentication auth,@RequestBody NewTenant input) {
        return ok(() -> { Objects.requireNonNull(input.requestId());Objects.requireNonNull(input.administratorAccountId());return service.createTenant(actor(auth),input.requestId(),input.name(),input.administratorAccountId()); });
    }
    @EndpointOperation("tenant:update") @PutMapping("/tenants/{id}")
    ResponseEntity<PlatformAdministration.Tenant> updateTenant(Authentication auth,@PathVariable UUID id,@RequestBody UpdateTenant input) {
        return ok(() -> service.updateTenant(actor(auth),id,input.name()));
    }
    @EndpointOperation("tenant:update") @PutMapping("/tenants/{id}/state")
    ResponseEntity<Void> tenantState(Authentication auth,@PathVariable UUID id,@RequestBody State state) {
        return ok(() -> { service.setTenantEnabled(actor(auth),id,enabled(state));return null; });
    }
    @EndpointOperation("tenant.member:read") @GetMapping("/tenants/{tenant}/members")
    ResponseEntity<PlatformAdministration.Page<PlatformAdministration.Member>> members(Authentication auth,@PathVariable UUID tenant,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String query) {
        return ok(() -> service.members(actor(auth),tenant,page,size,query));
    }
    @EndpointOperation("tenant:update") @PostMapping("/tenants/{tenant}/members")
    ResponseEntity<Void> addMember(Authentication auth,@PathVariable UUID tenant,@RequestBody NewMember input) {
        return ok(() -> { Objects.requireNonNull(input.accountId()); service.addMember(actor(auth),tenant,input.accountId());return null; });
    }
    @EndpointOperation("tenant:update") @PutMapping("/tenants/{tenant}/members/{member}/state")
    ResponseEntity<Void> memberState(Authentication auth,@PathVariable UUID tenant,@PathVariable UUID member,@RequestBody State state) {
        return ok(() -> { service.setMemberEnabled(actor(auth),tenant,member,enabled(state));return null; });
    }
    @EndpointOperation("tenant:update") @DeleteMapping("/tenants/{tenant}/members/{member}")
    ResponseEntity<Void> removeMember(Authentication auth,@PathVariable UUID tenant,@PathVariable UUID member) {
        return ok(() -> { service.removeMember(actor(auth),tenant,member);return null; });
    }
    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
    private boolean enabled(State state) { return Objects.requireNonNull(state.enabled()); }
    /** 统一错误码，不向客户端回显 SQL、用户名冲突细节或原始异常。 */
    private <T> ResponseEntity<T> ok(Supplier<T> action) {
        try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get()); }
        catch(SecurityException denied) { throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"PLATFORM_DENIED","无平台管理权限"); }
        catch(org.springframework.dao.DuplicateKeyException duplicate) { throw AccountSettingsController.problem(HttpStatus.CONFLICT,"DIRECTORY_CONFLICT","账号、成员或请求标识已存在，请刷新后重试"); }
        catch(IllegalArgumentException | NullPointerException invalid) { throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_DIRECTORY_INPUT","参数、目标状态或操作约束不满足要求"); }
    }
}
