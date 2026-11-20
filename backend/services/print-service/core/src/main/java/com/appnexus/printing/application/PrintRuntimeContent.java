package com.appnexus.printing.application;
/** 打印运行固定内容技术输出端口；领域校验只消费JDK JSON值，不依赖树模型。 */
public interface PrintRuntimeContent extends PrintSceneContent,PrintPayloadContent {
 /** @param value 原固定模板JSON事实 @return 经原大小、深度、可执行键及URL限制验证的模板编码，缺省语义保持原值 */
 String template(Object value);
}
