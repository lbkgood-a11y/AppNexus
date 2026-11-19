package com.appnexus.platform.businessobject;

import com.appnexus.contracts.businessobject.*;
import static com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.workflow.*;
import java.util.*;

/** 对象配置公开输入端口；调用方必须提供可信上下文，执行器仍逐请求复核权限、状态与版本。 */
public interface BusinessObjectManagement extends BusinessObjectBindings, InteractionCatalog {
  /**
   * 投影唯一代码声明的交互目录，不创建任何业务任务。
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   */
  List<InteractionContract> contracts();
  /**
   * 在配置管理授权后读取当前操作兼容的打印场景，不授予业务原文读取权。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param code 对象内已登记操作编码
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  List<com.appnexus.contracts.printing.BusinessPrinting.Scene> scenes(Context c,String type,String code);
  /**
   * 只读诊断发布依赖，不创建解析、流程或打印任务。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  List<Check> readiness(Context c,String type);
  /**
   * 读取对象目录；平台管理与租户维护分别执行真实授权。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param global true 使用平台管理权限，false 使用租户维护权限
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  List<Map<String,Object>> catalog(Context c, boolean global);
  /**
   * 读取实际页面入口与管控版本，未保存覆盖时版本为 -1。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  Map<String,Object> pageRelationships(Context c,String type);
  /**
   * 原子保存入口完整覆盖与操作审计；仅供受控迁移及内部管控。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param revision 读取的并发版本；草稿或入口覆盖首次为 -1，已有记录回传原值
   * @param list 完整目标集合；绑定最多 50 条、每条条件最多 10 项，入口关系最多 100 项
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  void savePageRelationships(Context c,String type,long revision,List<PageRelationship> list);
  /**
   * 按版本原子启停新命令入口并追加原因审计，不改变历史实例。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param revision 读取的并发版本；草稿或入口覆盖首次为 -1，已有记录回传原值
   * @param operation 已登记页面操作标识，须与对象及操作匹配
   * @param enabled 是否允许新操作；不能替代授权及业务状态判断
   * @param reason 必填管控原因，1–200 字，不得含凭据或业务正文
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  void controlPage(Context c,String type,long revision,String operation,boolean enabled,String reason);
  /**
   * 执行前复核入口关系，撤销管控后旧按钮及旧配置不得绕过。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param code 对象内已登记操作编码
   * @param operation 已登记页面操作标识，须与对象及操作匹配
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  void verifyPageOperation(Context c,String type,String code,String operation);
  /**
   * 按版本启停平台对象类型，不自动授权任何租户。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param revision 读取的并发版本；草稿或入口覆盖首次为 -1，已有记录回传原值
   * @param enabled 是否允许新操作；不能替代授权及业务状态判断
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  void activate(Context c,String type,long revision,boolean enabled);
  /**
   * 读取对象已配置的租户授权，不扩大租户目录读取权限。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  List<Map<String,Object>> grants(Context c,String type);
  /**
   * 原子更新对象租户维护资格及审计，不删除历史发布和实例。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param tenant 经平台权限校验的目标租户 UUID
   * @param enabled 是否允许新操作；不能替代授权及业务状态判断
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  void grant(Context c,String type,String tenant,boolean enabled);
  /**
   * 授权后读取草稿与发布版本，首次未保存返回 revision=-1。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  Map<String,Object> configuration(Context c,String type);
  /**
   * 读取当前操作兼容的已发布工作流版本，未装配引擎返回空集合。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param operationCode 对象内已登记操作编码
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  List<WorkflowDefinitionCatalog.Option> options(Context c,String type,String operationCode);
  /**
   * 按并发版本原子保存草稿及审计，不推进发布指针。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param revision 读取的并发版本；草稿或入口覆盖首次为 -1，已有记录回传原值
   * @param list 完整目标集合；绑定最多 50 条、每条条件最多 10 项，入口关系最多 100 项
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  void save(Context c,String type,long revision,List<Binding> list);
  /**
   * 锁定草稿后原子发布不可变版本并追加审计，不改变运行中实例快照。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param revision 读取的并发版本；草稿或入口覆盖首次为 -1，已有记录回传原值
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  void publish(Context c,String type,long revision);
  /**
   * 在事务内固定发布快照并幂等解析唯一匹配策略，每次执行重新检查准入。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param operationCode 对象内已登记操作编码
   * @param object 业务服务授权读取的目标对象 UUID
   * @param basis 对象并发版本，非负，必须与已保存解析一致
   * @param facts 业务服务授权后重读的声明字段事实；每值最多 200 字，不接受任意客户端事实
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  Resolution resolveOperation(Context c,String type,String operationCode,String object,long basis,Map<String,String> facts);
  /**
   * 只读检查配置及目标能力可用性，不写解析或幂等回执，不保证后续执行成功。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param type 唯一代码声明的业务对象编码
   * @param code 对象内已登记操作编码
   * @param facts 业务服务授权后重读的声明字段事实；每值最多 200 字，不接受任意客户端事实
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  String configurationStatus(Context c,String type,String code,Map<String,String> facts);
  /**
   * 按可信租户及账号核验已解析结果，拒绝对象、版本和事实不一致。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param id 已解析结果 UUID，读取必须同时限定当前租户与账号
   * @param type 唯一代码声明的业务对象编码
   * @param object 业务服务授权读取的目标对象 UUID
   * @param basis 对象并发版本，非负，必须与已保存解析一致
   * @param facts 业务服务授权后重读的声明字段事实；每值最多 200 字，不接受任意客户端事实
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  Resolution verify(Context c,String id,String type,String object,long basis,Map<String,String> facts);
  /**
   * 复核已解析审批的目的、入口、对象和版本，拒绝非审批服务结果。
   * @param c 服务端建立的可信租户、账号及请求上下文，不接受正文覆盖
   * @param id 已解析结果 UUID，读取必须同时限定当前租户与账号
   * @param type 唯一代码声明的业务对象编码
   * @param object 业务服务授权读取的目标对象 UUID
   * @param basis 对象并发版本，非负，必须与已保存解析一致
   * @param facts 业务服务授权后重读的声明字段事实；每值最多 200 字，不接受任意客户端事实
   * @param purpose 声明的审批目的编码，不能由其他操作推断
   * @param entry 声明的审批入口，须与目的及当前操作一致
   * @return 授权范围内的业务结果；空集合与诊断阻断保持原接口语义，不伪造成功
   * @throws Rejection 主体、权限、版本、对象归属或业务条件不满足时拒绝
   */
  Resolution verifyApproval(Context c,String id,String type,String object,long basis,Map<String,String> facts,String purpose,String entry);
}
