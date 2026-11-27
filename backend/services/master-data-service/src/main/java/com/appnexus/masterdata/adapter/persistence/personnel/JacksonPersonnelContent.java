package com.appnexus.masterdata.adapter.persistence.personnel;
import com.appnexus.masterdata.organization.port.personnel.PersonnelContent;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
/** 人员明细固定Map存储编码；不启用多态类型解析，不接收客户端目标类型。 */
@Component
public final class JacksonPersonnelContent implements PersonnelContent {
 private final JsonMapper json=JsonMapper.builder().build();
 public String encode(Map<String,String> values){return json.writeValueAsString(values);}
 @SuppressWarnings("unchecked") public Map<String,String> decode(String content){return (Map<String,String>)json.readValue(content,Map.class);}
}
