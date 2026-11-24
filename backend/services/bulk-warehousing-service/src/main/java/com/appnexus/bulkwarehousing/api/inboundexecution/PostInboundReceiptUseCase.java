package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;

/** 对已执行采样门槛的外部散货草稿过账，质量未判定时保留待化验库存。 */
public interface PostInboundReceiptUseCase {
    String RESOURCE = CreateBulkReceiptDraftUseCase.RESOURCE;
    String PERMISSION = RESOURCE + ":post";

    /**
     * @param taskId 来源作业 UUID
     * @param receiptId 同作业的入库单 UUID
     * @param taskVersion 当前作业行版本，非负
     * @param receiptVersion 当前入库单行版本，非负
     * @param idempotencyKey 同一请求重试时保持稳定的键，1 至 96 个 ASCII 标识字符
     */
    record Command(UUID taskId, UUID receiptId, long taskVersion, long receiptVersion, String idempotencyKey) {}
    /** 已过账的入库单和当次版本。 */
    record InboundReceiptPosting(UUID receiptId, long receiptVersion) {}

    /** @param context 可信租户及执行账号 @param command 版本化过账命令 @return 首次或幂等过账结果 */
    InboundReceiptPosting post(VerifiedTenantContext context, Command command);
}
