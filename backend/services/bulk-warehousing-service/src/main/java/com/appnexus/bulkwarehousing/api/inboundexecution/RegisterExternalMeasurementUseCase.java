package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.UUID;

/** 授权计量员登记经过现场复核的外部整单净重凭证。 */
public interface RegisterExternalMeasurementUseCase {
    String RESOURCE = "warehousing.inboundmetering";
    String PERMISSION = RESOURCE + ":register_external";

    /** 外部整单证据来源，本命令由现场人员复核登记。 */
    enum Source { RAIL_SCALE, DRAFT_SURVEY }

    /**
     * @param taskId 来源作业 UUID
     * @param taskVersion 客户端已读取的作业版本，非负
     * @param meteringNo 本计量单编码，1 至 64 个标识字符
     * @param source 凭证种类
     * @param externalReference 来源方已签发的整单凭证业务标识，1 至 96 个标识字符
     * @param netKg 外部凭证物理净重，正数，千克，最多四位小数
     */
    record Command(UUID taskId, long taskVersion, String meteringNo, Source source,
                   String externalReference, BigDecimal netKg) {}

    /** 已确认计量单、作业版本和四位小数千克十进制字符串。 */
    record ExternalMeasurementRegistration(UUID meteringId, long meteringVersion, long taskVersion, String netKg) {}

    /**
     * @param context 服务端已验证的租户主体
     * @param command 人工办理的外部计量凭证登记
     * @return 已入库执行事务提交的计量事实
     */
    ExternalMeasurementRegistration registerExternal(VerifiedTenantContext context, Command command);
}
