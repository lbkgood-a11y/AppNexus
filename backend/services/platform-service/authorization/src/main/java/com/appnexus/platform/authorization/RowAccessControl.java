package com.appnexus.platform.authorization;

import java.util.List;
import java.util.UUID;

/**
 * 行范围公共执行边界，仅供业务存储适配器消费本次可信权限计划。
 * <p>不执行 SQL、不解析客户端表达式、不代替功能授权和租户隔离；实现由平台持久化适配器提供。
 * 支持已解析组织集合、ALL及声明接通的组合规则；不支持的范围失败关闭，新增维度必须接通声明与执行。
 */
public interface RowAccessControl {
    /**
     * 为固定MyBatis条件生成已解析的组织范围；只负责计划转换，不重新决定角色或合并策略。
     * @param plan 当前操作的可信计划；null、DENY、未解析子树或RULES均失败关闭
     * @return ALL或已解析组织UUID集合；空集合且非ALL表示拒绝
     */
    default OrganizationRange organizationRange(AccessPlan plan){
        if(plan==null)return new OrganizationRange(false,List.of());
        if(plan.rows().type()==DataScopeAuthorization.ScopeType.ALL)return new OrganizationRange(true,List.of());
        if(plan.rows().type()==DataScopeAuthorization.ScopeType.ORGANIZATION)
            return new OrganizationRange(false,plan.rows().organizationIds().stream().sorted().map(UUID::toString).toList());
        return new OrganizationRange(false,List.of());
    }
    /**
     * 固定Mapper可消费的范围值，不承载SQL或客户端策略。
     * @param all 只有可信计划为ALL时才为true；租户条件始终独立强制
     * @param ids 已解析的组织UUID字符串；非ALL空集合表示拒绝，不作全部范围回退
     */
    record OrganizationRange(boolean all,List<String> ids){public OrganizationRange{ids=List.copyOf(ids);}}
    /**
     * 编译已匹配主体的组合规则，组内 AND、组间 OR；租户条件仍必须由业务查询独立强制。
     * @param plan 本次操作的可信权限计划
     * @param mapping 业务代码固定列映射，不能由请求或授权配置构造
     * @return 参数化附加条件；不认识的条件或映射直接拒绝，不能忽略
     */
    Predicate predicate(AccessPlan plan, Mapping mapping);

    /** @param column 固定 SQL 标识符，可带单层别名 @param type UUID/TEXT 决定参数绑定方式 */
    record Column(String column,DataGrantRules.ValueType type) {
        public Column {
            if(column==null||!column.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)?")||type==null)throw new IllegalArgumentException("Invalid fixed column mapping");
        }
    }
    /**
     * 固定业务资源映射，避免权限计划跨资源使用。
     * @param resource 与 AccessPlan 精确一致的资源键
     * @param recordId 主记录 UUID 列
     * @param organization 旧组织范围匹配列
     * @param dimensions 统一声明业务维度到固定列的映射；不接收任意属性路径
     */
    record Mapping(String resource,Column recordId,Column organization,java.util.Map<String,Column> dimensions) {
        public Mapping {
            java.util.Objects.requireNonNull(resource);java.util.Objects.requireNonNull(recordId);java.util.Objects.requireNonNull(organization);
            if(recordId.type()!=DataGrantRules.ValueType.UUID||organization.type()!=DataGrantRules.ValueType.UUID)throw new IllegalArgumentException("Identity mappings require UUID");
            dimensions=java.util.Map.copyOf(dimensions);
        }
    }
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
     * @param parameters 占位符对应标量文本，UUID按映射转换；深复制且不可修改
     * @param plan 标准受控入口使用的本次计划；旧适配器可为空
     */
    record Predicate(String sql,List<String> parameters,AccessPlan plan) {
        /** 保留旧专用存储适配器的编译入口；受控 Mapper 不接受没有计划的谓词。 */
        public Predicate(String sql,List<String> parameters){this(sql,parameters,null);}
        public Predicate {
            java.util.Objects.requireNonNull(sql);
            parameters = List.copyOf(parameters);
        }
    }
}
