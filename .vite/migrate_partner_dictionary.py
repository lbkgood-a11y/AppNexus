from pathlib import Path
root=Path('backend/services/platform-service/adapters/security/src/main/java/com/appnexus/platform/security')
condition=" FROM tenant_dictionary d JOIN tenant_dictionary_item i ON i.dictionary_id=d.id AND i.tenant_id=d.tenant_id WHERE d.tenant_id=UUID_TO_BIN(#{tenant}) AND i.tenant_id=UUID_TO_BIN(#{tenant}) AND d.code=#{code} AND d.status='ENABLED' AND d.deleted_at IS NULL AND i.status='ENABLED' AND i.deleted_at IS NULL"
(root/'PartnerDictionaryMapper.java').write_text('''package com.appnexus.platform.security;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 机密客户端字典固定读取；Token范围和租户绑定由原入口逐次验证，返回最小公开DTO。 */
@Mapper
@PlatformScoped(tables={"tenant_dictionary","tenant_dictionary_item"})
public interface PartnerDictionaryMapper {
 /** @param tenant 已验证Token的固定租户UUID @param code 标准字典编码 @param size 每页1–100条 @param offset 从0开始偏移 @return 启用项业务值和名称，不返回内部审计 */
 @Select("SELECT i.code,i.item_value value,i.name'''+condition+''' ORDER BY i.sort_order,i.code LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> items(@Param("tenant") String tenant,@Param("code") String code,@Param("size") int size,@Param("offset") long offset);
 /** @param tenant 与列表同一Token租户 @param code 与列表同一字典 @return 相同租户及状态谓词下总数 */
 @Select("SELECT COUNT(*)'''+condition+'''") long count(@Param("tenant") String tenant,@Param("code") String code);
}
''',encoding='utf-8')
p=root/'PartnerDictionaryController.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','private final PartnerDictionaryMapper mapper;').replace('PartnerDictionaryController(JdbcTemplate jdbc){this.jdbc=jdbc;}','PartnerDictionaryController(PartnerDictionaryMapper mapper){this.mapper=mapper;}')
start=s.index('            String condition=');end=s.index('            return ResponseEntity.ok()',start)
s=s[:start]+'''            var items=mapper.items(tenant,code,pageSize,(long)(page-1)*pageSize).stream().map(row->new Item((String)row.get("code"),(String)row.get("value"),(String)row.get("name"))).toList();
            long total=mapper.count(tenant,code);
'''+s[end:];p.write_text(s,encoding='utf-8')
