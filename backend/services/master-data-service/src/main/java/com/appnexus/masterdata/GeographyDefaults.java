package com.appnexus.masterdata;
import java.util.UUID;
/** 标准地理初始化；固定Mapper只补缺失，不修改历史资源或覆盖租户定制。 */
public final class GeographyDefaults {
 private GeographyDefaults(){}
 /** @param mapper 同迁移/开通事务固定Mapper @param tenant 可信租户UUID @param actor 实际账号，历史迁移可空 */
 public static void seed(GeographyMapper mapper,UUID tenant,UUID actor){String account=actor==null?null:actor.toString();for(var entry:GeographyStandards.entries(false))mapper.seedCountry(tenant.toString(),entry.code(),entry.name(),entry.attributes().get("alpha3Code"),entry.attributes().get("numericCode"),account);for(var entry:GeographyStandards.entries(true))mapper.seedSubdivision(tenant.toString(),entry.code(),entry.name(),entry.attributes().get("countryCode"),entry.attributes().get("subdivisionType"),entry.attributes().get("parentCode"),account);}
}
