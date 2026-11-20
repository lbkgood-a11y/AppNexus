from pathlib import Path
root=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/configuration');p=root/'HistoricalDictionaryMapper.java';s=p.read_text(encoding='utf-8');s=s.rstrip()[:-1]+'''
 /** 迁移内固定字段绑定提取；列映射必须属于公开业务字典契约。 */
 @SelectProvider(type=Sql.class,method="values") List<String> values(@Param("tenant") String tenant,@Param("table") String table,@Param("column") String column);
 /** 历史引用迁移不扩大角色或功能权限。 */
 @InsertProvider(type=Sql.class,method="references") int references(@Param("tenant") String tenant,@Param("table") String table,@Param("column") String column,@Param("code") String code,@Param("multiple") boolean multiple);
 /** 只选完整字段匹配或固定多值token匹配，禁止全文替换正文。 */
 @SelectProvider(type=Sql.class,method="records") List<Map<String,Object>> records(@Param("tenant") String tenant,@Param("table") String table,@Param("column") String column,@Param("parent") String parent,@Param("multiple") boolean multiple,@Param("old") String old);
 /** 已锁定记录的主键更新，创建审计不变。 */
 @UpdateProvider(type=Sql.class,method="replace") int replace(@Param("tenant") String tenant,@Param("table") String table,@Param("column") String column,@Param("parent") String parent,@Param("id") Object id,@Param("value") String value);
 /** 原聚合版本随明细修改推进，同迁移事务。 */
 @UpdateProvider(type=Sql.class,method="touch") int touch(@Param("tenant") String tenant,@Param("table") String table,@Param("id") String id);
 final class Sql {
  private Sql(){}
  private static final Map<String,Map<String,String>> BINDINGS=Map.of("md_partner",com.appnexus.platform.configuration.BusinessValueCatalog.PARTNER,"md_product",com.appnexus.platform.configuration.BusinessValueCatalog.PRODUCT,"md_person",com.appnexus.platform.configuration.BusinessValueCatalog.PERSON,"md_warehouse",com.appnexus.platform.configuration.BusinessValueCatalog.WAREHOUSE,"md_partner_contact",Map.of("contactType","partner_contact_type"),"md_partner_bank_account",Map.of("currency","currency"));
  private static String table(Map<String,Object> p){String table=(String)p.get("table");if(!BINDINGS.containsKey(table)&&!"md_person_detail".equals(table))throw new SecurityException("HISTORICAL_TABLE_UNSUPPORTED");return table;}
  private static String column(Map<String,Object> p){String table=table(p),column=(String)p.get("column");if(table.equals("md_person_detail")){boolean valid=com.appnexus.platform.configuration.BusinessValueCatalog.PERSON.keySet().stream().anyMatch(key->column.equals("content->>'$."+key+"'"));if(!valid)throw new SecurityException("HISTORICAL_FIELD_UNSUPPORTED");return column;}if(BINDINGS.get(table).keySet().stream().noneMatch(key->column.equals(key.replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(java.util.Locale.ROOT))))throw new SecurityException("HISTORICAL_FIELD_UNSUPPORTED");return column;}
  public static String values(Map<String,Object> p){String t=table(p),c=column(p);return "SELECT DISTINCT "+c+" FROM "+t+" WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND "+c+"<>''";}
  public static String references(Map<String,Object> p){String t=table(p),c=column(p);String match=Boolean.TRUE.equals(p.get("multiple"))?"FIND_IN_SET(i.item_value,IF(b."+c+"='BOTH','CUSTOMER,SUPPLIER',b."+c+"))>0":"i.item_value=b."+c;return "INSERT INTO tenant_dictionary_item_reference(tenant_id,item_id,consumer,reference_key,created_by,created_at,updated_by,updated_at) SELECT b.tenant_id,i.id,#{table},BIN_TO_UUID(b.id),NULL,CURRENT_TIMESTAMP(6),NULL,CURRENT_TIMESTAMP(6) FROM "+t+" b JOIN tenant_dictionary d ON d.tenant_id=b.tenant_id AND d.code=#{code} AND d.deleted_at IS NULL JOIN tenant_dictionary_item i ON i.tenant_id=b.tenant_id AND i.dictionary_id=d.id AND i.deleted_at IS NULL AND "+match+" WHERE b.tenant_id=UUID_TO_BIN(#{tenant}) AND NOT EXISTS(SELECT 1 FROM tenant_dictionary_item_reference r WHERE r.tenant_id=b.tenant_id AND r.item_id=i.id AND r.consumer=#{table} AND r.reference_key=BIN_TO_UUID(b.id))";}
  private static String parent(Map<String,Object> p){Object value=p.get("parent");if(value==null)return null;String expected=table(p).equals("md_partner_contact")||table(p).equals("md_partner_bank_account")?"partner_id":null;if(!java.util.Objects.equals(value,expected))throw new SecurityException("HISTORICAL_PARENT_UNSUPPORTED");return expected;}
  public static String records(Map<String,Object> p){String t=table(p),c=column(p),parent=parent(p);return "SELECT BIN_TO_UUID(id) id,"+c+" value"+(parent==null?"":",BIN_TO_UUID("+parent+") parent_id")+" FROM "+t+" WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND "+(Boolean.TRUE.equals(p.get("multiple"))?"FIND_IN_SET(BINARY #{old},BINARY "+c+")>0":"BINARY "+c+"=BINARY #{old}");}
  public static String replace(Map<String,Object> p){String t=table(p),c=column(p),parent=parent(p);return "UPDATE "+t+" SET "+c+"=#{value},updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6)"+(parent==null?",version=version+1":"")+" WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})";}
  public static String touch(Map<String,Object> p){return "UPDATE "+table(p)+" SET version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})";}
 }
}
''';p.write_text(s,encoding='utf-8')
p=root/'BusinessValueDefaults.java';s=p.read_text(encoding='utf-8');start=s.index('jdbc.queryForList("SELECT DISTINCT');end=s.index(')){',start);s=s[:start]+'mapper.values(tenant.toString(),table,column)'+s[end:]
start=s.index('  String match=');end=s.index('\n }',start);s=s[:start]+'  mapper.references(tenant.toString(),table,expression,code,multiple);'+s[end:];p.write_text(s,encoding='utf-8')
p=root/'DictionaryStandardMigration.java';s=p.read_text(encoding='utf-8');start=s.index('jdbc.queryForList("SELECT BIN_TO_UUID(id) id,"');end=s.index(';',start);s=s[:start]+'mapper.records(tenant,table,column,parentColumn,multiple,change.oldValue())'+s[end:]
start=s.index('                jdbc.update(');end=s.index(';',start);s=s[:start]+'                mapper.replace(tenant,table,column,parentColumn,record.get("id"),value)'+s[end:]
start=s.index('        jdbc.update(');end=s.index(';',start);s=s[:start]+'        mapper.touch(tenant,table,id)'+s[end:];p.write_text(s,encoding='utf-8')
