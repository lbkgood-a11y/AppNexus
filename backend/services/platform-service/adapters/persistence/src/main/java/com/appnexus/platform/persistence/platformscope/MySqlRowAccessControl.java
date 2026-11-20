package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType;
import com.appnexus.platform.authorization.RowAccessControl;
import com.appnexus.platform.authorization.DataGrantRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 公共 MySQL 行范围编译器；仅生成固定谓词及绑定值，不查询业务表、不持有跨请求授权缓存。
 * <p>索引列保持原值比较，UUID 转换只作用于参数；租户边界必须由调用方始终独立强制。
 */
@Component
public final class MySqlRowAccessControl implements RowAccessControl {
    @Override
    public Predicate predicate(AccessPlan plan, Mapping mapping) {
        if(plan==null)return new Predicate(" AND 1=0",List.of());
        if(!plan.resource().equals(mapping.resource()))throw new SecurityException("ROW_ACCESS_RESOURCE_MISMATCH");
        if(plan.rows().type()!=ScopeType.RULES)return organizationPredicate(plan,mapping.organization().column());
        var branches=new ArrayList<String>();var parameters=new ArrayList<String>();
        if(!plan.rows().organizationIds().isEmpty())branches.add(in(mapping.organization(),plan.rows().organizationIds().stream().sorted().map(UUID::toString).toList(),parameters));
        for(var rule:plan.rows().rules()) {
            var terms=new ArrayList<String>();
            if(rule.allRecords())return new Predicate("",List.of());
            if(!rule.recordIds().isEmpty())terms.add(in(mapping.recordId(),rule.recordIds().stream().sorted().map(UUID::toString).toList(),parameters));
            for(var condition:rule.conditions()) {
                var column=mapping.dimensions().get(condition.field());
                if(column==null)throw new SecurityException("ROW_ACCESS_FIELD_UNMAPPED");
                terms.add(in(column,condition.values(),parameters));
            }
            if(terms.isEmpty())throw new SecurityException("ROW_ACCESS_EMPTY_RULE");
            branches.add("("+String.join(" AND ",terms)+")");
        }
        return new Predicate(branches.isEmpty()?" AND 1=0":" AND ("+String.join(" OR ",branches)+")",parameters);
    }

    /** 业务索引列不做函数转换，值仅通过占位符传入；UUID 需规范化，避免数据库宽松转换。 */
    private String in(Column column,List<String> values,List<String> parameters) {
        if(column.type()==DataGrantRules.ValueType.UUID)for(String value:values)
            if(!UUID.fromString(value).toString().equals(value))throw new SecurityException("ROW_ACCESS_UUID_INVALID");
        parameters.addAll(values);
        return column.column()+" IN ("+String.join(",",Collections.nCopies(values.size(),column.type()==DataGrantRules.ValueType.UUID?"UUID_TO_BIN(?)":"?"))+")";
    }
    @Override
    public Predicate organizationPredicate(AccessPlan plan, String organizationColumn) {
        if (organizationColumn == null || !organizationColumn.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)?")) {
            throw new IllegalArgumentException("Organization column must be a fixed SQL identifier");
        }
        if (plan == null) return new Predicate(" AND 1=0", List.of());
        if (plan.rows().type() == ScopeType.ALL) return new Predicate("", List.of());
        if (plan.rows().type() != ScopeType.ORGANIZATION || plan.rows().organizationIds().isEmpty()) {
            return new Predicate(" AND 1=0", List.of());
        }
        var parameters = plan.rows().organizationIds().stream().sorted().map(UUID::toString).toList();
        return new Predicate(" AND " + organizationColumn + " IN ("
                + String.join(",", Collections.nCopies(parameters.size(), "UUID_TO_BIN(?)")) + ")", parameters);
    }

    @Override
    public boolean allowsOrganization(AccessPlan plan, UUID organizationId) {
        return plan != null && organizationId != null
                && (plan.rows().type() == ScopeType.ALL
                || plan.rows().type() == ScopeType.ORGANIZATION && plan.rows().organizationIds().contains(organizationId));
    }
}
