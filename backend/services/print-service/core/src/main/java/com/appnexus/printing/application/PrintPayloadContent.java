package com.appnexus.printing.application;
/** 打印固定请求摘要编码输出端口，禁止指定Java目标类型或执行表达式。 */
public interface PrintPayloadContent {
 /** @param payload 已绑定公开打印契约的请求事实 @return 沿用原JSON配置的编码，不能改变幂等摘要 */
 String encode(Object payload);
}
