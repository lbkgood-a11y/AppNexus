from pathlib import Path
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata');business=root/'product';adapters=root/'adapter/jdbc'
def add(name,sql):
 p=adapters/(name+'SqlMapper.java');s=p.read_text(encoding='utf-8');p.write_text(s.rstrip()[:-1]+sql+'\n}\n',encoding='utf-8')
add('ProductAttributeService','''
 /** @param tenant 可信租户 @return 属性组摘要，原独立功能准入保持 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,enabled,version FROM md_product_attribute_group WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY code,id") List<Map<String,Object>> groups(@Param("tenant") String tenant);
 /** @param tenant 可信租户 @return 属性摘要；空组UUID用空字符串保持旧协议 */
 @Select("SELECT BIN_TO_UUID(id) id,code,COALESCE(BIN_TO_UUID(group_id),'') groupId,enabled,version FROM md_product_attribute WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY code,id") List<Map<String,Object>> attributes(@Param("tenant") String tenant);
 /** @param group 构建内对象选择，不接收表名 @param tenant 可信租户 @param id 已校验UUID @param enabled 目标状态 @param actor 实际账号 @param version 原CAS版本 @return 更新行数 */
 @Update({"<script>","UPDATE <choose><when test='group'>md_product_attribute_group</when><otherwise>md_product_attribute</otherwise></choose> SET enabled=#{enabled},version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) AND version=#{version}","</script>"})
 int status(@Param("group") boolean group,@Param("tenant") String tenant,@Param("id") String id,@Param("enabled") boolean enabled,@Param("actor") String actor,@Param("version") long version);''')
p=business/'ProductAttributeService.java';s=p.read_text(encoding='utf-8')
for method in ['groups','attributes']:
 start=s.index('return jdbc.query(',s.index('public Object '+method));end=s.index(';}',start);s=s[:start]+'return statements.'+method+'(tenant(c))'+s[end:]
start=s.index('jdbc.update("UPDATE "+table');end=s.index('!=1)',start);s=s[:start]+'statements.status(table.equals("md_product_attribute_group"),tenant(c),id,input.enabled(),actor(c),input.version())'+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','');p.write_text(s,encoding='utf-8')
add('ProductStructureService','''
 /** 固定结构状态变更；kind经原用例白名单验证，不接收SQL表名。 */
 @Update({"<script>","UPDATE <choose><when test=\"kind == 'specification'\">md_product_specification</when><when test=\"kind == 'sku'\">md_product_sku</when><otherwise>md_product_component</otherwise></choose> SET enabled=#{enabled},version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product}) AND id=UUID_TO_BIN(#{id}) AND version=#{version} AND enabled&lt;&gt;#{enabled}<if test=\"kind == 'specification'\"> AND retired_at IS NULL</if>","</script>"})
 int state(@Param("kind") String kind,@Param("enabled") boolean enabled,@Param("actor") String actor,@Param("tenant") String tenant,@Param("product") String product,@Param("id") String id,@Param("version") long version);
 /** 已授权主商品父行状态，不通过子记录ID扩大范围。 */
 @Select({"<script>","SELECT product_form,specification_mode,BIN_TO_UUID(category_config_id) configId,BIN_TO_UUID(base_unit_id) unitId,status,draft_archived_at archived,structure_state structure,version FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","<if test='lock'> FOR UPDATE</if>","</script>"}) List<Map<String,Object>> parent(@Param("tenant") String tenant,@Param("id") String id,@Param("lock") boolean lock);
 /** 固定子表审计投影，表名仅由构建内三类choose选择。 */
 @Select({"<script>","SELECT BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM <choose><when test=\"table == 'md_product_specification'\">md_product_specification</when><when test=\"table == 'md_product_sku'\">md_product_sku</when><otherwise>md_product_component</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","</script>"}) List<Map<String,Object>> audit(@Param("table") String table,@Param("tenant") String tenant,@Param("id") Object id);'''.replace('test="','test=\\"').replace("'\">","'\\\">"))
p=business/'ProductStructureService.java';s=p.read_text(encoding='utf-8');start=s.index('jdbc.update("UPDATE "+table');end=s.index(';if(n!=1)',start);s=s[:start]+'statements.state(kind,i.enabled(),actor(c),tenant(c),product,id,i.rowVersion())'+s[end:]
start=s.index('var audit=jdbc.query(');end=s.index(';if(!audit.isEmpty())',start);s=s[:start]+'''var audit=statements.audit(table,tenant(c),row.get("id")).stream().map(raw->{var result=new LinkedHashMap<String,Object>(raw);for(String field:List.of("createdAt","updatedAt")){Object value=result.get(field);if(value instanceof java.sql.Timestamp time)result.put(field,time.toInstant().toString());}return result;}).toList()'''+s[end:]
start=s.index('jdbc.queryForList("SELECT product_form');end=s.index(';if(rows.size()',start);s=s[:start]+'statements.parent(tenant(c),id,lock)'+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','');p.write_text(s,encoding='utf-8')
