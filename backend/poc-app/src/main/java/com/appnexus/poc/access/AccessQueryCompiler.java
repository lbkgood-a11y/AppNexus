package com.appnexus.poc.access;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

public final class AccessQueryCompiler {

    public CompiledAccessQuery compileSelect(String baseSql, ResourceDescriptor resource, AccessPlan plan) {
        return compileSelect(baseSql, resource, List.of(), plan);
    }

    public CompiledAccessQuery compileSelect(
            String baseSql,
            ResourceDescriptor resource,
            List<ResourceDescriptor> joinedTenantResources,
            AccessPlan plan) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(baseSql);
            PlainSelect body = select.getPlainSelect();
            assertRootResource(body, resource);
            Map<String, Object> parameters = new LinkedHashMap<>();
            enforceJoinedTenantBoundaries(body, resource, joinedTenantResources);
            Expression finalPolicy = buildPolicy(resource, plan, parameters);
            body.setWhere(body.getWhere() == null ? finalPolicy : new AndExpression(body.getWhere(), finalPolicy));
            return new CompiledAccessQuery(select.toString(), Map.copyOf(parameters));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("SQL cannot be safely compiled", exception);
        }
    }

    public CompiledAccessQuery compileUpdate(String baseSql, ResourceDescriptor resource, AccessPlan plan) {
        try {
            Update update = (Update) CCJSqlParserUtil.parse(baseSql);
            assertTable(update.getTable().toString(), resource);
            Map<String, Object> parameters = new LinkedHashMap<>();
            Expression policy = buildPolicy(resource, plan, parameters);
            update.setWhere(update.getWhere() == null ? policy : new AndExpression(update.getWhere(), policy));
            return new CompiledAccessQuery(update.toString(), Map.copyOf(parameters));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("UPDATE cannot be safely compiled", exception);
        }
    }

    public CompiledAccessQuery compileDelete(String baseSql, ResourceDescriptor resource, AccessPlan plan) {
        try {
            Delete delete = (Delete) CCJSqlParserUtil.parse(baseSql);
            assertTable(delete.getTable().toString(), resource);
            Map<String, Object> parameters = new LinkedHashMap<>();
            Expression policy = buildPolicy(resource, plan, parameters);
            delete.setWhere(delete.getWhere() == null ? policy : new AndExpression(delete.getWhere(), policy));
            return new CompiledAccessQuery(delete.toString(), Map.copyOf(parameters));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("DELETE cannot be safely compiled", exception);
        }
    }

    private static void assertRootResource(PlainSelect select, ResourceDescriptor resource) {
        assertTable(select.getFromItem().toString(), resource);
    }

    private static void assertTable(String table, ResourceDescriptor resource) {
        String expected = resource.tableName() + " " + resource.alias();
        if (!table.equalsIgnoreCase(expected) && !table.equalsIgnoreCase(resource.tableName() + " AS " + resource.alias())) {
            throw new IllegalArgumentException("SQL root table does not match the registered resource");
        }
    }

    private static void enforceJoinedTenantBoundaries(
            PlainSelect select,
            ResourceDescriptor root,
            List<ResourceDescriptor> joinedResources) {
        List<Join> joins = select.getJoins();
        if (joins == null || joins.isEmpty()) {
            if (!joinedResources.isEmpty()) {
                throw new IllegalArgumentException("Registered joined resources are missing from SQL");
            }
            return;
        }
        if (joins.size() != joinedResources.size()) {
            throw new IllegalArgumentException("Every joined table must have a registered access descriptor");
        }
        for (ResourceDescriptor joined : joinedResources) {
            Join join = joins.stream()
                    .filter(candidate -> tableMatches(candidate.getRightItem().toString(), joined))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Registered join is missing from SQL: " + joined.tableName()));
            Expression tenantEquality = new EqualsTo(
                    new Column(joined.tenantColumnReference()),
                    new Column(root.tenantColumnReference()));
            join.setOnExpression(join.getOnExpression() == null
                    ? tenantEquality
                    : new AndExpression(join.getOnExpression(), tenantEquality));
        }
    }

    private static boolean tableMatches(String table, ResourceDescriptor resource) {
        return table.equalsIgnoreCase(resource.tableName() + " " + resource.alias())
                || table.equalsIgnoreCase(resource.tableName() + " AS " + resource.alias());
    }

    private static Expression buildPolicy(
            ResourceDescriptor resource,
            AccessPlan plan,
            Map<String, Object> parameters) throws Exception {
        parameters.put("accessTenantId", plan.tenantId());
        Expression tenantBoundary = new EqualsTo(
                new Column(resource.tenantColumnReference()),
                new JdbcNamedParameter("accessTenantId"));
        Expression grants = combineOr(plan.grants(), resource, parameters, "grant");
        Expression finalPolicy = new AndExpression(tenantBoundary, grants);
        if (!plan.mandatoryDenies().isEmpty()) {
            Expression denies = combineOr(plan.mandatoryDenies(), resource, parameters, "deny");
            finalPolicy = new AndExpression(finalPolicy, CCJSqlParserUtil.parseCondExpression("NOT (" + denies + ")"));
        }
        return finalPolicy;
    }

    private static Expression combineOr(
            List<AccessPredicate> predicates,
            ResourceDescriptor resource,
            Map<String, Object> parameters,
            String prefix) {
        Expression result = null;
        for (int index = 0; index < predicates.size(); index++) {
            Expression current = compilePredicate(predicates.get(index), resource, parameters, prefix + index);
            result = result == null ? current : new OrExpression(result, current);
        }
        return result;
    }

    private static Expression compilePredicate(
            AccessPredicate predicate,
            ResourceDescriptor resource,
            Map<String, Object> parameters,
            String parameterName) {
        if (!(predicate instanceof ComparisonPredicate comparison)) {
            throw new IllegalArgumentException("Unsupported access predicate: " + predicate.getClass().getSimpleName());
        }
        parameters.put(parameterName, comparison.value());
        Column column = new Column(resource.column(comparison.fieldId()));
        JdbcNamedParameter parameter = new JdbcNamedParameter(parameterName);
        return switch (comparison.operator()) {
            case EQUALS -> new EqualsTo(column, parameter);
            case NOT_EQUALS -> new NotEqualsTo(column, parameter);
        };
    }
}
