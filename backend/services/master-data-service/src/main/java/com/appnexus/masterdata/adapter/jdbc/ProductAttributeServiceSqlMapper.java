package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ProductAttributeService固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_product_attribute","md_product_attribute_group","md_product_attribute_option","md_product_attribute_revision","md_product_unit"})
public interface ProductAttributeServiceSqlMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(attribute_id) attributeId,revision_no revision,name,data_type dataType,BIN_TO_UUID(unit_id) unitId,numeric_scale numericScale,CAST(validation_json AS CHAR) validationJson,state,BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(#{p0}) AND attribute_id=UUID_TO_BIN(#{p1}) ORDER BY revision_no DESC")
 List<Map<String,Object>> statement1(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,option_code code,label,sort_order sortOrder FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1}) ORDER BY sort_order,id")
 List<Map<String,Object>> statement2(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_attribute_group(id,tenant_id,code,name,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}))")
 int statement3(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_attribute(id,tenant_id,code,group_id,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}))")
 int statement4(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND enabled=TRUE")
 Integer statement5(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COALESCE(MAX(revision_no),0)+1 FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(#{p0}) AND attribute_id=UUID_TO_BIN(#{p1})")
 Long statement6(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_attribute_revision(id,tenant_id,attribute_id,revision_no,name,data_type,unit_id,numeric_scale,validation_json,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},#{p5},UUID_TO_BIN(#{p6}),#{p7},#{p8},UUID_TO_BIN(#{p9}),UUID_TO_BIN(#{p10}))")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_attribute_option(id,tenant_id,revision_id,option_code,label,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}))")
 int statement8(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1})")
 Integer statement9(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_attribute_revision SET state='PUBLISHED',published_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND state='DRAFT'")
 int statement10(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_attribute_option(id,tenant_id,revision_id,option_code,label,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}))")
 int statement11(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_attribute_group SET name=#{p0},version=version+1,updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND code=#{p4} AND version=#{p5}")
 int statement12(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_attribute SET group_id=UUID_TO_BIN(#{p0}),version=version+1,updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND code=#{p4} AND version=#{p5}")
 int statement13(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(attribute_id) id FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<String> statement14(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_attribute_revision SET state='RETIRED',updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND state='PUBLISHED'")
 int statement15(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句16；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND enabled=TRUE")
 Integer statement16(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句17；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_attribute_revision SET name=#{p0},data_type=#{p1},unit_id=UUID_TO_BIN(#{p2}),numeric_scale=#{p3},validation_json=#{p4},updated_by=UUID_TO_BIN(#{p5}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p6}) AND id=UUID_TO_BIN(#{p7})")
 int statement17(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句18；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Delete("DELETE FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1})")
 int statement18(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句19；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_attribute_option(id,tenant_id,revision_id,option_code,label,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}))")
 int statement19(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句20；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT version,enabled FROM md_product_attribute WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> statement20(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句21；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_attribute SET version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int statement21(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句22；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(attribute_id) attribute_id,data_type,state FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> statement22(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句23；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT state FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 String statement23(@Param("p0") Object p0,@Param("p1") Object p1);

 /** @param tenant 可信租户 @return 属性组摘要，原独立功能准入保持 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,enabled,version FROM md_product_attribute_group WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY code,id") List<Map<String,Object>> groups(@Param("tenant") String tenant);
 /** @param tenant 可信租户 @return 属性摘要；空组UUID用空字符串保持旧协议 */
 @Select("SELECT BIN_TO_UUID(id) id,code,COALESCE(BIN_TO_UUID(group_id),'') groupId,enabled,version FROM md_product_attribute WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY code,id") List<Map<String,Object>> attributes(@Param("tenant") String tenant);
 /** @param group 构建内对象选择，不接收表名 @param tenant 可信租户 @param id 已校验UUID @param enabled 目标状态 @param actor 实际账号 @param version 原CAS版本 @return 更新行数 */
 @Update({"<script>","UPDATE <choose><when test='group'>md_product_attribute_group</when><otherwise>md_product_attribute</otherwise></choose> SET enabled=#{enabled},version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) AND version=#{version}","</script>"})
 int status(@Param("group") boolean group,@Param("tenant") String tenant,@Param("id") String id,@Param("enabled") boolean enabled,@Param("actor") String actor,@Param("version") long version);
}
