package com.appnexus.platform.configuration.port;
/** 机密客户端当前资格输出端口，由平台安全适配器连接权威客户端策略。 */
public interface PartnerClientAuthority {
 /**
  * @param client 认证边界验证的客户端
  * @param tenant 原权威客户端锁校验的租户 UUID
  * @return 当前策略通过并已绑定事务许可时为真
  */
 boolean authorizeReference(String client,String tenant);
}
