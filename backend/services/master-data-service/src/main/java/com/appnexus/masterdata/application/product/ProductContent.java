package com.appnexus.masterdata.application.product;
import java.util.Map;
/** 商品受控事实的JSON编码输出端口，不接受目标类型、SQL或表达式。 */
public interface ProductContent {
 /** @param value 已验证且按原规则排序的业务事实 @return 保持原序列化配置的JSON，用于快照或摘要；不得改变数值精度及键顺序 */
 String encode(Map<String,?> value);
 /** @param content 数据库中原已受控快照JSON @return JDK对象、集合及标量，不解析客户端Java类型 */
 Object decode(String content);
}
