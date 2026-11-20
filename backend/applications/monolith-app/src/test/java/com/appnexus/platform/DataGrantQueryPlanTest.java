package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.platformscope.MySqlRowAccessControl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** 在隔离MySQL中用一万条客商验证真实编译条件和索引计划，不以耗时阈值冒充生产容量保证。 */
@SpringBootTest(properties="app.public-url=http://localhost:18080")
@ActiveProfiles("local")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class DataGrantQueryPlanTest extends DatabaseTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test void selectiveConditionsAndRecordIntersectionUseIndexes() {
        UUID tenant=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO md_partner(id,tenant_id,code,name,partner_type)
            SELECT UUID_TO_BIN(UUID()),UUID_TO_BIN(?),CONCAT('P',n),CONCAT('Partner ',n),'CUSTOMER'
            FROM (SELECT a.n+10*b.n+100*c.n+1000*d.n n
              FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
              CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
              CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
              CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d) numbers
            """,tenant.toString());
        jdbc.execute("ANALYZE TABLE md_partner");
        var mapping=new RowAccessControl.Mapping("masterdata.partner",new RowAccessControl.Column("id",DataGrantRules.ValueType.UUID),
            new RowAccessControl.Column("organization_id",DataGrantRules.ValueType.UUID),Map.of("code",new RowAccessControl.Column("code",DataGrantRules.ValueType.TEXT),
                "name",new RowAccessControl.Column("name",DataGrantRules.ValueType.TEXT)));
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),tenant,UUID.randomUUID());
        var compiler=new MySqlRowAccessControl();
        String record=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM md_partner WHERE tenant_id=UUID_TO_BIN(?) AND code='P500'",String.class,tenant.toString());
        for(String field:List.of("code","name")) {
            var rule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),List.of(
                new DataGrantRules.Condition(field,DataGrantRules.Operator.EQ,List.of(field.equals("code")?"P500":"Partner 500"))),false);
            var plan=new AccessPlan(subject,"masterdata.partner:read","masterdata.partner",new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.RULES,Set.of(),List.of(rule)),Map.of(),Instant.now());
            var predicate=compiler.predicate(plan,mapping);var args=new ArrayList<Object>();args.add(tenant.toString());args.addAll(predicate.parameters());
            String sql="SELECT id FROM md_partner WHERE tenant_id=UUID_TO_BIN(?)"+predicate.sql();
            var explain=jdbc.queryForMap("EXPLAIN "+sql,args.toArray());
            assertThat(explain.get("key")).isEqualTo(field.equals("code")?"uk_md_partner_tenant_code":"ix_partner_tenant_name");
            assertThat(((Number)explain.get("rows")).longValue()).isLessThanOrEqualTo(2);
            assertThat(jdbc.queryForList(sql,args.toArray())).hasSize(1);
            String analyzed=jdbc.queryForObject("EXPLAIN ANALYZE "+sql,String.class,args.toArray());
            assertThat(analyzed).contains("rows=1");System.out.println("DATA_GRANT_EXPLAIN "+field+": "+analyzed);
        }
        var intersection=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(UUID.fromString(record)),
            List.of(new DataGrantRules.Condition("code",DataGrantRules.Operator.EQ,List.of("P501"))),false);
        var plan=new AccessPlan(subject,"masterdata.partner:read","masterdata.partner",new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.RULES,Set.of(),List.of(intersection)),Map.of(),Instant.now());
        var predicate=compiler.predicate(plan,mapping);var args=new ArrayList<Object>();args.add(tenant.toString());args.addAll(predicate.parameters());
        assertThat(jdbc.queryForList("SELECT id FROM md_partner WHERE tenant_id=UUID_TO_BIN(?)"+predicate.sql(),args.toArray())).isEmpty();
    }
}
