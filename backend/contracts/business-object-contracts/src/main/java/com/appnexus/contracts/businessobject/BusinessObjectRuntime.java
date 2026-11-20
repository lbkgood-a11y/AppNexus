package com.appnexus.contracts.businessobject;

import java.util.Map;

/**
 * 业务对象统一运行端口。
 * <p>工作流、打印和编码驱动器只依赖此端口，不再为每个对象实现能力专用 Provider。
 * 实现必须在服务端重读记录、复核统一授权、租户归属和版本；调用方提供的 values 仅作为受控命令输入。
 */
public interface BusinessObjectRuntime {
  /**
   * 读取能力契约允许的字段投影。
   * @param context 可信租户、账号及请求幂等上下文
   * @param objectType 对象契约中的稳定对象编码
   * @param objectId 业务记录标识
   * @param operationCode 已声明且具备准入的操作编码
   * @param basisVersion 调用方所依据的版本；非负数，必须与当前记录一致
   * @param fields 由能力驱动器从契约派生的字段集合，不接受客户端扩展
   * @return 授权字段及当前版本；不返回未声明或受保护原值
   * @throws BusinessObjects.Rejection 记录不存在、无权、版本冲突或字段不允许时抛出
   */
  Snapshot read(BusinessObjects.Context context, String objectType, String objectId,
      String operationCode, long basisVersion, java.util.Set<String> fields);

  /**
   * 执行契约中已声明的业务操作。
   * @param context 可信租户、账号及请求幂等上下文
   * @param objectType 对象契约中的稳定对象编码
   * @param objectId 业务记录标识
   * @param operationCode 已声明操作；禁止传入类名、URL、SQL 或脚本
   * @param basisVersion 执行依据版本，必须在同一事务内锁定复核
   * @param values 仅允许契约声明的输入字段，禁止携带审批或打印事实冒充业务数据
   * @return 操作后的公共摘要及新版本
   */
  Result execute(BusinessObjects.Context context, String objectType, String objectId,
      String operationCode, long basisVersion, Map<String, ?> values);

  /**
   * 执行前的对象业务约束扩展；不用于另建授权或专业能力接线。
   * @param context 已完成统一准入和对象授权的可信身份
   * @param objectId 已锁定记录标识
   * @param operationCode 已声明操作
   * @param service 已由公共执行器解析且确认声明的专业能力
   */
  default void beforeCapability(BusinessObjects.Context context,String objectId,String operationCode,InteractionContract.Service service) {}

  /**
   * 读取冻结审批记录；仅由公共审批桥接在事务内调用，返回值不作为浏览器响应。
   * @param context 可信调用身份；公共桥接逐次核验申请人或有效任务
   * @param objectId 当前租户记录标识
   * @param lock 是否锁定记录；终态写入必须为 true
   * @return 真实业务关联及非敏感契约数据
   */
  default ApprovalRecord approvalRecord(BusinessObjects.Context context,String objectId,boolean lock){
    throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_APPROVAL_UNSUPPORTED");
  }

  /**
   * 保存已通过公共桥接验证的终态及交付回执，必须加入调用者事务。
   * @param context 可信租户、实际账号及稳定交付请求键
   * @param objectId 已锁定对象标识
   * @param basisVersion 已核验的非负依据版本
   * @param invocationId 工作流稳定交付标识，重复调用由公共桥接先判定
   * @param instanceId 与记录一致的本轮流程实例
   * @param result 契约明确允许的终态，不能由浏览器提交
   */
  default void completeApproval(BusinessObjects.Context context,String objectId,long basisVersion,String invocationId,String instanceId,String result){
    throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_APPROVAL_UNSUPPORTED");
  }

  /**
   * 冻结记录的公共关联，不包含业务原文。
   * @param version 当前非负依据版本
   * @param organizationId 记录真实归属组织，不能来自客户端声明
   * @param applicantId 原申请账号，不是被代表人员
   * @param state 对象当前状态，终态回放必须与交付结果一致
   * @param instanceId 本轮流程实例，未启动时为空
   * @param deliveryId 当前依据版本已应用的交付标识，无回执时为空
   * @param values 非敏感契约字段；桥接再次执行契约白名单投影
   */
  record ApprovalRecord(long version,String organizationId,String applicantId,String state,String instanceId,String deliveryId,Map<String,Object> values){
    public ApprovalRecord {values=Map.copyOf(values);}
  }

  /**
   * 受统一授权和字段保护后的不可变数据快照。
   * @param version 实际读取的非负依据版本
   * @param organizationId 真实记录归属，用于流程路由；无归属时为空且工作流驱动拒绝
   * @param values 授权后的对象字段，公共执行器按能力再次投影
   */
  record Snapshot(long version, String organizationId, Map<String, ?> values) {
    public Snapshot { values = Map.copyOf(values); }
  }

  /** 业务操作结果；能力驱动器只使用声明的版本和状态摘要。 */
  record Result(long version, Map<String, ?> values) {
    public Result { values = Map.copyOf(values); }
  }
}
