package com.appnexus.platform.appapi.configuration;

import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.configuration.AttachmentPolicies;
import com.appnexus.platform.configuration.AttachmentRecords;
import com.appnexus.platform.security.AccountSettingsController;
import com.appnexus.contracts.operation.EndpointPolicy;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 可信租户内的附件查询与上传。HTTP 范围和权限由统一入口执行。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/api/configuration/attachments")
public final class AttachmentController {
    private final AttachmentRecords records;
    private final AttachmentPolicies policies;

    /** @param records 附件用例 @param policies 当前上传策略 */
    public AttachmentController(AttachmentRecords records, AttachmentPolicies policies) {
        this.records = records;
        this.policies = policies;
    }

    /** @return 当前可用的上传格式与限制，停用项不会返回 */
    @OperationBinding(id="ManagedAttachment.rules", reference = true, enforceDeclaredPermission=true)

    @GetMapping("/rules")
    ResponseEntity<List<AttachmentPolicies.Rule>> rules(HttpServletRequest request) {
        return response(() -> policies.uploadRules(owner(request)));
    }

    /** 分页获取当前租户内本账号上传的关联附件。 */
    @OperationBinding(id="ManagedAttachment.list", reference = true, enforceDeclaredPermission=true)

    @GetMapping
    ResponseEntity<AttachmentRecords.Page> list(HttpServletRequest request,
            @RequestParam String businessId, @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return response(() -> records.list(owner(request), businessId, offset, limit));
    }

    /**
     * 读取单个文件原始请求流，避免在授权及大小检查前做 multipart 磁盘解析。
     * @param encodedName UTF-8 百分号编码的展示文件名
     * @param key 同一文件重试使用的 UUID
     */
    @OperationBinding(id="ManagedAttachment.upload", reference = true, enforceDeclaredPermission=true)

    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<AttachmentRecords.Item> upload(HttpServletRequest request,
            @RequestHeader("X-Attachment-Business-Id") String encodedBusinessId,
            @RequestHeader("X-Attachment-Name") String encodedName,
            @RequestHeader("Idempotency-Key") UUID key) throws IOException {
        var owner = owner(request);
        try (var stream = request.getInputStream()) {
            return response(() -> records.upload(owner, URLDecoder.decode(encodedBusinessId, StandardCharsets.UTF_8),
                    URLDecoder.decode(encodedName, StandardCharsets.UTF_8), key, stream));
        }
    }

    /** @return 供本包附件入口共享的可信租户与账号，缺少租户上下文时拒绝请求 */
    static AttachmentRecords.Owner owner(HttpServletRequest request) {
        if (!(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext tenant)) {
            throw AccountSettingsController.problem(HttpStatus.CONFLICT, "TENANT_REQUIRED", "请选择有效租户");
        }
        return new AttachmentRecords.Owner(tenant.tenantId(), tenant.accountId(), tenant.memberId());
    }

    private static <T> ResponseEntity<T> response(Supplier<T> result) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.get());
        } catch (IllegalArgumentException invalid) {
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT", invalid.getMessage());
        } catch (ConcurrentModificationException conflict) {
            throw AccountSettingsController.problem(HttpStatus.CONFLICT, "ATTACHMENT_CONFLICT", conflict.getMessage());
        } catch (IllegalStateException unavailable) {
            throw AccountSettingsController.problem(HttpStatus.SERVICE_UNAVAILABLE,
                    "ATTACHMENT_UNAVAILABLE", "附件服务暂不可用，请重试");
        }
    }
}
