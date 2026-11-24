package com.appnexus.bulkwarehousing.domain.inboundexecution;

import java.util.Optional;
import java.util.UUID;

/** 按模板检查过皮、确认计量、生成入库和过账各阶段的业务门槛。 */
public interface QualityGateStrategy {
    /** 各写入点分别对最新事实重新求值。 */
    enum Point { CAPTURE_TARE, CONFIRM_MEASUREMENT, CREATE_RECEIPT, POST_RECEIPT }
    /** 暂估进入待检库存。 */
    enum Outcome { BLOCK, ACCEPT, ACCEPT_PENDING_LAB }

    /** 同一作业当前质检版本的证据和结论。 */
    record QualityFact(UUID id, int version, boolean sampled, boolean passed, boolean rejected,
                       boolean appearanceVerified, boolean mtcVerified) {}

    /** 放行决策和受控质检引用，已批准免检时可空。 */
    record Decision(Outcome outcome, String reasonCode, Optional<UUID> qualityId) {}

    /**
     * @param point 即将执行的写入点
     * @param activeQuality 锁内读取的现行质检版本
     * @param unloading 同一作业的监卸确认、证据及现场二次扣量
     * @return 放行或阻断，拒收和退车不得放行入库
     */
    Decision evaluate(Point point, Optional<QualityFact> activeQuality, UnloadingFact unloading);
}
