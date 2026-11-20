package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ProductStructureService固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_product_attribute_option","md_product_attribute_revision","md_product_category_config_item","md_product_category_guard","md_product_component","md_product_sku","md_product_spec_choice","md_product_spec_value","md_product_specification","md_product_template_item","md_product_unit","md_product_v1","tenant_id"})
public interface ProductStructureServiceSqlMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(s.id) id,s.name,s.description,s.mode,s.enabled,s.version,s.retired_at retiredAt,(s.enabled AND s.retired_at IS NULL AND p.status='ENABLED') effective FROM md_product_specification s JOIN md_product_v1 p ON p.tenant_id=s.tenant_id AND p.id=s.product_id WHERE s.tenant_id=UUID_TO_BIN(#{p0}) AND s.product_id=UUID_TO_BIN(#{p1}) ORDER BY s.created_at,s.id")
 List<Map<String,Object>> statement1(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(k.id) id,BIN_TO_UUID(k.specification_id) specificationId,k.code,k.enabled,k.version,(k.enabled AND s.enabled AND s.retired_at IS NULL AND p.status='ENABLED') effective FROM md_product_sku k JOIN md_product_specification s ON s.tenant_id=k.tenant_id AND s.id=k.specification_id JOIN md_product_v1 p ON p.tenant_id=k.tenant_id AND p.id=k.product_id WHERE k.tenant_id=UUID_TO_BIN(#{p0}) AND k.product_id=UUID_TO_BIN(#{p1}) ORDER BY k.code")
 List<Map<String,Object>> statement2(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(c.id) id,BIN_TO_UUID(c.component_product_id) componentId,p.code,p.name,CAST(c.quantity AS CHAR) quantity,c.component_version componentVersion,c.enabled,c.version,(c.enabled AND p.status='ENABLED' AND p.version=c.component_version AND EXISTS(SELECT 1 FROM md_product_v1 owner WHERE owner.tenant_id=c.tenant_id AND owner.id=c.product_id AND owner.status='ENABLED')) effective FROM md_product_component c JOIN md_product_v1 p ON p.tenant_id=c.tenant_id AND p.id=c.component_product_id WHERE c.tenant_id=UUID_TO_BIN(#{p0}) AND c.product_id=UUID_TO_BIN(#{p1}) ORDER BY p.code")
 List<Map<String,Object>> statement3(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(ci.template_item_id) item,BIN_TO_UUID(ci.attribute_revision_id) revision,ci.data_type type,r.numeric_scale scale,CAST(r.validation_json AS CHAR) rules FROM md_product_category_config_item ci JOIN md_product_attribute_revision r ON r.tenant_id=ci.tenant_id AND r.id=ci.attribute_revision_id WHERE ci.tenant_id=UUID_TO_BIN(#{p0}) AND ci.config_id=UUID_TO_BIN(#{p1}) AND ci.purpose='SPEC' ORDER BY ci.attribute_id")
 List<Map<String,Object>> statement4(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT option_code FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND enabled=TRUE")
 List<Map<String,Object>> statement5(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_specification(id,tenant_id,product_id,config_id,mode,name,description,combination_canonical,combination_hash,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},#{p5},#{p6},#{p7},#{p8},UUID_TO_BIN(#{p9}),UUID_TO_BIN(#{p10}))")
 int statement6(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_spec_value(id,tenant_id,specification_id,config_id,template_item_id,attribute_revision_id,value_type,integer_value,decimal_value,boolean_value,option_id,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),#{p6},#{p7},#{p8},#{p9},UUID_TO_BIN(#{p10}),UUID_TO_BIN(#{p11}),UUID_TO_BIN(#{p12}))")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(ci.template_item_id) item,BIN_TO_UUID(ci.attribute_revision_id) revision,ci.data_type type,r.numeric_scale scale,CAST(r.validation_json AS CHAR) rules,i.required_flag requiredFlag,i.allow_custom allowCustom FROM md_product_category_config_item ci JOIN md_product_attribute_revision r ON r.tenant_id=ci.tenant_id AND r.id=ci.attribute_revision_id JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id WHERE ci.tenant_id=UUID_TO_BIN(#{p0}) AND ci.config_id=UUID_TO_BIN(#{p1}) AND ci.purpose='SPEC' ORDER BY ci.attribute_id")
 List<Map<String,Object>> statement8(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND enabled=TRUE")
 Integer statement9(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_spec_value(id,tenant_id,specification_id,config_id,template_item_id,attribute_revision_id,value_type,text_value,date_value,integer_value,decimal_value,boolean_value,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),#{p6},#{p7},#{p8},#{p9},#{p10},#{p11},UUID_TO_BIN(#{p12}),UUID_TO_BIN(#{p13}))")
 int statement10(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12,@Param("p13") Object p13);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_spec_choice(id,tenant_id,value_id,attribute_revision_id,value_type,option_id,custom_text,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},UUID_TO_BIN(#{p5}),#{p6},UUID_TO_BIN(#{p7}),UUID_TO_BIN(#{p8}))")
 int statement11(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(v.id) id,BIN_TO_UUID(v.template_item_id) templateItemId,r.name,v.value_type dataType,u.code unitCode,v.text_value textValue,CAST(v.date_value AS CHAR) dateValue,CAST(v.integer_value AS CHAR) integerValue,CAST(v.decimal_value AS CHAR) decimalValue,v.boolean_value booleanValue,BIN_TO_UUID(v.option_id) optionId FROM md_product_spec_value v JOIN md_product_attribute_revision r ON r.tenant_id=v.tenant_id AND r.id=v.attribute_revision_id LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id WHERE v.tenant_id=UUID_TO_BIN(#{p0}) AND v.specification_id=UUID_TO_BIN(#{p1}) ORDER BY v.id")
 List<Map<String,Object>> statement12(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(ch.option_id) optionId,COALESCE(o.label,ch.custom_text) label,ch.custom_text customText FROM md_product_spec_choice ch LEFT JOIN md_product_attribute_option o ON o.tenant_id=ch.tenant_id AND o.id=ch.option_id WHERE ch.tenant_id=UUID_TO_BIN(#{p0}) AND ch.value_id=UUID_TO_BIN(#{p1}) ORDER BY ch.id")
 List<Map<String,Object>> statement13(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) optionId,label,NULL customText FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> statement14(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_specification WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND enabled=TRUE AND retired_at IS NULL")
 Integer statement15(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句16；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_sku(id,tenant_id,product_id,specification_id,code,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},UUID_TO_BIN(#{p5}),UUID_TO_BIN(#{p6}))")
 int statement16(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定业务语句17；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT precision_scale FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND enabled=TRUE")
 Integer statement17(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句18；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(component_product_id) FROM md_product_component WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND enabled=TRUE")
 List<String> statement18(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句19；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,enabled FROM md_product_component WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND component_product_id=UUID_TO_BIN(#{p2}) FOR UPDATE")
 List<Map<String,Object>> statement19(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句20；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_component SET quantity=#{p0},component_version=#{p1},enabled=TRUE,version=version+1,updated_by=UUID_TO_BIN(#{p2}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p3}) AND id=UUID_TO_BIN(#{p4})")
 int statement20(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句21；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_component(id,tenant_id,product_id,component_product_id,quantity,component_version,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}))")
 int statement21(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句22；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_category_guard(tenant_id,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2})) ON DUPLICATE KEY UPDATE tenant_id=tenant_id")
 int statement22(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句23；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT tenant_id FROM md_product_category_guard WHERE tenant_id=UUID_TO_BIN(#{p0}) FOR UPDATE")
 List<Map<String,Object>> statement23(@Param("p0") Object p0);

 /** 固定业务语句24；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND version=#{p3}")
 int statement24(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定结构状态变更；kind经原用例白名单验证，不接收SQL表名。 */
 @Update({"<script>","UPDATE <choose><when test=\"kind == 'specification'\">md_product_specification</when><when test=\"kind == 'sku'\">md_product_sku</when><otherwise>md_product_component</otherwise></choose> SET enabled=#{enabled},version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product}) AND id=UUID_TO_BIN(#{id}) AND version=#{version} AND enabled&lt;&gt;#{enabled}<if test=\"kind == 'specification'\"> AND retired_at IS NULL</if>","</script>"})
 int state(@Param("kind") String kind,@Param("enabled") boolean enabled,@Param("actor") String actor,@Param("tenant") String tenant,@Param("product") String product,@Param("id") String id,@Param("version") long version);
 /** 已授权主商品父行状态，不通过子记录ID扩大范围。 */
 @Select({"<script>","SELECT product_form,specification_mode,BIN_TO_UUID(category_config_id) configId,BIN_TO_UUID(base_unit_id) unitId,status,draft_archived_at archived,structure_state structure,version FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","<if test='lock'> FOR UPDATE</if>","</script>"}) List<Map<String,Object>> parent(@Param("tenant") String tenant,@Param("id") String id,@Param("lock") boolean lock);
 /** 固定子表审计投影，表名仅由构建内三类choose选择。 */
 @Select({"<script>","SELECT BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM <choose><when test=\"table == 'md_product_specification'\">md_product_specification</when><when test=\"table == 'md_product_sku'\">md_product_sku</when><otherwise>md_product_component</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","</script>"}) List<Map<String,Object>> audit(@Param("table") String table,@Param("tenant") String tenant,@Param("id") Object id);
}
