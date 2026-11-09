package com.appnexus.platform.authorization;

import java.util.Set;
import java.util.UUID;

/**
 * 结构化数据范围解析端口；返回值只能由受控仓储编译器消费，禁止拼接 SQL、SpEL 或脚本。
 * 缺少策略、上下文无效、权限不支持数据范围时均返回 {@link ScopeType#DENY}。
 */
public interface DataScopeAuthorization {
    /** 数据范围类型；DENY 是失败关闭结果，不可由管理员保存。 */
    enum ScopeType { DENY, SELF, ORGANIZATION, ORGANIZATION_SUBTREE, ALL }
    /**
     * 已解析数据范围。
     * @param type 范围类型
     * @param organizationIds 当前租户内组织 UUID；SELF、ALL、DENY 时为空
     */
    record DataScope(ScopeType type, Set<UUID> organizationIds) {
        /** 创建不可放行任何数据的稳定结果。 */
        public static DataScope deny(){return new DataScope(ScopeType.DENY,Set.of());}
    }
    /**
     * 解析主体对资源类型的有效数据范围。
     * @param subject 已验证执行主体
     * @param permissionCode 已通过功能权限判定的稳定权限码
     * @param resourceType 仓储注册的资源类型，不接受表名或 SQL 片段
     * @return 多角色合并后的结构化范围；无法安全合并时失败关闭
     */
    DataScope resolveDataScope(AccessAuthorization.Subject subject,String permissionCode,String resourceType);
}
