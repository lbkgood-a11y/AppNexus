package com.appnexus.platform.configuration.internal.application.dictionary;
import com.appnexus.platform.configuration.*;
import com.appnexus.platform.configuration.port.PartnerDictionaryRecords;
/** 对外字典分页用例，参数校验集中在应用边界，不接受自由 SQL 或租户切换。 */
public final class PartnerDictionaryQueryService implements PartnerDictionaryQueries {
 private final PartnerDictionaryRecords records;
 /** @param records 双表强制租户条件的固定读取端口 */
 public PartnerDictionaryQueryService(PartnerDictionaryRecords records){this.records=records;}
 public Page items(String tenant,String code,int page,int size){if(!DictionaryValueStandard.validCode(code)||page<1||page>100000||size<1||size>100)throw new IllegalArgumentException("INVALID_PARTNER_QUERY");var items=records.items(tenant,code,size,(long)(page-1)*size).stream().map(row->new Item((String)row.get("code"),(String)row.get("value"),(String)row.get("name"))).toList();return new Page(items,records.count(tenant,code),page,size);}
}
