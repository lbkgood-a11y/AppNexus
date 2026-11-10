package com.appnexus.platform.persistence.organization;

import com.appnexus.platform.organization.OrganizationSummary;
import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 固定 B 级联表和 C 级分组安全样例；两表显式租户谓词，聚合前应用 AccessPlan。 */
@Service
public class ScopedOrganizationSummary implements OrganizationSummary {
    private final JdbcTemplate jdbc;
    private final AccessPlanning planning;
    public ScopedOrganizationSummary(JdbcTemplate jdbc,AccessPlanning planning){this.jdbc=jdbc;this.planning=planning;}
    @Transactional(readOnly=true)
    public List<Group> summarize(AccessAuthorization.Subject subject){
        var plan=planning.plan(subject,"organization.unit:read","organization.unit");var scope=plan.rows();
        if(scope.type()==DataScopeAuthorization.ScopeType.DENY)return List.of();
        if(scope.type()!=DataScopeAuthorization.ScopeType.ALL&&scope.type()!=DataScopeAuthorization.ScopeType.ORGANIZATION)
            throw new SecurityException("Unsupported aggregate scope");
        List<Object> arguments=new ArrayList<>();arguments.add(subject.tenantId().toString());arguments.add(subject.tenantId().toString());
        String condition="";
        if(scope.type()==DataScopeAuthorization.ScopeType.ORGANIZATION){
            if(scope.organizationIds().isEmpty())return List.of();
            condition=" AND u.id IN ("+String.join(",",Collections.nCopies(scope.organizationIds().size(),"UUID_TO_BIN(?)"))+")";
            scope.organizationIds().stream().sorted().map(UUID::toString).forEach(arguments::add);
        }
        // 仅占位符数量动态生成，表名、联接、分组与排序均固定；不能接受请求 SQL/列名。
        String sql="SELECT t.code,t.name,u.enabled,COUNT(*) total FROM tenant_organization_unit u JOIN tenant_organization_type t ON t.id=u.type_id AND t.tenant_id=u.tenant_id WHERE u.tenant_id=UUID_TO_BIN(?) AND t.tenant_id=UUID_TO_BIN(?)"+condition+" GROUP BY t.code,t.name,u.enabled ORDER BY t.code,u.enabled";
        return jdbc.query(sql,(row,index)->new Group(row.getString("code"),row.getString("name"),row.getBoolean("enabled"),row.getLong("total")),arguments.toArray());
    }
}
