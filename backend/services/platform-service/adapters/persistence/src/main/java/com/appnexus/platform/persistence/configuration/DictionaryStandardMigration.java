package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.configuration.BusinessValueCatalog;
import com.appnexus.platform.configuration.DictionaryValueStandard;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V102 一次性转换字典及自有业务引用；仅迁移可跨业务表，运行时仍通过公开字典端口读写。
 * 调用者必须提供覆盖整个迁移的事务并停写旧版本应用；不更改主键、层级、创建审计和历史请求摘要。
 */
public final class DictionaryStandardMigration {
    private static final String LEGACY = "[历史项：未映射标准值，仅供历史记录使用]";
    private DictionaryStandardMigration() {}

    /** 内存计划不持久化敏感业务正文；legacy 表示无法安全映射，保留旧值并禁止新选。 */
    private record Change(String id, String dictionaryId, String dictionaryCode, String code,
                          String oldValue, String value, boolean legacy) {}

    /**
     * 先全量检查目标唯一性，再同事务执行数据和 JSON 字段转换；任何冲突都拒绝提交。
     * @param jdbc 当前 Flyway 或隔离测试事务连接
     * @param tenant 固定租户 UUID，所有查询及更新均显式限定此租户
     * @throws IllegalStateException 目标编码或值已被其他字典项占用；禁止自动合并或吞掉冲突
     */
    public static void migrateTenant(JdbcTemplate jdbc, UUID tenant) {
        String tenantId = tenant.toString();
        var entries = StandardDictionaryDefaults.entries();
        var changes = new ArrayList<Change>();
        var rows = jdbc.queryForList("SELECT BIN_TO_UUID(i.id) id,BIN_TO_UUID(i.dictionary_id) dictionary_id,d.code dictionary_code,i.code,i.item_value,i.status,i.description "
            + "FROM tenant_dictionary_item i JOIN tenant_dictionary d ON d.tenant_id=i.tenant_id AND d.id=i.dictionary_id "
            + "WHERE i.tenant_id=UUID_TO_BIN(?) ORDER BY d.code,i.id FOR UPDATE", tenantId);
        for (var row : rows) {
            String dictionaryCode = (String) row.get("dictionary_code"), value = (String) row.get("item_value");
            String code = (String) row.get("code");
            var entry = entries.stream().filter(e -> e.dictionaryCode().equals(dictionaryCode)
                && (e.oldValue().equals(value) || e.value().equals(value))).findFirst();
            boolean legacy = false;
            String newValue = value, newCode = code;
            if (entry.isPresent()) {
                newValue = entry.get().value();
                newCode = entry.get().code();
            } else if (DictionaryValueStandard.validValue(value)) {
                // 旧自动编码只对本身已有语义的值可机械还原，不把中文或 UUID 猜成业务含义。
                if (code.matches("v_[0-9a-f]{32}"))
                    newCode = (Character.isDigit(value.charAt(0)) ? "value_" : "") + value.toLowerCase(Locale.ROOT);
                legacy = !DictionaryValueStandard.validCode(newCode);
                if (legacy) newCode = code;
            } else legacy = true;
            if (!DictionaryValueStandard.validCode(dictionaryCode)) legacy = true;
            if (!newCode.equals(code) || !newValue.equals(value)
                || legacy && (!"DISABLED".equals(row.get("status")) || !Objects.toString(row.get("description"), "").startsWith(LEGACY)))
                changes.add(new Change((String) row.get("id"), (String) row.get("dictionary_id"), dictionaryCode, newCode, value, newValue, legacy));
        }
        for (var change : changes) {
            Integer conflicts = jdbc.queryForObject("SELECT COUNT(*) FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(?) "
                + "AND dictionary_id=UUID_TO_BIN(?) AND id<>UUID_TO_BIN(?) AND (code=? OR item_value=?)",
                Integer.class, tenantId, change.dictionaryId(), change.id(), change.code(), change.value());
            if (conflicts != null && conflicts > 0) throw new IllegalStateException("DICTIONARY_STANDARD_CONFLICT:" + change.dictionaryCode() + ":" + change.id());
        }
        for (var change : changes) {
            if (!change.oldValue().equals(change.value())) replaceReferences(jdbc, tenantId, change);
            jdbc.update("UPDATE tenant_dictionary_item SET code=?,item_value=?,status=IF(?,'DISABLED',status),"
                + "description=IF(?,CONCAT(?,LEFT(COALESCE(description,''),?)),description),version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6) "
                + "WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",
                change.code(), change.value(), change.legacy(), change.legacy(), LEGACY, 500 - LEGACY.length(), tenantId, change.id());
        }
    }

    /** 固定绑定白名单转换列值；SQL 标识符只来自构建内常量，不接收请求或字典名称。 */
    private static void replaceReferences(JdbcTemplate jdbc, String tenant, Change change) {
        replaceColumns(jdbc, tenant, change, "md_partner", BusinessValueCatalog.PARTNER, null);
        replaceColumns(jdbc, tenant, change, "md_product", BusinessValueCatalog.PRODUCT, null);
        replaceColumns(jdbc, tenant, change, "md_person", BusinessValueCatalog.PERSON, null);
        replaceColumns(jdbc, tenant, change, "md_warehouse", BusinessValueCatalog.WAREHOUSE, null);
        replaceColumns(jdbc, tenant, change, "md_partner_contact", Map.of("contactType", "partner_contact_type"), "partner_id");
        replaceColumns(jdbc, tenant, change, "md_partner_bank_account", Map.of("currency", "currency"), "partner_id");
        for (var binding : BusinessValueCatalog.PERSON.entrySet()) {
            if (!binding.getValue().equals(change.dictionaryCode())) continue;
            String path = "$." + binding.getKey();
            var details = jdbc.queryForList("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(person_id) person_id FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(?) "
                + "AND JSON_TYPE(JSON_EXTRACT(content,?))='STRING' AND BINARY JSON_UNQUOTE(JSON_EXTRACT(content,?))=BINARY ? FOR UPDATE",
                tenant, path, path, change.oldValue());
            for (var detail : details) {
                // 读取时精确匹配并锁定，再按主键更新，兼容运行时审计 SQL 解析且不扩大修改范围。
                jdbc.update("UPDATE md_person_detail SET content=JSON_SET(content,?,?),updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6) "
                    + "WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",
                    path, change.value(), tenant, detail.get("id"));
                touch(jdbc, tenant, "md_person", (String) detail.get("person_id"));
            }
        }
    }

    /** 只改完整字段值；客商多选逐 token 替换，不使用全文替换污染名称、备注或证件内容。 */
    private static void replaceColumns(JdbcTemplate jdbc, String tenant, Change change, String table,
                                       Map<String, String> bindings, String parentColumn) {
        for (var binding : bindings.entrySet()) {
            if (!binding.getValue().equals(change.dictionaryCode())) continue;
            String column = binding.getKey().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",
                Integer.class, table, column) == 0) continue;
            boolean multiple = binding.getKey().equals("partnerType");
            var records = jdbc.queryForList("SELECT BIN_TO_UUID(id) id," + column + " value"
                + (parentColumn == null ? "" : ",BIN_TO_UUID(" + parentColumn + ") parent_id")
                + " FROM " + table + " WHERE tenant_id=UUID_TO_BIN(?) AND "
                + (multiple ? "FIND_IN_SET(BINARY ?,BINARY " + column + ")>0" : "BINARY " + column + "=BINARY ?"), tenant, change.oldValue());
            for (var record : records) {
                String value = change.value();
                if (multiple) value = String.join(",", Arrays.stream(((String) record.get("value")).split(",", -1))
                    .map(v -> v.equals(change.oldValue()) ? change.value() : v).toList());
                jdbc.update("UPDATE " + table + " SET " + column + "=?,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)"
                    + (parentColumn == null ? ",version=version+1" : "") + " WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",
                    value, tenant, record.get("id"));
                if (parentColumn != null) touch(jdbc, tenant, "md_partner", (String) record.get("parent_id"));
            }
        }
    }

    /** 明细变化同步推进聚合版本，确保旧客户端不能用迁移前的草稿覆盖新标准引用。 */
    private static void touch(JdbcTemplate jdbc, String tenant, String table, String id) {
        jdbc.update("UPDATE " + table + " SET version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)", tenant, id);
    }
}
