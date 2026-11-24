package com.appnexus.platform.managementapi.configuration;

import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.configuration.AttachmentPolicies;
import com.appnexus.platform.security.AccountSettingsController;
import com.appnexus.platform.security.EndpointOperation;
import com.appnexus.platform.security.EndpointPolicy;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 平台管理域的附件安全策略入口，操作者仅从认证上下文取得。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
@RequestMapping("/bff/platform/attachment-policies")
public final class AttachmentPolicyController {
    private final AttachmentPolicies policies;

    /** @param policies 平台附件规则用例 */
    public AttachmentPolicyController(AttachmentPolicies policies) {
        this.policies = policies;
    }

    /** 策略编辑请求；限额单位为字节，版本取上次查询结果。 */
    public record UpdateRule(Boolean enabled, Long maxBytes, Long version) {}

    /** @return 含停用类型的规则列表，禁止客户端缓存 */
    @OperationBinding(id="AttachmentUploadPolicy.list", reference = true)
    @EndpointOperation("configuration.attachmentpolicy:read")
    @GetMapping
    ResponseEntity<List<AttachmentPolicies.Rule>> list(Authentication actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(policies.rules(UUID.fromString(actor.getName())));
    }

    /** 按请求版本原子更新，冲突时提示重新加载。 */
    @OperationBinding(id="AttachmentUploadPolicy.update", reference = true)
    @EndpointOperation("configuration.attachmentpolicy:update")
    @PutMapping("/{typeKey}")
    ResponseEntity<AttachmentPolicies.Rule> update(Authentication actor, @PathVariable String typeKey,
                                                   @RequestBody UpdateRule input) {
        if (input == null || input.enabled() == null || input.maxBytes() == null || input.version() == null) {
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,
                    "INVALID_ATTACHMENT_POLICY", "请填写附件规则状态、大小和版本");
        }
        try {
            var updated = policies.update(UUID.fromString(actor.getName()), typeKey,
                    input.enabled(), input.maxBytes(), input.version());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(updated);
        } catch (ConcurrentModificationException e) {
            throw AccountSettingsController.problem(HttpStatus.CONFLICT,
                    "ATTACHMENT_POLICY_CHANGED", "附件规则已变更，请刷新后修改");
        } catch (IllegalArgumentException e) {
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST,
                    "INVALID_ATTACHMENT_POLICY", e.getMessage());
        }
    }
}
