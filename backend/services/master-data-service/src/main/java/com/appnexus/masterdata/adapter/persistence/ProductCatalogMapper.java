package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ProductCatalogService固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_product_category","md_product_category_config","md_product_category_config_item","md_product_category_guard","md_product_category_template","md_product_template","md_product_template_item","md_product_template_version","md_product_unit"})
public interface ProductCatalogMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_unit(id,tenant_id,code,name,dimension_code,precision_scale,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}))")
 int createUnit(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_unit SET name=#{p0},version=version+1,updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND code=#{p4} AND dimension_code=#{p5} AND precision_scale=#{p6} AND version=#{p7}")
 int renameUnit(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_unit SET enabled=#{p0},version=version+1,updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND version=#{p4}")
 int setUnitStatus(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_category(id,tenant_id,code,name,parent_id,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),UUID_TO_BIN(#{p6}))")
 int createCategory(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_category SET name=#{p0},parent_id=UUID_TO_BIN(#{p1}),version=version+1,updated_by=UUID_TO_BIN(#{p2}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p3}) AND id=UUID_TO_BIN(#{p4}) AND version=#{p5}")
 int moveCategory(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_category SET enabled=#{p0},version=version+1,updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND version=#{p4}")
 int setCategoryStatus(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT tv.state,t.purpose,t.enabled FROM md_product_template_version tv JOIN md_product_template t ON t.tenant_id=tv.tenant_id AND t.id=tv.template_id WHERE tv.tenant_id=UUID_TO_BIN(#{p0}) AND tv.id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> publishedTemplate(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(attribute_id) id FROM md_product_template_item WHERE tenant_id=UUID_TO_BIN(#{p0}) AND template_version_id=UUID_TO_BIN(#{p1})")
 List<String> templateAttributes(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COALESCE(MAX(revision_no),0)+1 FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(#{p0}) AND category_id=UUID_TO_BIN(#{p1})")
 Long nextConfigurationRevision(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_category_config(id,tenant_id,category_id,revision_no,display_path_snapshot,service_unit_required,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}))")
 int createConfiguration(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_category_template(id,tenant_id,config_id,purpose,template_version_id,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),#{p3},UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),UUID_TO_BIN(#{p6}))")
 int bindTemplate(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_category_config_item(id,tenant_id,config_id,purpose,template_version_id,template_item_id,attribute_id,attribute_revision_id,data_type,created_by,updated_by) SELECT UUID_TO_BIN(UUID()),b.tenant_id,b.config_id,b.purpose,b.template_version_id,i.id,i.attribute_id,i.attribute_revision_id,i.data_type,UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}) FROM md_product_category_template b JOIN md_product_template_item i ON i.tenant_id=b.tenant_id AND i.template_version_id=b.template_version_id WHERE b.tenant_id=UUID_TO_BIN(#{p2}) AND b.config_id=UUID_TO_BIN(#{p3})")
 int freezeConfigurationItem(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_category SET current_config_id=UUID_TO_BIN(#{p0}),version=version+1,updated_by=UUID_TO_BIN(#{p1}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND version=#{p4}")
 int activateConfiguration(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT IGNORE INTO md_product_category_guard(tenant_id,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}))")
 int ensureCategoryGuard(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(tenant_id) FROM md_product_category_guard WHERE tenant_id=UUID_TO_BIN(#{p0}) FOR UPDATE")
 String lockCategoryGuard(@Param("p0") Object p0);

 /** 固定目录查询；分类版本和单位引用保持原业务准入及租户边界。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,dimension_code,precision_scale,enabled,version,BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND (LOCATE(#{keyword},code)>0 OR LOCATE(#{keyword},name)>0) ORDER BY code,id LIMIT #{size} OFFSET #{offset}") List<Map<String,Object>> units(@Param("tenant") String tenant,@Param("keyword") String keyword,@Param("size") int size,@Param("offset") int offset);
 /** 固定目录查询；分类版本和单位引用保持原业务准入及租户边界。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,dimension_code,precision_scale,enabled,version,BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})") List<Map<String,Object>> unit(@Param("tenant") String tenant,@Param("id") String id);
 /** 固定目录查询；分类版本和单位引用保持原业务准入及租户边界。 */
 @Select("SELECT BIN_TO_UUID(id) id,revision_no,display_path_snapshot,service_unit_required,BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM md_product_category_config WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND category_id=UUID_TO_BIN(#{category}) AND id=UUID_TO_BIN(#{id})") List<Map<String,Object>> configuration(@Param("tenant") String tenant,@Param("category") String category,@Param("id") String id);
 /** 固定目录查询；分类版本和单位引用保持原业务准入及租户边界。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,BIN_TO_UUID(parent_id) parent,enabled,BIN_TO_UUID(current_config_id) config,version,BIN_TO_UUID(created_by) creator,created_at,BIN_TO_UUID(updated_by) updater,updated_at FROM md_product_category WHERE tenant_id=UUID_TO_BIN(#{tenant})") List<Map<String,Object>> tree(@Param("tenant") String tenant);
 /** 固定目录查询；分类版本和单位引用保持原业务准入及租户边界。 */
 @Select("SELECT purpose,BIN_TO_UUID(template_version_id) version_id FROM md_product_category_template WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND config_id=UUID_TO_BIN(#{id})") List<Map<String,Object>> slots(@Param("tenant") String tenant,@Param("id") String id);
 /** @param tenant 可信租户 @param keyword 与列表一致的字面过滤 @return 同租户匹配总数 */
 @Select("SELECT COUNT(*) FROM md_product_unit WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND (LOCATE(#{keyword},code)>0 OR LOCATE(#{keyword},name)>0)") long unitCount(@Param("tenant") String tenant,@Param("keyword") String keyword);
}
