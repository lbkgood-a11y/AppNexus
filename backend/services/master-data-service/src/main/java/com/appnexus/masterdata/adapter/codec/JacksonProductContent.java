package com.appnexus.masterdata.adapter.codec;
import com.appnexus.masterdata.application.product.ProductContent;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
/** 商品事实固定JSON编码；沿用原ObjectMapper默认配置，不能静默更改幂等摘要。 */
@Component
public final class JacksonProductContent implements ProductContent {
 private final ObjectMapper json=new ObjectMapper();
 public String encode(Map<String,?> value){return json.writeValueAsString(value);}
 public Object decode(String content){return json.readValue(content,Object.class);}
}
