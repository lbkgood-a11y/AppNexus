package com.appnexus.printing.application;
import java.util.*;
/** 打印模板、设备、任务、租约和附件固定存取输出端口，不决定功能准入或业务状态，不开放SQL。 */
public interface PrintRuntimeRepository {
 /** 仅表示原任务唯一请求冲突，不暴露数据库异常给应用。 */
 final class DuplicateRequest extends RuntimeException {
  /** @param cause 原唯一约束冲突，仅供服务端追溯 */
  public DuplicateRequest(Throwable cause){super("PRINT_REQUEST_DUPLICATE",cause);}
 }

 
 
 int createTemplate( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9);

 
 
 int updateTemplate( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9);

 
 
 List<Map<String,Object>> lockTemplate( Object p0, Object p1);

 
 
 int publishTemplate( Object p0, Object p1, Object p2, Object p3, Object p4);

 
 
 int freezeTemplateVersion( Object p0, Object p1, Object p2, Object p3);

 
 
 int restoreTemplate( Object p0, Object p1, Object p2, Object p3, Object p4);

 
 
 int createDevice( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

 
 
 int updateDevice( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);

 
 
 Map<String,Object> publishedTemplate( Object p0, Object p1, Object p2);

 
 
 int createJob( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10, Object p11, Object p12);

 
 
 List<Map<String,Object>> existingReprint( Object p0, Object p1, Object p2);

 
 
 int copyReprint( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5);

 
 
 int expireLeases( Object p0);

 
 
 List<String> claimableJobs( Object p0, Object p1);

 
 
 int claimJob( Object p0, Object p1, Object p2);

 
 
 int expireRendererLeases( Object p0, Object p1);

 
 
 int savePdf( Object p0, Object p1, Object p2);

 
 
 int completePdf( Object p0, Object p1);

 
 
 Integer enabledDeviceCount( Object p0, Object p1);

 
 
 int copyPreview( Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);

 
 
 List<Map<String,Object>> templates( Object p0);

 
 
 List<Map<String,Object>> template( Object p0, Object p1);

 
 
 List<Map<String,Object>> versions( Object p0, Object p1);

 
 
 List<Map<String,Object>> devices( Object p0);

 
 
 List<Map<String,Object>> device( Object p0, Object p1);

 
 
 List<Map<String,Object>> jobs( Object p0);

 
 
 List<Map<String,Object>> enabledDevice( Object p0, Object p1);

 
 
 List<Map<String,Object>> currentTemplate( Object p0, Object p1);

 
 
 List<Map<String,Object>> claimedJob( Object p0, Object p1);

 
 
 List<Map<String,Object>> artifactJob( Object p0, Object p1);

 
 
 List<Map<String,Object>> pdfArtifact( Object p0, Object p1);

 
 
 List<Map<String,Object>> requestJob( Object p0, Object p1, Object p2);

 
 
 List<Map<String,Object>> job( Object p0, Object p1);

 
 
 List<Map<String,Object>> permissionTemplate( Object p0, Object p1);
}
