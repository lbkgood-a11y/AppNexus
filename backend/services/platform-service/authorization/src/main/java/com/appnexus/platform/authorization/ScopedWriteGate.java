package com.appnexus.platform.authorization;

import java.util.UUID;
import java.util.List;

/** 标准写入许可端口；范围校验后出具，具体执行必须绑定当前数据库事务。 */
public interface ScopedWriteGate {
    /** @param plan 已实际验证目标或原记录的计划 @param id 固定记录 UUID */
    default void authorize(AccessPlan plan,UUID id){authorize(plan,id,null);}
    /** @param plan 可信计划 @param id 当前主记录 @param target 已验证的具体目标归属可空 */
    void authorize(AccessPlan plan,UUID id,UUID target);
    /**
     * 由业务适配器在子操作准入与父范围锁均通过后签发子记录许可。
     * @param parent 已锁定的父范围计划，具备可信租户
     * @param resource 子资源唯一键
     * @param action 已准入的子操作动作
     * @param id 子记录 UUID，新增时由服务端分配
     * @param parentKeys 按 Mapper 固定顺序的已校验关联键，新增至少一个，无关联变更时为空集合
     */
    void authorizeChild(AccessPlan parent,String resource,String action,UUID id,List<String> parentKeys);
    /** 为仅支持固定租户全部的复合单据写事务签发指定操作许可。 */
    void authorizeOperation(AccessPlan plan);
    /**
     * 无记录范围的原目录发布需在写事务重新确认统一功能权限。
     * @param authorization 权威功能权限入口
     * @param subject 当前可信租户成员
     * @param permission 原声明的发布权限码
     */
    void authorizeCatalogPublication(AccessAuthorization authorization,AccessAuthorization.Subject subject,String permission);
    /** 仅接受目录发布许可，禁止替代范围受控单据的操作许可。 */
    void requireCatalogPublication(UUID tenant,String permission);
    /**
     * 在技术资源事务中核验可信服务/人员主体的既有功能权限，并绑定固定目标键。
     * @param tenant 权威租户
     * @param permission 已登记的实际技术操作
     * @param keys 可信主体及业务固定的目标键，按 Mapper 声明顺序
     * @param allowed 宿主权威权限检查，必须逐次执行
     */
    void authorizeServiceOperation(UUID tenant,String permission,List<String> keys,java.util.function.BooleanSupplier allowed);
    /** 同事务校验技术操作的完整目标键，不能用普通业务许可代替。 */
    void requireServiceOperation(UUID tenant,String permission,List<String> keys);
    /** 校验批次语句所接受的真实单据操作，空集合必须拒绝。 */
    void requireOperation(UUID tenant,String[] permissions);
    /** 校验同事务子记录新增许可及精确关联键。 */
    void requireParentKey(UUID tenant,UUID id,String resource,String[] actions,List<String> parentKeys);
    /** @param tenant 可信租户 @param id 记录 UUID @param resource 资源键 @param actions 允许此语句复用的动作 @param target 语句指定的目标组织，可空 @param bindTarget 是否独立绑定目标 */
    void require(UUID tenant,UUID id,String resource,String[] actions,UUID target,boolean bindTarget);
}
