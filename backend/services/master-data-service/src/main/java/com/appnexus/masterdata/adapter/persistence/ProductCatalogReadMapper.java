package com.appnexus.masterdata.adapter.persistence;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/**
 * 商品引用目录固定Mapper；上层先验证消费资格，所有多表关系限定同一可信租户。
 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_attribute_option","md_product_attribute_revision","md_product_category","md_product_category_config","md_product_category_config_item","md_product_category_template","md_product_template","md_product_template_item","md_product_template_version","md_product_unit"},statements={"configurationVersions","configurationCount","configuration","fields","fieldOptions","validateCategory","validateConfiguration","validateTemplateStates","validateUnit","templates","options","optionCount"})
public interface ProductCatalogReadMapper {
 /**
 * configurationVersions 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param categoryId 当前租户分类UUID
 * @param size 每页1–100条，由上层验证
 * @param offset 从0开始的分页偏移，由合法页码计算
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select("SELECT BIN_TO_UUID(id) id,revision_no revision,display_path_snapshot path FROM md_product_category_config WHERE tenant_id=#{__verifiedTenant} AND category_id=UUID_TO_BIN(#{categoryId}) ORDER BY revision_no DESC LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> configurationVersions(@Param("__verifiedTenant") byte[] tenant,@Param("categoryId") String categoryId,@Param("size") int size,@Param("offset") int offset);

 /**
 * configurationCount 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 */
 @Select("SELECT COUNT(*) FROM md_product_category_config WHERE tenant_id=#{__verifiedTenant} AND category_id=UUID_TO_BIN(#{categoryId})")
 Long configurationCount(@Param("__verifiedTenant") byte[] tenant,@Param("categoryId") String categoryId);

 /**
 * configuration 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(category_id) categoryId,revision_no revision,display_path_snapshot path,service_unit_required serviceUnitRequired FROM md_product_category_config WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})")
 List<Map<String,Object>> configuration(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

 /**
 * fields 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select("SELECT BIN_TO_UUID(i.id) templateItemId,BIN_TO_UUID(i.attribute_id) attributeId,BIN_TO_UUID(i.attribute_revision_id) revisionId,ci.purpose,r.name,r.data_type dataType,r.numeric_scale numericScale,CAST(r.validation_json AS CHAR) validationJson,i.group_code groupCode,i.group_name groupName,i.required_flag requiredFlag,i.searchable,i.allow_custom allowCustom,BIN_TO_UUID(r.unit_id) unitId,u.code unitCode,u.name unitName FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id JOIN md_product_attribute_revision r ON r.tenant_id=i.tenant_id AND r.id=i.attribute_revision_id LEFT JOIN md_product_unit u ON u.tenant_id=r.tenant_id AND u.id=r.unit_id WHERE ci.tenant_id=#{__verifiedTenant} AND ci.config_id=UUID_TO_BIN(#{id}) ORDER BY ci.purpose,i.sort_order,i.id")
 List<Map<String,Object>> fields(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);

 /**
 * fieldOptions 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param revision 真实属性修订UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select("SELECT BIN_TO_UUID(id) id,option_code code,label,enabled FROM md_product_attribute_option WHERE tenant_id=#{__verifiedTenant} AND revision_id=UUID_TO_BIN(#{revision}) ORDER BY sort_order,id")
 List<Map<String,Object>> fieldOptions(@Param("__verifiedTenant") byte[] tenant,@Param("revision") Object revision);

 /**
 * validateCategory 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param category 当前租户分类UUID
 * @param locks 写用例是否按原顺序加共享锁，false用于无写锁预检
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select({"<script>","SELECT enabled FROM md_product_category WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{category})","<if test='locks'> FOR SHARE</if>","</script>"})
 List<Map<String,Object>> validateCategory(@Param("__verifiedTenant") byte[] tenant,@Param("category") String category,@Param("locks") boolean locks);

 /**
 * validateConfiguration 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param config 商品明确绑定的配置版本UUID，不追随最新版本
 * @param category 当前租户分类UUID
 * @return 原存储契约定义的结果；并发或引用冲突由调用用例复核
 */
 @Select("SELECT COUNT(*) FROM md_product_category_config WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{config}) AND category_id=UUID_TO_BIN(#{category})")
 Integer validateConfiguration(@Param("__verifiedTenant") byte[] tenant,@Param("config") String config,@Param("category") String category);

 /**
 * validateTemplateStates 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param config 商品明确绑定的配置版本UUID，不追随最新版本
 * @param locks 写用例是否按原顺序加共享锁，false用于无写锁预检
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select({"<script>","SELECT v.state,t.enabled FROM md_product_category_template b JOIN md_product_template_version v ON v.tenant_id=b.tenant_id AND v.id=b.template_version_id JOIN md_product_template t ON t.tenant_id=v.tenant_id AND t.id=v.template_id WHERE b.tenant_id=#{__verifiedTenant} AND b.config_id=UUID_TO_BIN(#{config}) ORDER BY v.id","<if test='locks'> FOR SHARE</if>","</script>"})
 List<Map<String,Object>> validateTemplateStates(@Param("__verifiedTenant") byte[] tenant,@Param("config") String config,@Param("locks") boolean locks);

 /**
 * validateUnit 固定配置及引用查询；可信租户限定，锁顺序沿用原用例。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param unit 当前租户基础单位UUID
 * @param locks 写用例是否按原顺序加共享锁，false用于无写锁预检
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select({"<script>","SELECT enabled FROM md_product_unit WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{unit})","<if test='locks'> FOR SHARE</if>","</script>"})
 List<Map<String,Object>> validateUnit(@Param("__verifiedTenant") byte[] tenant,@Param("unit") String unit,@Param("locks") boolean locks);

 /**
 * @param tenant 可信租户二进制UUID
 * @param config 明确配置版本UUID
 * @return 该配置固定模板绑定
 */
 @Select("SELECT purpose,BIN_TO_UUID(template_version_id) versionId FROM md_product_category_template WHERE tenant_id=#{__verifiedTenant} AND config_id=UUID_TO_BIN(#{config})")
 List<Map<String,Object>> templates(@Param("__verifiedTenant") byte[] tenant,@Param("config") String config);
 /**
 * @param tenant 可信租户二进制UUID
 * @param kind 已验证的category/unit
 * @param keyword 最多200字字面包含
 * @param size 每页1–100条
 * @param offset 从0开始偏移
 * @return 同租户启用目录候选，不提供维护权限
 */
 @Select({"<script>","SELECT BIN_TO_UUID(id) id,code,name",
 "<choose><when test=\"kind == 'category'\">,BIN_TO_UUID(current_config_id) configId FROM md_product_category</when><otherwise>,dimension_code dimensionCode,precision_scale precisionScale FROM md_product_unit</otherwise></choose>",
 "WHERE tenant_id=#{__verifiedTenant} AND enabled=TRUE AND (LOCATE(#{keyword},code)&gt;0 OR LOCATE(#{keyword},name)&gt;0) ORDER BY code,id LIMIT #{size} OFFSET #{offset}","</script>"})
 List<Map<String,Object>> options(@Param("__verifiedTenant") byte[] tenant,@Param("kind") String kind,@Param("keyword") String keyword,@Param("size") int size,@Param("offset") int offset);
 /**
 * @param tenant 与候选列表同一可信租户
 * @param kind 固定category/unit
 * @param keyword 与列表相同的过滤
 * @return 相同条件下总数
 */
 @Select({"<script>","SELECT COUNT(*) FROM <choose><when test=\"kind == 'category'\">md_product_category</when><otherwise>md_product_unit</otherwise></choose>",
 "WHERE tenant_id=#{__verifiedTenant} AND enabled=TRUE AND (LOCATE(#{keyword},code)&gt;0 OR LOCATE(#{keyword},name)&gt;0)","</script>"})
 long optionCount(@Param("__verifiedTenant") byte[] tenant,@Param("kind") String kind,@Param("keyword") String keyword);

}
