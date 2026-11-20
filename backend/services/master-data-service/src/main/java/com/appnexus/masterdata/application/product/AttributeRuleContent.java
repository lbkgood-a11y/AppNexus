package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.domain.product.AttributeRulePolicy;
/** 属性规则内容解析输出端口，只允许固定JSON技术适配器实现，不接受目标类型或表达式。 */
public interface AttributeRuleContent {
 /** @param content 原固定规则对象JSON，null表示空对象；未知键和标量类型不得在解析阶段丢弃 @return 完整中立规则值，领域负责业务边界校验 */
 AttributeRulePolicy.Rules decode(String content);
}
