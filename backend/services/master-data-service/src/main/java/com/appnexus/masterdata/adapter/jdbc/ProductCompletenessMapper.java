package com.appnexus.masterdata.adapter.jdbc;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/**
 * 商品完整性事实固定Mapper；只访问主数据拥有的聚合与配置表，不提供动态SQL。
 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_category_config","md_product_category_config_item","md_product_component","md_product_specification","md_product_template_item","md_product_v1","md_product_v1_attribute_value"},statements={"serviceUnitRequired","missingAttributes","specificationCount","components","refresh"})
public interface ProductCompletenessMapper {
 /**
 * serviceUnitRequired 固定查询或状态更新；在调用方商品聚合授权和事务内执行，不扩大字段权限。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param config 商品明确绑定的配置版本UUID，不追随最新版本
 * @return 原存储契约定义的结果；并发或引用冲突由调用用例复核
 */
 @Select("SELECT service_unit_required FROM md_product_category_config WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{config})")
 Boolean serviceUnitRequired(@Param("__verifiedTenant") byte[] tenant,@Param("config") Object config);

 /**
 * missingAttributes 固定查询或状态更新；在调用方商品聚合授权和事务内执行，不扩大字段权限。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param config 商品明确绑定的配置版本UUID，不追随最新版本
 * @param product 已通过原商品操作准入的父商品UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select("SELECT BIN_TO_UUID(i.id) FROM md_product_category_config_item ci JOIN md_product_template_item i ON i.tenant_id=ci.tenant_id AND i.id=ci.template_item_id WHERE ci.tenant_id=#{__verifiedTenant} AND ci.config_id=UUID_TO_BIN(#{config}) AND ci.purpose<>'SPEC' AND i.required_flag=TRUE AND NOT EXISTS(SELECT 1 FROM md_product_v1_attribute_value v WHERE v.tenant_id=ci.tenant_id AND v.product_id=UUID_TO_BIN(#{product}) AND v.template_item_id=i.id)")
 List<String> missingAttributes(@Param("__verifiedTenant") byte[] tenant,@Param("config") Object config,@Param("product") Object product);

 /**
 * specificationCount 固定查询或状态更新；在调用方商品聚合授权和事务内执行，不扩大字段权限。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param pid 已通过原商品操作准入的父商品UUID
 * @return 原存储契约定义的结果；并发或引用冲突由调用用例复核
 */
 @Select("SELECT COUNT(*) FROM md_product_specification WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{pid}) AND retired_at IS NULL AND enabled=TRUE")
 Integer specificationCount(@Param("__verifiedTenant") byte[] tenant,@Param("pid") String pid);

 /**
 * components 固定查询或状态更新；在调用方商品聚合授权和事务内执行，不扩大字段权限。
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param pid 已通过原商品操作准入的父商品UUID
 * @return 当前租户固定查询结果，不扩展原操作授权
 */
 @Select("SELECT p.status,p.version,c.component_version FROM md_product_component c JOIN md_product_v1 p ON p.tenant_id=c.tenant_id AND p.id=c.component_product_id WHERE c.tenant_id=#{__verifiedTenant} AND c.product_id=UUID_TO_BIN(#{pid}) AND c.enabled=TRUE")
 List<Map<String,Object>> components(@Param("__verifiedTenant") byte[] tenant,@Param("pid") String pid);

 /**
 * refresh 固定查询或状态更新；在调用方商品聚合授权和事务内执行，不扩大字段权限。
 * @param state 领域规则产生的固定完整性状态，不来自客户端自报事实
 * @param actor 来自可信上下文的实际账号UUID，不冒充被代表人员
 * @param tenant 安全链可信租户二进制UUID，执行时再次复核，不接受页面指定其他租户
 * @param id 已由调用用例校验的记录UUID
 * @return 原存储契约定义的结果；并发或引用冲突由调用用例复核
 */
 @Update("UPDATE md_product_v1 SET structure_state=#{state},updated_by=UUID_TO_BIN(#{actor}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{id})")
 int refresh(@Param("state") String state,@Param("actor") String actor,@Param("__verifiedTenant") byte[] tenant,@Param("id") String id);
}
