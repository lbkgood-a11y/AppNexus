package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 已授权商品聚合属性固定SQL，不向外提供任意SQL或技术表接口。 */
@Mapper
@PlatformScoped(tables={"md_product_attribute_option","md_product_v1_attribute_choice","md_product_v1_attribute_value"})
public interface ProductAttributeValueMapper {
 /** 固定属性语句1；父锁、值校验、原创建审计及整笔回滚契约保持。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p0",recordId="p1",actions={"create","update","migrate_structure"})
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(template_item_id) item,BIN_TO_UUID(created_by) creator,created_at FROM md_product_v1_attribute_value WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> statement1(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定属性语句2；父锁、值校验、原创建审计及整笔回滚契约保持。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p0",recordId="p1",actions={"create","update","migrate_structure"})
 @Select("SELECT BIN_TO_UUID(ch.id) id,BIN_TO_UUID(v.template_item_id) item,BIN_TO_UUID(ch.option_id) optionId,ch.custom_text customText,BIN_TO_UUID(ch.created_by) creator,ch.created_at FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id WHERE ch.tenant_id=UUID_TO_BIN(#{p0}) AND v.product_id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> statement2(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定属性语句3；父锁、值校验、原创建审计及整笔回滚契约保持。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p0",recordId="p1",actions={"create","update","migrate_structure"})
 @Delete("DELETE ch FROM md_product_v1_attribute_choice ch JOIN md_product_v1_attribute_value v ON v.tenant_id=ch.tenant_id AND v.id=ch.value_id WHERE ch.tenant_id=UUID_TO_BIN(#{p0}) AND v.product_id=UUID_TO_BIN(#{p1})")
 int statement3(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定属性语句4；父锁、值校验、原创建审计及整笔回滚契约保持。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p0",recordId="p1",actions={"create","update","migrate_structure"})
 @Delete("DELETE FROM md_product_v1_attribute_value WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1})")
 int statement4(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定属性语句5；父锁、值校验、原创建审计及整笔回滚契约保持。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="p2",actions={"create","update","migrate_structure"})
 @Insert("INSERT INTO md_product_v1_attribute_value(id,tenant_id,product_id,category_config_id,template_item_id,attribute_revision_id,value_type,text_value,integer_value,decimal_value,boolean_value,date_value,created_by,created_at,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),#{p6},#{p7},#{p8},#{p9},#{p10},#{p11},UUID_TO_BIN(#{p12}),COALESCE(#{p13},CURRENT_TIMESTAMP(6)),UUID_TO_BIN(#{p14}))")
 int statement5(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12,@Param("p13") Object p13,@Param("p14") Object p14);

 /** 固定属性语句6；父锁、值校验、原创建审计及整笔回滚契约保持。 */
 @Select("SELECT COUNT(*) FROM md_product_attribute_option WHERE tenant_id=UUID_TO_BIN(#{p0}) AND revision_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND enabled=TRUE")
 Integer statement6(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定属性语句7；父锁、值校验、原创建审计及整笔回滚契约保持。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="product",actions={"create","update","migrate_structure"})
 @Insert("INSERT INTO md_product_v1_attribute_choice(id,tenant_id,value_id,attribute_revision_id,option_id,created_by,created_at,updated_by) SELECT UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),UUID_TO_BIN(#{p4}),UUID_TO_BIN(#{p5}),COALESCE(#{p6},CURRENT_TIMESTAMP(6)),UUID_TO_BIN(#{p7}) FROM md_product_v1_attribute_value v WHERE v.tenant_id=UUID_TO_BIN(#{p1}) AND v.id=UUID_TO_BIN(#{p2}) AND v.product_id=UUID_TO_BIN(#{product})")
 int statement7(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("product") String product);

 /** 固定属性语句8；父锁、值校验、原创建审计及整笔回滚契约保持。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="p1",recordId="product",actions={"create","update","migrate_structure"})
 @Insert("INSERT INTO md_product_v1_attribute_choice(id,tenant_id,value_id,attribute_revision_id,custom_text,created_by,created_at,updated_by) SELECT UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},UUID_TO_BIN(#{p5}),COALESCE(#{p6},CURRENT_TIMESTAMP(6)),UUID_TO_BIN(#{p7}) FROM md_product_v1_attribute_value v WHERE v.tenant_id=UUID_TO_BIN(#{p1}) AND v.id=UUID_TO_BIN(#{p2}) AND v.product_id=UUID_TO_BIN(#{product})")
 int statement8(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("product") String product);
}
