package com.appnexus.platform.managementapi.configuration;

import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.configuration.AttachmentRecords;
import com.appnexus.platform.security.AccountSettingsController;
import com.appnexus.platform.security.EndpointOperation;
import com.appnexus.platform.security.EndpointPolicy;
import java.util.UUID;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 平台工作台资料查询，跨租户读取使用独立的平台台账权限。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.PLATFORM_ADMINISTRATION)
@RequestMapping("/bff/platform/attachments")
public final class AttachmentLedgerController {
    private final AttachmentRecords records;

    /** @param records 附件资料用例 */
    public AttachmentLedgerController(AttachmentRecords records) {
        this.records = records;
    }

    /** @return 经过筛选的台账分页，包含平台权限允许查看的对象路径 */
    @OperationBinding(id="ManagedAttachment.ledger", reference = true)
    @EndpointOperation("configuration.attachmentledger:read")
    @GetMapping
    ResponseEntity<AttachmentRecords.Page> ledger(Authentication actor, @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String businessId, @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String typeKey, @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(records.ledger(UUID.fromString(actor.getName()),
                    new AttachmentRecords.Filter(tenantId, businessId, fileName, typeKey, createdBy, from, until), offset, limit));
        } catch (IllegalArgumentException invalid) {
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_FILTER", invalid.getMessage());
        }
    }
}
