package com.appnexus.contracts.businessobject;

/** 宿主授权桥接；管理权限不授予业务原文访问权。 */
public interface BusinessObjectPlatform {
  /** 当前账号是否为有效平台管理员。 */
  boolean platformAdministrator(String actor);
  /** 当前租户有效成员是否拥有既有租户管理能力。 */
  boolean tenantAdministrator(BusinessObjects.Context context);
  /** 解析时复核有效成员，不能因无配置权拒绝普通业务用户。 */
  boolean active(BusinessObjects.Context context);
  /** 发号复核当前有效成员的对象操作权限；宿主未接通时默认拒绝，不借用配置管理权。 */
  default boolean codingAllowed(BusinessObjects.Context context,String operationId,String permissionCode){return false;}
  /** 平台授权对象必须是实际存在的租户，不能保存悬空 UUID。 */
  boolean tenantExists(String tenant);
}
