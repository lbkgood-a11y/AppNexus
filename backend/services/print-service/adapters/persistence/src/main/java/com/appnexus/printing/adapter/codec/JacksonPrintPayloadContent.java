package com.appnexus.printing.adapter.codec;
import com.appnexus.printing.application.PrintPayloadContent;
import tools.jackson.databind.json.JsonMapper;
/** 原打印请求JSON编码配置，保留幂等摘要语义，不启用多态类型解析。 */
public final class JacksonPrintPayloadContent implements PrintPayloadContent {
 private final JsonMapper json=JsonMapper.builder().build();
 public String encode(Object payload){return json.writeValueAsString(payload);}
}
