package com.appnexus.platform.persistence.configuration;

import com.appnexus.platform.configuration.AttachmentRecords.Item;
import java.time.Instant;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 附件固定 SQL。业务查询绑定本人，台账以独立权限在可信租户内查询。 */
@Mapper
@PlatformScoped(tables = "platform_attachment")
interface AttachmentRecordMapper {
    String COLUMNS = """
        BIN_TO_UUID(id) AS id, BIN_TO_UUID(tenant_id) AS tenantId, business_id AS businessId,
        file_name AS fileName, size_bytes AS sizeBytes, media_type AS mediaType,
        storage_path AS storagePath, upload_status AS status, BIN_TO_UUID(created_by) AS createdBy,
        created_at AS createdAt, BIN_TO_UUID(updated_by) AS updatedBy, updated_at AS updatedAt
        """;
    String OWNER = """
        FROM platform_attachment WHERE tenant_id = UUID_TO_BIN(#{tenant}) AND created_by = UUID_TO_BIN(#{actor})
          AND business_id = #{business} AND upload_status = 'AVAILABLE'
        """;
    /** 适配端查询参数，UUID 使用规范字符串以匹配 MySQL UUID_TO_BIN。 */
    record Query(String businessId, String fileName, String typeKey,
                 String createdBy, Instant from, Instant until) {}

    String LEDGER = """
        FROM platform_attachment WHERE tenant_id = UUID_TO_BIN(#{tenant})
          AND UUID_TO_BIN(#{actor}) IS NOT NULL
          AND (#{filter.businessId,jdbcType=VARCHAR} IS NULL OR business_id = #{filter.businessId,jdbcType=VARCHAR})
          AND (#{filter.fileName,jdbcType=VARCHAR} IS NULL OR LOCATE(#{filter.fileName,jdbcType=VARCHAR}, file_name) > 0)
          AND (#{filter.typeKey,jdbcType=VARCHAR} IS NULL OR type_key = #{filter.typeKey,jdbcType=VARCHAR})
          AND (#{filter.createdBy,jdbcType=VARCHAR} IS NULL
            OR created_by = UUID_TO_BIN(#{filter.createdBy,jdbcType=VARCHAR}))
          AND (#{filter.from,jdbcType=TIMESTAMP} IS NULL OR created_at >= #{filter.from,jdbcType=TIMESTAMP})
          AND (#{filter.until,jdbcType=TIMESTAMP} IS NULL OR created_at < #{filter.until,jdbcType=TIMESTAMP})
        """;
    String ORDER = " ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}";

    /** @return 可信租户与上传人范围内的可用分页 */
    @Select("SELECT " + COLUMNS + OWNER + ORDER)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:read", serviceKeys = {"actor", "business"})
    List<Item> list(@Param("tenant") String tenant, @Param("actor") String actor,
                    @Param("business") String business, @Param("offset") int offset, @Param("limit") int limit);

    /** @return 与本人分页共用范围的记录数 */
    @Select("SELECT COUNT(*) " + OWNER)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:read", serviceKeys = {"actor", "business"})
    long count(@Param("tenant") String tenant, @Param("actor") String actor, @Param("business") String business);

    /** @return 仅供租户台账准入调用的筛选分页 */
    @Select("SELECT " + COLUMNS + LEDGER + ORDER)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:ledger", serviceKeys = {"actor"})
    List<Item> ledger(@Param("tenant") String tenant, @Param("actor") String actor, @Param("filter") Query filter,
                      @Param("offset") int offset, @Param("limit") int limit);

    /** @return 与租户台账使用同一租户及筛选的记录数 */
    @Select("SELECT COUNT(*) " + LEDGER)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:ledger", serviceKeys = {"actor"})
    long ledgerCount(@Param("tenant") String tenant, @Param("actor") String actor, @Param("filter") Query filter);

    /** @return 与台账使用同一租户门禁的指定可用附件 */
    @Select("SELECT " + COLUMNS + """
        FROM platform_attachment WHERE tenant_id = UUID_TO_BIN(#{tenant}) AND UUID_TO_BIN(#{actor}) IS NOT NULL
          AND id = UUID_TO_BIN(#{id}) AND upload_status = 'AVAILABLE'
        """)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:ledger", serviceKeys = {"actor", "id"})
    Item findAvailableInLedger(@Param("tenant") String tenant, @Param("actor") String actor, @Param("id") String id);

    /** @return 当前主体的幂等请求记录 */
    @Select("SELECT " + COLUMNS + """
        FROM platform_attachment WHERE tenant_id = UUID_TO_BIN(#{tenant}) AND created_by = UUID_TO_BIN(#{actor})
          AND idempotency_key = UUID_TO_BIN(#{key})
        """)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:upload", serviceKeys = {"actor", "key"})
    Item findUpload(@Param("tenant") String tenant, @Param("actor") String actor, @Param("key") String key);

    /** @return 原件 SHA-256 的十六进制文本，避免 MyBatis 将单个二进制值映射为数值数组 */
    @Select("SELECT LOWER(HEX(content_sha256)) FROM platform_attachment WHERE id = UUID_TO_BIN(#{id}) AND tenant_id = UUID_TO_BIN(#{tenant}) AND created_by = UUID_TO_BIN(#{actor})")
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:upload", serviceKeys = {"actor", "id"})
    String digest(@Param("tenant") String tenant, @Param("actor") String actor, @Param("id") String id);

    /** 先登记固定对象键，依赖唯一约束排除并发的重复上传。 */
    @Insert("""
        INSERT INTO platform_attachment(id, tenant_id, business_id, file_name, size_bytes, type_key, media_type,
          content_sha256, storage_path, upload_status, write_token, idempotency_key, created_by, updated_by)
        VALUES(UUID_TO_BIN(#{id}), UUID_TO_BIN(#{tenant}), #{business}, #{name}, #{size}, #{type}, #{media},
          #{digest}, #{path}, 'WRITING', UUID_TO_BIN(#{token}), UUID_TO_BIN(#{key}), UUID_TO_BIN(#{actor}), UUID_TO_BIN(#{actor}))
        """)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:upload", serviceKeys = {"actor", "id"})
    void reserve(@Param("id") String id, @Param("tenant") String tenant, @Param("actor") String actor,
                 @Param("business") String business, @Param("name") String name, @Param("size") long size,
                 @Param("type") String type, @Param("media") String media, @Param("digest") byte[] digest,
                 @Param("path") String path, @Param("key") String key, @Param("token") String token);

    /** @return 合法前置状态下实际更新的行数 */
    @Update("""
        UPDATE platform_attachment SET upload_status = #{next}, write_token = NULL, updated_by = UUID_TO_BIN(#{actor}),
          updated_at = CURRENT_TIMESTAMP(6)
        WHERE id = UUID_TO_BIN(#{id}) AND upload_status = 'WRITING' AND write_token = UUID_TO_BIN(#{token})
          AND tenant_id = UUID_TO_BIN(#{tenant}) AND created_by = UUID_TO_BIN(#{actor})
        """)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:upload", serviceKeys = {"actor", "id"})
    int transition(@Param("tenant") String tenant, @Param("id") String id, @Param("actor") String actor,
                   @Param("token") String token, @Param("next") String next);

    /**
     * 回收失败或超过五分钟未提交的同一写入记录，同名同摘要重试继续使用原键。
     * @return 一行表示已取得写入租约
     */
    @Update("""
        UPDATE platform_attachment SET upload_status = 'WRITING', write_token = UUID_TO_BIN(#{token}),
            updated_by = UUID_TO_BIN(#{actor}), updated_at = CURRENT_TIMESTAMP(6)
        WHERE id = UUID_TO_BIN(#{id}) AND tenant_id = UUID_TO_BIN(#{tenant}) AND created_by = UUID_TO_BIN(#{actor})
          AND (upload_status = 'FAILED' OR (upload_status = 'WRITING'
              AND updated_at <= CURRENT_TIMESTAMP(6) - INTERVAL 5 MINUTE))
        """)
    @GuardedScope(tenant = "tenant", serviceOperation = "configuration.attachment:upload", serviceKeys = {"actor", "id"})
    int reclaim(@Param("tenant") String tenant, @Param("actor") String actor,
                @Param("id") String id, @Param("token") String token);
}
