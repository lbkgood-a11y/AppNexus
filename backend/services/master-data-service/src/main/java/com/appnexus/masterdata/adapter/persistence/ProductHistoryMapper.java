package com.appnexus.masterdata.adapter.persistence;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
/**
 * 商品追加历史固定Mapper；业务快照授权、父CAS和历史在同一事务完成，不保存预检Token。
 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_v1_change"},statements={"append","list","count"})
public interface ProductHistoryMapper {
 /**
 * @param tenant 可信租户二进制UUID
 * @param id 服务端生成历史UUID
 * @param product 已授权商品UUID
 * @param version 更新后的父版本
 * @param event 固定业务事件码
 * @param before 创建时可空的原JSON快照
 * @param after 已授权结果JSON快照
 * @param actor 可信实际账号UUID
 * @return 追加行数，失败与父修改共同回滚
 */
 @Insert("INSERT INTO md_product_v1_change(id,tenant_id,product_id,to_version,event_type,before_snapshot,after_snapshot,created_by,updated_by) VALUES(UUID_TO_BIN(#{id}),#{__verifiedTenant},UUID_TO_BIN(#{product}),#{version},#{event},#{before},#{after},UUID_TO_BIN(#{actor}),UUID_TO_BIN(#{actor}))")
 int append(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("product") String product,@Param("version") Object version,@Param("event") String event,@Param("before") String before,@Param("after") String after,@Param("actor") String actor);
 /**
 * @param tenant 可信租户二进制UUID
 * @param product 已通过原对象读取权限的商品UUID
 * @param size 每页1至100条
 * @param offset 从0开始偏移
 * @return 同租户商品历史，按版本倒序
 */
 @Select("SELECT BIN_TO_UUID(id) id,to_version version,event_type eventType,CAST(before_snapshot AS CHAR) beforeSnapshot,CAST(after_snapshot AS CHAR) afterSnapshot,schema_version schemaVersion,created_at createdAt,BIN_TO_UUID(created_by) createdBy FROM md_product_v1_change WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{product}) ORDER BY to_version DESC LIMIT #{size} OFFSET #{offset}")
 List<Map<String,Object>> list(@Param("__verifiedTenant") byte[] tenant,@Param("product") String product,@Param("size") int size,@Param("offset") int offset);
 /**
 * @param tenant 可信租户二进制UUID
 * @param product 与分页相同的商品UUID
 * @return 与列表相同租户及父记录条件的总数
 */
 @Select("SELECT COUNT(*) FROM md_product_v1_change WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{product})")
 long count(@Param("__verifiedTenant") byte[] tenant,@Param("product") String product);
}
