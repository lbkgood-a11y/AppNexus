package com.appnexus.contracts.businessobject;

/** 宿主授权桥接；管理权限不授予业务原文访问权。 */
public interface BusinessObjectPlatform {
  /** 当前账号是否为有效平台管理员。 */
  boolean platformAdministrator(String actor);
  /** 当前租户有效成员是否拥有既有租户管理能力。 */
  boolean tenantAdministrator(BusinessObjects.Context context);
  /** 解析时复核有效成员，不能因无配置权拒绝普通业务用户。 */
  boolean active(BusinessObjects.Context context);
  /** 通用能力复核当前有效成员的对象操作权限；宿主未接通时默认拒绝，不借用配置管理权。 */
  default boolean operationAllowed(BusinessObjects.Context context,String operationId,String permissionCode){return false;}
  /**
   * 对真实对象快照执行统一行范围和字段保护，必须在专业能力及业务写入之前调用。
   * @param context 可信主体，禁止使用快照中的账号构造授权主体
   * @param descriptor 唯一对象声明及数据权限能力
   * @param operationCode 已完成功能准入的本对象操作
   * @param snapshot 服务端从本租户重读的记录版本、真实组织及字段
   * @return 已保护快照；没有真实实现必须失败关闭
   */
  default BusinessObjectRuntime.Snapshot authorizeRecord(BusinessObjects.Context context,BusinessObjects.Descriptor descriptor,String operationCode,BusinessObjectRuntime.Snapshot snapshot){
    throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_DATA_ACCESS_DENIED");
  }
  /** 平台授权对象必须是实际存在的租户，不能保存悬空 UUID。 */
  boolean tenantExists(String tenant);
}
