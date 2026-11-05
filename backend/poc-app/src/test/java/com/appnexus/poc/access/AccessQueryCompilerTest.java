package com.appnexus.poc.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessQueryCompilerTest {

    private static final UUID TENANT_ID = UUID.fromString("019a56e8-4ae4-7000-8000-000000000001");
    private static final ResourceDescriptor RESOURCE = new ResourceDescriptor(
            "org_unit",
            "u",
            "tenant_id",
            Map.of("organizationId", "id", "status", "status"));
    private static final ResourceDescriptor MEMBER_RESOURCE = new ResourceDescriptor(
            "tenant_member",
            "m",
            "tenant_id",
            Map.of("memberId", "id"));

    private final AccessQueryCompiler compiler = new AccessQueryCompiler();

    @Test
    void compilesTenantBoundaryGrantUnionAndMandatoryDenyAsOnePolicy() {
        AccessPlan plan = new AccessPlan(
                TENANT_ID,
                List.of(
                        new ComparisonPredicate("organizationId", ComparisonPredicate.Operator.EQUALS, "org-a"),
                        new ComparisonPredicate("organizationId", ComparisonPredicate.Operator.EQUALS, "org-b")),
                List.of(new ComparisonPredicate("status", ComparisonPredicate.Operator.EQUALS, "restricted")));

        CompiledAccessQuery compiled = compiler.compileSelect(
                "SELECT u.id, u.name FROM org_unit u WHERE u.enabled = true",
                RESOURCE,
                plan);

        assertThat(compiled.sql())
                .contains("u.enabled = true")
                .contains("u.tenant_id = :accessTenantId")
                .contains("u.id = :grant0 OR u.id = :grant1")
                .contains("NOT (u.status = :deny0)");
        assertThat(compiled.parameters())
                .containsEntry("accessTenantId", TENANT_ID)
                .containsEntry("grant0", "org-a")
                .containsEntry("grant1", "org-b")
                .containsEntry("deny0", "restricted");
    }

    @Test
    void rejectsUnregisteredFields() {
        AccessPlan plan = new AccessPlan(
                TENANT_ID,
                List.of(new ComparisonPredicate("rawColumn", ComparisonPredicate.Operator.EQUALS, "x")),
                List.of());

        assertThatThrownBy(() -> compiler.compileSelect("SELECT u.id FROM org_unit u", RESOURCE, plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unregistered access field");
    }

    @Test
    void rejectsSqlForAnotherRootResource() {
        AccessPlan plan = new AccessPlan(
                TENANT_ID,
                List.of(new ComparisonPredicate("organizationId", ComparisonPredicate.Operator.EQUALS, "org-a")),
                List.of());

        assertThatThrownBy(() -> compiler.compileSelect("SELECT r.id FROM role r", RESOURCE, plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root table");
    }

    @Test
    void updateAndDeleteReuseTheSameTenantAndDataPolicy() {
        AccessPlan plan = basicPlan();

        CompiledAccessQuery update = compiler.compileUpdate(
                "UPDATE org_unit u SET u.name = :name WHERE u.id = :id AND u.version = :version",
                RESOURCE,
                plan);
        CompiledAccessQuery delete = compiler.compileDelete(
                "DELETE FROM org_unit u WHERE u.id = :id",
                RESOURCE,
                plan);

        assertThat(update.sql())
                .contains("u.id = :id")
                .contains("u.version = :version")
                .contains("u.tenant_id = :accessTenantId")
                .contains("u.id = :grant0");
        assertThat(delete.sql())
                .contains("u.id = :id")
                .contains("u.tenant_id = :accessTenantId")
                .contains("u.id = :grant0");
    }

    @Test
    void countUsesTheSameAccessPlanAsListQuery() {
        CompiledAccessQuery count = compiler.compileSelect(
                "SELECT COUNT(*) FROM org_unit u WHERE u.enabled = true",
                RESOURCE,
                basicPlan());

        assertThat(count.sql())
                .contains("COUNT(*)")
                .contains("u.tenant_id = :accessTenantId")
                .contains("u.id = :grant0");
    }

    @Test
    void fixedLeftJoinKeepsTenantConsistencyInsideOnClause() {
        CompiledAccessQuery compiled = compiler.compileSelect(
                "SELECT u.id, m.id FROM org_unit u LEFT JOIN tenant_member m ON m.id = u.leader_member_id",
                RESOURCE,
                List.of(MEMBER_RESOURCE),
                basicPlan());

        assertThat(compiled.sql())
                .contains("LEFT JOIN tenant_member m ON m.id = u.leader_member_id AND m.tenant_id = u.tenant_id")
                .contains("WHERE u.tenant_id = :accessTenantId");
    }

    @Test
    void rejectsJoinWhenCallerTriesToOmitItsTenantDescriptor() {
        assertThatThrownBy(() -> compiler.compileSelect(
                        "SELECT u.id, m.id FROM org_unit u JOIN tenant_member m ON m.id = u.leader_member_id",
                        RESOURCE,
                        basicPlan()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Every joined table");
    }

    private static AccessPlan basicPlan() {
        return new AccessPlan(
                TENANT_ID,
                List.of(new ComparisonPredicate("organizationId", ComparisonPredicate.Operator.EQUALS, "org-a")),
                List.of());
    }
}
