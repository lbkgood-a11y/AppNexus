package com.appnexus.platform.configuration;
import java.util.*;
/** 机密客户端公开字典查询；调用方必须先由统一安全链验证 scope 和固定租户。 */
public interface PartnerDictionaryQueries {
 /** 对外字典项，不包含内部主键或审计身份。 */
 record Item(String code,String value,String name) {}
 /** 标准分页结果。 */
 record Page(List<Item> items,long total,int page,int pageSize) {}
 /** @param tenant 安全链确定的固定租户 @param code 标准字典编码 @param page 1–100000 @param size 1–100 @return 启用字典项 */
 Page items(String tenant,String code,int page,int size);
 /** 无框架装配入口。 @param records 固定存储输出端口 @return 公开查询用例 */
 static PartnerDictionaryQueries create(com.appnexus.platform.configuration.port.PartnerDictionaryRecords records){return new com.appnexus.platform.configuration.internal.application.dictionary.PartnerDictionaryQueryService(records);}
}
