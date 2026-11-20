package com.appnexus.printing.application;
import java.util.*;
/** 打印运行固定存取端口，只允许本模块持久化适配器实现；不决定授权、状态或模板安全规则。 */
public interface PrintRuntimeRepository {
 /** 原任务唯一请求冲突，不向应用暴露数据库异常。 */
 final class DuplicateRequest extends RuntimeException {
  /** @param cause 原唯一约束异常，仅供服务端追溯 */
  public DuplicateRequest(Throwable cause){super("PRINT_REQUEST_DUPLICATE",cause);}
 }
 /** createTemplate固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param name 去首尾空白的名称，必填，按原模板或设备输入长度限制
  * @param category 原模板分类标识，不接受自由执行脚本
  * @param contract 当前固定数据契约编码，须与场景及发布版本一致
  * @param scope 经原深度、大小及可执行键限制验证的固定范围JSON
  * @param template 经原模板安全限制验证的固定JSON，不允许URL或脚本
  * @param creator 可信原创建账号UUID，不覆盖已有创建信息
  * @param updater 本次可信实际账号UUID，与业务写入同事务
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int createTemplate(String tenant,String id,String code,String name,String category,String contract,String scope,String template,String creator,String updater);
 /** updateTemplate固定存取；版本、任务、租约及审计参与原完整事务。
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param name 去首尾空白的名称，必填，按原模板或设备输入长度限制
  * @param category 原模板分类标识，不接受自由执行脚本
  * @param contract 当前固定数据契约编码，须与场景及发布版本一致
  * @param scope 经原深度、大小及可执行键限制验证的固定范围JSON
  * @param template 经原模板安全限制验证的固定JSON，不允许URL或脚本
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param revision 非负草稿乐观锁版本，冲突不覆盖
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int updateTemplate(String code,String name,String category,String contract,String scope,String template,String actor,String tenant,String id,long revision);
 /** lockTemplate固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> lockTemplate(String tenant,String id);
 /** publishTemplate固定存取；版本、任务、租约及审计参与原完整事务。
  * @param version 固定发布版本，正整数；新任务不得自动改用新版本
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param schema 发布时冻结的契约JSON；原历史缺省可null
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int publishTemplate(int version,String actor,String schema,String tenant,String id);
 /** freezeTemplateVersion固定存取；版本、任务、租约及审计参与原完整事务。
  * @param version 固定发布版本，正整数；新任务不得自动改用新版本
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int freezeTemplateVersion(int version,String actor,String tenant,String id);
 /** restoreTemplate固定存取；版本、任务、租约及审计参与原完整事务。
  * @param version 固定发布版本，正整数；新任务不得自动改用新版本
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param revision 非负草稿乐观锁版本，冲突不覆盖
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int restoreTemplate(int version,String actor,String tenant,String id,long revision);
 /** createDevice固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param name 去首尾空白的名称，必填，按原模板或设备输入长度限制
  * @param site 当前认证代理站点编码，不跨站点领取任务
  * @param queue 原系统队列名称，禁止控制字符
  * @param capabilities 经原安全限制验证的设备能力JSON，不执行表达式
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int createDevice(String tenant,String id,String code,String name,String site,String queue,String capabilities,String actor);
 /** updateDevice固定存取；版本、任务、租约及审计参与原完整事务。
  * @param code 固定业务编码，1至64字符，创建后不可修改
  * @param name 去首尾空白的名称，必填，按原模板或设备输入长度限制
  * @param site 当前认证代理站点编码，不跨站点领取任务
  * @param queue 原系统队列名称，禁止控制字符
  * @param capabilities 经原安全限制验证的设备能力JSON，不执行表达式
  * @param enabled 显式设备启停状态，不删除历史任务
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int updateDevice(String code,String name,String site,String queue,String capabilities,boolean enabled,String tenant,String id);
 /** publishedTemplate固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param version 固定发布版本，正整数；新任务不得自动改用新版本
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 Map<String,Object> publishedTemplate(String tenant,String id,int version);
 /** createJob固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param request 原规范UUID请求键，保持原幂等作用域
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param sourceType 原固定业务来源类型，非空；不能作为表名或类名
  * @param sourceId 已授权原业务来源标识，非空，不扩大正文访问
  * @param templateId 当前租户已发布启用模板UUID
  * @param version 固定发布版本，正整数；新任务不得自动改用新版本
  * @param output PDF或PRINTER，PDF不绑定实体设备
  * @param device 当前租户启用设备UUID；PDF为null
  * @param copies 份数1至99；PDF保持原1份约定
  * @param snapshot 服务端验证并编码的原契约数据快照，不接受任意字段
  * @param schema 发布时冻结的契约JSON；原历史缺省可null
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int createJob(String tenant,String id,String request,String actor,String sourceType,String sourceId,String templateId,int version,String output,String device,int copies,String snapshot,String schema);
 /** existingReprint固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param request 原规范UUID请求键，保持原幂等作用域
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> existingReprint(String tenant,String actor,String request);
 /** copyReprint固定存取；版本、任务、租约及审计参与原完整事务。
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param request 原规范UUID请求键，保持原幂等作用域
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param reason 补打原因，去首尾空白后1至500字符
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param sourceId 已授权原业务来源标识，非空，不扩大正文访问
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int copyReprint(String id,String request,String actor,String reason,String tenant,String sourceId);
 /** expireLeases固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int expireLeases(String tenant);
 /** claimableJobs固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param site 当前认证代理站点编码，不跨站点领取任务
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<String> claimableJobs(String tenant,String site);
 /** claimJob固定存取；版本、任务、租约及审计参与原完整事务。
  * @param agent 当前认证领取代理标识，1至128字符
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int claimJob(String agent,String tenant,String id);
 /** expireRendererLeases固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param site 当前认证代理站点编码，不跨站点领取任务
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int expireRendererLeases(String tenant,String site);
 /** savePdf固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param pdf PDF字节，5字节至10MiB，以%PDF-开头，必须来自当前租约代理
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int savePdf(String tenant,String id,byte[] pdf);
 /** completePdf固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int completePdf(String tenant,String id);
 /** enabledDeviceCount固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 Integer enabledDeviceCount(String tenant,String id);
 /** copyPreview固定存取；版本、任务、租约及审计参与原完整事务。
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @param request 原规范UUID请求键，保持原幂等作用域
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param device 当前租户启用设备UUID；PDF为null
  * @param copies 份数1至99；PDF保持原1份约定
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param sourceId 已授权原业务来源标识，非空，不扩大正文访问
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 int copyPreview(String id,String request,String actor,String device,int copies,String tenant,String sourceId);
 /** templates固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> templates(String tenant);
 /** template固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> template(String tenant,String id);
 /** versions固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> versions(String tenant,String id);
 /** devices固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> devices(String tenant);
 /** device固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> device(String tenant,String id);
 /** jobs固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> jobs(String tenant);
 /** enabledDevice固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> enabledDevice(String tenant,String id);
 /** currentTemplate固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> currentTemplate(String tenant,String id);
 /** claimedJob固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> claimedJob(String tenant,String id);
 /** artifactJob固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> artifactJob(String tenant,String id);
 /** pdfArtifact固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> pdfArtifact(String tenant,String id);
 /** requestJob固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param actor 原可信审计主体键；机密客户端沿用原稳定UUID映射，不伪造人员账号
  * @param request 原规范UUID请求键，保持原幂等作用域
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> requestJob(String tenant,String actor,String request);
 /** job固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> job(String tenant,String id);
 /** permissionTemplate固定存取；版本、任务、租约及审计参与原完整事务。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 本操作当前租户资源UUID，必须已验证归属和操作资格
  * @return 原固定投影、事实或影响行数；空结果不跨主体或租户回退
  */
 List<Map<String,Object>> permissionTemplate(String tenant,String id);
}
