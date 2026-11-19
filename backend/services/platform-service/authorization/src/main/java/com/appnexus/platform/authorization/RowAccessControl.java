package com.appnexus.platform.authorization;

import java.util.List;
import java.util.UUID;

/**
 * 行范围公共执行边界，仅供业务存储适配器消费本次可信权限计划。
 * <p>不执行 SQL、不解析客户端表达式、不代替功能授权和租户隔离；实现由平台持久化适配器提供。
 * 当前仅执行已解析的组织集合和 ALL，其他范围失败关闭；未来维度需同时扩展声明与真实执行。
 */
public interface RowAccessControl {
    /**
     * 编译附加组织条件；调用方必须在同一查询中独立绑定可信租户。
     * @param plan 当前操作经 AccessPlanning 生成的计划，不允许跨请求缓存
     * @param organizationColumn 业务代码固定的组织列名，可带单层表别名；禁止来自请求或策略配置
     * @return 带 AND 前缀的条件及按占位符顺序排列的 UUID 文本参数；ALL 返回空条件
     * @throws IllegalArgumentException 列名不是允许的简单标识符
     */
    Predicate organizationPredicate(AccessPlan plan, String organizationColumn);

    /**
     * 判断目标组织是否在本次操作范围，不能替代组织属于租户及启用状态的校验。
     * @param plan 当前操作的可信计划
     * @param organizationId 目标组织 UUID；null 返回 false，历史空归属由业务显式处理
     * @return 仅 ALL 或已解析的组织集合命中时为 true
     */
    boolean allowsOrganization(AccessPlan plan, UUID organizationId);

    /**
     * 不可变的参数化附加条件，不是可接受任意 SQL 的执行入口。
     * @param sql 由平台实现生成的固定 SQL 形状；不得作为租户条件使用
     * @param parameters 占位符对应 UUID 文本，深复制且不可修改
     */
    record Predicate(String sql, List<String> parameters) {
        public Predicate {
            java.util.Objects.requireNonNull(sql);
            parameters = List.copyOf(parameters);
        }
    }
}
