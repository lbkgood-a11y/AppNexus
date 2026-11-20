package com.appnexus.contracts.businessobject;

import java.util.Map;

/** 对象操作的公共执行入口；不接受客户端提供业务事实或指定流程、模板及解析引用。 */
public interface BusinessObjectExecution {
  /**
   * 从唯一契约读取记录、解析关联配置并执行能力；失败必须回滚解析及本地能力写入。
   * @param context 认证链建立的可信身份和稳定请求键
   * @param request 对象、操作及读取版本，不包含业务数据
   * @return 实际采用的固定关联和能力输出
   */
  Result execute(BusinessObjects.Context context, Request request);

  /**
   * 已声明对象操作；打印使用 PREVIEW，设备及确认由输出任务管理。
   * @param objectType 唯一对象类型
   * @param objectId 已持久化记录标识
   * @param operationCode 对象操作编码
   * @param operationId 真实页面入口或已声明别名
   * @param basisVersion 非负依据版本，陈旧版本拒绝执行
   */
  record Request(String objectType,String objectId,String operationCode,String operationId,long basisVersion) {}
  /** 关联结果及专业能力返回值；不把解析标识作为授权凭证。 */
  record Result(BusinessObjects.Resolution resolution,Map<String,Object> output) {
    /** 专业能力的可选状态字段允许null，不能因打印尚无设备或回执而回滚已创建任务。 */
    public Result {output=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(output));}
  }
}
