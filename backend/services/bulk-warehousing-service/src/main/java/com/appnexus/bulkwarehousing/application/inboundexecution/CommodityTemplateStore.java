package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CommodityTemplates.*;
import com.appnexus.platform.authorization.AccessPlan;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 模板维护事务，所有方法绑定当前租户和统一许可。 */
public interface CommodityTemplateStore {
    /** 在受控主库事务执行。 */
    <T> T transaction(AccessPlan access, Supplier<T> work);
    /** 读取授权页及同一快照总数。 */
    CommodityTemplatePage list(UUID tenant, int offset, int limit);
    /** 锁定草稿。 */
    CommodityTemplateVersion lock(UUID tenant, UUID id);
    /** 发布时读取同编码锁的定位信息，不在此处抢占单行锁。 */
    CommodityTemplateVersion find(UUID tenant, UUID id);
    /** 锁定逻辑模板的全部版本以串行化发布。 */
    List<CommodityTemplateVersion> lockCode(UUID tenant, String code);
    /** 写入新草稿。 */
    void insert(UUID tenant, UUID actor, UUID id, CommodityTemplateDraft draft);
    /** 按版本更新草稿。 */
    int update(UUID tenant, UUID actor, UUID id, long rowVersion, CommodityTemplateDraft draft);
    /** 只停用同编码原发布版本，旧作业仍按旧版本执行。 */
    void retire(UUID tenant, UUID actor, String code, UUID id);
    /** 发布草稿并更新版本。 */
    int publish(UUID tenant, UUID actor, UUID id, long rowVersion);
}
