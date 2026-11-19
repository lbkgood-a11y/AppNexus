package com.appnexus.platform;

import com.appnexus.platform.configuration.DictionaryManagement;
import com.appnexus.platform.configuration.DictionaryValueStandard;
import com.appnexus.platform.persistence.configuration.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** 真实 MySQL 验证 V102 存量替换、引用不变、聚合审计、冲突回滚及新建入口标准。 */
@SpringBootTest
@ActiveProfiles("local")
class DictionaryStandardIntegrationTest extends DatabaseTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DictionaryManagement dictionaries;
    @Autowired com.appnexus.platform.configuration.BusinessValueCatalog catalog;
    @Autowired com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap bootstrap;

    /** 旧版本资源保持不变，用旧初始化器构造真实升级前状态。 */
    private UUID oldTenant() {
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'字典升级测试租户')", tenant.toString());
        BusinessValueDefaults.seed(jdbc, tenant, null);
        WarehouseValueDefaults.seed(jdbc, tenant, null);
        return tenant;
    }

    private void migrate(UUID tenant) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> DictionaryStandardMigration.migrateTenant(jdbc, tenant));
    }

    private Map<String,Object> item(UUID tenant, String dictionary, String value) {
        return jdbc.queryForMap("SELECT BIN_TO_UUID(i.id) id,i.code,i.item_value,i.name,i.status,i.description,i.created_at,i.created_by,i.updated_by,i.version "
            + "FROM tenant_dictionary_item i JOIN tenant_dictionary d ON d.id=i.dictionary_id AND d.tenant_id=i.tenant_id "
            + "WHERE i.tenant_id=UUID_TO_BIN(?) AND d.code=? AND BINARY i.item_value=BINARY ?", tenant.toString(), dictionary, value);
    }

    @Test void upgradeReplacesColumnsAndJsonWithoutChangingIdentityOrHistoricalMeaning() {
        UUID tenant = oldTenant(), other = oldTenant();
        String t = tenant.toString(), partner = UUID.randomUUID().toString(), person = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO md_partner(id,tenant_id,code,name,partner_type,nature,staff_size,created_by,created_at) "
            + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PARTNER','民营客户','CUSTOMER','民营','1-49',NULL,'2020-01-01')", partner, t);
        jdbc.update("INSERT INTO md_partner_contact(id,tenant_id,partner_id,contact_type,contact_name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'业务','业务')", UUID.randomUUID().toString(), t, partner);
        jdbc.update("INSERT INTO md_product(id,tenant_id,code,name,unit) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PRODUCT','件','件')", UUID.randomUUID().toString(), t);
        jdbc.update("INSERT INTO md_person(id,tenant_id,code,name,ethnicity,political_status) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PERSON','人员','汉族','群众')", person, t);
        jdbc.update("INSERT INTO md_person_detail(id,tenant_id,person_id,section_code,content) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'contacts',?)",
            UUID.randomUUID().toString(), t, person, "{\"contactType\":\"电话\",\"remarks\":\"电话\",\"contactValue\":\"不应替换\"}");
        new TransactionTemplate(transactions).executeWithoutResult(status -> BusinessValueDefaults.legacy(jdbc, tenant));
        var before = item(tenant, "partner_nature", "民营");
        jdbc.update("UPDATE tenant_dictionary_item SET name='租户自定义名称',status='DISABLED',sort_order=7 WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)", t, before.get("id"));
        var references = jdbc.queryForList("SELECT HEX(item_id) item_id,consumer,reference_key FROM tenant_dictionary_item_reference WHERE tenant_id=UUID_TO_BIN(?) ORDER BY item_id,consumer,reference_key", t);
        migrate(tenant);
        var after = item(tenant, "partner_nature", "PRIVATE");
        assertThat(after).containsEntry("id", before.get("id")).containsEntry("code", "private").containsEntry("name", "租户自定义名称")
            .containsEntry("status", "DISABLED").containsEntry("created_at", before.get("created_at")).containsEntry("created_by", before.get("created_by")).containsEntry("updated_by", null);
        assertThat(jdbc.queryForMap("SELECT nature,staff_size,name,created_by,created_at,updated_by,version FROM md_partner WHERE id=UUID_TO_BIN(?)", partner))
            .containsEntry("nature", "PRIVATE").containsEntry("staff_size", "SIZE_1_49").containsEntry("name", "民营客户").containsEntry("created_by", null).containsEntry("updated_by", null);
        assertThat(jdbc.queryForObject("SELECT YEAR(created_at) FROM md_partner WHERE id=UUID_TO_BIN(?)", Integer.class, partner)).isEqualTo(2020);
        assertThat(jdbc.queryForObject("SELECT version FROM md_partner WHERE id=UUID_TO_BIN(?)", Long.class, partner)).isGreaterThan(0L);
        assertThat(jdbc.queryForObject("SELECT contact_type FROM md_partner_contact WHERE partner_id=UUID_TO_BIN(?)", String.class, partner)).isEqualTo("BUSINESS");
        assertThat(jdbc.queryForObject("SELECT unit FROM md_product WHERE tenant_id=UUID_TO_BIN(?)", String.class, t)).isEqualTo("PIECE");
        assertThat(jdbc.queryForMap("SELECT ethnicity,political_status FROM md_person WHERE id=UUID_TO_BIN(?)", person)).containsEntry("ethnicity", "HAN").containsEntry("political_status", "GENERAL_PUBLIC");
        assertThat(jdbc.queryForMap("SELECT content->>'$.contactType' contact_type,content->>'$.remarks' remarks FROM md_person_detail WHERE person_id=UUID_TO_BIN(?)", person))
            .containsEntry("contact_type", "PHONE").containsEntry("remarks", "电话");
        assertThat(jdbc.queryForList("SELECT HEX(item_id) item_id,consumer,reference_key FROM tenant_dictionary_item_reference WHERE tenant_id=UUID_TO_BIN(?) ORDER BY item_id,consumer,reference_key", t)).isEqualTo(references);
        assertThat(item(other, "partner_nature", "民营").get("code")).as("其他租户未被迁移当前租户的调用影响").asString().startsWith("v_");
        var snapshot = jdbc.queryForList("SELECT code,item_value,status,name,version,updated_at FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(?) ORDER BY id", t);
        migrate(tenant);
        StandardDictionaryDefaults.seed(jdbc, tenant, null);
        assertThat(jdbc.queryForList("SELECT code,item_value,status,name,version,updated_at FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(?) ORDER BY id", t)).isEqualTo(snapshot);
        assertThat(snapshot).allSatisfy(row -> {
            assertThat(DictionaryValueStandard.validCode((String) row.get("code"))).isTrue();
            assertThat(DictionaryValueStandard.validValue((String) row.get("item_value"))).isTrue();
        });
    }

    @Test void targetCollisionRollsBackRatherThanMergingDifferentItems() {
        UUID tenant = oldTenant(), first = oldTenant(); String t = tenant.toString();
        jdbc.update("INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name) "
            + "SELECT UUID_TO_BIN(?),tenant_id,id,'private','CUSTOM_PRIVATE','冲突自定义项' FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?) AND code='partner_nature'", UUID.randomUUID().toString(), t);
        var before = jdbc.queryForList("SELECT code,item_value,version,updated_at FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(?) ORDER BY id", t);
        var firstBefore = item(first, "partner_nature", "民营");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            DictionaryStandardMigration.migrateTenant(jdbc, first);
            DictionaryStandardMigration.migrateTenant(jdbc, tenant);
        })).hasMessageContaining("DICTIONARY_STANDARD_CONFLICT");
        assertThat(jdbc.queryForList("SELECT code,item_value,version,updated_at FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(?) ORDER BY id", t)).isEqualTo(before);
        assertThat(item(first, "partner_nature", "民营")).isEqualTo(firstBefore);
    }

    @Test void unmappedHistoryIsRetainedButUnavailableForNewSelection() {
        UUID tenant = oldTenant(); String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO md_partner(id,tenant_id,code,name,nature) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'HISTORY','历史','历史特殊性质')", id, tenant.toString());
        new TransactionTemplate(transactions).executeWithoutResult(status -> BusinessValueDefaults.legacy(jdbc, tenant));
        String itemId = (String) item(tenant, "partner_nature", "历史特殊性质").get("id");
        migrate(tenant);
        assertThat(item(tenant, "partner_nature", "历史特殊性质")).containsEntry("id", itemId).containsEntry("status", "DISABLED");
        assertThat(item(tenant, "partner_nature", "历史特殊性质").get("description")).asString().startsWith("[历史项：");
        assertThat(jdbc.queryForObject("SELECT nature FROM md_partner WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("历史特殊性质");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_dictionary_item_reference WHERE tenant_id=UUID_TO_BIN(?) AND item_id=UUID_TO_BIN(?)", Integer.class, tenant.toString(), itemId)).isPositive();
    }

    @Test void creationEnforcesSemanticCodesAndPreservesLeadingZeros() {
        UUID tenant = oldTenant(), actor = UUID.randomUUID(), member = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'标准测试账号','unused')", actor.toString(), "standard-" + actor);
        jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))", member.toString(), tenant.toString(), actor.toString());
        bootstrap.initialize(tenant, member);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName(), new com.appnexus.platform.tenant.VerifiedTenantContext(actor, tenant, member, 1));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            var context = new DictionaryManagement.Context(actor, tenant, member);
            var dictionary = dictionaries.createDictionary(context, new DictionaryManagement.CreateDictionary("standard_test", "标准测试", null, 1));
            assertThat(dictionaries.createItem(context, dictionary.id(), new DictionaryManagement.CreateItem(null, "a", "01", "标准数字码", null, 1)).value()).isEqualTo("01");
            for (String value : List.of("中文", "lowercase", " HAS_SPACE", "HAS_SPACE ", "BAD\nVALUE", UUID.randomUUID().toString(), "V_0123456789ABCDEF0123456789ABCDEF"))
                assertThatThrownBy(() -> dictionaries.createItem(context, dictionary.id(), new DictionaryManagement.CreateItem(null, "invalid_item", value, "非法值", null, 1))).isInstanceOf(IllegalArgumentException.class);
            for (String code : List.of("Uppercase", "bad__code", "bad_", "v_0123456789abcdef0123456789abcdef"))
                assertThatThrownBy(() -> dictionaries.createItem(context, dictionary.id(), new DictionaryManagement.CreateItem(null, code, "VALID", "非法编码", null, 1))).isInstanceOf(IllegalArgumentException.class);
            String legacy = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'legacy_item','历史旧值','历史项')",
                legacy, tenant.toString(), dictionary.id().toString());
            assertThat(catalog.options(tenant, Map.of("test", "standard_test")).get("test"))
                .anyMatch(option -> option.value().equals("历史旧值") && !option.enabled());
            assertThatThrownBy(() -> dictionaries.updateItem(context, dictionary.id(), UUID.fromString(legacy),
                new DictionaryManagement.UpdateItem(null, "非法重新启用", null, DictionaryManagement.Status.ENABLED, 1, 0)))
                .hasMessageContaining("历史字典项不符合统一标准");
            dictionaries.updateItem(context, dictionary.id(), UUID.fromString(legacy),
                new DictionaryManagement.UpdateItem(null, "可维护历史名称", null, DictionaryManagement.Status.DISABLED, 1, 0));
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }
}
