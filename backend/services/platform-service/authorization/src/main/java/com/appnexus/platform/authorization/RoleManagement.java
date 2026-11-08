package com.appnexus.platform.authorization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 角色生命周期用例；角色授权和用户绑定不属于本接口。
 * 调用方必须使用已验证的账号、租户及成员上下文，客户端不得指定操作者身份。
 */
public interface RoleManagement {
    /** 角色管理域。 */
    enum ScopeType { PLATFORM, TENANT }
    /** 角色来源类型；系统角色由初始化流程维护。 */
    enum RoleType { SYSTEM, CUSTOM }
    /** 角色可用状态；逻辑删除不作为可恢复状态暴露。 */
    enum Status { ENABLED, DISABLED }

    /**
     * 可信管理上下文。
     * @param actorAccountId 已认证账号 UUID
     * @param tenantId 平台角色为空；租户角色为已验证目标租户
     * @param actorMemberId 当前租户自助管理时必填；平台代管时为空
     */
    record Context(UUID actorAccountId, UUID tenantId, UUID actorMemberId) {}

    /**
     * 角色视图。
     * @param code 创建后不可修改的稳定编码
     * @param version 从 0 开始的乐观锁版本，写操作必须原样回传
     * @param updatedAt 服务端 UTC 时间点
     */
    record Role(UUID id, ScopeType scopeType, UUID tenantId, RoleType roleType,
                String systemKey, String code, String name, String description,
                Status status, int sortOrder, long version, Instant updatedAt) {}

    /** @param items 当前页数据 @param total 符合筛选条件的总数 */
    record Page(List<Role> items, long total, int page, int pageSize) {}

    /**
     * 查询条件。
     * @param query 名称或编码关键词，空字符串表示不过滤
     * @param page 从 1 开始
     * @param pageSize 每页 1–100 条
     */
    record Query(String query, Status status, RoleType roleType, int page, int pageSize) {}

    /**
     * 创建字段。
     * @param code 2–64 位小写字母、数字或下划线，且以小写字母开头
     * @param name 展示名称，1–100 字符
     * @param description 可空，最长 500 字符
     * @param sortOrder 排序值 0–9999，默认由客户端显式传入 100
     */
    record Create(String code, String name, String description, int sortOrder) {}

    /** 可变字段；编码、管理域、类型和状态由专用流程维护。 */
    record Update(String name, String description, int sortOrder, long version) {}

    Page list(Context context, Query query);
    Role create(Context context, Create input);
    Role update(Context context, UUID roleId, Update input);
    Role changeStatus(Context context, UUID roleId, long version, Status status, String reason);
    void delete(Context context, UUID roleId, long version, String confirmationCode, String reason);
}
