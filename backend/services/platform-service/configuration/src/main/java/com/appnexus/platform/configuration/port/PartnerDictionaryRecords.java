package com.appnexus.platform.configuration.port;
import java.util.*;
/** 固定字典读取输出端口，只允许平台持久化适配器实现。 */
public interface PartnerDictionaryRecords {
 /** @param tenant 可信固定租户 @param code 字典语义码 @param size 1–100 @param offset 从零开始偏移 @return 不含内部主键的存储投影 */
 List<Map<String,Object>> items(String tenant,String code,int size,long offset);
 /** @param tenant 同列表租户 @param code 同列表字典 @return 相同条件总数 */
 long count(String tenant,String code);
}
