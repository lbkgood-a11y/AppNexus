package com.appnexus.masterdata.adapter.jdbc;

import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品列表固定 SQL 映射；COUNT 和列表复用同一 XML 条件，不接受表名、列名或 SQL 输入。
 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_v1","md_product_v1_attribute_value","md_product_v1_attribute_choice","md_product_category_config_item","md_product_template_item","md_product_attribute_revision","md_product_attribute_option"},statements={"list","count","definitions","optionCount"})
public interface ProductListMapper {
    /**
 * 已验证属性条件；业务筛选不替代功能准入、行范围或字段保护。
 * @param item 固定配置内可搜索属性 UUID
 * @param type INTEGER/DECIMAL/DATE/BOOLEAN/SINGLE_SELECT/MULTI_SELECT，来自数据库定义
 * @param operator 已由领域规则校验的 eq/gte/lte/between/anyOf
 * @param values 类型化且非空的绑定值，最多50个；不含SQL表达式
 */
    record Filter(String item,String type,String operator,List<Object> values){public Filter{values=List.copyOf(values);}}
    /**
 * 商品列表的已验证业务筛选参数，不承载授权事实。
 * @param range 本次读取操作的统一组织范围，非ALL空集合拒绝
     * @param name 最多200字的字面包含名称，默认空
 * @param code 最多64字的字面包含编码，默认空
 * @param category 可空分类 UUID
 * @param form 可空商品形态
 * @param status 可空商品状态
 * @param archived true仅已归档，false仅未归档
 * @param config 可空明确配置版本 UUID
 * @param filters 单一配置最多3个已验证属性条件
 * @param size 每页1–100条
 * @param offset 从0开始的分页偏移
 */
    record Query(com.appnexus.platform.authorization.RowAccessControl.OrganizationRange range,String name,String code,String category,String form,String status,boolean archived,String config,List<Filter> filters,int size,int offset){public Query{filters=List.copyOf(filters);}}
    /**
 * @param tenant 可信租户二进制UUID，执行拦截器再次覆盖
 * @param query 已验证业务筛选
 * @return 按编码及ID稳定排序的当前页
 */
    List<Map<String,Object>> list(@Param("__verifiedTenant") byte[] tenant,@Param("query") Query query);
    /**
 * @param tenant 与列表相同的可信租户
 * @param query 与列表相同的过滤参数
 * @return 相同SQL条件下的总数
 */
    long count(@Param("__verifiedTenant") byte[] tenant,@Param("query") Query query);
    /**
 * @param tenant 可信租户
 * @param config 明确配置UUID
 * @param item 候选属性UUID
 * @return 该配置实际包含且允许搜索的属性定义
 */
    List<Map<String,Object>> definitions(@Param("__verifiedTenant") byte[] tenant,@Param("config") String config,@Param("item") String item);
    /**
 * @param tenant 可信租户
 * @param revision 真实属性修订UUID
 * @param option 待验证选项UUID
 * @return 属于同一属性及租户时为1，否则为0
 */
    int optionCount(@Param("__verifiedTenant") byte[] tenant,@Param("revision") Object revision,@Param("option") String option);
}
