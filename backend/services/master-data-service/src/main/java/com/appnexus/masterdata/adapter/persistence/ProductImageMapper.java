package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ProductImageService固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_product_image","md_product_v1"})
public interface ProductImageMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,width_px width,height_px height,media_type mediaType FROM md_product_image WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND deleted_at IS NULL ORDER BY created_at,id")
 List<Map<String,Object>> list(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM md_product_image WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) AND deleted_at IS NULL")
 Integer count(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO md_product_image(id,tenant_id,product_id,media_type,image_data,content_hash,width_px,height_px,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),'image/png',#{p3},#{p4},#{p5},#{p6},UUID_TO_BIN(#{p7}),UUID_TO_BIN(#{p8}))")
 int insert(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_image SET deleted_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),updated_by=UUID_TO_BIN(#{p0}) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND product_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3}) AND deleted_at IS NULL")
 int detach(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2}) AND version=#{p3}")
 int advanceParent(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** @param tenant 可信租户UUID @param product 已授权父商品UUID @param id 图片UUID @return 固定PNG内容，缺失不泄漏其他租户记录 */
 @Select("SELECT media_type mediaType,image_data bytes FROM md_product_image WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND product_id=UUID_TO_BIN(#{product}) AND id=UUID_TO_BIN(#{id}) AND deleted_at IS NULL")
 List<Map<String,Object>> content(@Param("tenant") String tenant,@Param("product") String product,@Param("id") String id);
 /** @param tenant 可信租户 @param id 已授权父商品 @param lock 写用例持有行锁 @return 固定状态及版本，不提供任意列 */
 @Select({"<script>","SELECT status,draft_archived_at archived,structure_state structure,version FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})","<if test='lock'> FOR UPDATE</if>","</script>"})
 List<Map<String,Object>> parent(@Param("tenant") String tenant,@Param("id") String id,@Param("lock") boolean lock);
}
