package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.organization.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.fasterxml.uuid.Generators;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 组织单元入口；过滤器逐请求检查操作权限，仓储强制租户条件，客户端不能指定租户。 */
@RestController
@RequestMapping("/api/organization")
public class OrganizationController {
    private final OrganizationUnitRepository units;
    private final OrganizationStructure structure;
    private final OrganizationAuthorization authorization;
    public OrganizationController(OrganizationUnitRepository units, OrganizationAuthorization authorization, OrganizationStructure structure) {
        this.units=units; this.authorization=authorization;this.structure=structure;
    }
    /** 名称必填，1–100 个 Java UTF-16 字符；不接受客户端指定 ID 或租户。 */
    public record Name(String name) {}

    /** 当前成员能力仅用于菜单、按钮展示，不代替每个写接口的服务端权限校验。 */
    @GetMapping("/capabilities")
    ResponseEntity<List<OrganizationCapability>> capabilities(HttpServletRequest request) {
        if (!(request.getAttribute(TenantExecutionFilter.ATTRIBUTE) instanceof VerifiedTenantContext c))
            throw AccountSettingsController.problem(HttpStatus.CONFLICT,"TENANT_REQUIRED","请选择有效租户");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            Arrays.stream(OrganizationCapability.values()).filter(cap -> authorization.allows(c.tenantId(),c.memberId(),cap)).toList());
    }
    /** @return 当前租户的组织单元，不返回其他租户的数据 */
    @GetMapping("/units")
    ResponseEntity<List<OrganizationUnit>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(units.findAll());
    }
    /** 创建 ID 由服务端生成；重名允许，提交期间客户端须防重复点击。 */
    @PostMapping("/units")
    ResponseEntity<OrganizationUnit> create(@RequestBody Name input) {
        var type=structure.types().stream().filter(value->value.code().equals("BUSINESS")).findFirst()
            .orElseGet(()->structure.createType("BUSINESS","业务组织"));
        var created=structure.createUnit("ORG-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT),type.id(),null,validName(input));
        var unit=new OrganizationUnit(created.id(),created.name());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(unit);
    }
    /** 仅重命名当前租户目标；找不到时不暴露其他租户是否存在相同 ID。 */
    @PutMapping("/units/{id}")
    ResponseEntity<Void> rename(@PathVariable UUID id,@RequestBody Name input) {
        if (!units.rename(id,validName(input))) throw missing();
        return ResponseEntity.noContent().build();
    }
    /** 删除仅影响当前租户；重复删除返回 404。 */
    @DeleteMapping("/units/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        try { if (!units.delete(id)) throw missing(); }
        catch(IllegalStateException assigned) { throw AccountSettingsController.problem(HttpStatus.CONFLICT,"ORGANIZATION_HAS_MEMBERS","请先解除成员组织归属再删除"); }
        return ResponseEntity.noContent().build();
    }
    /** 与领域名称约束一致，禁止空白或超过 100 字符的名称进入持久层。 */
    private String validName(Name input) {
        if(input.name()==null || input.name().isBlank() || input.name().length()>100)
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,"INVALID_ORGANIZATION_NAME","组织名称须为 1–100 个字符且不能为空白");
        return input.name();
    }
    private org.springframework.web.ErrorResponseException missing() {
        return AccountSettingsController.problem(HttpStatus.NOT_FOUND,"ORGANIZATION_NOT_FOUND","组织单元不存在");
    }
}
