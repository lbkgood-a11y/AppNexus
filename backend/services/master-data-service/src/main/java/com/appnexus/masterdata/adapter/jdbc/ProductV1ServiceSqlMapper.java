package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ProductV1Service固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_product","md_product_approval","md_product_attribute_option","md_product_attribute_revision","md_product_category","md_product_category_config","md_product_category_config_item","md_product_category_template","md_product_component","md_product_specification","md_product_structure_request","md_product_template","md_product_template_item","md_product_template_version","md_product_unit","md_product_v1","md_product_v1_attribute_choice","md_product_v1_attribute_value"})
public interface ProductV1ServiceSqlMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_v1(id,tenant_id,code,name,short_name,category_id,category_config_id,product_form,base_unit_id,description,brand,origin_text,barcode,organization_id,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},#{p4},UUID_TO_BIN(#{p5}),UUID_TO_BIN(#{p6}),#{p7},UUID_TO_BIN(#{p8}),#{p9},#{p10},#{p11},#{p12},UUID_TO_BIN(#{p13}),UUID_TO_BIN(#{p14}),UUID_TO_BIN(#{p15}))")
 int statement1(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12,@Param("p13") Object p13,@Param("p14") Object p14,@Param("p15") Object p15);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_v1 SET name=#{p0},short_name=#{p1},description=#{p2},brand=#{p3},origin_text=#{p4},barcode=COALESCE(#{p5},barcode),organization_id=COALESCE(UUID_TO_BIN(#{p6}),organization_id) WHERE tenant_id=UUID_TO_BIN(#{p7}) AND id=UUID_TO_BIN(#{p8})")
 int statement2(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT state,product_version FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) ORDER BY created_at DESC LIMIT 1")
 List<Map<String,Object>> statement3(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_v1 SET status=#{p0} WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int statement4(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_v1 SET category_id=UUID_TO_BIN(#{p0}),category_config_id=UUID_TO_BIN(#{p1}),product_form=#{p2},base_unit_id=UUID_TO_BIN(#{p3}),structure_state='INCOMPLETE',structure_revision=structure_revision+1,legacy_specification_disposition=CASE WHEN legacy_enabled_snapshot IS NOT NULL THEN COALESCE(#{p4},legacy_specification_disposition) ELSE legacy_specification_disposition END WHERE tenant_id=UUID_TO_BIN(#{p5}) AND id=UUID_TO_BIN(#{p6})")
 int statement5(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_specification SET retired_at=CURRENT_TIMESTAMP(6),enabled=FALSE,version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND product_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND retired_at IS NULL")
 int statement6(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_v1 SET specification_mode=#{p0} WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_component SET enabled=FALSE,version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND product_id=UUID_TO_BIN(#{p2}) AND enabled=TRUE")
 int statement8(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_structure_request(id,tenant_id,account_id,product_id,request_key,payload_digest,result_product_version,result_structure_revision,change_id,result_json,expires_at,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UNHEX(#{p5}),#{p6},#{p7},UUID_TO_BIN(#{p8}),#{p9},TIMESTAMPADD(DAY,7,CURRENT_TIMESTAMP(6)),UUID_TO_BIN(#{p10}),UUID_TO_BIN(#{p11}))")
 int statement9(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT service_unit_required FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 Boolean statement10(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(i.id) FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id WHERE ci.tenant_id=UUID_TO_BIN(#{p0}) AND ci.config_id=UUID_TO_BIN(#{p1}) AND ci.purpose<>'SPEC' AND i.required_flag=TRUE")
 List<String> statement11(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT enabled,version FROM md_product_category WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> statement12(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(v.id) id,v.state,t.enabled,t.version FROM md_product_category_template b JOIN md_product_template_version v ON v.tenant_id=b.tenant_id AND v.id=b.template_version_id JOIN md_product_template t ON t.tenant_id=v.tenant_id AND t.id=v.template_id WHERE b.tenant_id=UUID_TO_BIN(#{p0}) AND b.config_id=UUID_TO_BIN(#{p1}) ORDER BY v.id")
 List<Map<String,Object>> statement13(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT enabled,version FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> statement14(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Delete("DELETE ch FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id WHERE ch.tenant_id=UUID_TO_BIN(#{p0}) AND v.product_id=UUID_TO_BIN(#{p1})")
 int statement15(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句16；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Delete("DELETE FROM md_product_v1_attribute_value WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1})")
 int statement16(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句17；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_v1 SET legacy_specification_disposition=#{p0} WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int statement17(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句18；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id FROM md_product WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> statement18(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句19；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 Integer statement19(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句20；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_v1(id,tenant_id,code,name,barcode,status,structure_state,version,legacy_enabled_snapshot,legacy_specification_original,legacy_specification_disposition,legacy_unit_original,legacy_source_version,legacy_name_original,legacy_updated_by_original,legacy_updated_at_original,created_by,created_at,updated_by,updated_at) SELECT id,tenant_id,code,name,barcode,CASE WHEN enabled THEN 'ENABLED' ELSE 'DISABLED' END,'LEGACY_PENDING',version,enabled,specification,'UNREVIEWED',unit,version,name,updated_by,updated_at,created_by,created_at,updated_by,updated_at FROM md_product WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 int statement20(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句21；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(o.id) id,o.code,CASE WHEN p.id IS NULL THEN 'MISSING' WHEN p.legacy_source_version<>o.version OR NOT(BINARY p.code<=>BINARY o.code) OR NOT(BINARY p.legacy_name_original<=>BINARY o.name) OR NOT(BINARY p.barcode<=>BINARY o.barcode) OR NOT(BINARY p.legacy_specification_original<=>BINARY o.specification) OR NOT(BINARY p.legacy_unit_original<=>BINARY o.unit) OR NOT(p.legacy_enabled_snapshot<=>o.enabled) OR NOT(p.created_by<=>o.created_by) OR NOT(p.created_at<=>o.created_at) OR NOT(p.legacy_updated_by_original<=>o.updated_by) OR NOT(p.legacy_updated_at_original<=>o.updated_at) THEN 'SOURCE_CHANGED' ELSE 'MATCHED' END result FROM md_product o LEFT JOIN md_product_v1 p ON p.tenant_id=o.tenant_id AND p.id=o.id WHERE o.tenant_id=UUID_TO_BIN(#{p0}) ORDER BY o.code,o.id LIMIT #{p1} OFFSET #{p2}")
 List<Map<String,Object>> statement21(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句22；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product WHERE tenant_id=UUID_TO_BIN(#{p0})")
 Long statement22(@Param("p0") Object p0);

 /** 固定业务语句23；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND state='SUBMITTED'")
 Integer statement23(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句24；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(ci.attribute_revision_id) revision,r.data_type,r.numeric_scale,CAST(r.validation_json AS CHAR) validation,i.allow_custom FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id JOIN md_product_attribute_revision r ON r.tenant_id=ci.tenant_id AND r.id=ci.attribute_revision_id WHERE ci.tenant_id=UUID_TO_BIN(#{p0}) AND ci.config_id=UUID_TO_BIN(#{p1}) AND ci.purpose<>'SPEC' AND ci.template_item_id=UUID_TO_BIN(#{p2})")
 List<Map<String,Object>> statement24(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句25；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND enabled=TRUE")
 Integer statement25(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** @param tenant 可信租户UUID @param id 已锁定授权商品 @param archived 归档或恢复，由领域规则验证 @return 修改行数 */
 @Update({"<script>","UPDATE md_product_v1 SET draft_archived_at=<choose><when test='archived'>CURRENT_TIMESTAMP(6)</when><otherwise>NULL</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","</script>"})
 int archive(@Param("tenant") String tenant,@Param("id") String id,@Param("archived") boolean archived);
 /** @param tenant 可信租户 @param actor 可信实际账号 @param id 已授权商品 @param key 幂等请求UUID @param lock 是否在原事务锁定回执 @return 仅同主体商品操作的历史摘要 */
 @Select({"<script>","SELECT HEX(payload_digest) digest,result_product_version version,result_structure_revision revision,BIN_TO_UUID(change_id) changeId,expires_at,expires_at>CURRENT_TIMESTAMP(6) valid FROM md_product_structure_request WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND account_id=UUID_TO_BIN(#{actor}) AND product_id=UUID_TO_BIN(#{id}) AND operation_code='migrateStructure' AND request_key=UUID_TO_BIN(#{key})","<if test='lock'> FOR UPDATE</if>","</script>"})
 List<Map<String,Object>> replay(@Param("tenant") String tenant,@Param("actor") String actor,@Param("id") String id,@Param("key") String key,@Param("lock") boolean lock);
}
