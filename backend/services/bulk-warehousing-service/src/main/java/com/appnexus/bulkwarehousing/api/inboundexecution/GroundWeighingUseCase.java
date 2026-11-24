package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 人工复核的地磅毛重、监卸和皮重命令。 */
public interface GroundWeighingUseCase {
    String GROSS = RegisterExternalMeasurementUseCase.RESOURCE + ":record_gross";
    String TARE = RegisterExternalMeasurementUseCase.RESOURCE + ":record_tare";
    String UNLOAD = ReadInboundWorkbenchUseCase.RESOURCE + ":confirm_unloading";

    /** 已复核的站点、票据及实际读数。重量为正，千克，最多四位小数。 */
    record Ticket(String station, String ticketNo, Instant observedAt, BigDecimal kg) {}
    /** 毛重登记输入，作业版本非负且必须与锁内值相同。 */
    record Gross(UUID taskId, long taskVersion, String meteringNo, Ticket ticket) {}
    /** 现场监卸结论。 */
    enum UnloadOutcome { ACCEPTED, ACCEPTED_WITH_DEDUCTION, RETURN }
    /** 监卸输入，异常时填写正扣重，千克；其他结论传零。 */
    record Unload(UUID taskId, long taskVersion, long meterVersion, UnloadOutcome outcome,
                  BigDecimal extraDeductionKg) {}
    /** 过皮或退车离场输入。 */
    record Tare(UUID taskId, long taskVersion, long meterVersion, Ticket ticket) {}
    /** 已提交计量和作业版本，确认实收时返回精确净重字符串。 */
    record GroundWeighingResult(UUID meteringId, long meterVersion, long taskVersion, String state, String netKg) {}

    /** @param context 可信租户与操作账号 @param command 毛重票据 @return 已提交计量 */
    GroundWeighingResult gross(VerifiedTenantContext context, Gross command);
    /** @param context 可信保管员 @param command 监卸结论 @return 已提交作业及计量版本 */
    GroundWeighingResult unload(VerifiedTenantContext context, Unload command);
    /** @param context 可信计量员 @param command 皮重票据 @return 实收或退车结果 */
    GroundWeighingResult tare(VerifiedTenantContext context, Tare command);
}
