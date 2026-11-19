package com.appnexus.platform.businessobject.port;

import java.util.List;
import java.util.Map;

/**
 * 对象配置输出端口；仅暴露固定业务记录操作，不接受 SQL、表名或跨模块数据库访问。
 * 租户与账号由应用授权后传入；所有写入及锁必须参与同一用例事务。
 * JSON 字符串是原版本化快照，编解码由独立端口完成，不接受客户端任意存储内容。
 */
public interface ConfigurationStore {
  /**
   * 读取对象类型启用状态，未登记返回 false。
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  Boolean typeEnabled(String type);
  /**
   * 读取租户已发布配置，不加锁且不创建记录。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> publishedConfiguration(String tenant, String type);
  /**
   * 读取平台对象启用状态及并发版本。
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> typeState(String type);
  /**
   * 读取平台入口管控并发版本。
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> pageRelationshipState(String type);
  /**
   * 首次创建入口覆盖，冲突返回零行。
   * @param type 已登记业务对象编码
   * @param relationships 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param disabled 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int createPageRelationships(String type, String relationships, String disabled, String actor);
  /**
   * 按版本更新入口覆盖，创建信息保持不变。
   * @param relationships 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param disabled 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @param type 已登记业务对象编码
   * @param revision 读取的并发版本，非负；冲突不得覆盖
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int updatePageRelationships(String relationships, String disabled, String actor, String type, long revision);
  /**
   * 追加入口启停原因，与覆盖更新同事务。
   * @param type 已登记业务对象编码
   * @param operation 已登记操作标识或编码，由对应业务入口确定
   * @param enabled 显式启停状态，不替代实际授权
   * @param reason 管控原因，必填且最多 200 字
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int appendEntryControl(String type, String operation, boolean enabled, String reason, String actor);
  /**
   * 读取平台入口禁用集合的持久化快照。
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> disabledOperations(String type);
  /**
   * 确保对象类型记录存在，不覆盖既有状态。
   * @param type 已登记业务对象编码
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int ensureType(String type);
  /**
   * 按版本更新启用状态，冲突返回零行。
   * @param enabled 显式启停状态，不替代实际授权
   * @param type 已登记业务对象编码
   * @param revision 读取的并发版本，非负；冲突不得覆盖
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int updateTypeState(boolean enabled, String type, long revision);
  /**
   * 读取对象已配置租户授权，不扩展租户资料权限。
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> tenantGrants(String type);
  /**
   * 更新指定租户授权，与审计同事务。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @param enabled 显式启停状态，不替代实际授权
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int saveTenantGrant(String tenant, String type, boolean enabled);
  /**
   * 读取租户草稿及发布版本，不隐式初始化。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> configuration(String tenant, String type);
  /**
   * 首次创建租户草稿，冲突返回零行。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @param draft 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int createDraft(String tenant, String type, String draft);
  /**
   * 按版本更新草稿，不发布。
   * @param draft 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @param revision 读取的并发版本，非负；冲突不得覆盖
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int updateDraft(String draft, String tenant, String type, long revision);
  /**
   * 排他锁定配置，必须在用例事务内调用。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> lockConfiguration(String tenant, String type);
  /**
   * 追加不可变发布版本，与发布指针同事务。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @param version 已发布配置版本，按原数据库数值类型传递
   * @param descriptorVersion 代码声明版本，正整数
   * @param bindings 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int appendConfigurationVersion(String tenant, String type, int version, int descriptorVersion, String bindings, String actor);
  /**
   * 推进发布指针，调用方须持有配置排他锁。
   * @param draft 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param published 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param version 已发布配置版本，按原数据库数值类型传递
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int publishConfiguration(String draft, String published, int version, String tenant, String type);
  /**
   * 按可信租户和账号读取幂等解析记录。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @param request 服务端验证的幂等请求 UUID
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> resolutionByRequest(String tenant, String actor, String request);
  /**
   * 共享锁固定发布快照，必须在解析事务内调用。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> sharePublishedConfiguration(String tenant, String type);
  /**
   * 读取已发布配置对应代码契约版本。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @param version 已发布配置版本，按原数据库数值类型传递
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  Integer descriptorVersion(String tenant, String type, Object version);
  /**
   * 插入幂等解析记录，唯一冲突保留原记录供复核。
   * @param id 服务端生成或已授权解析记录 UUID
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @param request 服务端验证的幂等请求 UUID
   * @param type 已登记业务对象编码
   * @param object 已授权业务对象 UUID
   * @param basis 业务对象版本，非负
   * @param version 已发布配置版本，按原数据库数值类型传递
   * @param definition 流程定义标识，直接业务或打印允许 null
   * @param definitionVersion 流程发布版本；非工作流服务时为 null
   * @param facts 应用生成的版本化 JSON 快照，不允许任意客户端透传
   * @param operation 已登记操作标识或编码，由对应业务入口确定
   * @param service 已登记能力类别 BUSINESS、WORKFLOW 或 PRINT
   * @param scene 打印场景编码，非打印允许 null
   * @param contract 打印数据契约编码，非打印允许 null
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int createResolution(String id, String tenant, String actor, String request, String type, String object, long basis, Object version, String definition, Integer definitionVersion, String facts, String operation, String service, String scene, String contract);
  /**
   * 重读幂等记录，缺失作为存储异常拒绝。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @param request 服务端验证的幂等请求 UUID
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  Map<String, Object> requiredResolutionByRequest(String tenant, String actor, String request);
  /**
   * 只读预检已发布配置及版本，不取得业务写锁。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> publishedConfigurationState(String tenant, String type);
  /**
   * 按可信租户与账号读取解析记录，避免跨主体访问。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @param id 服务端生成或已授权解析记录 UUID
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  List<Map<String, Object>> resolutionById(String tenant, String actor, String id);
  /**
   * 读取租户对象授权，不将类型启用状态混入配置维护权。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param type 已登记业务对象编码
   * @return 授权范围内的原始存储快照或标量；查询不产生写入副作用
   */
  Boolean tenantGranted(String tenant, String type);
  /**
   * 追加配置操作审计，与配置变更同事务。
   * @param tenant 已授权可信租户 UUID，不接受客户端覆盖
   * @param actor 可信实际账号 UUID，不能由请求正文指定
   * @param type 已登记业务对象编码
   * @param action 本次配置审计操作编码
   * @return 实际影响行数；零表示冲突或已存在，由用例判定
   */
  int appendAudit(String tenant, String actor, String type, String action);
}
