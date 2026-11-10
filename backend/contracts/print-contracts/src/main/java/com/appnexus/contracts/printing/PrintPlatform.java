package com.appnexus.contracts.printing;

/** 平台权限桥接；打印服务每次操作复核有效成员与显式权限，模板或设备归属不能扩大权限。 */
public interface PrintPlatform {
  /**
   * 复核当前用户权限。
   * @param context 宿主认证链建立的当前租户和实际账号
   * @param permission 打印权限码，必须来自服务端固定操作映射
   * @return 有效成员且明确授权时返回 true，未知权限失败关闭
   */
  boolean allowed(PrintContext context, String permission);
}
