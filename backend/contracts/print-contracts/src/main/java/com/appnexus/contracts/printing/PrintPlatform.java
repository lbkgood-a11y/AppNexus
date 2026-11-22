package com.appnexus.contracts.printing;

/** 平台权限桥接；打印服务每次操作复核有效成员与显式权限，模板或设备归属不能扩大权限。 */
public interface PrintPlatform {
  /** 已认证代理的固定租户及服务身份，只含公开元数据，不能由客户端自报覆盖。 */
  default java.util.Map<String,String> agentIdentity(PrintContext context){throw new SecurityException("PRINT_AGENT_IDENTITY_UNAVAILABLE");}
  /** @param context 可信代理上下文 @param site 已校验站点 @param agent 已校验上报码 */
  default void authorizeAgentQueues(PrintContext context,String site,String agent){
    throw new PrintRejection("PRINT_ACCESS_DENIED",403);
  }
  /**
   * 为当前事务签发固定目标的打印写入许可。
   * @param context 可信租户及实际操作主体
   * @param permission 用例声明的原操作权限
   * @param targets 当前 SQL 依次绑定的任务键及适用的认证主体
   */
  default void authorizeRecordWrite(PrintContext context,String permission,String... targets){
    throw new PrintRejection("PRINT_ACCESS_DENIED",403);
  }
  /**
   * 复核当前用户权限。
   * @param context 宿主认证链建立的当前租户和实际账号
   * @param permission 打印权限码，必须来自服务端固定操作映射
   * @return 有效成员且明确授权时返回 true，未知权限失败关闭
   */
  boolean allowed(PrintContext context, String permission);
}
