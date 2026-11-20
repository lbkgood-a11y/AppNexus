package com.appnexus.printing.application;
import java.util.*;
/** 打印场景、本人单据、契约及代理队列固定仓储输出端口；保持可信租户与完整事务，不开放SQL。 */
public interface PrintSceneRepository {
 /** scenes固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> scenes(String tenant);
 /** lockScene固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param scene 同租户启用场景UUID，不允许任意模板调用
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> lockScene(String tenant,String scene);
 /** sceneTemplate固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，需原发布版本及数据契约校验
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> sceneTemplate(String tenant,String template);
 /** createScene固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 当前操作固定资源UUID，归属与状态已由用例复核
  * @param code 稳定业务编码，1至64字符，保存后不可修改
  * @param name 显示名称，去首尾空白后1至128字符
  * @param contract 已登记固定数据契约编码，与发布版本完全匹配
  * @param template 当前租户模板UUID，需原发布版本及数据契约校验
  * @param copies 份数，1至99；PDF实际任务保持原1份约定
  * @param enabled 显式启停状态，不删除历史或改变已创建任务
  * @param actor 认证链建立的真实账号或受控机密客户端身份，不能客户端自报
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 int createScene(String tenant,String id,String code,String name,String contract,String template,int copies,boolean enabled,String actor);
 /** updateScene固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param code 稳定业务编码，1至64字符，保存后不可修改
  * @param name 显示名称，去首尾空白后1至128字符
  * @param contract 已登记固定数据契约编码，与发布版本完全匹配
  * @param template 当前租户模板UUID，需原发布版本及数据契约校验
  * @param copies 份数，1至99；PDF实际任务保持原1份约定
  * @param enabled 显式启停状态，不删除历史或改变已创建任务
  * @param actor 认证链建立的真实账号或受控机密客户端身份，不能客户端自报
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 当前操作固定资源UUID，归属与状态已由用例复核
  * @param revision 非负乐观锁修订号；冲突不得覆盖
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 int updateScene(String code,String name,String contract,String template,int copies,boolean enabled,String actor,String tenant,String id,long revision);
 /** ownDocuments固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param actor 认证链建立的真实账号或受控机密客户端身份，不能客户端自报
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> ownDocuments(String tenant,String actor);
 /** createDocument固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 当前操作固定资源UUID，归属与状态已由用例复核
  * @param scene 同租户启用场景UUID，不允许任意模板调用
  * @param number 本人单据编号，1至128字符，与正文documentNumber一致
  * @param content 原固定JSON编码，已经过字段白名单、深度、长度及可执行键限制
  * @param actor 认证链建立的真实账号或受控机密客户端身份，不能客户端自报
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 int createDocument(String tenant,String id,String scene,String number,String content,String actor);
 /** ownDocument固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param id 当前操作固定资源UUID，归属与状态已由用例复核
  * @param actor 认证链建立的真实账号或受控机密客户端身份，不能客户端自报
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> ownDocument(String tenant,String id,String actor);
 /** publishedTemplate固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，需原发布版本及数据契约校验
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> publishedTemplate(String tenant,String template);
 /** snapshotTemplate固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param template 当前租户模板UUID，需原发布版本及数据契约校验
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> snapshotTemplate(String tenant,String template);
 /** contractDefinitions固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<String> contractDefinitions(String tenant);
 /** createContract固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param code 稳定业务编码，1至64字符，保存后不可修改
  * @param content 原固定JSON编码，已经过字段白名单、深度、长度及可执行键限制
  * @param actor 认证链建立的真实账号或受控机密客户端身份，不能客户端自报
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 int createContract(String tenant,String code,String content,String actor);
 /** deleteAgentQueues固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param site 已验证代理站点编码，1至64字符
  * @param agent 当前认证代理实例标识，1至128字符，不能替换其他实例
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 int deleteAgentQueues(String tenant,String site,String agent);
 /** reportAgentQueue固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param site 已验证代理站点编码，1至64字符
  * @param agent 当前认证代理实例标识，1至128字符，不能替换其他实例
  * @param queue 队列名称，1至255字符，无控制字符；只更新自身代理范围
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 int reportAgentQueue(String tenant,String site,String agent,String queue);
 /** queues固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> queues(String tenant);
 /** enabledScene固定存取；请求、任务、确认及审计参与完整用例事务，不独立提交。
  * @param tenant 可信固定租户UUID文本，必填，不接受请求体指定
  * @param scene 同租户启用场景UUID，不允许任意模板调用
  * @return 原固定投影、状态或影响行数；空结果不扩大到其他租户或主体
  */
 List<Map<String,Object>> enabledScene(String tenant,String scene);
}
