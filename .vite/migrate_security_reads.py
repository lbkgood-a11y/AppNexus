from pathlib import Path
root=Path('backend/services/platform-service/adapters/security/src/main/java/com/appnexus/platform/security')
(root/'ClientSecretMapper.java').write_text('''package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** 客户端旧密钥窗口固定查询；仅验证当前已注册哈希匹配的同一客户端，不记录密钥或返回外部。 */
@Mapper
@PlatformScoped(tables={"integration_client","oauth2_registered_client","platform_tenant"})
public interface ClientSecretMapper {
 /** @param encoded 框架已加载的当前注册Argon2id哈希，禁止日志记录 @return 同客户端600秒窗口内的旧哈希；不匹配或停用时空集合 */
 @Select("SELECT c.previous_secret_hash FROM integration_client c JOIN oauth2_registered_client r ON r.id=c.id JOIN platform_tenant t ON t.id=c.tenant_id WHERE r.client_secret=#{encoded} AND c.enabled=TRUE AND t.enabled=TRUE AND c.previous_secret_until>CURRENT_TIMESTAMP(6) AND c.previous_secret_hash IS NOT NULL")
 List<String> previous(@Param("encoded") String encoded);
}
''',encoding='utf-8')
p=root/'RotatingClientPasswordEncoder.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('JdbcTemplate jdbc','ClientSecretMapper mapper').replace('this.jdbc=jdbc','this.mapper=mapper').replace('private final JdbcTemplate jdbc','private final ClientSecretMapper mapper')
start=s.index('jdbc.queryForList(');end=s.index(';',start);s=s[:start]+'mapper.previous(encoded)'+s[end:];p.write_text(s,encoding='utf-8')
union="SELECT 'platform' source,CAST(id AS CHAR) event_id,BIN_TO_UUID(actor_account_id) actor,action,BIN_TO_UUID(target_id) target,NULL trace_id,NULL status,occurred_at FROM platform_administration_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'dictionary',CAST(id AS CHAR),BIN_TO_UUID(actor_account_id),action,BIN_TO_UUID(dictionary_id),NULL,NULL,occurred_at FROM tenant_dictionary_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'parameter',CAST(id AS CHAR),BIN_TO_UUID(actor_id),'PARAMETER_UPDATED',parameter_key,NULL,NULL,occurred_at FROM tenant_parameter_audit WHERE tenant_id=UUID_TO_BIN(#{tenant}) UNION ALL SELECT 'integration',CAST(id AS CHAR),client_id,operation,NULL,trace_id,status,occurred_at FROM integration_call_audit WHERE tenant_id=UUID_TO_BIN(#{tenant})"
(root/'UnifiedAuditMapper.java').write_text('''package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
/** 统一审计固定只读映射；平台和租户准入仍由用例校验，不提供动态表或SQL。 */
@Mapper
@PlatformScoped(tables={"platform_administration_audit","tenant_dictionary_audit","tenant_parameter_audit","integration_call_audit"})
public interface UnifiedAuditMapper {
 /** @param size 每页1–100条 @param offset 从0开始偏移 @return 仅平台操作摘要，不含业务正文及凭据 */
 @Select("SELECT CAST(id AS CHAR) event_id,BIN_TO_UUID(actor_account_id) actor,action,BIN_TO_UUID(target_id) target,occurred_at FROM platform_administration_audit WHERE tenant_id IS NULL ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> platform(@Param("size") int size,@Param("offset") long offset);
 /** @return 与平台列表相同条件的总数 */
 @Select("SELECT COUNT(*) FROM platform_administration_audit WHERE tenant_id IS NULL") long platformCount();
 /** @param tenant 已授权可信租户UUID @param size 每页1–100条 @param offset 从0开始偏移 @return 同租户四类审计摘要，每个UNION分支独立绑定租户 */
 @Select("SELECT * FROM ('''+union+''') audit_rows ORDER BY occurred_at DESC,source,event_id DESC LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> tenant(@Param("tenant") String tenant,@Param("size") int size,@Param("offset") long offset);
 /** @param tenant 与列表相同的可信租户 @return 相同UNION条件下的总数 */
 @Select("SELECT COUNT(*) FROM ('''+union+''') audit_rows") long tenantCount(@Param("tenant") String tenant);
}
''',encoding='utf-8')
p=root/'UnifiedAudit.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('private final JdbcTemplate jdbc;','private final UnifiedAuditMapper mapper;').replace('UnifiedAudit(JdbcTemplate jdbc,','UnifiedAudit(UnifiedAuditMapper mapper,').replace('{this.jdbc=jdbc;','{this.mapper=mapper;')
start=s.index('        var items=jdbc.query(');end=s.index('\n    }',start);s=s[:start]+'''        var items=mapper.platform(size,(long)(page-1)*size).stream().map(row->entry(row,"platform")).toList();
        return new Page(items,mapper.platformCount(),page,size);'''+s[end:]
start=s.index('        validate(page,size);String id=tenant.toString();');end=s.index('\n    }',start);s=s[:start]+'''        validate(page,size);String id=tenant.toString();
        return new Page(mapper.tenant(id,size,(long)(page-1)*size).stream().map(row->entry(row,(String)row.get("source"))).toList(),mapper.tenantCount(id),page,size);'''+s[end:]
pos=s.index('    private void validate(');s=s[:pos]+'''    /** 固定查询别名转UTC摘要；NULL历史值保持未知，不生成敏感正文。 */
    private Entry entry(Map<String,Object> row,String source){Object at=row.get("occurred_at");Instant occurred=at instanceof java.sql.Timestamp time?time.toInstant():at instanceof java.time.LocalDateTime time?time.toInstant(java.time.ZoneOffset.UTC):(Instant)at;return new Entry(source,Objects.toString(row.get("event_id"),null),(String)row.get("actor"),(String)row.get("action"),(String)row.get("target"),(String)row.get("trace_id"),row.get("status")==null?null:((Number)row.get("status")).intValue(),occurred);}
'''+s[pos:];p.write_text(s,encoding='utf-8')
p=Path('backend/services/platform-service/adapters/security/pom.xml');s=p.read_text(encoding='utf-8').replace('<dependencies>','''<dependencies>
    <!-- 安全适配器固定Mapper仅共享中立范围元数据，不依赖平台存储实现。 -->
    <dependency><groupId>com.appnexus</groupId><artifactId>mybatis-scope</artifactId><version>${project.version}</version></dependency>
    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${mybatis-spring-boot.version}</version></dependency>''',1);p.write_text(s,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/main/java/com/appnexus/platform/PlatformApplication.java');s=p.read_text(encoding='utf-8').replace('"com.appnexus.printing.adapter.jdbc"}','"com.appnexus.printing.adapter.jdbc","com.appnexus.platform.security"}');p.write_text(s,encoding='utf-8')
