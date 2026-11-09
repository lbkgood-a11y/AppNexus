package com.appnexus.platform.security;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 组织授权管理入口；仅有 GRANT 能力的当前租户成员可操作，管理能力本身不可通过本接口委派。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/api/organization/grants")
public class OrganizationGrantController {
    private final OrganizationAuthorization authorization;
    private final OrganizationGrantManagement grants;
    public OrganizationGrantController(OrganizationAuthorization authorization,OrganizationGrantManagement grants) {
        this.authorization=authorization;this.grants=grants;
    }
    /** 只接受目标成员、操作和可选到期时间；操作者与租户由服务端验证上下文决定。 */
    public record Change(UUID memberId, OrganizationCapability capability, Instant expiresAt) {}
    /** 主要组织 UUID，null 清空；不修改成员的操作能力或租户归属。 */
    public record Organization(UUID organizationId) {}
    /** 组织归属管理沿用 GRANT 权限；目标成员和组织均复核同租户。 */
    @EndpointOperation("organization.permission:grant") @PutMapping("/members/{memberId}/organization")
    ResponseEntity<Void> assign(@PathVariable UUID memberId,@RequestBody Organization input,HttpServletRequest request) {
        var c=requireAuthority(request);
        try { grants.assignOrganization(c.tenantId(),memberId,c.memberId(),input.organizationId());return ResponseEntity.noContent().build(); }
        catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"GRANT_DENIED","无授权管理权限");}
        catch(IllegalArgumentException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_GRANT","成员或组织不属于当前有效租户");}
    }
    /** 选择器只向有管理权限的成员返回当前租户的有效成员。 */
    @EndpointOperation("organization.permission:grant") @GetMapping("/members")
    ResponseEntity<List<OrganizationGrantManagement.Member>> members(HttpServletRequest request) {
        var c=requireAuthority(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(grants.members(c.tenantId()));
    }
    /** 查询目标成员有效授权，不能查询其他租户。 */
    @EndpointOperation("organization.permission:grant") @GetMapping("/{memberId}")
    ResponseEntity<List<OrganizationGrantManagement.Grant>> list(@PathVariable UUID memberId,HttpServletRequest request) {
        var context=requireAuthority(request);
        try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(grants.list(context.tenantId(),memberId)); }
        catch(IllegalArgumentException invalid){throw AccountSettingsController.problem(HttpStatus.NOT_FOUND,"MEMBER_UNAVAILABLE","成员不存在或不可用");}
    }
    /** 有效期和目标成员由后端复核，CSRF 由安全过滤器统一执行。 */
    @EndpointOperation("organization.permission:grant") @PostMapping
    ResponseEntity<OrganizationGrantManagement.Grant> grant(@RequestBody Change input,HttpServletRequest request) {
        var c=requireAuthority(request);
        validate(input);
        try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            grants.grant(c.tenantId(),input.memberId(),c.memberId(),input.capability(),input.expiresAt())); }
        catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"GRANT_DENIED","无授权管理权限");}
        catch(IllegalArgumentException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_GRANT","成员或授权有效期不符合要求");}
    }
    /** 撤权不接受客户端操作者身份；不存在或已撤销时返回 404。 */
    @EndpointOperation("organization.permission:grant") @DeleteMapping("/{memberId}/{capability}")
    ResponseEntity<Void> revoke(@PathVariable UUID memberId,@PathVariable OrganizationCapability capability,HttpServletRequest request) {
        var c=requireAuthority(request);validate(new Change(memberId,capability,null));
        try {
            if(!grants.revoke(c.tenantId(),memberId,c.memberId(),capability))
                throw AccountSettingsController.problem(HttpStatus.NOT_FOUND,"GRANT_NOT_FOUND","授权不存在或已撤销");
            return ResponseEntity.noContent().build();
        } catch(SecurityException denied){throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"GRANT_DENIED","无授权管理权限");}
        catch(IllegalArgumentException invalid){throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_GRANT","成员或授权不符合要求");}
    }
    /** 验证范围内管理能力，默认拒绝；不能用用户名、全局账号或普通成员身份作为捷径。 */
    private VerifiedTenantContext requireAuthority(HttpServletRequest request) {
        if(!(request.getAttribute(TenantExecutionFilter.ATTRIBUTE) instanceof VerifiedTenantContext c))
            throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        if(!authorization.allows(c.tenantId(),c.memberId(),OrganizationCapability.GRANT))
            throw AccountSettingsController.problem(HttpStatus.FORBIDDEN,"GRANT_DENIED","无授权管理权限");
        return c;
    }
    private void validate(Change input) {
        if(input.memberId()==null||input.capability()==null||input.capability()==OrganizationCapability.GRANT)
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_GRANT","目标成员或授权操作不符合要求，管理权限不可委派");
    }
}
