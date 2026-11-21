package com.appnexus.printing.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** PrintOutputs固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"platform_tenant","print_agent_queue","print_device","print_document","print_job","print_job_artifact","print_output_request","print_scene","print_template","print_template_version"})
public interface PrintOutputsMapper extends com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor {
 /** @return 受原用例身份复核和固定存储约束保护的专业服务资源 */
 @Override default java.util.Set<String> enforcedResources(){return java.util.Set.of("print.output");}
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id FROM print_scene WHERE tenant_id=UUID_TO_BIN(#{p0}) AND code=#{p1} AND data_contract=#{p2} AND enabled=TRUE")
 List<Map<String,Object>> businessScene(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(scene_id) scene FROM print_document WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND created_by=UUID_TO_BIN(#{p2})")
 List<Map<String,Object>> ownDocument(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT EXISTS(SELECT 1 FROM print_scene s JOIN print_template t ON t.tenant_id=s.tenant_id AND t.id=s.template_id JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=t.published_version WHERE s.tenant_id=UUID_TO_BIN(#{p0}) AND s.id=UUID_TO_BIN(#{p1}) AND s.enabled=TRUE AND t.enabled=TRUE AND v.data_contract=s.data_contract)")
 Boolean sceneReady(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT EXISTS(SELECT 1 FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(#{p0}) AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND))")
 Boolean rendererReady(@Param("p0") Object p0);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT site_code,system_name FROM print_device WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND enabled=TRUE")
 List<Map<String,Object>> enabledDevice(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT EXISTS(SELECT 1 FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(#{p0}) AND site_code=#{p1} AND queue_name=#{p2} AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND))")
 Boolean deviceQueueOnline(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,data_contract FROM print_scene WHERE tenant_id=UUID_TO_BIN(#{p0}) AND code=#{p1} AND enabled=TRUE")
 List<Map<String,Object>> externalScene(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT id FROM platform_tenant WHERE id=UUID_TO_BIN(#{p0}) FOR UPDATE")
 List<Map<String,Object>> lockTenantOutputs(@Param("p0") Object p0);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id,payload_hash FROM print_output_request WHERE tenant_id=UUID_TO_BIN(#{p0}) AND owner=#{p1} AND request_id=UUID_TO_BIN(#{p2})")
 List<Map<String,Object>> existingRequest(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_output_request(tenant_id,id,owner,request_id,payload_hash,mode,job_id,device_id,copies) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},UUID_TO_BIN(#{p3}),#{p4},#{p5},UUID_TO_BIN(#{p6}),UUID_TO_BIN(#{p7}),#{p8})")
 int createOutput(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(r.id) id,r.owner,r.mode,BIN_TO_UUID(r.job_id) jobId,BIN_TO_UUID(r.confirmed_job_id) confirmedJobId,BIN_TO_UUID(r.device_id) deviceId,r.copies,j.state,EXISTS(SELECT 1 FROM print_job_artifact a WHERE a.tenant_id=r.tenant_id AND a.job_id=r.job_id) pdfReady FROM print_output_request r JOIN print_job j ON j.tenant_id=r.tenant_id AND j.id=r.job_id WHERE r.tenant_id=UUID_TO_BIN(#{p0}) AND r.id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> outputStatus(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT EXISTS(SELECT 1 FROM print_agent_queue WHERE tenant_id=UUID_TO_BIN(#{p0}) AND seen_at>DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 120 SECOND))")
 Boolean rendererOnline(@Param("p0") Object p0);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT state FROM print_job WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 Map<String,Object> confirmedJob(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT id FROM print_output_request WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> lockOutput(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_output_request SET confirmed_job_id=UUID_TO_BIN(#{p0}) WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int confirmOutput(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);
}
