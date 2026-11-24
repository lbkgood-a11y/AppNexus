package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;

/** 对尚未发生后续业务的原入库单执行整笔冲红。 */
public interface ReverseInboundReceiptUseCase {
    String PERMISSION = PostInboundReceiptUseCase.RESOURCE + ":reverse";

    /**
     * @param taskId 来源作业 UUID
     * @param receiptId 已过账原入库单 UUID
     * @param taskVersion 当前作业非负行版本
     * @param receiptVersion 当前原单非负行版本
     * @param idempotencyKey 客户端重试时复用的 ASCII 幂等键，1 至 96 字符
     */
    record Command(UUID taskId, UUID receiptId, long taskVersion, long receiptVersion, String idempotencyKey) {}
    /** 冲红保留原单身份并推进原单版本。 */
    record InboundReceiptReversal(UUID receiptId, long receiptVersion) {}

    /** @param context 可信租户及执行账号 @param command 冲红命令 @return 首次或幂等结果 */
    InboundReceiptReversal reverse(VerifiedTenantContext context, Command command);
}
