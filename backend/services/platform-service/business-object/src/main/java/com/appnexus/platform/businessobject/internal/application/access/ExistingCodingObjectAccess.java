package com.appnexus.platform.businessobject.internal.application.access;

import com.appnexus.platform.businessobject.port.CodingObjectAccess;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;

/** 同上下文对象准入协调；编码用例只依赖端口，继续执行原对象配置和页面操作门禁。 */
public final class ExistingCodingObjectAccess implements CodingObjectAccess {
  private final com.appnexus.platform.businessobject.BusinessObjectManagement objects;

  /** @param objects 当前宿主的真实对象执行器，不使用前端声明或缓存结果代替 */
  public ExistingCodingObjectAccess(com.appnexus.platform.businessobject.BusinessObjectManagement objects) {
    this.objects = objects;
  }

  @Override
  public void requireConfiguration(Context context, String objectType) {
    objects.configuration(context, objectType);
  }

  @Override
  public void requireOperation(Context context, String objectType, String operationCode, String operationId) {
    objects.verifyPageOperation(context, objectType, operationCode, operationId);
  }
}
