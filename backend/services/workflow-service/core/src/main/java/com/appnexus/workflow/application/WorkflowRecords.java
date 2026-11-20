package com.appnexus.workflow.application;
import java.util.*;
import java.time.Instant;
/** 工作流固定存储输出端口，只允许本服务持久化适配器实现，不接受SQL、自由表名或客户端身份。 */
public interface WorkflowRecords {
 /** publishedDefinitions固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> publishedDefinitions(String tenant);
 /** openActorTask固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> openActorTask(String tenant,String task,String actor);
 /** definitions固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> definitions(String tenant);
 /** createDefinition固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param id 本操作固定资源标识，由服务端生成或校验，不作为授权凭据
  * @param model 原受控模型JSON，已验证大小、节点及固定能力，不执行脚本
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int createDefinition(String tenant,String id,String model);
 /** updateDraft固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param model 原受控模型JSON，已验证大小、节点及固定能力，不执行脚本
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param id 本操作固定资源标识，由服务端生成或校验，不作为授权凭据
  * @param revision 原非负草稿CAS版本，冲突不得覆盖
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int updateDraft(String model,String tenant,String id,long revision);
 /** createPublishedVersion固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param definition 当前租户固定流程定义标识
  * @param version 不可变发布版本，正整数；候选查询null表示原发布指针
  * @param model 原受控模型JSON，已验证大小、节点及固定能力，不执行脚本
  * @param bpmn 由固定编译器生成的BPMN文本，不接受外部任意部署
  * @param engineDefinition 受控引擎部署返回的固定定义标识
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param schema 原发布时冻结的数据契约JSON，历史缺省保持null
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int createPublishedVersion(String tenant,String definition,int version,String model,String bpmn,String engineDefinition,String actor,String schema);
 /** activateVersion固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param version 不可变发布版本，正整数；候选查询null表示原发布指针
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param id 本操作固定资源标识，由服务端生成或校验，不作为授权凭据
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int activateVersion(int version,String tenant,String id);
 /** disableDefinition固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param id 本操作固定资源标识，由服务端生成或校验，不作为授权凭据
  * @param revision 原非负草稿CAS版本，冲突不得覆盖
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int disableDefinition(String tenant,String id,long revision);
 /** definitionVersions固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param definition 当前租户固定流程定义标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> definitionVersions(String tenant,String definition);
 /** createInstance固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param id 本操作固定资源标识，由服务端生成或校验，不作为授权凭据
  * @param definition 当前租户固定流程定义标识
  * @param version 不可变发布版本，正整数；候选查询null表示原发布指针
  * @param objectType 代码唯一声明对象类型，不是表名或Java类名
  * @param objectId 原已授权业务记录标识
  * @param objectVersion 原非负业务依据版本，冻结后重验
  * @param purpose 已声明的流程用途，不可借用其他操作
  * @param organization 原可信业务组织，不替代记录归属与权限验证
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param digest 服务端按原固定序列化规则计算的指纹，不接受客户端摘要
  * @param snapshot 服务端重读并验证的数据契约事实JSON
  * @param schema 原发布时冻结的数据契约JSON，历史缺省保持null
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int createInstance(String tenant,String id,String definition,int version,String objectType,String objectId,long objectVersion,String purpose,String organization,String actor,String digest,String snapshot,String schema);
 /** copyActor固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int copyActor(String tenant,String instance,String actor);
 /** bindEngineInstance固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param engineInstance 原引擎启动返回的实例标识
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param id 本操作固定资源标识，由服务端生成或校验，不作为授权凭据
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int bindEngineInstance(String engineInstance,String tenant,String id);
 /** copiedInstances固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> copiedInstances(String tenant,String actor);
 /** participantInstances固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> participantInstances(String tenant,String actor);
 /** startedInstances固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> startedInstances(String tenant);
 /** instanceTasks固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> instanceTasks(String tenant,String instance);
 /** instanceAudit固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> instanceAudit(String tenant,String instance);
 /** instanceJobs固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> instanceJobs(String tenant,String instance);
 /** objectInstances固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param objectType 代码唯一声明对象类型，不是表名或Java类名
  * @param objectId 原已授权业务记录标识
  * @param purpose 已声明的流程用途，不可借用其他操作
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param operator 本次权威运维准入判定，不是客户端开关
  * @param participant 原参与者身份，用于固定查询分支
  * @param copiedActor 原抄送主体身份，用于固定查询分支
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> objectInstances(String tenant,String objectType,String objectId,String purpose,String actor,boolean operator,String participant,String copiedActor);
 /** saveTaskComment固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param comment 原任务意见，长度和控制字符由原业务规则约束
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int saveTaskComment(String comment,String tenant,String task);
 /** completeTask固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param decision 原固定节点动作，不能直接指定任意终态
  * @param comment 原任务意见，长度和控制字符由原业务规则约束
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int completeTask(String decision,String comment,String tenant,String task);
 /** cancelOpenJobs固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int cancelOpenJobs(String tenant,String instance);
 /** duplicateAssignmentCount固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param node 受控模型节点标识，非任意引擎表达式
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 Long duplicateAssignmentCount(String tenant,String instance,String node,String actor,String task);
 /** assignTask固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int assignTask(String actor,String tenant,String task);
 /** retryJob固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int retryJob(String tenant,String job);
 /** cancelInstanceJobs固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int cancelInstanceJobs(String tenant,String instance);
 /** createTask固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param id 本操作固定资源标识，由服务端生成或校验，不作为授权凭据
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param node 受控模型节点标识，非任意引擎表达式
  * @param name 受控模型节点名称，不含业务敏感原文
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param deadline 服务端计算的UTC到期Instant，不能客户端指定
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int createTask(String tenant,String id,String instance,String node,String name,String actor,Instant deadline);
 /** endInstance固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param result 原固定终态或已验证结果JSON，按本方法原职责使用
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int endInstance(String result,String tenant,String instance);
 /** createDeliveryJob固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param id 本操作固定资源标识，由服务端生成或校验，不作为授权凭据
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param node 受控模型节点标识，非任意引擎表达式
  * @param execution 固定引擎执行标识，来自监听器持久化记录
  * @param capability 代码注册能力标识，不接受任意处理类或URL
  * @param kind 原模型节点类型，受控枚举
  * @param payload 按原能力输入字段白名单编码的JSON
  * @param attempts 最大尝试次数，单位次，来自已校验模型重试约束
  * @param deadline 服务端计算的UTC到期Instant，不能客户端指定
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int createDeliveryJob(String tenant,String id,String instance,String node,String execution,String capability,String kind,String payload,int attempts,Instant deadline);
 /** lockDueJob固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> lockDueJob();
 /** markLeaseExhausted固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int markLeaseExhausted(String tenant,String job);
 /** claimJobLease固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param token 服务端生成的租约令牌，过期执行者不得覆盖新结果
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int claimJobLease(String token,String tenant,String job);
 /** awaitCallback固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param result 原固定终态或已验证结果JSON，按本方法原职责使用
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int awaitCallback(String result,String tenant,String job);
 /** scheduleRetry固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param state 原可靠任务状态，受原状态转换规则限制
  * @param error 安全稳定故障码，不含SQL或业务异常原文
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int scheduleRetry(String state,String error,String tenant,String job);
 /** completeDelivery固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param result 原固定终态或已验证结果JSON，按本方法原职责使用
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int completeDelivery(String result,String tenant,String job);
 /** auditExpiredAssignment固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int auditExpiredAssignment();
 /** timeoutJobs固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int timeoutJobs();
 /** tasksForMaintenance固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> tasksForMaintenance();
 /** markTaskChecked固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int markTaskChecked(String tenant,String task);
 /** unassignTask固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int unassignTask(String tenant,String task);
 /** openInstanceTasks固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> openInstanceTasks(String tenant,String instance);
 /** cancelTask固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int cancelTask(String tenant,String task);
 /** deliveryNode固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> deliveryNode(String tenant,String job);
 /** copyCount固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 Long copyCount(String tenant,String instance,String actor);
 /** participantTaskCount固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 Long participantTaskCount(String tenant,String instance,String actor);
 /** registerRequest固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param request 可信主体的稳定UUID幂等请求键
  * @param digest 服务端按原固定序列化规则计算的指纹，不接受客户端摘要
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int registerRequest(String tenant,String actor,String request,String digest);
 /** saveRequestResponse固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param response 原用例成功响应JSON，不保存失败为成功
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param request 可信主体的稳定UUID幂等请求键
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int saveRequestResponse(String response,String tenant,String actor,String request);
 /** auditAssignment固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param action 原固定审计动作
  * @param target 原审计目标标识，不含敏感业务正文
  * @param detail 已约束审计说明，不含凭据或原业务敏感值
  * @param previous 原任务分配账号，可空
  * @param next 目标有效账号，可空表示解除分配
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int auditAssignment(String tenant,String instance,String actor,String action,String target,String detail,String previous,String next);
 /** auditOperation固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param action 原固定审计动作
  * @param target 原审计目标标识，不含敏感业务正文
  * @param detail 已约束审计说明，不含凭据或原业务敏感值
  * @param person 经验证的被代表人员，可空，不能冒充实际账号
  * @param organization 原可信业务组织，不替代记录归属与权限验证
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 int auditOperation(String tenant,String instance,String actor,String action,String target,String detail,String person,String organization);
 /** unique0固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param definition 当前租户固定流程定义标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> lockDefinition(String tenant,String definition);
 /** unique1固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> taskRecord(String tenant,String task);
 /** unique2固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> taskForCompletion(String tenant,String task);
 /** unique3固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> lockTaskForCompletion(String tenant,String task);
 /** unique4固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> taskForReassignment(String tenant,String task);
 /** unique5固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> lockTaskForReassignment(String tenant,String task);
 /** unique6固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> lockRetryJob(String tenant,String job);
 /** unique7固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param definition 当前租户固定流程定义标识
  * @param version 不可变发布版本，正整数；候选查询null表示原发布指针
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> frozenDefinition(String tenant,String definition,int version);
 /** unique8固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> lockDispatchedJob(String tenant,String job);
 /** unique9固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param job 当前租户原可靠交付任务标识
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> lockCallbackJob(String tenant,String job);
 /** unique10固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> taskNodeForAction(String tenant,String task);
 /** unique11固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param task 当前租户原任务标识，不允许按裸ID扩大权限
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> taskNodeForCallback(String tenant,String task);
 /** unique12固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param instance 当前租户流程实例标识，必须经参与者或管理权限复核
  * @param lock 写入前是否在当前完整事务加锁，不能由客户端解除
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> instanceRecord(String tenant,String instance,boolean lock);
 /** unique13固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param request 可信主体的稳定UUID幂等请求键
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> lockRequest(String tenant,String actor,String request);
 /** publishedCandidates固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param version 不可变发布版本，正整数；候选查询null表示原发布指针
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> publishedCandidates(String tenant,Integer version);
 /** actorTasks固定存取；引擎推进、请求、任务及审计参与原完整事务，不独立提交。
  * @param tenant 可信租户标识，业务请求由安全链建立；后台交付来自已锁定持久化任务
  * @param actor 真实可信执行账号，不允许前端替换；系统无主体场景沿用原空值语义
  * @param todo true仅OPEN待办，false仅原已结束任务
  * @return 原固定投影或受影响行数；空结果不跨租户或主体回退
  */
 List<Map<String,Object>> actorTasks(String tenant,String actor,boolean todo);
}
