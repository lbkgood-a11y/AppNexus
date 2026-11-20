from pathlib import Path
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata');business=root/'product';adapters=root/'adapter/jdbc'
def append(name,text):
 p=adapters/(name+'SqlMapper.java');s=p.read_text(encoding='utf-8');p.write_text(s.rstrip()[:-1]+text+'\n}\n',encoding='utf-8')
append('ProductImageService','''
 /** @param tenant 可信租户UUID @param product 已授权父商品UUID @param id 图片UUID @return 固定PNG内容，缺失不泄漏其他租户记录 */
 @Select("SELECT media_type mediaType,image_data bytes FROM md_product_image WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product}) AND id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL")
 List<Map<String,Object>> content(@Param("tenant") String tenant,@Param("product") String product,@Param("id") String id);
 /** @param tenant 可信租户 @param id 已授权父商品 @param lock 写用例持有行锁 @return 固定状态及版本，不提供任意列 */
 @Select({"<script>","SELECT status,draft_archived_at archived,structure_state structure,version FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","<if test='lock'> FOR UPDATE</if>","</script>"})
 List<Map<String,Object>> parent(@Param("tenant") String tenant,@Param("id") String id,@Param("lock") boolean lock);''')
p=business/'ProductImageService.java';s=p.read_text(encoding='utf-8');start=s.index('jdbc.query("SELECT media_type');end=s.index(';if(rows.size()',start);s=s[:start]+'statements.content(c.tenantId().toString(),product,id).stream().map(row->new Content((String)row.get("mediaType"),(byte[])row.get("bytes"))).toList()'+s[end:]
start=s.index('jdbc.queryForList("SELECT status');end=s.index(';if(rows.size()',start);s=s[:start]+'statements.parent(c.tenantId().toString(),id,write)'+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','');p.write_text(s,encoding='utf-8')
p=business/'ProductApprovalService.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','');p.write_text(s,encoding='utf-8')
append('ProductTemplateService','''
 /** @param tenant 可信租户 @return 当前租户模板摘要，原功能准入不变 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,purpose,enabled,version FROM md_product_template WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY code,id") List<Map<String,Object>> summaries(@Param("tenant") String tenant);
 /** @param tenant 可信租户 @param version 模板版本UUID @return 同租户父模板状态 */
 @Select("SELECT tv.state,t.purpose FROM md_product_template_version tv JOIN md_product_template t ON t.tenant_id=tv.tenant_id AND t.id=tv.template_id WHERE tv.tenant_id=UUID_TO_BIN(#{tenant}) AND tv.id=UUID_TO_BIN(#{version})") List<Map<String,Object>> versionState(@Param("tenant") String tenant,@Param("version") String version);''')
p=business/'ProductTemplateService.java';s=p.read_text(encoding='utf-8');start=s.index('jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,purpose');end=s.index(';}',start);s=s[:start]+'statements.summaries(tenant(c))'+s[end:]
start=s.index('jdbc.query("SELECT tv.state');end=s.index(';if(rows.isEmpty()',start);s=s[:start]+'statements.versionState(tenant(c),versionId)'+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','');p.write_text(s,encoding='utf-8')
