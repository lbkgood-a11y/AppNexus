package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;

/** 从确认净重生成散货入库草稿，尚不产生库存或额度扣减。 */
public interface CreateBulkReceiptDraftUseCase {
    String RESOURCE = "warehousing.receipt";
    String PERMISSION = RESOURCE + ":create_bulk_draft";

    /**
     * @param taskId 来源作业 UUID
     * @param taskVersion 已读取的作业行版本，非负
     * @param meteringVersion 已读取的计量行版本，非负
     * @param noticeLineId 同一通知的入库明细 UUID，本命令使用整单单行
     * @param receiptNo 新单编码，1 至 64 字符
     * @param batchNo 本次批次号，1 至 64 字符
     * @param position 现有库位、库区、仓库与档案版本
     * @param kilogramUnitId 已授权且启用的千克单位 UUID
     * @param conversionRuleId 千克至库存单位换算规则，库存单位为千克时为空
     * @param conversionRuleVersion 换算规则原版本，无规则时为空
     * @param replacesReceiptId 冲红重开时必须是同作业最近已冲红原单 UUID，首次草稿为空
     */
    record Command(UUID taskId, long taskVersion, long meteringVersion, UUID noticeLineId,
                   String receiptNo, String batchNo, Position position, UUID kilogramUnitId,
                   UUID conversionRuleId, Long conversionRuleVersion, UUID replacesReceiptId) {}

    /** 已提交入库草稿及作业版本。 */
    record BulkReceiptDraft(UUID receiptId, long receiptVersion, long taskVersion, String qualityState) {}

    /** @param context 可信租户与执行账号 @param command 单行草稿输入 @return 已提交草稿 */
    BulkReceiptDraft create(VerifiedTenantContext context, Command command);
}
