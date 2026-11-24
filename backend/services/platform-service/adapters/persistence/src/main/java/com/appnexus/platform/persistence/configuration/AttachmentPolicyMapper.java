package com.appnexus.platform.persistence.configuration;

import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 平台级附件策略的固定 SQL，仅供策略用例使用。规则没有租户维度。 */
@Mapper
@PlatformScoped(tables = "platform_attachment_upload_policy")
interface AttachmentPolicyMapper {
    /** 策略行，大小单位为原始文件字节。 */
    record Row(String typeKey, boolean enabled, long maxBytes, long version, String createdBy,
               Instant createdAt, String updatedBy, Instant updatedAt) {}

    /** @return 按类型键排序的规则行 */
    @Select("""
        SELECT type_key AS typeKey, enabled, max_bytes AS maxBytes, version,
            BIN_TO_UUID(created_by) AS createdBy, created_at AS createdAt,
            BIN_TO_UUID(updated_by) AS updatedBy, updated_at AS updatedAt
        FROM platform_attachment_upload_policy WHERE UUID_TO_BIN(#{actor}) IS NOT NULL ORDER BY type_key
        """)
    @GuardedScope(platformActor = "actor", platformPermission = "configuration.attachmentpolicy:read")
    List<Row> list(@Param("actor") String actor);

    /** @return 当前上传策略，只允许可信租户的已授权上传主体读取 */
    @Select("""
        SELECT type_key AS typeKey, enabled, max_bytes AS maxBytes, version,
            BIN_TO_UUID(created_by) AS createdBy, created_at AS createdAt,
            BIN_TO_UUID(updated_by) AS updatedBy, updated_at AS updatedAt
        FROM platform_attachment_upload_policy
        WHERE enabled = TRUE AND UUID_TO_BIN(#{tenant}) IS NOT NULL AND UUID_TO_BIN(#{actor}) IS NOT NULL
        ORDER BY type_key
        """)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:upload", serviceKeys = {"actor"})
    List<Row> uploadRules(@Param("tenant") String tenant, @Param("actor") String actor);

    /**
     * 按当前版本原子更新，审计账号由用例提供、时间由数据库生成。
     * @return 更新行数，零表示版本冲突或规则不存在
     */
    @Update("""
        UPDATE platform_attachment_upload_policy
        SET enabled = #{enabled}, max_bytes = #{maxBytes}, version = version + 1,
            updated_by = UUID_TO_BIN(#{actor}), updated_at = CURRENT_TIMESTAMP(6)
        WHERE type_key = #{typeKey} AND version = #{version}
        """)
    @GuardedScope(platformActor = "actor", platformPermission = "configuration.attachmentpolicy:update", platformUpdate = true)
    int update(@Param("actor") String actor, @Param("typeKey") String typeKey,
               @Param("enabled") boolean enabled, @Param("maxBytes") long maxBytes,
               @Param("version") long version);
}
