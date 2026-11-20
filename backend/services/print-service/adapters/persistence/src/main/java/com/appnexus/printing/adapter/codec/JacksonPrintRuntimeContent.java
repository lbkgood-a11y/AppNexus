package com.appnexus.printing.adapter.codec;
import com.appnexus.printing.application.PrintRuntimeContent;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
/** 运行时固定JSON技术适配；保留原JsonMapper和PrintJson限制，不启用多态解析。 */
public final class JacksonPrintRuntimeContent implements PrintRuntimeContent {
 private final ObjectMapper mapper=JsonMapper.builder().build();private final PrintJson json=new PrintJson(mapper);
 public Object plain(Object value){return value==null?null:mapper.convertValue(value,Object.class);}
 public Object decode(String value){return mapper.readValue(value,Object.class);}
 public String encode(Object value){return mapper.writeValueAsString(value);}
 public String template(Object value){return json.template(value==null?null:mapper.valueToTree(value));}
 public String snapshot(Object value){return json.snapshot(value==null?null:mapper.valueToTree(value));}
 public String object(Object value){return json.object(value==null?null:mapper.valueToTree(value));}
}
