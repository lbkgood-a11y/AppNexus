package com.appnexus.platform.configuration.port;
/** 机密客户端当前资格输出端口，由平台安全适配器连接权威客户端策略。 */
public interface PartnerClientAuthority {
 /** @param client 已验证客户端标识 @return 当前权威策略启用时为true，缺失不得放行 */
 boolean enabled(String client);
}
