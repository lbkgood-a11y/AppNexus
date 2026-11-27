package com.appnexus.platform.configuration;

import com.appnexus.platform.configuration.internal.application.attachment.AttachmentService;
import com.appnexus.platform.configuration.port.AttachmentBlobStorage;
import com.appnexus.platform.configuration.port.AttachmentInspection;
import com.appnexus.platform.configuration.port.AttachmentStore;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 附件专用用例。调用端先执行统一准入，租户与账号来自可信认证上下文。 */
public interface AttachmentRecords {
    /** @return 仅供组合装配的附件用例，调用方使用接口执行受控操作 */
    static AttachmentRecords assemble(AttachmentStore records, AttachmentPolicies policies,
                                      AttachmentBlobStorage storage, AttachmentInspection inspection) {
        return new AttachmentService(records, policies, storage, inspection);
    }

    /** 已验证的租户与当前账号。 */
    record Owner(UUID tenantId, UUID accountId, UUID memberId) {}

    /** 附件资料投影，存储路径仅用于服务端定位原件。 */
    record Item(String id, String tenantId, String businessId, String fileName, long sizeBytes,
                String mediaType, String storagePath, String status, String createdBy, Instant createdAt,
                String updatedBy, Instant updatedAt) {}

    /** 分页结果；total 与结果范围一致。 */
    record Page(List<Item> rows, long total) {}

    /** @return 当前账号在关联 ID 下的可用附件，offset 从零开始 */
    Page list(Owner owner, String businessId, int offset, int limit);

    /** 租户台账筛选。时间使用 UTC，结束时间为排除边界。 */
    record Filter(String businessId, String fileName, String typeKey,
                  UUID createdBy, Instant from, Instant until) {}

    /** @return 当前租户已获台账授权的分页资料 */
    Page ledger(Owner owner, Filter filter, int offset, int limit);

    /** 私有原件及响应元数据。调用方负责关闭原件流。 */
    record Download(String fileName, long sizeBytes, InputStream stream) {}

    /**
     * 读取当前租户台账中的可用原件。
     * @param owner 已认证的租户主体
     * @param id 台账中的附件 UUID
     * @return 私有原件，附件不存在或尚不可用时返回空
     */
    Download download(Owner owner, UUID id);

    /**
     * 验证并上传一份附件，完成记录写入后才返回。
     * @param owner 服务端验证的租户及上传账号
     * @param businessId 租户内稳定关联值，最多 128 字符
     * @param fileName 原始展示名，不能包含路径
     * @param idempotencyKey 同一次上传重试复用的标识
     * @param stream 原始请求流，由入口管理生命周期
     * @return 已可用的记录，不含对象存储路径
     */
    Item upload(Owner owner, String businessId, String fileName, UUID idempotencyKey, InputStream stream);
}
