package com.appnexus.platform.persistence.configuration;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 仅历史Flyway隔离连接使用，不注册为运行期Mapper，不提供业务入口。 */
public interface HistoricalDictionaryMapper {
 @Select("SELECT BIN_TO_UUID(id) FROM platform_tenant ORDER BY id") List<String> tenants();
 /** 历史数据转换固定语句0；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Select("SELECT section_code,content FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(#{p0})")
 List<Map<String,Object>> statement0(@Param("p0") Object p0);

 /** 历史数据转换固定语句1；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Select("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=#{p0} AND column_name=#{p1}")
 Integer statement1(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 历史数据转换固定语句2；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Insert("INSERT INTO tenant_dictionary(id,tenant_id,code,name,description,status,sort_order,created_by,created_at,updated_by,updated_at) SELECT UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},#{p4},'ENABLED',#{p5},UUID_TO_BIN(#{p6}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{p7}),CURRENT_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(#{p8}) AND code=#{p9})")
 int statement2(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9);

 /** 历史数据转换固定语句3；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Select("SELECT BIN_TO_UUID(id) id FROM tenant_dictionary WHERE tenant_id=UUID_TO_BIN(#{p0}) AND code=#{p1} AND deleted_at IS NULL")
 List<String> statement3(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 历史数据转换固定语句4；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Insert("INSERT INTO tenant_dictionary_item(id,tenant_id,dictionary_id,code,item_value,name,status,sort_order,created_by,created_at,updated_by,updated_at) SELECT UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},#{p5},'ENABLED',#{p6},UUID_TO_BIN(#{p7}),CURRENT_TIMESTAMP(6),UUID_TO_BIN(#{p8}),CURRENT_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(#{p9}) AND dictionary_id=UUID_TO_BIN(#{p10}) AND item_value=#{p11})")
 int statement4(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11);

 /** 历史数据转换固定语句5；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Select("SELECT BIN_TO_UUID(i.id) id,BIN_TO_UUID(i.dictionary_id) dictionary_id,d.code dictionary_code,i.code,i.item_value,i.status,i.description FROM tenant_dictionary_item i JOIN tenant_dictionary d ON d.tenant_id=i.tenant_id AND d.id=i.dictionary_id WHERE i.tenant_id=UUID_TO_BIN(#{p0}) ORDER BY d.code,i.id FOR UPDATE")
 List<Map<String,Object>> statement5(@Param("p0") Object p0);

 /** 历史数据转换固定语句6；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Select("SELECT COUNT(*) FROM tenant_dictionary_item WHERE tenant_id=UUID_TO_BIN(#{p0}) AND dictionary_id=UUID_TO_BIN(#{p1}) AND id<>UUID_TO_BIN(#{p2}) AND (code=#{p3} OR item_value=#{p4})")
 Integer statement6(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 历史数据转换固定语句7；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Update("UPDATE tenant_dictionary_item SET code=#{p0},item_value=#{p1},status=IF(#{p2},'DISABLED',status),description=IF(#{p3},CONCAT(#{p4},LEFT(COALESCE(description,''),#{p5})),description),version=version+1,updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p6}) AND id=UUID_TO_BIN(#{p7})")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 历史数据转换固定语句8；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(person_id) person_id FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(#{p0}) AND JSON_TYPE(JSON_EXTRACT(content,#{p1}))='STRING' AND BINARY JSON_UNQUOTE(JSON_EXTRACT(content,#{p2}))=BINARY #{p3} FOR UPDATE")
 List<Map<String,Object>> statement8(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 历史数据转换固定语句9；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Update("UPDATE md_person_detail SET content=JSON_SET(content,#{p0},#{p1}),updated_by=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3})")
 int statement9(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 历史数据转换固定语句10；原资源、校验和及整批事务保持，不供业务HTTP调用。 */
 @Select("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=#{p0} AND column_name=#{p1}")
 Integer statement10(@Param("p0") Object p0,@Param("p1") Object p1);

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
