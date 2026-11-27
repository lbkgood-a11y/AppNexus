package com.appnexus.platform.appapi.configuration;

import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.platform.configuration.AttachmentRecords;
import com.appnexus.platform.security.AccountSettingsController;
import com.appnexus.contracts.operation.EndpointPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 当前租户的全量附件台账，区别于业务页面的本人附件查询。 */
@RestController
@EndpointPolicy(EndpointPolicy.Domain.TENANT_BUSINESS)
@RequestMapping("/api/configuration/attachment-ledger")
public final class AttachmentLedgerController {
    private final AttachmentRecords records;

    /** @param records 附件资料用例 */
    public AttachmentLedgerController(AttachmentRecords records) {
        this.records = records;
    }

    /** @return 当前租户经过筛选的台账分页 */
    @OperationBinding(id = "ManagedAttachment.ledger", reference = true, enforceDeclaredPermission = true)

    @GetMapping
    ResponseEntity<AttachmentRecords.Page> ledger(HttpServletRequest request,
            @RequestParam(required = false) String businessId, @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String typeKey, @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(records.ledger(
                    AttachmentController.owner(request),
                    new AttachmentRecords.Filter(businessId, fileName, typeKey, createdBy, from, until),
                    offset, limit));
        } catch (IllegalArgumentException invalid) {
            throw AccountSettingsController.problem(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_FILTER",
                    invalid.getMessage());
        }
    }

    /**
     * 以附件方式传输私有原件，响应转换器负责关闭文件流。
     * @param request 持有已通过统一准入的租户会话
     * @param id 附件记录 UUID
     * @return 禁止缓存的原件响应
     */
    @OperationBinding(id = "ManagedAttachment.download", reference = true, enforceDeclaredPermission = true)
    @GetMapping("/{id}/download")
    ResponseEntity<InputStreamResource> download(HttpServletRequest request, @PathVariable UUID id) {
        AttachmentRecords.Download file;
        try {
            file = records.download(AttachmentController.owner(request), id);
        } catch (IllegalStateException unavailable) {
            throw AccountSettingsController.problem(HttpStatus.SERVICE_UNAVAILABLE,
                    "ATTACHMENT_UNAVAILABLE", "附件服务暂不可用，请重试");
        }
        if (file == null) {
            throw AccountSettingsController.problem(HttpStatus.NOT_FOUND,
                    "ATTACHMENT_NOT_FOUND", "附件不存在或尚不可下载");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.stream()));
    }
}
