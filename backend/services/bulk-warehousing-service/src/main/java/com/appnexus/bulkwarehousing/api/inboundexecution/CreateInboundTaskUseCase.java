package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.List;
import java.util.UUID;

/** 在已签发通知下登记到场车次，固定同一通知采用的品类模板版本。 */
public interface CreateInboundTaskUseCase {
    String PERMISSION = "warehousing.inboundtask:create_from_notice";
    String PATH = "/api/bulk-inbound-notices/{id}/tasks";

    /** 有牌车辆、无牌车辆或非车辆到场批次。 */
    enum VehicleIdentity { PLATED, UNPLATED, OTHER }

    /** 仅首车次允许提交明确选择的模板，重试使用同一请求 UUID。 */
    record InboundArrivalCommand(UUID requestId, UUID templateId, VehicleIdentity identity, String identifier) {}
    /** 已发布模板的最小提示。 */
    record InboundArrivalTemplate(UUID id, String code, int version, String qualityStrategy) {}
    /** 通知详情的安全作业记录，不包含物流身份。 */
    record InboundArrivalTask(UUID id, String number, String status, String stage) {}
    /** 创建上下文及最近 20 条车次。 */
    record InboundArrivalContext(boolean creatable, InboundArrivalTemplate template, List<InboundArrivalTemplate> templates,
                                 List<InboundArrivalTask> tasks, List<VehicleIdentity> identities) {}

    /** @return 来源通知与当前可选模板的受控快照 */
    InboundArrivalContext context(VerifiedTenantContext subject, UUID noticeId);

    /** @return 已登记的可追溯车次，重试相同请求返回原车次 */
    InboundArrivalTask create(VerifiedTenantContext subject, UUID noticeId, InboundArrivalCommand command);
}
