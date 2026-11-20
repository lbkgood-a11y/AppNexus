package com.appnexus.platform.authorization;

import java.util.*;

/** 统一组合授权值对象：组内指定记录与条件取 AND，组间取 OR；不接受 SQL 或任意条件树。 */
public final class DataGrantRules {
    private DataGrantRules() {}
    /** 授权主体；ROLE_MEMBERS 仍须具备本分支功能权限，组织和成员选择不额外授予角色。 */
    public enum SubjectType { ROLE_MEMBERS, MEMBER, ORGANIZATION }
    /** 首批条件运算，集合值内取 OR；不提供脚本、取反及模糊匹配。 */
    public enum Operator { EQ, IN }
    /** 条件字段类型；UUID 使用二进制比较，TEXT 采用业务列原有比较语义。 */
    public enum ValueType { UUID, TEXT }
    /**
     * 受控条件。
     * @param field 目录声明的稳定业务键，禁止 SQL 列名和属性路径
     * @param operator EQ 仅一个值，IN 为 1–100 个值
     * @param values 非空标量文本，单值 1–200 字符；UUID 另按声明类型校验
     */
    public record Condition(String field, Operator operator, List<String> values) {
        public Condition {
            if (field == null || !field.matches("[a-z][A-Za-z0-9]*") || operator == null || values == null
                    || values.isEmpty() || values.size() > 100 || operator == Operator.EQ && values.size() != 1
                    || values.stream().anyMatch(v -> v == null || v.isBlank() || v.length() > 200)) throw invalid();
            values = List.copyOf(values);
        }
    }
    /**
     * 单条授权。
     * @param subjectType 授权对象类型，必填
     * @param subjectIds MEMBER/ORGANIZATION 时 1–500 个同租户标识；ROLE_MEMBERS 必须为空
     * @param recordIds 指定记录 UUID，最多 500 个；空集合表示不增加记录选择条件
     * @param conditions 同条规则内全部满足，最多 8 项；空表示不增加字段条件
     * @param allRecords 显式全部；仅当记录和条件均为空时可为 true，不能用空配置隐式放行
     */
    public record Rule(SubjectType subjectType, Set<UUID> subjectIds, Set<UUID> recordIds,
                       List<Condition> conditions, boolean allRecords) {
        public Rule {
            if (subjectType == null || subjectIds == null || recordIds == null || conditions == null
                    || subjectIds.size() > 500 || recordIds.size() > 500 || conditions.size() > 8
                    || (subjectType == SubjectType.ROLE_MEMBERS) != subjectIds.isEmpty()
                    || allRecords != (recordIds.isEmpty() && conditions.isEmpty())) throw invalid();
            subjectIds = Set.copyOf(subjectIds); recordIds = Set.copyOf(recordIds); conditions = List.copyOf(conditions);
        }
    }
    /** 验证单策略规则数量；空规则不得保存，避免隐式放行。 */
    public static List<Rule> checked(List<Rule> rules) {
        if (rules == null || rules.isEmpty() || rules.size() > 20) throw invalid();
        return List.copyOf(rules);
    }
    /**
     * 判断规则授权主体，身份事实由平台公开端口在本次事务内提供；匹配不授予角色或功能权限。
     * @param rule 已通过目录校验的规则
     * @param memberId 可信当前租户成员，不能使用账号或人员档案标识；缺失拒绝
     * @param organizationIds 当前成员有效组织集合；缺失时组织规则拒绝，不影响成员规则
     * @return 仅规则主体命中时为true；记录和字段条件仍由公共执行层独立应用
     */
    public static boolean matchesSubject(Rule rule,UUID memberId,Set<UUID> organizationIds) {
        Objects.requireNonNull(rule);
        if(memberId==null)return false;
        return switch(rule.subjectType()) {
            case ROLE_MEMBERS -> true;
            case MEMBER -> rule.subjectIds().contains(memberId);
            case ORGANIZATION -> organizationIds!=null&&!Collections.disjoint(organizationIds,rule.subjectIds());
        };
    }
    /** 本次执行跨角色合并最多100条规则及10000个记录/条件值；超限拒绝，不静默丢弃授权。 */
    public static List<Rule> executionRules(List<Rule> rules) {
        if (rules == null || rules.size() > 100) throw invalid();
        if(rules.stream().mapToLong(rule->rule.recordIds().size()+rule.conditions().stream().mapToLong(c->c.values().size()).sum()).sum()>10000)throw invalid();
        return List.copyOf(rules);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("DATA_GRANT_RULE_INVALID"); }
}
