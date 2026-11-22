package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.platform.authorization.RowAccessControl.Predicate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/**
 * 商品快照固定查询映射；不开放任意列名、表名、SQL或客户端事实输入。
 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_approval","md_product_attribute_option","md_product_attribute_revision","md_product_category_config","md_product_category_template","md_product_component","md_product_image","md_product_sku","md_product_spec_choice","md_product_spec_value","md_product_specification","md_product_unit","md_product_v1","md_product_v1_attribute_choice","md_product_v1_attribute_value"},statements={"approvalPending","measurement","specifications","specificationValues","specificationChoices","skus","components","images","values","choices","categoryConfiguration","baseUnit","templates"})
public interface ProductSnapshotMapper {
 /** 已授权父商品的计量快照，JSON 原文用于恢复当时的配置。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="product",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT CAST(measurement_config AS CHAR) FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{product})","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> ${rangeSql}</if>","</script>"})
 String measurement(@Param("__verifiedTenant") byte[] tenant,@Param("product") String product,@Param("access") Predicate access);

 /**
 * approvalPending 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 原存储契约定义的结果；并发或引用冲突由调用用例复核
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT COUNT(*)&gt;0 FROM md_product_approval WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{id}) AND state='SUBMITTED'","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>","</script>"})
 Boolean approvalPending(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * specifications 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT BIN_TO_UUID(id) id,mode,name,enabled,retired_at retiredAt,combination_canonical combination FROM md_product_specification WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{id})","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>"," ORDER BY id","</script>"})
 List<Map<String,Object>> specifications(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * specificationValues 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT BIN_TO_UUID(v.specification_id) specificationId,BIN_TO_UUID(v.template_item_id) templateItemId,r.name attributeName,v.value_type dataType,u.code unitCode,v.text_value textValue,CAST(v.date_value AS CHAR) dateValue,CAST(v.integer_value AS CHAR) integerValue,CAST(v.decimal_value AS CHAR) decimalValue,v.boolean_value booleanValue,o.label optionLabel FROM md_product_spec_value v JOIN md_product_specification s ON s.tenant_id=v.tenant_id AND s.id=v.specification_id JOIN md_product_attribute_revision r ON r.tenant_id=v.tenant_id AND r.id=v.attribute_revision_id LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id LEFT JOIN md_product_attribute_option o ON o.tenant_id=v.tenant_id AND o.id=v.option_id WHERE s.tenant_id=#{__verifiedTenant} AND s.product_id=UUID_TO_BIN(#{id})","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>"," ORDER BY v.id","</script>"})
 List<Map<String,Object>> specificationValues(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * specificationChoices 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT BIN_TO_UUID(v.specification_id) specificationId,BIN_TO_UUID(v.template_item_id) templateItemId,COALESCE(o.label,ch.custom_text) label,ch.custom_text customText FROM md_product_spec_choice ch JOIN md_product_spec_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id JOIN md_product_specification s ON s.tenant_id=v.tenant_id AND s.id=v.specification_id LEFT JOIN md_product_attribute_option o ON o.tenant_id=ch.tenant_id AND o.id=ch.option_id WHERE s.tenant_id=#{__verifiedTenant} AND s.product_id=UUID_TO_BIN(#{id})","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>"," ORDER BY ch.id","</script>"})
 List<Map<String,Object>> specificationChoices(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * skus 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(specification_id) specificationId,code,enabled,version FROM md_product_sku WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{id})","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>"," ORDER BY id","</script>"})
 List<Map<String,Object>> skus(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * components 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT BIN_TO_UUID(c.component_product_id) componentId,p.code componentCode,p.name componentName,CAST(c.quantity AS CHAR) quantity,c.component_version componentVersion,c.enabled FROM md_product_component c JOIN md_product_v1 p ON p.tenant_id=c.tenant_id AND p.id=c.component_product_id WHERE c.tenant_id=#{__verifiedTenant} AND c.product_id=UUID_TO_BIN(#{id})","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>"," ORDER BY c.id","</script>"})
 List<Map<String,Object>> components(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * images 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT BIN_TO_UUID(id) id,HEX(content_hash) contentHash,width_px width,height_px height FROM md_product_image WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>"," ORDER BY id","</script>"})
 List<Map<String,Object>> images(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * values 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT BIN_TO_UUID(v.template_item_id) templateItemId,BIN_TO_UUID(v.attribute_revision_id) revisionId,r.name attributeName,r.data_type dataType,v.text_value textValue,CAST(v.integer_value AS CHAR) integerValue,CAST(v.decimal_value AS CHAR) decimalValue,v.boolean_value booleanValue,CAST(v.date_value AS CHAR) dateValue FROM md_product_v1_attribute_value v JOIN md_product_attribute_revision r ON r.tenant_id=v.tenant_id AND r.id=v.attribute_revision_id WHERE v.tenant_id=#{__verifiedTenant} AND v.product_id=UUID_TO_BIN(#{id})","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>"," ORDER BY v.id","</script>"})
 List<Map<String,Object>> values(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * choices 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="id",readFallback="access",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","stage_legacy","masterdata.product.approval:submit"})
 @Select({"<script>","SELECT BIN_TO_UUID(v.template_item_id) templateItemId,BIN_TO_UUID(o.id) optionId,o.option_code optionCode,COALESCE(o.label,ch.custom_text) label,ch.custom_text customText FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id LEFT JOIN md_product_attribute_option o ON o.tenant_id=ch.tenant_id AND o.id=ch.option_id WHERE ch.tenant_id=#{__verifiedTenant} AND v.product_id=UUID_TO_BIN(#{id})","<if test=\"access != null\"><bind name=\"rangeSql\" value=\"@com.appnexus.persistence.mybatis.ScopedSql@bind(access.sql(), access.parameters(), &quot;access&quot;)\"/> AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id}) ${rangeSql})</if>","</script>"})
 List<Map<String,Object>> choices(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("access") Predicate access);

 /**
 * categoryConfiguration 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param config 商品明确绑定的配置版本UUID，不追随最新版本
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select("SELECT revision_no revision,display_path_snapshot path,service_unit_required serviceUnitRequired FROM md_product_category_config WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{config})")
 Map<String,Object> categoryConfiguration(@Param("__verifiedTenant") byte[] tenant,@Param("config") Object config);

 /**
 * baseUnit 只读固定SQL；父记录已在同用例授权，所有关联仍强制同租户及父商品。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param unit 当前租户基础单位UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select("SELECT code,name,dimension_code dimensionCode,precision_scale precisionScale FROM md_product_unit WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{unit})")
 Map<String,Object> baseUnit(@Param("__verifiedTenant") byte[] tenant,@Param("unit") Object unit);

 /**
 * @param tenant 可信租户二进制UUID
 * @param config 父商品已绑定的配置UUID
 * @return 该版本的用途与模板绑定，不追随最新版本
 */
 @Select("SELECT purpose,BIN_TO_UUID(template_version_id) versionId FROM md_product_category_template WHERE tenant_id=#{__verifiedTenant} AND config_id=UUID_TO_BIN(#{config})")
 List<Map<String,Object>> templates(@Param("__verifiedTenant") byte[] tenant,@Param("config") Object config);
}
