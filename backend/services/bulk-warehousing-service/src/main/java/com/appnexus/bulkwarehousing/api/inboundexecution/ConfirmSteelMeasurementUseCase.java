package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.UUID;

/** 钢铁件货双计量。选择账面计量口径并保留实际和理计事实。 */
public interface ConfirmSteelMeasurementUseCase {
    String PERMISSION=RegisterExternalMeasurementUseCase.RESOURCE+":confirm_steel";

    /** 两种选定重量基准。 */
    enum Basis { SCALE, THEORETICAL }

    /**
     * 已核验的件数、理计和原始磅票，实重口径必须提供一对票。
     * @param taskId 来源作业 UUID
     * @param taskVersion 当前非负作业行版本，必填
     * @param meteringNo 计量单号，最长 64 个标识字符
     * @param basis 过账采纳的计量口径
     * @param packages 卷或捆数，正整数
     * @param theoreticalKg 公斤理重，正数，最多四位小数
     * @param gross 已复核毛重票，理计且模板允许缺少实重时为空
     * @param tare 已复核皮重票，与毛重同时提供或同时省略
     */
    record Command(UUID taskId,long taskVersion,String meteringNo,Basis basis,int packages,
                   BigDecimal theoreticalKg,GroundWeighingUseCase.Ticket gross,GroundWeighingUseCase.Ticket tare) {}

    /** 已提交的双计量结果，小数字段按十进制字符串传输。 */
    record SteelMeasurement(UUID meteringId,long meteringVersion,long taskVersion,String physicalNetKg,
                            String theoreticalKg,String varianceRatio,int packages) {}

    /** @param context 可信租户操作主体 @param input 双计量确认 @return 已确认计量及作业版本 */
    SteelMeasurement confirm(VerifiedTenantContext context,Command input);
}
