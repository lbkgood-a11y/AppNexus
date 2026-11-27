package com.appnexus.platform.persistence.configuration;

import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 租户附件规则固定 SQL，旧平台表仅作为冻结的初始规则目录。 */
@Mapper
@PlatformScoped(tables = {"platform_attachment_upload_policy", "tenant_attachment_upload_policy"})
interface AttachmentPolicyMapper {
    /** 策略行，大小单位为原始文件字节。 */
    record Row(String typeKey, boolean enabled, long maxBytes, long version, String createdBy,
               Instant createdAt, String updatedBy, Instant updatedAt) {}

    String RULES = """
        SELECT defaults.type_key AS typeKey, COALESCE(rule.enabled, defaults.enabled) AS enabled,
            COALESCE(rule.max_bytes, defaults.max_bytes) AS maxBytes, COALESCE(rule.version, 0) AS version,
            BIN_TO_UUID(rule.created_by) AS createdBy, rule.created_at AS createdAt,
            BIN_TO_UUID(rule.updated_by) AS updatedBy, rule.updated_at AS updatedAt
        FROM platform_attachment_upload_policy defaults
        LEFT JOIN tenant_attachment_upload_policy rule
          ON rule.tenant_id = UUID_TO_BIN(#{tenant}) AND rule.type_key = defaults.type_key
        WHERE UUID_TO_BIN(#{actor}) IS NOT NULL
        """;

    /** @return 当前租户按类型键排序的有效及停用规则 */
    @Select(RULES + " ORDER BY defaults.type_key")
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.tenantattachmentpolicy:read", serviceKeys = {"actor"})
    List<Row> list(@Param("tenant") String tenant, @Param("actor") String actor);

    /** @return 当前上传策略，只允许可信租户的已授权上传主体读取 */
    @Select(RULES + " AND COALESCE(rule.enabled, defaults.enabled) = TRUE ORDER BY defaults.type_key")
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:upload", serviceKeys = {"actor"})
    List<Row> uploadRules(@Param("tenant") String tenant, @Param("actor") String actor);

    /** 首次修改建立版本 1，唯一约束将同一租户和格式的并发创建转为冲突。 */
    @Insert("""
        INSERT INTO tenant_attachment_upload_policy(id, tenant_id, type_key, enabled, max_bytes, version, created_by, updated_by)
        VALUES(UUID_TO_BIN(#{id}), UUID_TO_BIN(#{tenant}), #{typeKey}, #{enabled}, #{maxBytes}, 1,
               UUID_TO_BIN(#{actor}), UUID_TO_BIN(#{actor}))
        """)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.tenantattachmentpolicy:update", serviceKeys = {"actor", "typeKey"})
    int create(@Param("tenant") String tenant, @Param("actor") String actor, @Param("id") String id,
               @Param("typeKey") String typeKey, @Param("enabled") boolean enabled, @Param("maxBytes") long maxBytes);

    /**
     * 按当前版本原子更新，审计账号由用例提供、时间由数据库生成。
     * @return 更新行数，零表示版本冲突或规则不存在
     */
    @Update("""
        UPDATE tenant_attachment_upload_policy
        SET enabled = #{enabled}, max_bytes = #{maxBytes}, version = version + 1,
            updated_by = UUID_TO_BIN(#{actor}), updated_at = CURRENT_TIMESTAMP(6)
        WHERE tenant_id = UUID_TO_BIN(#{tenant}) AND type_key = #{typeKey} AND version = #{version}
        """)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.tenantattachmentpolicy:update", serviceKeys = {"actor", "typeKey"})
    int update(@Param("tenant") String tenant, @Param("actor") String actor, @Param("typeKey") String typeKey,
               @Param("enabled") boolean enabled, @Param("maxBytes") long maxBytes,
               @Param("version") long version);
}
