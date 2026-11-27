package com.appnexus.platform.authorization;

import java.util.Set;
import java.util.UUID;

/**
 * 结构化数据范围解析端口；返回值只能由受控仓储编译器消费，禁止拼接 SQL、SpEL 或脚本。
 * 缺少策略、上下文无效、权限不支持数据范围时均返回 {@link ScopeType#DENY}。
 */
public interface DataScopeAuthorization {
    /** 数据范围类型；DENY 是失败关闭结果，不可由管理员保存。 */
    enum ScopeType {
        DENY, SELF, ORGANIZATION, ORGANIZATION_SUBTREE, ALL, RULES;

        /** @return 是否为授权中心开放配置的顶层范围；历史值只用于识别既有数据。 */
        public boolean configurable() { return this == ALL || this == RULES; }
    }
    /**
     * 已解析数据范围。
     * @param type 范围类型
     * @param organizationIds 历史诊断接口预留；查询执行不把此集合解释为授权
     * @param rules 已匹配主体的组合规则；仅 RULES 可携带
     */
    record DataScope(ScopeType type, Set<UUID> organizationIds, java.util.List<DataGrantRules.Rule> rules) {
        /** 既有组织范围不包含组合规则。 */
        public DataScope(ScopeType type,Set<UUID> organizationIds){this(type,organizationIds,java.util.List.of());}
        public DataScope {
            java.util.Objects.requireNonNull(type);organizationIds=Set.copyOf(organizationIds);
            rules=DataGrantRules.executionRules(rules);
            if(type!=ScopeType.RULES&&!rules.isEmpty())throw new IllegalArgumentException("Rules require RULES scope");
        }
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
