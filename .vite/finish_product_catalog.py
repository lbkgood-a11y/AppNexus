from pathlib import Path
root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata');p=root/'adapter/jdbc/ProductCatalogServiceSqlMapper.java';ms=p.read_text(encoding='utf-8')
audit='BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at'
queries={'units':'SELECT BIN_TO_UUID(id) id,code,name,dimension_code,precision_scale,enabled,version,'+audit+' FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND (LOCATE(#{keyword},code)>0 OR LOCATE(#{keyword},name)>0) ORDER BY code,id LIMIT #{size} OFFSET #{offset}', 'unit':'SELECT BIN_TO_UUID(id) id,code,name,dimension_code,precision_scale,enabled,version,'+audit+' FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})','configuration':'SELECT BIN_TO_UUID(id) id,revision_no,display_path_snapshot,service_unit_required,'+audit+' FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND category_id=UUID_TO_BIN(#{category}) AND id=UUID_TO_BIN(#{id})','tree':'SELECT BIN_TO_UUID(id) id,code,name,BIN_TO_UUID(parent_id) parent,enabled,BIN_TO_UUID(current_config_id) config,version,'+audit+' FROM md_product_category WHERE tenant_id=UUID_TO_BIN(#{tenant})','slots':'SELECT purpose,BIN_TO_UUID(template_version_id) version_id FROM md_product_category_template WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND config_id=UUID_TO_BIN(#{id})'}
params={'units':['tenant','keyword','size','offset'],'unit':['tenant','id'],'configuration':['tenant','category','id'],'tree':['tenant'],'slots':['tenant','id']};methods=[]
for name,sql in queries.items():methods.append(' /** 固定目录查询；分类版本和单位引用保持原业务准入及租户边界。 */\n @Select("'+sql+'") List<Map<String,Object>> '+name+'('+','.join('@Param("'+v+'") '+('int' if v in ['size','offset'] else 'String')+' '+v for v in params[name])+');')
methods.append(''' /** @param tenant 可信租户 @param keyword 与列表一致的字面过滤 @return 同租户匹配总数 */
 @Select("SELECT COUNT(*) FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND (LOCATE(#{keyword},code)>0 OR LOCATE(#{keyword},name)>0)") long unitCount(@Param("tenant") String tenant,@Param("keyword") String keyword);''')
p.write_text(ms.rstrip()[:-1]+'\n'+'\n'.join(methods)+'\n}\n',encoding='utf-8')
p=root/'product/ProductCatalogService.java';s=p.read_text(encoding='utf-8')
start=s.index('long total=jdbc.queryForObject(');end=s.index('    return new UnitPage',start);s=s[:start]+'''long total=statements.unitCount(tenant(c),keyword.trim());
    var rows=statements.units(tenant(c),keyword.trim(),size,(page-1)*size).stream().map(ProductCatalogService::unit).toList();
'''+s[end:]
start=s.index('var rows=jdbc.query(',s.index('private Configuration configRow'));end=s.index('    if(rows.isEmpty())',start);s=s[:start]+'''var rows=statements.configuration(tenant(c),categoryId,configId).stream().map(row->new Configuration((String)row.get("id"),categoryId,((Number)row.get("revision_no")).longValue(),(String)row.get("display_path_snapshot"),Boolean.TRUE.equals(row.get("service_unit_required")),slots(c,configId),audit(row))).toList();
'''+s[end:]
start=s.index('var rows=jdbc.query(',s.index('private Map<String,Category> readTree'));end=s.index('    var all=',start);s=s[:start]+'''var rows=statements.tree(tenant(c)).stream().map(row->new Category((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),(String)row.get("parent"),0,Boolean.TRUE.equals(row.get("enabled")),(String)row.get("config"),((Number)row.get("version")).longValue(),audit(row))).toList();
'''+s[end:]
start=s.index('var rows=jdbc.query(',s.index('private Unit readUnit'));end=s.index('    if(rows.isEmpty())',start);s=s[:start]+'''var rows=statements.unit(tenant(c),id).stream().map(ProductCatalogService::unit).toList();
'''+s[end:]
start=s.index('  private static Unit unit(ResultSet');end=s.index('  private Map<String,String> slots(',start);s=s[:start]+'''  private static Unit unit(Map<String,Object> row){return new Unit((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),(String)row.get("dimension_code"),((Number)row.get("precision_scale")).intValue(),Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("version")).longValue(),audit(row));}
  private static MasterData.Audit audit(Map<String,Object> row){return new MasterData.Audit((String)row.get("creator"),utc(row.get("created_at")),(String)row.get("updater"),utc(row.get("updated_at")));}
  private static String utc(Object value){return value==null?null:((java.sql.Timestamp)value).toInstant().toString();}
'''+s[end:]
start=s.index('    jdbc.query(',s.index('private Map<String,String> slots'));end=s.index('    return slots;',start);s=s[:start]+'''    for(var row:statements.slots(tenant(c),configId))slots.put((String)row.get("purpose"),(String)row.get("version_id"));
'''+s[end:]
s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('private final JdbcTemplate jdbc;','').replace('JdbcTemplate jdbc,','').replace('this.jdbc=jdbc;','');p.write_text(s,encoding='utf-8')
