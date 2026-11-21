package com.appnexus.printing.adapter.persistence;
import com.appnexus.persistence.mybatis.PlatformScoped;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
/** PrintRuntime固定SQL映射；不接收SQL或标识符，原用例逐次验证可信租户及操作。 */
@Mapper
@PlatformScoped(tables={"SKIP","print_device","print_job","print_job_artifact","print_template","print_template_version"})
public interface PrintRuntimeMapper extends com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor {
 /** @return 受原用例身份复核和固定存储约束保护的专业服务资源 */
 @Override default java.util.Set<String> enforcedResources(){return java.util.Set.of("print.device", "print.job", "print.job.artifact", "print.template", "print.template.version");}
 /** 固定业务语句1；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_template(tenant_id,id,code,name,category,data_contract,scope_json,draft_json,created_by,updated_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},#{p4},#{p5},CAST(#{p6} AS JSON),CAST(#{p7} AS JSON),UUID_TO_BIN(#{p8}),UUID_TO_BIN(#{p9}))")
 int createTemplate(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9);

 /** 固定业务语句2；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_template SET code=#{p0},name=#{p1},category=#{p2},data_contract=#{p3},scope_json=CAST(#{p4} AS JSON),draft_json=CAST(#{p5} AS JSON),draft_revision=draft_revision+1,updated_by=UUID_TO_BIN(#{p6}) WHERE tenant_id=UUID_TO_BIN(#{p7}) AND id=UUID_TO_BIN(#{p8}) AND draft_revision=#{p9}")
 int updateTemplate(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9);

 /** 固定业务语句3；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT id FROM print_template WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> lockTemplate(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句4；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_template_version(tenant_id,template_id,version,template_json,scope_json,data_contract,published_by,data_contract_schema) SELECT tenant_id,id,#{p0},draft_json,scope_json,data_contract,UUID_TO_BIN(#{p1}),CAST(#{p2} AS JSON) FROM print_template WHERE tenant_id=UUID_TO_BIN(#{p3}) AND id=UUID_TO_BIN(#{p4})")
 int publishTemplate(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句5；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_template SET published_version=#{p0},enabled=TRUE,updated_by=UUID_TO_BIN(#{p1}) WHERE tenant_id=UUID_TO_BIN(#{p2}) AND id=UUID_TO_BIN(#{p3})")
 int freezeTemplateVersion(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3);

 /** 固定业务语句6；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_template t JOIN print_template_version v ON v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.version=#{p0} SET t.draft_json=v.template_json,t.scope_json=v.scope_json,t.data_contract=v.data_contract,t.draft_revision=t.draft_revision+1,t.updated_by=UUID_TO_BIN(#{p1}) WHERE t.tenant_id=UUID_TO_BIN(#{p2}) AND t.id=UUID_TO_BIN(#{p3}) AND t.draft_revision=#{p4}")
 int restoreTemplate(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4);

 /** 固定业务语句7；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_device(tenant_id,id,code,name,site_code,system_name,capabilities_json,created_by) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2},#{p3},#{p4},#{p5},CAST(#{p6} AS JSON),UUID_TO_BIN(#{p7}))")
 int createDevice(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句8；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_device SET code=#{p0},name=#{p1},site_code=#{p2},system_name=#{p3},capabilities_json=CAST(#{p4} AS JSON),enabled=#{p5} WHERE tenant_id=UUID_TO_BIN(#{p6}) AND id=UUID_TO_BIN(#{p7})")
 int updateDevice(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7);

 /** 固定业务语句9；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT data_contract,data_contract_schema FROM print_template_version WHERE tenant_id=UUID_TO_BIN(#{p0}) AND template_id=UUID_TO_BIN(#{p1}) AND version=#{p2}")
 Map<String,Object> publishedTemplate(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句10；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_job(tenant_id,id,request_id,actor_id,source_type,source_id,template_id,template_version,output_type,device_id,copies,data_snapshot,data_contract_schema,state) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),UUID_TO_BIN(#{p3}),#{p4},#{p5},UUID_TO_BIN(#{p6}),#{p7},#{p8},UUID_TO_BIN(#{p9}),#{p10},CAST(#{p11} AS JSON),CAST(#{p12} AS JSON),'QUEUED')")
 int createJob(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6,@Param("p7") Object p7,@Param("p8") Object p8,@Param("p9") Object p9,@Param("p10") Object p10,@Param("p11") Object p11,@Param("p12") Object p12);

 /** 固定业务语句11；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(id) id FROM print_job WHERE tenant_id=UUID_TO_BIN(#{p0}) AND actor_id=UUID_TO_BIN(#{p1}) AND request_id=UUID_TO_BIN(#{p2})")
 List<Map<String,Object>> existingReprint(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句12；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_job(tenant_id,id,request_id,actor_id,source_type,source_id,template_id,template_version,output_type,device_id,copies,data_snapshot,data_contract_schema,state,parent_job_id,reprint_reason) SELECT tenant_id,UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),source_type,source_id,template_id,template_version,output_type,device_id,copies,data_snapshot,data_contract_schema,'QUEUED',id,#{p3} FROM print_job WHERE tenant_id=UUID_TO_BIN(#{p4}) AND id=UUID_TO_BIN(#{p5})")
 int copyReprint(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5);

 /** 固定业务语句13；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_job SET state='UNKNOWN',result_json=JSON_OBJECT('code','AGENT_LEASE_EXPIRED'),claim_expires_at=NULL WHERE tenant_id=UUID_TO_BIN(#{p0}) AND state='CLAIMED' AND claim_expires_at<CURRENT_TIMESTAMP(6)")
 int expireLeases(@Param("p0") Object p0);

 /** 固定业务语句14；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT BIN_TO_UUID(j.id) FROM print_job j LEFT JOIN print_device d ON d.tenant_id=j.tenant_id AND d.id=j.device_id WHERE j.tenant_id=UUID_TO_BIN(#{p0}) AND j.state='QUEUED' AND (j.output_type='PDF' OR (j.output_type='PRINTER' AND d.enabled=TRUE AND d.site_code=#{p1})) ORDER BY j.created_at LIMIT 1 FOR UPDATE SKIP LOCKED")
 List<String> claimableJobs(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句15；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_job SET state='CLAIMED',claimed_by=#{p0},claim_expires_at=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 2 MINUTE),attempt=attempt+1 WHERE tenant_id=UUID_TO_BIN(#{p1}) AND id=UUID_TO_BIN(#{p2})")
 int claimJob(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句16；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_device SET last_seen_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(#{p0}) AND site_code=#{p1}")
 int expireRendererLeases(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句17；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_job_artifact(tenant_id,job_id,pdf_bytes) VALUES(UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),#{p2})")
 int savePdf(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定业务语句18；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Update("UPDATE print_job SET state='SUCCEEDED',claim_expires_at=NULL,result_json=JSON_OBJECT('code','PDF_READY') WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 int completePdf(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句19；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Select("SELECT COUNT(*) FROM print_device WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) AND enabled=TRUE")
 Integer enabledDeviceCount(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定业务语句20；准入、参数规则、聚合锁及事务由原用例负责，所有值绑定。 */
 @Insert("INSERT INTO print_job(tenant_id,id,request_id,actor_id,source_type,source_id,template_id,template_version,output_type,device_id,copies,data_snapshot,data_contract_schema,state,parent_job_id) SELECT tenant_id,UUID_TO_BIN(#{p0}),UUID_TO_BIN(#{p1}),UUID_TO_BIN(#{p2}),source_type,source_id,template_id,template_version,'PRINTER',UUID_TO_BIN(#{p3}),#{p4},data_snapshot,data_contract_schema,'QUEUED',id FROM print_job WHERE tenant_id=UUID_TO_BIN(#{p5}) AND id=UUID_TO_BIN(#{p6}) AND output_type='PDF' AND state='SUCCEEDED'")
 int copyPreview(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2,@Param("p3") Object p3,@Param("p4") Object p4,@Param("p5") Object p5,@Param("p6") Object p6);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,category,data_contract,scope_json,draft_json,draft_revision,published_version,enabled,created_at,updated_at FROM print_template WHERE tenant_id=UUID_TO_BIN(#{p0}) ORDER BY updated_at DESC")
 List<Map<String,Object>> templates(@Param("p0") Object p0);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,category,data_contract,scope_json,draft_json,draft_revision,published_version,enabled,created_at,updated_at FROM print_template WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> template(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT version,template_json,scope_json,data_contract,BIN_TO_UUID(published_by) published_by,published_at FROM print_template_version WHERE tenant_id=UUID_TO_BIN(#{p0}) AND template_id=UUID_TO_BIN(#{p1}) ORDER BY version DESC")
 List<Map<String,Object>> versions(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,site_code,system_name,capabilities_json,enabled,last_seen_at,created_at FROM print_device WHERE tenant_id=UUID_TO_BIN(#{p0}) ORDER BY name")
 List<Map<String,Object>> devices(@Param("p0") Object p0);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,site_code,system_name,capabilities_json,enabled,last_seen_at,created_at FROM print_device WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> device(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(j.id) id,j.source_type,j.source_id,BIN_TO_UUID(j.template_id) template_id,j.template_version,j.output_type,BIN_TO_UUID(j.device_id) device_id,j.copies,j.state,j.attempt,j.result_json,BIN_TO_UUID(j.parent_job_id) parent_job_id,j.reprint_reason,j.created_at,j.updated_at,t.name template_name,d.name device_name FROM print_job j JOIN print_template t ON t.tenant_id=j.tenant_id AND t.id=j.template_id LEFT JOIN print_device d ON d.tenant_id=j.tenant_id AND d.id=j.device_id WHERE j.tenant_id=UUID_TO_BIN(#{p0}) ORDER BY j.created_at DESC LIMIT 500")
 List<Map<String,Object>> jobs(@Param("p0") Object p0);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT enabled FROM print_device WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> enabledDevice(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT published_version version,enabled FROM print_template WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> currentTemplate(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(j.id) id,j.output_type,j.copies,j.data_snapshot,v.template_json,d.system_name,d.capabilities_json FROM print_job j JOIN print_template_version v ON v.tenant_id=j.tenant_id AND v.template_id=j.template_id AND v.version=j.template_version LEFT JOIN print_device d ON d.tenant_id=j.tenant_id AND d.id=j.device_id WHERE j.tenant_id=UUID_TO_BIN(#{p0}) AND j.id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> claimedJob(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT output_type,state,claimed_by,(claim_expires_at>CURRENT_TIMESTAMP(6)) active FROM print_job WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1}) FOR UPDATE")
 List<Map<String,Object>> artifactJob(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT pdf_bytes FROM print_job_artifact WHERE tenant_id=UUID_TO_BIN(#{p0}) AND job_id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> pdfArtifact(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(id) id FROM print_job WHERE tenant_id=UUID_TO_BIN(#{p0}) AND actor_id=UUID_TO_BIN(#{p1}) AND request_id=UUID_TO_BIN(#{p2})")
 List<Map<String,Object>> requestJob(@Param("p0") Object p0,@Param("p1") Object p1,@Param("p2") Object p2);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(id) id,source_type,source_id,BIN_TO_UUID(template_id) template_id,template_version,output_type,BIN_TO_UUID(device_id) device_id,copies,data_snapshot,state,attempt,result_json,BIN_TO_UUID(parent_job_id) parent_job_id,reprint_reason,created_at,updated_at FROM print_job WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> job(@Param("p0") Object p0,@Param("p1") Object p1);

 /** 固定打印读取；已验证租户及操作准入，结果保持原JSON投影和错误契约。 */
 @Select("SELECT BIN_TO_UUID(id) id,code,name,category,data_contract,scope_json,draft_json,draft_revision,published_version,enabled,created_at,updated_at FROM print_template WHERE tenant_id=UUID_TO_BIN(#{p0}) AND id=UUID_TO_BIN(#{p1})")
 List<Map<String,Object>> permissionTemplate(@Param("p0") Object p0,@Param("p1") Object p1);
}
