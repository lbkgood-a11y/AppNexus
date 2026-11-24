package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.Position;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 以已确认钢铁计量生成每卷每捆一行的入库草稿。 */
public interface CreateSteelReceiptDraftUseCase {
    String PERMISSION=CreateBulkReceiptDraftUseCase.RESOURCE+":create_steel_draft";

    /**
     * 每卷或捆的已确认品规、炉批和位置。
     * @param noticeLineId 同一通知下的实际商品身份及额度来源
     * @param lpn 单据内唯一卷号或捆号，最长 64 字符
     * @param heatNo 必须匹配现行已合格质保书的炉批
     * @param specification 现场核对的规格，最长 64 字符
     * @param material 现场核对的材质，最长 64 字符
     * @param theoreticalKg 公斤理重，正数，最多四位小数
     * @param position 仓、区、库位及当前引用版本
     * @param batchNo 仓库库存批次号，最长 64 字符
     * @param kilogramUnitId 可信租户内启用的千克单位
     * @param conversionRuleId 千克和库存单位不同时采用的规则 UUID，无规则为空
     * @param conversionRuleVersion 采用的非负规则版本，无规则为空
     */
    record Line(UUID noticeLineId,String lpn,String heatNo,String specification,String material,BigDecimal theoreticalKg,
                Position position,String batchNo,UUID kilogramUnitId,UUID conversionRuleId,Long conversionRuleVersion) {}

    /** 草稿输入，作业及计量版本必须为当前非负行版本。 */
    record Command(UUID taskId,long taskVersion,long meteringVersion,String steelReceiptNo,List<Line> lines) {}
    /** 新头单以及新作业版本。 */
    record SteelReceiptDraft(UUID receiptId,long receiptVersion,long taskVersion) {}

    /** @param context 已验证租户和操作主体 @param input 多 LPN 草稿 @return 已提交草稿及行版本 */
    SteelReceiptDraft createSteel(VerifiedTenantContext context,Command input);
}
