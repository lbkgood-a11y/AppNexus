package com.appnexus.leave.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** 请假固定SQL映射；非HTTP审批终态也使用原可信实例与幂等校验，不伪造请求上下文。 */
@Mapper
@PlatformScoped(tables={"id","leave_poc_approval_cycle","leave_poc_request","leave_poc_submission"})
public interface LeaveRecordMapper {
 /** 固定请假存储语句1；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Select("SELECT instance_id,result FROM leave_poc_approval_cycle WHERE tenant_id=#{p0} AND object_id=#{p1} AND delivery_id IS NOT NULL")
 List<Map<String,Object>> appliedApprovals(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定请假存储语句2；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Insert("INSERT INTO leave_poc_request(tenant_id,id,applicant,organization_id,leave_type,start_date,end_date,days,reason,create_key,person_id) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4},#{p5},#{p6},#{p7},#{p8},#{p9},#{p10}) ON DUPLICATE KEY UPDATE id=id")
 int createDraft(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10);

 /** 固定请假存储语句3；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Select("SELECT * FROM leave_poc_request WHERE tenant_id=#{p0} AND applicant=#{p1} AND create_key=#{p2}")
 Map<String,Object> createdRequest(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定请假存储语句4；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Select("SELECT * FROM leave_poc_request WHERE tenant_id=#{p0} AND applicant=#{p1} AND (person_id=#{p2} OR person_id IS NULL) ORDER BY created_at DESC LIMIT 100")
 List<Map<String,Object>> ownRequests(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定请假存储语句5；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Update("UPDATE leave_poc_request SET state='SUBMITTED' WHERE tenant_id=#{p0} AND id=#{p1}")
 int markSubmitted(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定请假存储语句6；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Insert("INSERT INTO leave_poc_submission(tenant_id,object_id,basis_version,resolution_id,service) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4})")
 int recordSubmission(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定请假存储语句7；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Insert("INSERT INTO leave_poc_approval_cycle(tenant_id,id,object_id,basis_version,instance_id,resolution_id,configuration_version,basis_snapshot) VALUES(#{p0},#{p1},#{p2},#{p3},#{p4},#{p5},#{p6},JSON_OBJECT('leaveType',#{p7},'startDate',#{p8},'endDate',#{p9},'days',#{p10},'reason',#{p11},'organizationId',#{p12}))")
 int createApprovalCycle(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12);

 /** 固定请假存储语句8；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Update("UPDATE leave_poc_request SET instance_id=#{p0} WHERE tenant_id=#{p1} AND id=#{p2}")
 int bindInstance(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定请假存储语句9；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Update("UPDATE leave_poc_request SET organization_id=#{p0},leave_type=#{p1},start_date=#{p2},end_date=#{p3},days=#{p4},reason=#{p5},basis_version=basis_version+1,state='DRAFT',instance_id=NULL,last_delivery_id=NULL WHERE tenant_id=#{p6} AND id=#{p7}")
 int updateDraft(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定请假存储语句10；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Select("SELECT id,basis_version,instance_id,resolution_id,configuration_version,created_at FROM leave_poc_approval_cycle WHERE tenant_id=#{p0} AND object_id=#{p1} ORDER BY basis_version DESC")
 List<Map<String,Object>> approvalCycles(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定请假存储语句11；租户、本人归属、状态及任务准入由原用例复核，回执和状态保持同事务。 */
 @Select({"<script>","SELECT * FROM leave_poc_request WHERE tenant_id=#{p0} AND id=#{p1}","<if test='lock'> FOR UPDATE</if>","</script>"})
 List<Map<String,Object>> request(@Param("p0") Object p0,@Param("p1") Object p1,@Param("lock") boolean lock);
}
