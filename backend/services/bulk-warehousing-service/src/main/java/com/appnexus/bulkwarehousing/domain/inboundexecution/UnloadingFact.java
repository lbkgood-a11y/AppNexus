package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * 由应用层从同一作业的已授权记录读取的监卸事实。
 *
 * @param disposition 未确认、不适用、正常、异常扣量或退车
 * @param evidenceId 现场确认凭证 UUID，未确认或不适用为空
 * @param extraDeductionKg 现场二次扣量，千克，最多四位小数，非扣量结论传零
 */
public record UnloadingFact(Disposition disposition, UUID evidenceId, BigDecimal extraDeductionKg) {
    /** 保管员记录的监卸结果。 */
    public enum Disposition { UNCONFIRMED, NOT_REQUIRED, ACCEPTED, ACCEPTED_WITH_DEDUCTION, RETURN }

    /** 校验结论、证据和二次扣量一致性。 */
    public UnloadingFact {
        Objects.requireNonNull(disposition);
        extraDeductionKg = Objects.requireNonNull(extraDeductionKg).setScale(4);
        boolean confirmationRequired = switch (disposition) {
            case ACCEPTED, ACCEPTED_WITH_DEDUCTION, RETURN -> true;
            case UNCONFIRMED, NOT_REQUIRED -> false;
        };
        if ((evidenceId != null) != confirmationRequired
                || extraDeductionKg.signum() != (disposition == Disposition.ACCEPTED_WITH_DEDUCTION ? 1 : 0)) {
            throw new IllegalArgumentException("监卸结论、证据和二次扣量不一致");
        }
    }
}
