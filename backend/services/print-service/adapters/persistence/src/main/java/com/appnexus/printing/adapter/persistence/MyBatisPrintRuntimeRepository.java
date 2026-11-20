package com.appnexus.printing.adapter.persistence;
import java.util.*;
import com.appnexus.printing.application.PrintRuntimeRepository;
/** 打印运行固定SQL适配；驱动时间转换和唯一请求技术异常留在边界，业务状态由应用决定。 */
public final class MyBatisPrintRuntimeRepository implements PrintRuntimeRepository {
 private final PrintRuntimeMapper mapper;
 /** @param mapper 原固定打印运行SQL映射 */
 public MyBatisPrintRuntimeRepository(PrintRuntimeMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public int createTemplate( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9){return mapper.createTemplate(p0,p1,p2,p3,p4,p5,p6,p7,p8,p9);}
 /** {@inheritDoc} */
 public int updateTemplate( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9){return mapper.updateTemplate(p0,p1,p2,p3,p4,p5,p6,p7,p8,p9);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockTemplate( Object p0, Object p1){return PrintStorageValues.stable(mapper.lockTemplate(p0,p1));}
 /** {@inheritDoc} */
 public int publishTemplate( Object p0, Object p1, Object p2, Object p3, Object p4){return mapper.publishTemplate(p0,p1,p2,p3,p4);}
 /** {@inheritDoc} */
 public int freezeTemplateVersion( Object p0, Object p1, Object p2, Object p3){return mapper.freezeTemplateVersion(p0,p1,p2,p3);}
 /** {@inheritDoc} */
 public int restoreTemplate( Object p0, Object p1, Object p2, Object p3, Object p4){return mapper.restoreTemplate(p0,p1,p2,p3,p4);}
 /** {@inheritDoc} */
 public int createDevice( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7){return mapper.createDevice(p0,p1,p2,p3,p4,p5,p6,p7);}
 /** {@inheritDoc} */
 public int updateDevice( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7){return mapper.updateDevice(p0,p1,p2,p3,p4,p5,p6,p7);}
 /** {@inheritDoc} */
 public Map<String,Object> publishedTemplate( Object p0, Object p1, Object p2){return PrintStorageValues.stable(mapper.publishedTemplate(p0,p1,p2));}
 /** {@inheritDoc} */
 public int createJob( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10, Object p11, Object p12){try{return mapper.createJob(p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10,p11,p12);}catch(org.springframework.dao.DuplicateKeyException conflict){throw new DuplicateRequest(conflict);}}
 /** {@inheritDoc} */
 public List<Map<String,Object>> existingReprint( Object p0, Object p1, Object p2){return PrintStorageValues.stable(mapper.existingReprint(p0,p1,p2));}
 /** {@inheritDoc} */
 public int copyReprint( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5){return mapper.copyReprint(p0,p1,p2,p3,p4,p5);}
 /** {@inheritDoc} */
 public int expireLeases( Object p0){return mapper.expireLeases(p0);}
 /** {@inheritDoc} */
 public List<String> claimableJobs( Object p0, Object p1){return PrintStorageValues.stable(mapper.claimableJobs(p0,p1));}
 /** {@inheritDoc} */
 public int claimJob( Object p0, Object p1, Object p2){return mapper.claimJob(p0,p1,p2);}
 /** {@inheritDoc} */
 public int expireRendererLeases( Object p0, Object p1){return mapper.expireRendererLeases(p0,p1);}
 /** {@inheritDoc} */
 public int savePdf( Object p0, Object p1, Object p2){return mapper.savePdf(p0,p1,p2);}
 /** {@inheritDoc} */
 public int completePdf( Object p0, Object p1){return mapper.completePdf(p0,p1);}
 /** {@inheritDoc} */
 public Integer enabledDeviceCount( Object p0, Object p1){return mapper.enabledDeviceCount(p0,p1);}
 /** {@inheritDoc} */
 public int copyPreview( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6){return mapper.copyPreview(p0,p1,p2,p3,p4,p5,p6);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> templates( Object p0){return PrintStorageValues.stable(mapper.templates(p0));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> template( Object p0, Object p1){return PrintStorageValues.stable(mapper.template(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> versions( Object p0, Object p1){return PrintStorageValues.stable(mapper.versions(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> devices( Object p0){return PrintStorageValues.stable(mapper.devices(p0));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> device( Object p0, Object p1){return PrintStorageValues.stable(mapper.device(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> jobs( Object p0){return PrintStorageValues.stable(mapper.jobs(p0));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> enabledDevice( Object p0, Object p1){return PrintStorageValues.stable(mapper.enabledDevice(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> currentTemplate( Object p0, Object p1){return PrintStorageValues.stable(mapper.currentTemplate(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> claimedJob( Object p0, Object p1){return PrintStorageValues.stable(mapper.claimedJob(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> artifactJob( Object p0, Object p1){return PrintStorageValues.stable(mapper.artifactJob(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> pdfArtifact( Object p0, Object p1){return PrintStorageValues.stable(mapper.pdfArtifact(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> requestJob( Object p0, Object p1, Object p2){return PrintStorageValues.stable(mapper.requestJob(p0,p1,p2));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> job( Object p0, Object p1){return PrintStorageValues.stable(mapper.job(p0,p1));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> permissionTemplate( Object p0, Object p1){return PrintStorageValues.stable(mapper.permissionTemplate(p0,p1));}
}
