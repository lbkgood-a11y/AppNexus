package com.appnexus.platform.businessobject.port;

import com.appnexus.contracts.businessobject.BusinessObjects.Context;

/** 编码用例所需的对象准入端口；复用既有对象有效配置和页面绑定，禁止绕过宿主授权。 */
public interface CodingObjectAccess {
  /** 检查当前租户是否允许管理对象配置；失败抛出原业务拒绝，不返回默认可用配置。 */
  void requireConfiguration(Context context, String objectType);

  /**
   * 校验目标对象页面操作的真实准入；调用方已经检查功能权限，仍不能跳过对象开关。
   * @param context 可信租户和账号上下文
   * @param objectType 构建内唯一对象类型
   * @param operationCode 由字段声明引用的对象内操作编码
   * @param operationId 同一对象声明派生的全局操作标识
   */
  void requireOperation(Context context, String objectType, String operationCode, String operationId);
}
