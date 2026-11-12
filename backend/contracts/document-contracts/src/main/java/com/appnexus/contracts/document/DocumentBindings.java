package com.appnexus.contracts.document;

import java.util.Map;
import static com.appnexus.contracts.document.Documents.*;

/** 业务侧绑定解析端口；仅受信业务代码调用，不暴露浏览器任意事实解析入口。 */
public interface DocumentBindings {
  /**
   * 解析唯一发布绑定；业务先授权并从自身存储取得依据，调用方负责业务事务。
   * @param context 已认证租户、操作者及稳定 UUID 请求键
   * @param typeCode 代码登记类型，必填
   * @param objectId 已保存业务单据 UUID，必填
   * @param basisVersion 冻结依据版本，非负整数
   * @param facts 声明的非敏感标量，禁止传完整业务对象
   * @return submit 的固定策略与配置版本；审批含流程目标，直接提交无流程，同键异内容拒绝
   */
  Resolution resolve(Context context, String typeCode, String objectId, long basisVersion, Map<String,String> facts);
  /**
   * 按显式行为解析提交策略；首期仅开放 submit，未知行为失败关闭，不按名称猜测。
   * @param actionCode 业务声明的稳定行为标识，首期必须为 submit
   * @return DIRECT 或 APPROVAL 的固定结果；直接提交没有流程目标
   */
  default Resolution resolveAction(Context context,String typeCode,String actionCode,String objectId,long basisVersion,Map<String,String> facts) {
    if(!"submit".equals(actionCode))throw new Rejection(400,"DOCUMENT_ACTION_INVALID");
    return resolve(context,typeCode,objectId,basisVersion,facts);
  }
  /** 精确启动时复核解析记录与业务重读依据，禁止凭客户端流程标识绕过绑定。 */
  Resolution verify(Context context, String resolutionId, String typeCode, String objectId,
      long basisVersion, Map<String,String> facts);
}
