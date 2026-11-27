package com.appnexus.platform.appapi.configuration;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import static com.appnexus.contracts.businessobject.DedicatedOperations.operation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 附件资料与受控操作的唯一声明，租户台账使用独立权限。 */
@Component
public final class AttachmentDeclaration implements Provider {
    /** @return 附件资料字段、专用操作及本租户数据资源 */
    @Override
    public Descriptor descriptor() {
        return new Descriptor("ManagedAttachment", "附件资料", "Attachment record", "configuration", 3,
                Category.CONFIGURATION, List.of(
                    new Field("businessId", "业务关联 ID", "Business ID", "TEXT", "", true),
                    new Field("fileName", "附件名称", "File name", "TEXT", "", true),
                    new Field("sizeBytes", "附件大小", "File size", "NUMBER", "bytes", true)),
                List.of(
                    operation("ManagedAttachment", "list", "查询关联附件", "List related attachments",
                            "configuration.attachment:read"),
                    operation("ManagedAttachment", "rules", "查看上传规则", "View upload rules",
                            "configuration.attachment:upload"),
                    operation("ManagedAttachment", "upload", "上传附件", "Upload attachment",
                            "configuration.attachment:upload"),
                    operation("ManagedAttachment", "ledger", "查看租户附件台账", "View tenant attachment ledger",
                            "configuration.attachment:ledger",
                            new PageOperation("configuration/attachments", "/configuration/attachments", "查看租户附件台账",
                                    "View tenant attachment ledger", "GET", "/api/configuration/attachment-ledger")),
                    operation("ManagedAttachment", "download", "下载台账附件", "Download ledger attachment",
                            "configuration.attachment:ledger",
                            new PageOperation("configuration/attachments", "/configuration/attachments", "下载台账附件",
                                    "Download ledger attachment", "GET", "/api/configuration/attachment-ledger/{id}/download"))),
                new DataAccess("TENANT", List.of(new DataResource("configuration.attachment", "",
                        "附件资料", "Attachment records", List.of())), Map.of()));
    }
}
