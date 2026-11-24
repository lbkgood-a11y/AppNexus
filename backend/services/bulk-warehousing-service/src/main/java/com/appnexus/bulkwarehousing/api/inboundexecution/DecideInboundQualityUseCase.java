package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** 独立质检判定，已完成的待化验过账通过事件同步库存质量。 */
public interface DecideInboundQualityUseCase {
    String PERMISSION = RegisterInboundSampleUseCase.RESOURCE + ":decide_quality";

    /**
     * @param taskId 来源作业 UUID
     * @param taskVersion 客户端已读取的作业版本，非负
     * @param qualityId 同作业的现行质检单 UUID
     * @param qualityRowVersion 已读取的质检行版本，非负
     * @param metrics 按发布模板指标键提交原始数值，单位来自模板；最多 32 项
     */
    record Command(UUID taskId, long taskVersion, UUID qualityId, long qualityRowVersion,
                   Map<String, BigDecimal> metrics) {}

    /** 由模板阈值计算的结论及写入后的行版本。 */
    record Decided(UUID qualityId, String decision, long qualityRowVersion, long taskVersion) {}

    /**
     * @param context 服务端验证的租户与实际账号
     * @param command 现行样品和实验室指标
     * @return 已提交的质检结论
     */
    Decided decide(VerifiedTenantContext context, Command command);
}
