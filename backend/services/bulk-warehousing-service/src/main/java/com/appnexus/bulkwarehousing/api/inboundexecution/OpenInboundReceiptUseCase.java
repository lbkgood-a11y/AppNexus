package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;

/** 为已到场车次占用唯一入库草稿槽位。开单不消耗通知额度或库存。 */
public interface OpenInboundReceiptUseCase {
    String PERMISSION = CreateBulkReceiptDraftUseCase.RESOURCE + ":open_draft";
    String PATH = "/api/bulk-inbound-receipt-orders:open-draft";

    /** 草稿主键和服务端生成单号。 */
    record OpenedInboundReceipt(UUID receiptId, String receiptNo) {}

    /**
     * @param context 可信租户主体
     * @param taskId 已到场作业 UUID
     * @param taskVersion 作业行版本，非负
     * @return 该车次已打开或本次创建的草稿
     */
    OpenedInboundReceipt open(VerifiedTenantContext context, UUID taskId, long taskVersion);
}
