package com.appnexus.platform.appapi.configuration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 附件上传策略的唯一声明，管理与执行范围均为当前租户。 */
@Component
public final class AttachmentPolicyDeclaration implements Provider {
        /** @return 附件规则租户操作及资源声明 */
        @Override
        public Descriptor descriptor() {
                return new Descriptor("AttachmentUploadPolicy", "附件上传策略", "Attachment upload policy",
                                "configuration", 2, Category.CONFIGURATION, List.of(), List.of(
                                                operation("AttachmentUploadPolicy", "list", "查看附件策略",
                                                                "Read attachment policies",
                                                                "configuration.tenantattachmentpolicy:read",
                                                                new PageOperation("configuration/attachment-policies",
                                                                                "/configuration/attachment-policies",
                                                                                "查看附件策略", "Read attachment policies",
                                                                                "GET",
                                                                                "/api/configuration/attachment-policies")),
                                                operation("AttachmentUploadPolicy", "update", "修改附件策略",
                                                                "Update attachment policies",
                                                                "configuration.tenantattachmentpolicy:update",
                                                                new PageOperation("configuration/attachment-policies",
                                                                                "/configuration/attachment-policies",
                                                                                "修改附件策略", "Update attachment policies",
                                                                                "PUT",
                                                                                "/api/configuration/attachment-policies/{typeKey}"))),
                                new DataAccess("TENANT",
                                                List.of(new DataResource("configuration.tenantattachmentpolicy", "",
                                                                "附件上传规则", "Attachment upload rules", List.of())),
                                                Map.of()));
        }
}
