package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 从已授权的角色分支解析账号与成员范围，退役的历史分支按空范围处理。 */
@Service
public class MyBatisDataScopeAuthorization implements DataScopeAuthorization {
    private final DataScopeMapper mapper;
    private final DataAccessCatalog catalog;
    private final AccessAuthorization access;
    private final ScopedWriteGate sqlGate;

    /**
     * @param mapper 权威角色及成员策略读取器
     * @param catalog 业务对象声明派生的范围目录
     * @param access 查询前实时复核功能准入
     * @param sqlGate 绑定权威查询到当前事务、主体与操作
     */
    public MyBatisDataScopeAuthorization(DataScopeMapper mapper, DataAccessCatalog catalog, AccessAuthorization access, ScopedWriteGate sqlGate) {
        this.access = access;
        this.sqlGate = sqlGate;
        this.mapper = mapper;
        this.catalog = catalog;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public DataScope resolveDataScope(AccessAuthorization.Subject subject, String code, String resource) {
        if (subject == null || subject.accountId() == null || subject.tenantId() == null
                || subject.memberId() == null || code == null || resource == null
                || !resource.matches("[a-z][a-z0-9.]*")) return DataScope.deny();

        if (!access.hasPermission(subject, code)) return DataScope.deny();
        sqlGate.authorizeServiceRead(subject.tenantId(), "access.scope:resolve",
                java.util.List.of(subject.accountId().toString(), subject.memberId().toString(), code, resource),
                () -> true);
        var policies = mapper.policies(subject.accountId().toString(), subject.tenantId().toString(),
                subject.memberId().toString(), code, resource);
        if (policies.stream().anyMatch(policy -> "ALL".equals(policy.get("scope_type"))
                && (!objectDefault(policy) || catalog.supports(code, resource, ScopeType.ALL)))) {
            return new DataScope(ScopeType.ALL, Set.of());
        }

        var rules = new ArrayList<DataGrantRules.Rule>();
        var processed = new HashSet<String>();
        var dimensions = catalog.dimensions(code, resource);
        for (var policy : policies) {
            if (!"RULES".equals(policy.get("scope_type"))) continue;
            boolean inherited = objectDefault(policy);
            if (inherited && !catalog.supports(code, resource, ScopeType.RULES)) continue;
            Object json = policy.get("rules_json");
            // 同内容的显式覆盖仍要按原契约校验，不能被无效的继承分支遮蔽。
            if (!processed.add(inherited + ":" + Objects.toString(json, ""))) continue;
            var declared = DataGrantPolicySupport.decode(json);
            try {
                catalog.validateRules(code, resource, declared);
            } catch (IllegalArgumentException unsupported) {
                if (inherited) continue;
                throw unsupported;
            }
            for (var rule : declared) {
                // 废弃条件属于整条 AND 分支，丢弃条件本身会扩大历史授权。
                if (DataGrantPolicySupport.retiredRule(rule, dimensions)
                        || !DataGrantRules.matchesSubject(rule, subject.memberId(), Set.of())) continue;
                var conditions = rule.conditions().stream()
                        .map(condition -> condition.parameter() == null ? condition
                                : new DataGrantRules.Condition(condition.field(), DataGrantRules.Operator.EQ,
                                        java.util.List.of(subject.accountId().toString())))
                        .toList();
                rules.add(new DataGrantRules.Rule(rule.subjectType(), rule.subjectIds(),
                        rule.recordIds(), conditions, rule.allRecords()));
            }
        }
        return rules.isEmpty() ? DataScope.deny() : new DataScope(ScopeType.RULES, Set.of(), rules);
    }
    /** MySQL 的 CASE 标记区分显式策略与继承范围；旧测试及显式策略没有该标记。 */
    private static boolean objectDefault(Map<String,Object> policy) {
        return policy.get("object_default") instanceof Number flag && flag.intValue() == 1;
    }
}
