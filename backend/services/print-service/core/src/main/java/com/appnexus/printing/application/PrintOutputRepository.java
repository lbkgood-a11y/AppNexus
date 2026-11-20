package com.appnexus.printing.application;
import java.util.*;
/** 打印输出固定仓储端口；租户请求串行锁、任务状态和确认CAS保持原事务，不开放SQL。 */
public interface PrintOutputRepository {
 /** businessScene固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param sceneCode 已登记场景业务编码，1至64字符
  * @param contract 已登记固定数据契约编码，与发布版本完全匹配
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> businessScene(String tenant,String sceneCode,String contract);
 /** ownDocument固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param document 当前主体本人已保存单据UUID，不能越权引用
  * @param actor 认证链建立的真实账号或受控机密客户端身份，不能客户端自报
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> ownDocument(String tenant,String document,String actor);
 /** sceneReady固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param scene 同租户启用场景UUID，不允许任意模板调用
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 Boolean sceneReady(String tenant,String scene);
 /** rendererReady固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 Boolean rendererReady(String tenant);
 /** enabledDevice固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param device 同租户启用设备UUID；PDF/预览按原契约可null
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> enabledDevice(String tenant,String device);
 /** deviceQueueOnline固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param site 已验证代理站点编码，1至64字符
  * @param queue 队列名称，1至255字符，无控制字符；只更新自身代理范围
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 Boolean deviceQueueOnline(String tenant,String site,String queue);
 /** externalScene固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param sceneCode 已登记场景业务编码，1至64字符
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> externalScene(String tenant,String sceneCode);
 /** lockTenantOutputs固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> lockTenantOutputs(String tenant);
 /** existingRequest固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param owner 原请求所有者身份，人员和机密客户端分别保持原标识，不跨主体访问
  * @param request 规范UUID幂等请求键，作用域为原租户和主体
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> existingRequest(String tenant,String owner,String request);
 /** createOutput固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 当前操作固定资源UUID，归属与状态已由用例复核
  * @param owner 原请求所有者身份，人员和机密客户端分别保持原标识，不跨主体访问
  * @param request 规范UUID幂等请求键，作用域为原租户和主体
  * @param digest 原JSON配置生成的SHA-256摘要文本，不能客户端指定
  * @param mode PREVIEW或SILENT，预览确认与静默执行遵守原状态约束
  * @param job 同租户原统一任务UUID，不接受其他主体任意任务
  * @param device 同租户启用设备UUID；PDF/预览按原契约可null
  * @param copies 份数，1至99；PDF实际任务保持原1份约定
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 int createOutput(String tenant,String id,String owner,String request,String digest,String mode,String job,String device,int copies);
 /** outputStatus固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 当前操作固定资源UUID，归属与状态已由用例复核
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> outputStatus(String tenant,String id);
 /** rendererOnline固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 Boolean rendererOnline(String tenant);
 /** confirmedJob固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param job 同租户原统一任务UUID，不接受其他主体任意任务
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 Map<String,Object> confirmedJob(String tenant,String job);
 /** lockOutput固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 当前操作固定资源UUID，归属与状态已由用例复核
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> lockOutput(String tenant,String id);
 /** confirmOutput固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param job 同租户原统一任务UUID，不接受其他主体任意任务
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 当前操作固定资源UUID，归属与状态已由用例复核
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 int confirmOutput(String job,String tenant,String id);
}
