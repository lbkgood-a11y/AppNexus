from pathlib import Path
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata')
p=root/'adapter/jdbc/ProductV1ServiceSqlMapper.java';s=p.read_text(encoding='utf-8');s=s.rstrip()[:-1]+'''
 /** @param tenant 可信租户UUID @param id 已锁定授权商品 @param archived 归档或恢复，由领域规则验证 @return 修改行数 */
 @Update({"<script>","UPDATE md_product_v1 SET draft_archived_at=<choose><when test='archived'>CURRENT_TIMESTAMP(6)</when><otherwise>NULL</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","</script>"})
 int archive(@Param("tenant") String tenant,@Param("id") String id,@Param("archived") boolean archived);
 /** @param tenant 可信租户 @param actor 可信实际账号 @param id 已授权商品 @param key 幂等请求UUID @param lock 是否在原事务锁定回执 @return 仅同主体商品操作的历史摘要 */
 @Select({"<script>","SELECT HEX(payload_digest) digest,result_product_version version,result_structure_revision revision,BIN_TO_UUID(change_id) changeId,expires_at,expires_at>CURRENT_TIMESTAMP(6) valid FROM md_product_structure_request WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND account_id=UUID_TO_BIN(#{actor}) AND product_id=UUID_TO_BIN(#{id}) AND operation_code='migrateStructure' AND request_key=UUID_TO_BIN(#{key})","<if test='lock'> FOR UPDATE</if>","</script>"})
 List<Map<String,Object>> replay(@Param("tenant") String tenant,@Param("actor") String actor,@Param("id") String id,@Param("key") String key,@Param("lock") boolean lock);
}\n''';p.write_text(s,encoding='utf-8')
p=root/'product/ProductV1Service.java';s=p.read_text(encoding='utf-8');start=s.index('jdbc.update("UPDATE md_product_v1 SET draft_archived_at=');end=s.index(';',start);s=s[:start]+'statements.archive(tenant(context),id,archived)'+s[end:]
start=s.index('jdbc.queryForList("SELECT HEX(payload_digest)');end=s.index(';',start);s=s[:start]+'statements.replay(tenant(c),actor(c),id,key,lock)'+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','');p.write_text(s,encoding='utf-8')
