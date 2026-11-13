package com.appnexus.contracts.document;
import static com.appnexus.contracts.document.Documents.*;

/** 业务接入的轻量可执行性组合，不拥有授权源、状态机或事务，页面别名使用同一行为定义。 */
public final class DocumentActionAccess {
  private DocumentActionAccess() {}
  /**
   * 合并业务服务已经验证的事实，拒绝优先级固定为权限、页面关系、业务状态。
   * @param operationPermission 当前主体的权威操作授权，不能来自浏览器
   * @param pageRelation 当前平台页面关联是否启用
   * @param businessState 当前对象状态及强制业务规则是否允许；未知应传 false
   */
  public static Availability evaluate(String action,String operation,boolean operationPermission,boolean pageRelation,boolean businessState){
    String reason=!operationPermission?"OPERATION_PERMISSION_MISSING":!pageRelation?"PAGE_RELATION_DISABLED":!businessState?"BUSINESS_STATE_DENIED":"READY";
    return new Availability(action,operation,reason.equals("READY"),reason);
  }
}
