package com.appnexus.printing.adapter.persistence;
import java.util.*;
import com.appnexus.printing.application.PrintRuntimeRepository;
/** 固定运行SQL适配，驱动值与请求冲突异常在技术边界转换。 */
public final class MyBatisPrintRuntimeRepository implements PrintRuntimeRepository {
 private final PrintRuntimeMapper mapper;
 /** @param mapper 原固定打印运行SQL映射，与宿主事务共用连接 */
 public MyBatisPrintRuntimeRepository(PrintRuntimeMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public int createTemplate(String tenant,String id,String code,String name,String category,String contract,String scope,String template,String creator,String updater){return mapper.createTemplate(tenant,id,code,name,category,contract,scope,template,creator,updater);}
 /** {@inheritDoc} */
 public int updateTemplate(String code,String name,String category,String contract,String scope,String template,String actor,String tenant,String id,long revision){return mapper.updateTemplate(code,name,category,contract,scope,template,actor,tenant,id,revision);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockTemplate(String tenant,String id){return PrintStorageValues.stable(mapper.lockTemplate(tenant,id));}
 /** {@inheritDoc} */
 public int publishTemplate(int version,String actor,String schema,String tenant,String id){return mapper.publishTemplate(version,actor,schema,tenant,id);}
 /** {@inheritDoc} */
 public int freezeTemplateVersion(int version,String actor,String tenant,String id){return mapper.freezeTemplateVersion(version,actor,tenant,id);}
 /** {@inheritDoc} */
 public int restoreTemplate(int version,String actor,String tenant,String id,long revision){return mapper.restoreTemplate(version,actor,tenant,id,revision);}
 /** {@inheritDoc} */
 public int createDevice(String tenant,String id,String code,String name,String site,String queue,String capabilities,String actor){return mapper.createDevice(tenant,id,code,name,site,queue,capabilities,actor);}
 /** {@inheritDoc} */
 public int updateDevice(String code,String name,String site,String queue,String capabilities,boolean enabled,String tenant,String id){return mapper.updateDevice(code,name,site,queue,capabilities,enabled,tenant,id);}
 /** {@inheritDoc} */
 public Map<String,Object> publishedTemplate(String tenant,String id,int version){return PrintStorageValues.stable(mapper.publishedTemplate(tenant,id,version));}
 /** {@inheritDoc} */
 public int createJob(String tenant,String id,String request,String actor,String sourceType,String sourceId,String templateId,int version,String output,String device,int copies,String snapshot,String schema){try{return mapper.createJob(tenant,id,request,actor,sourceType,sourceId,templateId,version,output,device,copies,snapshot,schema);}catch(org.springframework.dao.DuplicateKeyException conflict){throw new DuplicateRequest(conflict);}}
 /** {@inheritDoc} */
 public List<Map<String,Object>> existingReprint(String tenant,String actor,String request){return PrintStorageValues.stable(mapper.existingReprint(tenant,actor,request));}
 /** {@inheritDoc} */
 public int copyReprint(String id,String request,String actor,String reason,String tenant,String sourceId){return mapper.copyReprint(id,request,actor,reason,tenant,sourceId);}
 /** {@inheritDoc} */
 public int expireLeases(String tenant){return mapper.expireLeases(tenant);}
 /** {@inheritDoc} */
 public List<String> claimableJobs(String tenant,String site){return PrintStorageValues.stable(mapper.claimableJobs(tenant,site));}
 /** {@inheritDoc} */
 public int claimJob(String agent,String tenant,String id){return mapper.claimJob(agent,tenant,id);}
 /** {@inheritDoc} */
 public int expireRendererLeases(String tenant,String site){return mapper.expireRendererLeases(tenant,site);}
 /** {@inheritDoc} */
 public int savePdf(String tenant,String id,byte[] pdf){return mapper.savePdf(tenant,id,pdf);}
 /** {@inheritDoc} */
 public int completePdf(String tenant,String id){return mapper.completePdf(tenant,id);}
 /** {@inheritDoc} */
 public Integer enabledDeviceCount(String tenant,String id){return mapper.enabledDeviceCount(tenant,id);}
 /** {@inheritDoc} */
 public int copyPreview(String id,String request,String actor,String device,int copies,String tenant,String sourceId){return mapper.copyPreview(id,request,actor,device,copies,tenant,sourceId);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> templates(String tenant){return PrintStorageValues.stable(mapper.templates(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> template(String tenant,String id){return PrintStorageValues.stable(mapper.template(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> versions(String tenant,String id){return PrintStorageValues.stable(mapper.versions(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> devices(String tenant){return PrintStorageValues.stable(mapper.devices(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> device(String tenant,String id){return PrintStorageValues.stable(mapper.device(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> jobs(String tenant){return PrintStorageValues.stable(mapper.jobs(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> enabledDevice(String tenant,String id){return PrintStorageValues.stable(mapper.enabledDevice(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> currentTemplate(String tenant,String id){return PrintStorageValues.stable(mapper.currentTemplate(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> claimedJob(String tenant,String id){return PrintStorageValues.stable(mapper.claimedJob(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> artifactJob(String tenant,String id){return PrintStorageValues.stable(mapper.artifactJob(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> pdfArtifact(String tenant,String id){return PrintStorageValues.stable(mapper.pdfArtifact(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> requestJob(String tenant,String actor,String request){return PrintStorageValues.stable(mapper.requestJob(tenant,actor,request));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> job(String tenant,String id){return PrintStorageValues.stable(mapper.job(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> permissionTemplate(String tenant,String id){return PrintStorageValues.stable(mapper.permissionTemplate(tenant,id));}
}
