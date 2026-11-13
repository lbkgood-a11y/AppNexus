package com.appnexus.contracts.document;

import java.util.Map;
import static com.appnexus.contracts.document.Documents.*;

/** 业务侧绑定解析端口；仅受信业务代码调用，不暴露浏览器任意事实解析入口。 */
public interface DocumentBindings {
  /** 页面入口在业务执行前验证平台维护的显式关系；历史异步结果不调用此入口以免中断已运行实例。 */
  void verifyPageOperation(Context context,String typeCode,String actionCode,String operationKey);
  /** 可用性查询只用于界面，业务写入口必须再次 verify；仅把关系禁用转换为 false。 */
  default boolean pageOperationAllowed(Context context,String typeCode,String actionCode,String operationKey){
    try{verifyPageOperation(context,typeCode,actionCode,operationKey);return true;}
    catch(Rejection e){if("DOCUMENT_PAGE_RELATION_DISABLED".equals(e.getMessage()))return false;throw e;}
  }
  /**
   * 按显式行为解析策略；实现须按类型及行为隔离，未知行为失败关闭，不按名称猜测。
   * @param context 认证链建立的租户、操作者与稳定 UUID 请求键，禁止从页面自由传入
   * @param typeCode 业务构建登记的单据类型，必填
   * @param actionCode 业务声明的稳定行为标识，例如 submit、confirm、cancel
   * @param objectId 已保存单据 UUID，不能使用尚未落库的临时标识
   * @param basisVersion 业务依据版本，非负；变更后旧解析不能用于新版本
   * @param facts 业务服务授权重读的非敏感标量，必须符合声明的条件字段，禁止完整业务原文
   * @throws Rejection 未授权、未配置、匹配冲突或同幂等键内容变化时拒绝
   * @return BUSINESS、WORKFLOW 或 PRINT 的固定结果；直接提交没有流程目标
   */
  Resolution resolveAction(Context context,String typeCode,String actionCode,String objectId,long basisVersion,Map<String,String> facts);
  /** 精确启动时复核解析记录与业务重读依据，禁止凭客户端流程标识绕过绑定。 */
  Resolution verify(Context context, String resolutionId, String typeCode, String objectId,
      long basisVersion, Map<String,String> facts);
  /** 启动审批前核对当前行为的用途及业务入口，防止跨行为复用同一解析引用。 */
  Resolution verifyApproval(Context context,String resolutionId,String typeCode,String objectId,
      long basisVersion,Map<String,String> facts,String purpose,String entry);
}
