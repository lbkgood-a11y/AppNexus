package com.appnexus.masterdata.adapter.persistence;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.appnexus.persistence.mybatis.TenantAggregateScoped;
import com.appnexus.persistence.mybatis.GuardedScope;
import com.appnexus.persistence.mybatis.ScopedSql;
import com.appnexus.platform.authorization.RowAccessControl;
/**
 * 商品追加历史固定Mapper；业务快照授权、父CAS和历史在同一事务完成，不保存预检Token。
 */
@Mapper
@TenantAggregateScoped(requireTenantMatch=true,tables={"md_product_v1_change","md_product_v1"},statements={"append","list","count"})
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
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product",tenant="__verifiedTenant",recordId="product",
     actions={"create","update","set_status","manage_structure","manage_draft","migrate_structure","masterdata.product.approval:submit"})
 int append(@Param("__verifiedTenant") byte[] tenant,@Param("id") String id,@Param("product") String product,@Param("version") Object version,@Param("event") String event,@Param("before") String before,@Param("after") String after,@Param("actor") String actor);
 /**
 * @param tenant 可信租户二进制UUID
 * @param product 已通过原对象读取权限的商品UUID
 * @param size 每页1至100条
 * @param offset 从0开始偏移
 * @param access 商品当前读取范围，由统一执行器生成
 * @return 同租户商品历史，按版本倒序
 */
 @GuardedScope(predicate="access",resource="masterdata.product",tenant="__verifiedTenant",actions="read")
 @SelectProvider(type=HistorySql.class,method="list")
 List<Map<String,Object>> list(@Param("__verifiedTenant") byte[] tenant,@Param("product") String product,@Param("size") int size,@Param("offset") int offset,@Param("access") RowAccessControl.Predicate access);
 /**
 * @param tenant 可信租户二进制UUID
 * @param product 与分页相同的商品UUID
 * @param access 与分页相同的可信谓词
 * @return 与列表相同租户及父记录条件的总数
 */
 @GuardedScope(predicate="access",resource="masterdata.product",tenant="__verifiedTenant",actions="read")
 @SelectProvider(type=HistorySql.class,method="count")
 long count(@Param("__verifiedTenant") byte[] tenant,@Param("product") String product,@Param("access") RowAccessControl.Predicate access);

 /** 列表和计数在同一固定父记录查询绑定权威谓词，历史行不能脱离商品范围读取。 */
 final class HistorySql {
   private HistorySql() {}

   private static String conditions(Map<String,Object> parameters){
     var access=(RowAccessControl.Predicate)parameters.get("access");
     return " FROM md_product_v1_change WHERE tenant_id=#{__verifiedTenant} AND product_id=UUID_TO_BIN(#{product})"
         +" AND EXISTS (SELECT 1 FROM md_product_v1 WHERE tenant_id=#{__verifiedTenant} AND id=UUID_TO_BIN(#{product})"
         +ScopedSql.bind(access.sql(),access.parameters(),"access")+")";
   }

   /** @param parameters 固定 Mapper 参数 @return 使用同一父范围条件的分页 SQL */
   public static String list(Map<String,Object> parameters){
     return "SELECT BIN_TO_UUID(id) id,to_version version,event_type eventType,"
         +"CAST(before_snapshot AS CHAR) beforeSnapshot,CAST(after_snapshot AS CHAR) afterSnapshot,"
         +"schema_version schemaVersion,created_at createdAt,BIN_TO_UUID(created_by) createdBy"
         +conditions(parameters)+" ORDER BY to_version DESC LIMIT #{size} OFFSET #{offset}";
   }

   /** @param parameters 固定 Mapper 参数 @return 使用同一父范围条件的计数 SQL */
   public static String count(Map<String,Object> parameters){
     return "SELECT COUNT(*)"+conditions(parameters);
   }
 }
}
