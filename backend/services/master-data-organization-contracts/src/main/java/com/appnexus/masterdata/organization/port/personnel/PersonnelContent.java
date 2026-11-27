package com.appnexus.masterdata.organization.port.personnel;
import java.util.Map;
/** 固定人员明细的存储内容编解码端口，禁止任意类型、脚本或多态反序列化。 */
public interface PersonnelContent {
 /** @param values 已通过分类白名单校验的文本字段 @return 固定存储内容 */
 String encode(Map<String,String> values);
 /** @param content 可信数据库原明细内容 @return 固定文本字段，不执行表达式 */
 Map<String,String> decode(String content);
}
