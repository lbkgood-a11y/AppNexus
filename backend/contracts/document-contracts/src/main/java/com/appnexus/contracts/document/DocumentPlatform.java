package com.appnexus.contracts.document;

/** 宿主授权桥接；管理权限不授予业务原文访问权。 */
public interface DocumentPlatform {
  /** 当前账号是否为有效平台管理员。 */
  boolean platformAdministrator(String actor);
  /** 当前租户有效成员是否拥有既有租户管理能力。 */
  boolean tenantAdministrator(Documents.Context context);
  /** 解析时复核有效成员，不能因无配置权拒绝普通业务用户。 */
  boolean active(Documents.Context context);
  /** 平台授权对象必须是实际存在的租户，不能保存悬空 UUID。 */
  boolean tenantExists(String tenant);
}
