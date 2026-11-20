package com.appnexus.printing.api;
import java.util.*;
/** 打印公开数据契约；JSON值以JDK对象、集合及标量传递，技术适配器负责树模型转换和原安全校验。 */
public final class PrintData {
 private PrintData(){}
/** 模板草稿；revision 为读取到的乐观锁版本，新建传 -1。 */
  public record TemplateDraft(String code,String name,String category,String dataContract,Object scope,Object template,long revision) {}
/** 设备草稿；enabled 缺省由调用方传 true，能力 JSON 不得包含凭据。 */
  public record DeviceDraft(String code,String name,String siteCode,String systemName,Object capabilities,boolean enabled) {}
/** 正式输出任务；data 必须是业务服务按 PRINT 用途授权后的快照。 */
  public record JobDraft(String sourceType,String sourceId,String templateId,String outputType,String deviceId,int copies,Object data) {}
/** 代理回执；结果只允许非敏感诊断码，不上传文档内容或系统日志。 */
  public record Receipt(String state,String agent,Object result) {}
/** 场景参数；revision 新建可传 -1，更新必须使用读取值，copies 默认由页面设 1。 */
  public record SceneRequest(String code,String name,String contract,String templateId,int copies,boolean enabled,long revision){}
/** 用户录入单据；data 仅允许已注册字段，number 是业务人员填写的显示编号。 */
  public record DocumentRequest(String sceneId,String number,Object data){}
/** 打印选项；设备为当前租户已授权设备，outputType 仅 PDF/PRINTER，copies 为 1—99。 */
  public record DocumentPrintRequest(String deviceId,String outputType,int copies){}
/** 认证代理上报；站点、实例稳定标识和实际系统队列名，不包含设备凭据。 */
  public record QueueReport(String site,String agent,List<String> queues){}
/** 分层预检事实；瞬时离线不等于没有权限，也不保证后续任务执行成功。 */
  public record TargetCheck(String layer,String status,String reason) {}
/** 外部参数；sourceId 为调用方单据号，data 必须符合已注册契约，mode 必填。 */
  public record ExternalOutputRequest(String sceneCode,String sourceId,Object data,String mode,String deviceId,int copies,String dataContract) {}
/** 人员入口不接受业务快照，mode 为 SILENT 或 PREVIEW。 */
  public record DocumentOutputRequest(String mode,String deviceId,int copies){}
}
