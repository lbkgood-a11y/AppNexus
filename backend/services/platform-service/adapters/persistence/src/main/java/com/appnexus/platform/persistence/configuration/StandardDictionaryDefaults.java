package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.configuration.DictionaryValueStandard;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** V102 起的预置唯一来源；旧 V89/V91 初始化器只供历史迁移使用，禁止改写其资源或执行语义。 */
public final class StandardDictionaryDefaults {
    private StandardDictionaryDefaults() {}

    /**
     * 预置及迁移映射；业务读取不支持旧值别名。
     * @param dictionaryCode 目标字典语义编码，租户内唯一
     * @param dictionaryName 新租户默认中文名称，不覆盖租户已维护名称
     * @param oldValue 仅供一次性离线迁移匹配的旧稳定值
     * @param code 新字典项小写语义编码，同字典内唯一
     * @param value 新稳定业务值，标准码或大写语义码
     * @param name 新租户默认显示名称，不覆盖现有项名称
     */
    public record Entry(String dictionaryCode, String dictionaryName, String oldValue,
                        String code, String value, String name) {}

    /** @return V102 不可变 UTF-8 资源；修改清单必须新增版本，不能改变已部署迁移校验和 */
    public static byte[] resource() {
        try (var input = StandardDictionaryDefaults.class.getResourceAsStream("/dictionary-value-standard-v102.tsv")) {
            return Objects.requireNonNull(input).readAllBytes();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("DICTIONARY_STANDARD_UNAVAILABLE", error);
        }
    }

    /** @return 已校验且无重复的预置项；禁止将资源错误静默转换为部分初始化 */
    public static List<Entry> entries() {
        var result = new ArrayList<Entry>();
        var codes = new HashSet<String>();
        var values = new HashSet<String>();
        for (String line : new String(resource(), StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            var fields = line.stripTrailing().split("\t", -1);
            if (fields.length != 6) throw new IllegalStateException("INVALID_DICTIONARY_STANDARD");
            var entry = new Entry(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]);
            if (!DictionaryValueStandard.validCode(entry.dictionaryCode()) || !DictionaryValueStandard.validCode(entry.code())
                || !DictionaryValueStandard.validValue(entry.value())
                || !codes.add(entry.dictionaryCode() + ":" + entry.code())
                || !values.add(entry.dictionaryCode() + ":" + entry.value()))
                throw new IllegalStateException("INVALID_DICTIONARY_STANDARD");
            result.add(entry);
        }
        return List.copyOf(result);
    }

    /**
     * 新租户开通同事务补预置；永久占用的编码和值（含软删除）都不复用，不恢复停用项。
     * @param jdbc 宿主开通事务的数据库访问器
     * @param tenant 可信租户 UUID，不接受业务请求指定其他租户
     * @param actor 真实开通账号；系统执行为空，不伪造账号
     */
    public static void seed(JdbcTemplate jdbc, UUID tenant, UUID actor) {
        String tenantId = tenant.toString(), actorId = actor == null ? null : actor.toString();
        int order = 0;
        for (var entry : entries()) {
            order++;
            String dictionary = stableId(tenantId + ":" + entry.dictionaryCode());
            jdbc.update("INSERT INTO tenant_dictionary(id,tenant_id,code,name,description,status,sort_order,created_by,updated_by) "
                + "SELECT UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,'ENABLED',?,UUID_TO_BIN(?),UUID_TO_BIN(?) "
                + "WHERE NOT EXISTS(SELECT 1 FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?) AND code=?)",
                dictionary, tenantId, entry.dictionaryCode(), entry.dictionaryName(), "统一标准预置；编码和业务值创建后不可修改。",
                order, actorId, actorId, tenantId, entry.dictionaryCode());
            var dictionaries = jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(?) AND code=? AND deleted_at IS NULL",
                String.class, tenantId, entry.dictionaryCode());
            if (dictionaries.isEmpty()) continue;
            dictionary = dictionaries.getFirst();
            String item = stableId(tenantId + ":" + entry.dictionaryCode() + ":" + entry.value());
            jdbc.update("INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name,status,sort_order,created_by,updated_by) "
                + "SELECT UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,'ENABLED',?,UUID_TO_BIN(?),UUID_TO_BIN(?) "
                + "WHERE NOT EXISTS(SELECT 1 FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(?) AND dictionary_id=UUID_TO_BIN(?) AND (code=? OR item_value=?))",
                item, tenantId, dictionary, entry.code(), entry.value(), entry.name(), order, actorId, actorId,
                tenantId, dictionary, entry.code(), entry.value());
        }
    }

    private static String stableId(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
