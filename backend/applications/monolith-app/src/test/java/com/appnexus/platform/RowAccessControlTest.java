package com.appnexus.platform;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.persistence.platformscope.MySqlRowAccessControl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 公共执行边界验证：未知范围失败关闭、参数绑定及业务固定列映射不能引入表达式。 */
class RowAccessControlTest {
    private final RowAccessControl control = new MySqlRowAccessControl();
    /** MyBatis固定条件只接受公共执行器转换后的ALL或已解析组织；未知范围不能退化为全部记录。 */
    @Test void mapperOrganizationRangesFailClosedWithoutResolvedPlan(){
        UUID id=UUID.randomUUID();
        assertThat(control.organizationRange(null).all()).isFalse();
        assertThat(control.organizationRange(null).ids()).isEmpty();
        assertThat(control.organizationRange(plan(DataScopeAuthorization.ScopeType.ALL,Set.of())).all()).isTrue();
        for(var type:List.of(DataScopeAuthorization.ScopeType.DENY,DataScopeAuthorization.ScopeType.SELF,DataScopeAuthorization.ScopeType.ORGANIZATION,DataScopeAuthorization.ScopeType.ORGANIZATION_SUBTREE,DataScopeAuthorization.ScopeType.RULES)){
            var range=control.organizationRange(plan(type,Set.of(id)));
            assertThat(range.all()).isFalse();assertThat(range.ids()).isEmpty();
        }
        assertThatThrownBy(()->control.organizationRange(plan(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(id))).ids().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private AccessPlan plan(DataScopeAuthorization.ScopeType type, Set<UUID> ids) {
        return new AccessPlan(AccessAuthorization.Subject.tenant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                "masterdata.partner:read", "masterdata.partner", new DataScopeAuthorization.DataScope(type, ids), Map.of(), Instant.now());
    }

    @Test void unresolvedOrUnsupportedScopesNeverBecomeUnrestricted() {
        UUID target = UUID.randomUUID();
        assertThat(control.organizationPredicate(null, "organization_id").sql()).isEqualTo(" AND 1=0");
        assertThat(control.allowsOrganization(null, target)).isFalse();
        for (var type : DataScopeAuthorization.ScopeType.values()) {
            if (type == DataScopeAuthorization.ScopeType.ALL) continue;
            var denied = plan(type, Set.of());
            assertThat(control.organizationPredicate(denied, "organization_id").sql()).isEqualTo(" AND 1=0");
            assertThat(control.allowsOrganization(denied, target)).isFalse();
        }
        var unresolved = plan(DataScopeAuthorization.ScopeType.ORGANIZATION_SUBTREE, Set.of(target));
        assertThat(control.allowsOrganization(unresolved, target)).isFalse();
        assertThat(control.organizationPredicate(unresolved, "organization_id").sql()).isEqualTo(" AND 1=0");
    }

    /** 退役组织集合即使随可信计划传入，也不会编译为授权条件。 */
    @Test void retiredOrganizationSetCannotAuthorizeRows() {
        UUID organization=UUID.randomUUID();
        var scoped=plan(DataScopeAuthorization.ScopeType.ORGANIZATION,Set.of(organization));
        assertThat(control.organizationPredicate(scoped,"d.document_organization_id").sql()).isEqualTo(" AND 1=0");
        assertThat(control.allowsOrganization(scoped,organization)).isFalse();
    }

    @Test void allOnlyOmitsAdditionalPredicateAndInvalidColumnsAreRejected() {
        var all = plan(DataScopeAuthorization.ScopeType.ALL, Set.of());
        assertThat(control.organizationPredicate(all, "organization_id").sql()).isEmpty();
        assertThat(control.organizationPredicate(all, "organization_id").parameters()).isEmpty();
        assertThat(control.allowsOrganization(all, UUID.randomUUID())).isTrue();
        assertThat(control.allowsOrganization(all, null)).isFalse();
        for (String invalid : List.of("organization_id OR 1=1", "p.id;DELETE", "UUID_TO_BIN(id)", "a.b.c", "`id`", "")) {
            assertThatThrownBy(() -> control.organizationPredicate(all, invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> control.organizationPredicate(all, null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 相同记录键不能跳过同分支上的字段条件，只有完整命中的分支才能授权。 */
    @Test void recordOnlyScopeNeverDropsConditions(){
        UUID record=UUID.randomUUID(),other=UUID.randomUUID();
        var base=plan(DataScopeAuthorization.ScopeType.RULES,Set.of());
        var conditional=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(record),
            List.of(new DataGrantRules.Condition("code",DataGrantRules.Operator.EQ,List.of("A"))),false);
        var exact=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(other),List.of(),false);
        var access=new AccessPlan(base.subject(),base.permissionCode(),base.resource(),
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.RULES,Set.of(),List.of(conditional,exact)),
            Map.of(),Instant.now());
        assertThat(control.allowsRecordId(access,record)).isFalse();
        assertThat(control.allowsRecordId(access,other)).isTrue();
        assertThat(control.allowsRecordId(access,UUID.randomUUID())).isFalse();
        assertThat(control.allowsRecordId(access,null)).isFalse();
    }

    /** 组合不能把指定记录和条件错误地取并集，也不能按未声明字段或其他资源编译。 */
    @Test void combinedRulesUseAndWithinBranchesAndOrAcrossBranches() {
        UUID record=UUID.randomUUID();
        var rule=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(record),
            List.of(new DataGrantRules.Condition("code",DataGrantRules.Operator.EQ,List.of("A' OR 1=1 --"))),false);
        var second=new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),
            List.of(new DataGrantRules.Condition("code",DataGrantRules.Operator.IN,List.of("B","C"))),false);
        var subject=AccessAuthorization.Subject.tenant(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var scoped=new AccessPlan(subject,"masterdata.partner:read","masterdata.partner",
            new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.RULES,Set.of(),List.of(rule,second)),Map.of(),Instant.now());
        var uuid=new RowAccessControl.Column("id",DataGrantRules.ValueType.UUID);
        var organization=new RowAccessControl.Column("organization_id",DataGrantRules.ValueType.UUID);
        var mapping=new RowAccessControl.Mapping("masterdata.partner",uuid,organization,Map.of("code",new RowAccessControl.Column("code",DataGrantRules.ValueType.TEXT)));
        var predicate=control.predicate(scoped,mapping);
        assertThat(predicate.sql()).isEqualTo(" AND ((id IN (UUID_TO_BIN(?)) AND code IN (?)) OR (code IN (?,?)))");
        assertThat(predicate.parameters()).containsExactly(record.toString(),"A' OR 1=1 --","B","C");
        assertThatThrownBy(()->control.predicate(scoped,new RowAccessControl.Mapping("other.resource",uuid,organization,mapping.dimensions()))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->control.predicate(scoped,new RowAccessControl.Mapping("masterdata.partner",uuid,organization,Map.of()))).isInstanceOf(SecurityException.class);
        assertThat(control.organizationPredicate(scoped,"organization_id").sql()).isEqualTo(" AND 1=0");
        assertThatThrownBy(()->new DataGrantRules.Rule(DataGrantRules.SubjectType.ROLE_MEMBERS,Set.of(),Set.of(),List.of(),false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DataGrantRules.Condition("phone.value",DataGrantRules.Operator.EQ,List.of("x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DataGrantRules.Condition("code",DataGrantRules.Operator.EQ,List.of("A","B"))).isInstanceOf(IllegalArgumentException.class);
    }
}
