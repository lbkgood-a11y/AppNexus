package com.appnexus.platform.managementapi.configuration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 附件上传策略的唯一声明，专用 API 通过平台管理域执行。 */
@Component
public final class AttachmentPolicyDeclaration implements Provider {
    /** @return 附件规则平台操作及资源声明 */
    @Override
    public Descriptor descriptor() {
        return new Descriptor("AttachmentUploadPolicy", "附件上传策略", "Attachment upload policy",
                "configuration", 1, Category.CONFIGURATION, List.of(), List.of(
                    operation("AttachmentUploadPolicy", "list", "查看附件策略", "Read attachment policies",
                            "configuration.attachmentpolicy:read", new PageOperation("platform/attachment-policies",
                                    "/platform/attachment-policies", "查看附件策略", "Read attachment policies",
                                    "GET", "/bff/platform/attachment-policies")),
                    operation("AttachmentUploadPolicy", "update", "修改附件策略", "Update attachment policies",
                            "configuration.attachmentpolicy:update", new PageOperation("platform/attachment-policies",
                                    "/platform/attachment-policies", "修改附件策略", "Update attachment policies",
                                    "PUT", "/bff/platform/attachment-policies/{typeKey}"))),
                new DataAccess("PLATFORM", List.of(new DataResource("configuration.attachmentpolicy", "",
                        "附件上传规则", "Attachment upload rules", List.of())), Map.of()));
    }
}
