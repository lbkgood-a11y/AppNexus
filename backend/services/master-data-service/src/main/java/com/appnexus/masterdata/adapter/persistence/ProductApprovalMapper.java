package com.appnexus.masterdata.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** ProductApprovalService固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"md_product_approval","md_product_v1"})
public interface ProductApprovalMapper {
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT status,version,structure_state,draft_archived_at FROM md_product_v1 WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product.approval",tenant="p0",recordId="p1",actions="submit")
 Map<String,Object> lockProduct(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(product_id) productId,product_version,instance_id instanceId,BIN_TO_UUID(organization_id) organization FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(#{p0}) AND applicant_id=UUID_TO_BIN(#{p1}) AND request_key=UUID_TO_BIN(#{p2})")
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="p0",serviceOperation="masterdata.product.approval:submit",serviceKeys={"p1","p2"})
 List<Map<String,Object>> existingRequest(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product.approval",tenant="p1",recordId="p2",actions="submit")
 @Update("UPDATE md_product_v1 SET version=version+1,updated_by=UUID_TO_BIN(#{p0}),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int freezeProductVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product.approval",tenant="p1",recordId="p0",actions="submit",target="p6")
 @Insert("INSERT INTO md_product_approval(id,tenant_id,product_id,request_key,product_version,applicant_id,organization_id,basis_snapshot,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},UUID_TO_BIN(#{p5}),UUID_TO_BIN(#{p6}),#{p7},UUID_TO_BIN(#{p8}),UUID_TO_BIN(#{p9}))")
 int createApproval(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @com.appnexus.persistence.mybatis.GuardedScope(resource="masterdata.product.approval",tenant="p2",recordId="p3",actions="submit")
 @Update("UPDATE md_product_approval SET instance_id=#{p0},resolution_id=#{p1} WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3})")
 int bindExecution(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,product_version productVersion,state,instance_id instanceId,created_at createdAt,updated_at updatedAt FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(#{p0}) AND product_id=UUID_TO_BIN(#{p1}) ORDER BY created_at DESC")
 List<Map<String,Object>> approvalHistory(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(product_id) productId,product_version productVersion,BIN_TO_UUID(applicant_id) applicant,BIN_TO_UUID(organization_id) organization,state,instance_id instanceId FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> approvalDetail(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 只有原申请人或当前有效任务获准后可读取固定审批快照。 */
 @com.appnexus.persistence.mybatis.GuardedScope(tenant="tenant",serviceOperation="masterdata.product.approval:snapshot",serviceKeys={"id"})
 @Select("SELECT CAST(basis_snapshot AS CHAR) FROM md_product_approval WHERE tenant_id=UUID_TO_BIN(#{tenant}) AND id=UUID_TO_BIN(#{id})")
 String approvalSnapshot(@Param("tenant") String tenant,@Param("id") String id);
}
