package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CreateInboundTaskUseCase.*;
import com.appnexus.platform.authorization.AccessPlan;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 车次登记和来源通知模板固定的同事务仓储端口。 */
public interface InboundTaskRegistrationStore {
    /** 通知模板及生命周期状态。 */
    record Notice(String status, UUID templateId) {}
    /** 幂等重试只比对业务输入，状态可随收货推进。 */
    record Existing(UUID noticeId, UUID templateId, String identity, String identifier, InboundArrivalTask task) {}
    /** 在统一许可及可信租户下执行事务。 */
    <T> T transaction(AccessPlan plan, Supplier<T> work);
    /** 先锁来源通知，避免并发首单绑定不同模板。 */
    Notice notice(UUID tenant, UUID id, boolean lock);
    /** 历史已登记的模板，最多两项用于识别混合绑定。 */
    List<UUID> previousTemplates(UUID tenant, UUID notice);
    /** 发布列表或已有固定模板查询。 */
    List<InboundArrivalTemplate> templates(UUID tenant, UUID id);
    /** 已为通知全部商品配置的已发布模板。 */
    List<InboundArrivalTemplate> matchingTemplates(UUID tenant, UUID notice);
    /** 按该通知读取安全作业记录。 */
    List<InboundArrivalTask> tasks(UUID tenant, UUID notice);
    /** 按请求标识取已保存车次。 */
    Existing existing(UUID tenant, UUID id);
    /** 对来源通知固定模板并推进版本。 */
    void bindTemplate(UUID tenant, UUID notice, UUID template, UUID actor);
    /** 将受控身份和状态作为到场事实写入。 */
    void insert(UUID tenant, UUID notice, UUID template, UUID actor, InboundArrivalCommand command, InboundArrivalTask task);
}
