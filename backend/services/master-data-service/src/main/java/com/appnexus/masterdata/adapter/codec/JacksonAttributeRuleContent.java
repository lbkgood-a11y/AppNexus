package com.appnexus.masterdata.adapter.codec;
import com.appnexus.masterdata.application.product.AttributeRuleContent;
import com.appnexus.masterdata.domain.product.AttributeRulePolicy;
import com.appnexus.masterdata.api.masterdata.MasterData;
import java.util.*;
import tools.jackson.databind.*;
import org.springframework.stereotype.Component;
/** 属性规则固定JSON解析；保留全部键与原标量类型，不执行脚本或忽略未知键。 */
@Component
public final class JacksonAttributeRuleContent implements AttributeRuleContent {
 private final ObjectMapper json=new ObjectMapper();
 public AttributeRulePolicy.Rules decode(String content){try{JsonNode node=content==null?json.createObjectNode():json.readTree(content);if(!node.isObject())throw invalid();var values=new LinkedHashMap<String,AttributeRulePolicy.Scalar>();for(String key:node.propertyNames()){var v=node.get(key);values.put(key,new AttributeRulePolicy.Scalar(v.asText(),v.isString(),v.isNumber(),v.isIntegralNumber(),v.isIntegralNumber()?v.asInt():0));}return new AttributeRulePolicy.Rules(values);}catch(MasterData.Rejection rejected){throw rejected;}catch(Exception malformed){throw invalid();}}
 private static MasterData.Rejection invalid(){return new MasterData.Rejection(400,"PRODUCT_ATTRIBUTE_RULE_INVALID");}
}
