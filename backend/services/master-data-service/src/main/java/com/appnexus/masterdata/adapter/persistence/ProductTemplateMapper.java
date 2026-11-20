package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ProductTemplateService固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_product_attribute","md_product_attribute_group","md_product_attribute_revision","md_product_template","md_product_template_item","md_product_template_version"})
public interface ProductTemplateMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,version_no versionNo,state,description,purpose,BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(#{p0}) AND template_id=UUID_TO_BIN(#{p1}) ORDER BY version_no DESC")
 List<Map<String,Object>> versions(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(i.id) id,BIN_TO_UUID(i.attribute_id) attributeId,BIN_TO_UUID(i.attribute_revision_id) revisionId,r.name,i.data_type dataType,i.required_flag requiredFlag,i.searchable,i.allow_custom allowCustom,i.group_code groupCode,i.group_name groupName FROM md_product_template_item i JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id WHERE i.tenant_id=UUID_TO_BIN(#{p0}) AND i.template_version_id=UUID_TO_BIN(#{p1}) ORDER BY i.sort_order,i.id")
 List<Map<String,Object>> items(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_template(id,tenant_id,code,name,purpose,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},#{p4},UUID_TO_BIN(#{p5}),UUID_TO_BIN(#{p6}))")
 int createTemplate(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT purpose FROM md_product_template WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 String purpose(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COALESCE(MAX(version_no),0)+1 FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(#{p0}) AND template_id=UUID_TO_BIN(#{p1})")
 Long nextVersion(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_template_version(id,tenant_id,template_id,version_no,purpose,description,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}))")
 int createVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(template_id) id,state FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> versionOwnerAndState(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT state FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 String lockVersionState(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Delete("DELETE FROM md_product_template_item WHERE tenant_id=UUID_TO_BIN(#{p0}) AND template_version_id=UUID_TO_BIN(#{p1})")
 int deleteVersionItems(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_template_version SET description=#{p0},updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3})")
 int updateDescription(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT data_type,state FROM md_product_attribute_revision WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND attribute_id=UUID_TO_BIN(#{p2})")
 List<Map<String,Object>> attributeRevision(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT g.code,g.name FROM md_product_attribute a JOIN md_product_attribute_group g ON g.tenant_id=a.tenant_id AND g.id=a.group_id WHERE a.tenant_id=UUID_TO_BIN(#{p0}) AND a.id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> attributeGroup(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_template_item(id,tenant_id,template_version_id,attribute_id,attribute_revision_id,data_type,group_code,group_name,required_flag,searchable,allow_custom,sort_order,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),#{p5},#{p6},#{p7},#{p8},#{p9},#{p10},#{p11},UUID_TO_BIN(#{p12}),UUID_TO_BIN(#{p13}))")
 int createItem(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12,@Param("p13") Object p13);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(template_id) id FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<String> versionOwners(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_template_item WHERE tenant_id=UUID_TO_BIN(#{p0}) AND template_version_id=UUID_TO_BIN(#{p1})")
 Integer itemCount(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句16；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_template_item i JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id JOIN md_product_attribute a ON a.tenant_id=i.tenant_id AND a.id=i.attribute_id WHERE i.tenant_id=UUID_TO_BIN(#{p0}) AND i.template_version_id=UUID_TO_BIN(#{p1}) AND (r.state<>'PUBLISHED' OR a.enabled=FALSE)")
 Integer unavailableItemCount(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句17；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_template_version SET state='PUBLISHED',published_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int publishVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句18；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_template SET name=#{p0},version=version+1,updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND code=#{p4} AND purpose=#{p5} AND version=#{p6}")
 int updateTemplate(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定业务语句19；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_template SET enabled=#{p0},version=version+1,updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND version=#{p4}")
 int setTemplateStatus(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句20；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(template_id) id FROM md_product_template_version WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<String> retirementOwners(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句21；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_template_version SET state='RETIRED',updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND state='PUBLISHED'")
 int retireVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句22；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT version,enabled FROM md_product_template WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> lockTemplate(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句23；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_template SET version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int advanceTemplateVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** @param tenant 可信租户 @return 当前租户模板摘要，原功能准入不变 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,purpose,enabled,version FROM md_product_template WHERE tenant_id=UUID_TO_BIN(#{tenant}) ORDER BY code,id") List<Map<String,Object>> summaries(@Param("tenant") String tenant);
 /** @param tenant 可信租户 @param version 模板版本UUID @return 同租户父模板状态 */
 @Select("SELECT tv.state,t.purpose FROM md_product_template_version tv JOIN md_product_template t ON t.tenant_id=tv.tenant_id AND t.id=tv.template_id WHERE tv.tenant_id=UUID_TO_BIN(#{tenant}) AND tv.id=UUID_TO_BIN(#{version})") List<Map<String,Object>> versionState(@Param("tenant") String tenant,@Param("version") String version);
}
