package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import com.appnexus.platform.authorization.RowAccessControl.Predicate;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ProductStructureService固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_product_attribute_option","md_product_attribute_revision","md_product_category_config_item","md_product_category_template","md_product_category_guard","md_product_component","md_product_sku","md_product_spec_choice","md_product_spec_value","md_product_specification","md_product_template_item","md_product_template","md_product_template_version","md_product_unit","md_product_unit_conversion","md_product_v1","tenant_id"})
public interface ProductStructureMapper {
 /** 当前父商品已加锁；调用方同事务推进父版本与审计。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update("UPDATE md_product_v1 SET measurement_config=CAST(#{configuration} AS JSON) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{product})")
 int saveMeasurement(@Param("tenant") String tenant,@Param("product") String product,@Param("configuration") String configuration);

 /** 同一事务内初始化货号计量；复核父商品与货号从属关系。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update("UPDATE md_product_sku SET measurement_config=CAST(#{configuration} AS JSON) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product}) AND id=UUID_TO_BIN(#{sku}) AND measurement_config IS NULL")
 int initializeSkuMeasurement(@Param("tenant") String tenant,@Param("product") String product,
     @Param("sku") String sku,@Param("configuration") String configuration);

 /** 仅在父商品写许可和货号版本匹配时保存，修改账号来自可信会话。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update("""
     UPDATE md_product_sku SET measurement_config=CAST(#{configuration} AS JSON),
       version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
     WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product})
       AND id=UUID_TO_BIN(#{sku}) AND version=#{version}
     """)
 int updateSkuMeasurement(@Param("tenant") String tenant,@Param("product") String product,
     @Param("sku") String sku,@Param("version") long version,@Param("configuration") String configuration,
     @Param("actor") String actor);
 /** 在货号版本保护下校验并绑定同租户的可用批次模板。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update("""
     UPDATE md_product_sku SET batch_template_version_id=UUID_TO_BIN(#{templateVersion,jdbcType=VARCHAR}),
       version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6)
     WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product})
       AND id=UUID_TO_BIN(#{sku}) AND version=#{version}
       AND (#{templateVersion,jdbcType=VARCHAR} IS NULL OR EXISTS (
         SELECT 1 FROM md_product_template_version v JOIN md_product_template t
         ON t.tenant_id=v.tenant_id AND t.id=v.template_id
         WHERE v.tenant_id=UUID_TO_BIN(#{tenant}) AND v.id=UUID_TO_BIN(#{templateVersion,jdbcType=VARCHAR})
           AND v.state='PUBLISHED' AND v.purpose='BATCH' AND t.purpose='BATCH' AND t.enabled
       ))
     """)
 int updateSkuBatchTemplate(@Param("tenant") String tenant,@Param("product") String product,
     @Param("sku") String sku,@Param("version") long version,@Param("templateVersion") String templateVersion,
     @Param("actor") String actor);

 /** 通过货号绑定取得固定批次字段，SQL 同时复核可信父商品记录范围。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="tenant",actions="read")
 @Select({"<script>",
     """
     SELECT BIN_TO_UUID(i.id) itemId,r.name,i.data_type data_type,i.required_flag requiredFlag,
       BIN_TO_UUID(i.attribute_revision_id) revision,r.numeric_scale numeric_scale,
       CAST(r.validation_json AS CHAR) validation,i.allow_custom allow_custom
     FROM md_product_sku k
     JOIN md_product_template_item i ON i.tenant_id=k.tenant_id AND i.template_version_id=k.batch_template_version_id
     JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id
     WHERE k.tenant_id=UUID_TO_BIN(#{tenant}) AND k.product_id=UUID_TO_BIN(#{product}) AND k.id=UUID_TO_BIN(#{sku})
       AND k.batch_template_version_id=UUID_TO_BIN(#{template})
       AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant})
           AND id=UUID_TO_BIN(#{product}) /*appnexus-bind:access*/)
     ORDER BY i.sort_order,i.id
     ""","</script>"})
 List<Map<String,Object>> skuBatchFields(@Param("tenant") String tenant,@Param("product") String product,
     @Param("sku") String sku,@Param("template") String template,@Param("access") Predicate access);
 /** 仅按父商品许可绑定同租户的可用规格轴模板。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update("""
     UPDATE md_product_v1 SET specification_template_version_id=UUID_TO_BIN(#{template,jdbcType=VARCHAR})
     WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{product})
       AND (#{template,jdbcType=VARCHAR} IS NULL OR EXISTS (
         SELECT 1 FROM md_product_template_version v JOIN md_product_template t
           ON t.tenant_id=v.tenant_id AND t.id=v.template_id
         WHERE v.tenant_id=UUID_TO_BIN(#{tenant}) AND v.id=UUID_TO_BIN(#{template,jdbcType=VARCHAR})
           AND v.state='PUBLISHED' AND v.purpose='SPEC' AND t.purpose='SPEC' AND t.enabled
       ))
     """)
 int updateSpecificationTemplate(@Param("tenant") String tenant,@Param("product") String product,@Param("template") String template);

 /** 仅按父商品许可绑定同租户的可用普通货号模板。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update("""
     UPDATE md_product_v1 SET sku_attribute_template_version_id=UUID_TO_BIN(#{template,jdbcType=VARCHAR})
     WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{product})
       AND (#{template,jdbcType=VARCHAR} IS NULL OR EXISTS (
         SELECT 1 FROM md_product_template_version v JOIN md_product_template t
           ON t.tenant_id=v.tenant_id AND t.id=v.template_id
         WHERE v.tenant_id=UUID_TO_BIN(#{tenant}) AND v.id=UUID_TO_BIN(#{template,jdbcType=VARCHAR})
           AND v.state='PUBLISHED' AND v.purpose='SKU' AND t.purpose='SKU' AND t.enabled
       ))
     """)
 int updateSkuAttributeTemplate(@Param("tenant") String tenant,@Param("product") String product,@Param("template") String template);

 /** 货号只能使用 SPU 确认版本。不同版本的值留待重新维护。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="tenant",actions="read")
 @Select({"<script>",
     """
     SELECT k.version rowVersion,CAST(CASE WHEN k.attribute_template_version_id=p.sku_attribute_template_version_id THEN k.attribute_values END AS CHAR) attributeValues,
       BIN_TO_UUID(p.sku_attribute_template_version_id) templateVersionId
     FROM md_product_sku k JOIN md_product_v1 p ON p.tenant_id=k.tenant_id AND p.id=k.product_id
     WHERE k.tenant_id=UUID_TO_BIN(#{tenant}) AND k.product_id=UUID_TO_BIN(#{product}) AND k.id=UUID_TO_BIN(#{sku})
       AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant})
           AND id=UUID_TO_BIN(#{product}) /*appnexus-bind:access*/)
     ""","</script>"})
 List<Map<String,Object>> skuAttributeBinding(@Param("tenant") String tenant,@Param("product") String product,
     @Param("sku") String sku,@Param("access") Predicate access);

 /** 模板项连接同修订选项，父商品授权范围及模板绑定都在 SQL 中复核。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="tenant",actions="read")
 @Select({"<script>",
     """
     SELECT BIN_TO_UUID(i.id) itemId,BIN_TO_UUID(i.attribute_id) attributeId,r.name,
       i.data_type data_type,i.required_flag requiredFlag,i.allow_custom allow_custom,
       BIN_TO_UUID(i.attribute_revision_id) revision,r.numeric_scale numeric_scale,
       CAST(r.validation_json AS CHAR) validation,i.group_code groupCode,i.group_name groupName,
       BIN_TO_UUID(u.id) unitId,u.code unitCode,u.name unitName,
       BIN_TO_UUID(o.id) optionId,o.option_code optionCode,o.label optionLabel,o.enabled optionEnabled
     FROM md_product_sku k JOIN md_product_v1 p ON p.tenant_id=k.tenant_id AND p.id=k.product_id
     JOIN md_product_template_version v ON v.tenant_id=k.tenant_id
       AND v.id=p.sku_attribute_template_version_id
     JOIN md_product_template_item i ON i.tenant_id=v.tenant_id AND i.template_version_id=v.id
     JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id
     LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id
     LEFT JOIN md_product_attribute_option o ON o.tenant_id=r.tenant_id AND o.revision_id=r.id
     WHERE k.tenant_id=UUID_TO_BIN(#{tenant}) AND k.product_id=UUID_TO_BIN(#{product}) AND k.id=UUID_TO_BIN(#{sku})
       AND v.id=UUID_TO_BIN(#{template}) AND v.purpose='SKU'
       AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant})
           AND id=UUID_TO_BIN(#{product}) /*appnexus-bind:access*/)
     ORDER BY i.sort_order,i.id,o.sort_order,o.id
     ""","</script>"})
 List<Map<String,Object>> skuAttributeFields(@Param("tenant") String tenant,@Param("product") String product,
     @Param("sku") String sku,@Param("template") String template,@Param("access") Predicate access);

 /** 模板项连接同修订选项，父商品授权范围及模板绑定都在 SQL 中复核。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="tenant",actions="read")
 @Select({"<script>",
     """
     SELECT BIN_TO_UUID(i.id) itemId,BIN_TO_UUID(i.attribute_id) attributeId,r.name,
       i.data_type data_type,i.required_flag requiredFlag,i.allow_custom allow_custom,
       BIN_TO_UUID(i.attribute_revision_id) revision,r.numeric_scale numeric_scale,
       CAST(r.validation_json AS CHAR) validation,i.group_code groupCode,i.group_name groupName,
       BIN_TO_UUID(u.id) unitId,u.code unitCode,u.name unitName,
       BIN_TO_UUID(o.id) optionId,o.option_code optionCode,o.label optionLabel,o.enabled optionEnabled
     FROM md_product_v1 p
     JOIN md_product_template_version v ON v.tenant_id=p.tenant_id
       AND v.id=p.specification_template_version_id
     JOIN md_product_template_item i ON i.tenant_id=v.tenant_id AND i.template_version_id=v.id
     JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id
     LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id
     LEFT JOIN md_product_attribute_option o ON o.tenant_id=r.tenant_id AND o.revision_id=r.id
     WHERE p.tenant_id=UUID_TO_BIN(#{tenant}) AND p.id=UUID_TO_BIN(#{product})
       AND v.id=UUID_TO_BIN(#{template}) AND v.purpose='SPEC'
       AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant})
           AND id=UUID_TO_BIN(#{product}) /*appnexus-bind:access*/)
     ORDER BY i.sort_order,i.id,o.sort_order,o.id
     ""","</script>"})
 List<Map<String,Object>> specificationTemplateFields(@Param("tenant") String tenant,@Param("product") String product,
     @Param("template") String template,@Param("access") Predicate access);

 /** 父写许可、子版本和原绑定共同约束普通货号属性保存。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update("""
     UPDATE md_product_sku SET attribute_template_version_id=UUID_TO_BIN(#{template}),
       attribute_values=CAST(#{values} AS JSON),version=version+1,updated_by=UUID_TO_BIN(#{actor}),
       updated_at=CURRENT_TIMESTAMP(6)
     WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product})
       AND id=UUID_TO_BIN(#{sku}) AND version=#{version}
     """)
 int updateSkuAttributes(@Param("tenant") String tenant,@Param("product") String product,
     @Param("sku") String sku,@Param("version") long version,@Param("template") String template,
     @Param("values") String values,@Param("actor") String actor);

 /** 在创建事务内固定规格快照，复核商品与规格归属。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update("""
     UPDATE md_product_specification SET template_version_id=UUID_TO_BIN(#{template}),template_values=CAST(#{values} AS JSON)
     WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product})
       AND id=UUID_TO_BIN(#{specification}) AND template_version_id IS NULL
     """)
 int initializeSpecificationValues(@Param("tenant") String tenant,@Param("product") String product,
     @Param("specification") String specification,@Param("template") String template,@Param("values") String values);

 /** 固定方向关联要求两端同组、同租户并启用。 */
 @Select("SELECT EXISTS(SELECT 1 FROM md_product_unit_conversion x JOIN md_product_unit s ON s.tenant_id=x.tenant_id AND s.id=x.source_unit_id JOIN md_product_unit t ON t.tenant_id=x.tenant_id AND t.id=x.target_unit_id WHERE x.tenant_id=UUID_TO_BIN(#{tenant}) AND s.id=UUID_TO_BIN(#{source}) AND t.id=UUID_TO_BIN(#{target}) AND x.enabled AND s.enabled AND t.enabled AND s.dimension_code=t.dimension_code)")
 boolean hasMeasurementConversion(@Param("tenant") String tenant,@Param("source") String source,@Param("target") String target);

 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="p0",actions="read")
 @Select({"<script>","SELECT BIN_TO_UUID(s.id) id,s.name,s.description,s.mode,s.enabled,s.version,s.retired_at retiredAt,CAST(s.template_values AS CHAR) templateValues,(s.enabled AND s.retired_at IS NULL AND p.status='ENABLED') effective FROM md_product_specification s JOIN md_product_v1 p ON p.tenant_id=s.tenant_id AND p.id=s.product_id WHERE s.tenant_id=UUID_TO_BIN(#{p0}) AND s.product_id=UUID_TO_BIN(#{p1}) AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) /*appnexus-bind:access*/) ORDER BY s.created_at,s.id","</script>"})
 List<Map<String,Object>> specifications(@Param("p0") Object p0,@Param("p1") Object p1,@Param("access") Predicate access);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="p0",actions="read")
 @Select({"<script>","SELECT BIN_TO_UUID(k.id) id,BIN_TO_UUID(k.specification_id) specificationId,k.code,k.enabled,k.version,BIN_TO_UUID(k.batch_template_version_id) batchTemplateVersionId,CAST(k.measurement_config AS CHAR) measurementConfig,(k.enabled AND s.enabled AND s.retired_at IS NULL AND p.status='ENABLED') effective FROM md_product_sku k JOIN md_product_specification s ON s.tenant_id=k.tenant_id AND s.id=k.specification_id JOIN md_product_v1 p ON p.tenant_id=k.tenant_id AND p.id=k.product_id WHERE k.tenant_id=UUID_TO_BIN(#{p0}) AND k.product_id=UUID_TO_BIN(#{p1}) AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) /*appnexus-bind:access*/) ORDER BY k.code","</script>"})
 List<Map<String,Object>> skus(@Param("p0") Object p0,@Param("p1") Object p1,@Param("access") Predicate access);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="p0",actions="read")
 @Select({"<script>","SELECT BIN_TO_UUID(c.id) id,BIN_TO_UUID(c.component_product_id) componentId,p.code,p.name,CAST(c.quantity AS CHAR) quantity,c.component_version componentVersion,c.enabled,c.version,(c.enabled AND p.status='ENABLED' AND p.version=c.component_version AND EXISTS(SELECT 1 FROM md_product_v1 owner WHERE owner.tenant_id=c.tenant_id AND owner.id=c.product_id AND owner.status='ENABLED')) effective FROM md_product_component c JOIN md_product_v1 p ON p.tenant_id=c.tenant_id AND p.id=c.component_product_id WHERE c.tenant_id=UUID_TO_BIN(#{p0}) AND c.product_id=UUID_TO_BIN(#{p1}) AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) /*appnexus-bind:access*/) ORDER BY p.code","</script>"})
 List<Map<String,Object>> components(@Param("p0") Object p0,@Param("p1") Object p1,@Param("access") Predicate access);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(ci.template_item_id) item,BIN_TO_UUID(ci.attribute_revision_id) revision,ci.data_type type,r.numeric_scale scale,CAST(r.validation_json AS CHAR) rules FROM md_product_category_config_item ci JOIN md_product_attribute_revision r ON r.tenant_id=ci.tenant_id AND r.id=ci.attribute_revision_id WHERE ci.tenant_id=UUID_TO_BIN(#{p0}) AND ci.config_id=UUID_TO_BIN(#{p1}) AND ci.purpose='SPEC' ORDER BY ci.attribute_id")
 List<Map<String,Object>> standardDefinitions(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT option_code FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND enabled=TRUE")
 List<Map<String,Object>> standardOption(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="p2",actions="manage_structure")
 @Insert("INSERT INTO md_product_specification(id,tenant_id,product_id,config_id,mode,name,description,combination_canonical,combination_hash,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},#{p5},#{p6},#{p7},#{p8},UUID_TO_BIN(#{p9}),UUID_TO_BIN(#{p10}))")
 int createSpecification(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="product",actions="manage_structure")
 @Insert("INSERT INTO md_product_spec_value(id,tenant_id,specification_id,config_id,template_item_id,attribute_revision_id,value_type,integer_value,decimal_value,boolean_value,option_id,created_by,updated_by) SELECT UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),#{p6},#{p7},#{p8},#{p9},UUID_TO_BIN(#{p10}),UUID_TO_BIN(#{p11}),UUID_TO_BIN(#{p12}) FROM md_product_specification s WHERE s.tenant_id=UUID_TO_BIN(#{p1}) AND s.id=UUID_TO_BIN(#{p2}) AND s.product_id=UUID_TO_BIN(#{product})")
 int createStandardValue(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12,@Param("product") String product);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(ci.template_item_id) item,BIN_TO_UUID(ci.attribute_revision_id) revision,ci.data_type type,r.numeric_scale scale,CAST(r.validation_json AS CHAR) rules,i.required_flag requiredFlag,i.allow_custom allowCustom FROM md_product_category_config_item ci JOIN md_product_attribute_revision r ON r.tenant_id=ci.tenant_id AND r.id=ci.attribute_revision_id JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id WHERE ci.tenant_id=UUID_TO_BIN(#{p0}) AND ci.config_id=UUID_TO_BIN(#{p1}) AND ci.purpose='SPEC' ORDER BY ci.attribute_id")
 List<Map<String,Object>> customDefinitions(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND enabled=TRUE")
 Integer validOptionCount(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="product",actions="manage_structure")
 @Insert("INSERT INTO md_product_spec_value(id,tenant_id,specification_id,config_id,template_item_id,attribute_revision_id,value_type,text_value,date_value,integer_value,decimal_value,boolean_value,created_by,updated_by) SELECT UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),#{p6},#{p7},#{p8},#{p9},#{p10},#{p11},UUID_TO_BIN(#{p12}),UUID_TO_BIN(#{p13}) FROM md_product_specification s WHERE s.tenant_id=UUID_TO_BIN(#{p1}) AND s.id=UUID_TO_BIN(#{p2}) AND s.product_id=UUID_TO_BIN(#{product})")
 int createCustomValue(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12,@Param("p13") Object p13,@Param("product") String product);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="product",actions="manage_structure")
 @Insert("INSERT INTO md_product_spec_choice(id,tenant_id,value_id,attribute_revision_id,value_type,option_id,custom_text,created_by,updated_by) SELECT UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},UUID_TO_BIN(#{p5}),#{p6},UUID_TO_BIN(#{p7}),UUID_TO_BIN(#{p8}) FROM md_product_spec_value v JOIN md_product_specification s ON s.tenant_id=v.tenant_id AND s.id=v.specification_id WHERE v.tenant_id=UUID_TO_BIN(#{p1}) AND v.id=UUID_TO_BIN(#{p2}) AND s.product_id=UUID_TO_BIN(#{product})")
 int createChoice(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("product") String product);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="p0",actions="read")
 @Select({"<script>","SELECT BIN_TO_UUID(v.id) id,BIN_TO_UUID(v.template_item_id) templateItemId,r.name,v.value_type dataType,u.code unitCode,v.text_value textValue,CAST(v.date_value AS CHAR) dateValue,CAST(v.integer_value AS CHAR) integerValue,CAST(v.decimal_value AS CHAR) decimalValue,v.boolean_value booleanValue,BIN_TO_UUID(v.option_id) optionId FROM md_product_spec_value v JOIN md_product_attribute_revision r ON r.tenant_id=v.tenant_id AND r.id=v.attribute_revision_id LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id WHERE v.tenant_id=UUID_TO_BIN(#{p0}) AND v.specification_id=UUID_TO_BIN(#{p1}) AND EXISTS (SELECT 1 FROM md_product_specification sp WHERE sp.tenant_id=v.tenant_id AND sp.id=v.specification_id AND sp.product_id=UUID_TO_BIN(#{product})) AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{product}) /*appnexus-bind:access*/) ORDER BY v.id","</script>"})
 List<Map<String,Object>> specificationValues(@Param("p0") Object p0,@Param("p1") Object p1,@Param("product") String product,@Param("access") Predicate access);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="p0",actions="read")
 @Select({"<script>","SELECT BIN_TO_UUID(ch.option_id) optionId,COALESCE(o.label,ch.custom_text) label,ch.custom_text customText FROM md_product_spec_choice ch LEFT JOIN md_product_attribute_option o ON o.tenant_id=ch.tenant_id AND o.id=ch.option_id WHERE ch.tenant_id=UUID_TO_BIN(#{p0}) AND ch.value_id=UUID_TO_BIN(#{p1}) AND EXISTS (SELECT 1 FROM md_product_spec_value val JOIN md_product_specification sp ON sp.tenant_id=val.tenant_id AND sp.id=val.specification_id WHERE val.tenant_id=ch.tenant_id AND val.id=ch.value_id AND sp.product_id=UUID_TO_BIN(#{product})) AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{product}) /*appnexus-bind:access*/) ORDER BY ch.id","</script>"})
 List<Map<String,Object>> customChoices(@Param("p0") Object p0,@Param("p1") Object p1,@Param("product") String product,@Param("access") Predicate access);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) optionId,label,NULL customText FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> standardChoices(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_specification WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND enabled=TRUE AND retired_at IS NULL")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p0",recordId="p1",actions="manage_structure")
 Integer specificationCount(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句16；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="p2",actions="manage_structure")
 @Insert("INSERT INTO md_product_sku(id,tenant_id,product_id,specification_id,code,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},UUID_TO_BIN(#{p5}),UUID_TO_BIN(#{p6}))")
 int createSku(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定业务语句17；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT precision_scale FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND enabled=TRUE")
 Integer unitPrecision(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句18；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(component_product_id) FROM md_product_component WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND enabled=TRUE")
 List<String> componentChildren(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句19；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,enabled FROM md_product_component WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND component_product_id=UUID_TO_BIN(#{p2}) FOR UPDATE")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p0",recordId="p1",actions="manage_structure")
 List<Map<String,Object>> existingComponent(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句20；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p3",recordId="product",actions="manage_structure")
 @Update("UPDATE md_product_component SET quantity=#{p0},component_version=#{p1},enabled=TRUE,version=version+1,updated_by=UUID_TO_BIN(#{p2}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p3}) AND id=UUID_TO_BIN(#{p4}) AND product_id=UUID_TO_BIN(#{product})")
 int updateComponent(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("product") String product);

 /** 固定业务语句21；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="p2",actions="manage_structure")
 @Insert("INSERT INTO md_product_component(id,tenant_id,product_id,component_product_id,quantity,component_version,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}))")
 int createComponent(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句22；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_category_guard(tenant_id,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2})) ON DUPLICATE KEY UPDATE tenant_id=tenant_id")
 int ensureGraphGuard(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句23；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT tenant_id FROM md_product_category_guard WHERE tenant_id=UUID_TO_BIN(#{p0}) FOR UPDATE")
 List<Map<String,Object>> lockGraphGuard(@Param("p0") Object p0);

 /** 固定业务语句24；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="p2",actions="manage_structure")
 @Update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND version=#{p3}")
 int advanceParentVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定结构状态变更；kind经原用例白名单验证，不接收SQL表名。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="product",actions="manage_structure")
 @Update({"<script>","UPDATE <choose><when test=\"kind == 'specification'\">md_product_specification</when><when test=\"kind == 'sku'\">md_product_sku</when><otherwise>md_product_component</otherwise></choose> SET enabled=#{enabled},version=version+1,updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product}) AND id=UUID_TO_BIN(#{id}) AND version=#{version} AND enabled&lt;&gt;#{enabled}<if test=\"kind == 'specification'\"> AND retired_at IS NULL</if>","</script>"})
 int state(@Param("kind") String kind,@Param("enabled") boolean enabled,@Param("actor") String actor,@Param("tenant") String tenant,@Param("product") String product,@Param("id") String id,@Param("version") long version);
 /** 已授权主商品父行状态，不通过子记录ID扩大范围。 */
 @com.appnexus.persistence.mybatis.GuardedScope(predicate="access",resource="masterdata.product",tenant="tenant",actions={"read","manage_structure"})
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="tenant",recordId="id",actions="manage_structure",when="lock",whenValue="true")
 @Select({"<script>","SELECT product_form,specification_mode,BIN_TO_UUID(category_config_id) configId,BIN_TO_UUID(base_unit_id) unitId,BIN_TO_UUID(sku_attribute_template_version_id) skuAttributeTemplateVersionId,BIN_TO_UUID(specification_template_version_id) specificationTemplateVersionId,status,draft_archived_at archived,structure_state structure,version,CAST(measurement_config AS CHAR) measurementConfig FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id}) /*appnexus-bind:access*/","<if test='lock'> FOR UPDATE</if>","</script>"}) List<Map<String,Object>> parent(@Param("tenant") String tenant,@Param("id") String id,@Param("lock") boolean lock,@Param("access") Predicate access);
 /** 固定子表审计投影，表名仅由构建内三类choose选择。 */
 @Select({"<script>","SELECT BIN_TO_UUID(created_by) createdBy,created_at createdAt,BIN_TO_UUID(updated_by) updatedBy,updated_at updatedAt FROM <choose><when test=\"table == 'md_product_specification'\">md_product_specification</when><when test=\"table == 'md_product_sku'\">md_product_sku</when><otherwise>md_product_component</otherwise></choose> WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","</script>"}) List<Map<String,Object>> audit(@Param("table") String table,@Param("tenant") String tenant,@Param("id") Object id);
}
