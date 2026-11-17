package com.appnexus.masterdata;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 地理目录初始化；只插入缺失编码，保留租户名称、停用状态、版本和创建审计。 */
public final class GeographyDefaults {
  private GeographyDefaults() {}

  /**
   * 在调用方事务内初始化完整目录；失败须回滚，禁止吞掉格式或数据库异常。
   * @param jdbc 当前迁移或开通事务的数据库连接；时间由数据库按 UTC 生成
   * @param tenant 可信租户 UUID，必填，不接受浏览器传入的任意租户
   * @param actor 可信开通账号 UUID；迁移无账号时为 null，不伪造操作者
   */
  public static void seed(JdbcTemplate jdbc,UUID tenant,UUID actor) {
    String account=actor==null?null:actor.toString();
    var countries=new ArrayList<Object[]>();
    for(var e:GeographyStandards.entries(false))countries.add(new Object[]{tenant.toString(),e.code(),e.name(),e.name(),e.attributes().get("alpha3Code"),e.attributes().get("numericCode"),account,account,tenant.toString(),e.code()});
    jdbc.batchUpdate("""
      INSERT INTO md_country_region(id,tenant_id,code,name,english_name,alpha3_code,numeric_code,created_by,created_at,updated_by,updated_at)
      SELECT UUID_TO_BIN(UUID()),UUID_TO_BIN(?),?,?,?,?,?,UUID_TO_BIN(?),CURRENT_TIMESTAMP(6),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6)
      WHERE NOT EXISTS(SELECT 1 FROM md_country_region WHERE tenant_id=UUID_TO_BIN(?) AND code=?)
      """,countries);
    var subdivisions=new ArrayList<Object[]>();
    for(var e:GeographyStandards.entries(true))subdivisions.add(new Object[]{tenant.toString(),e.code(),e.name(),e.attributes().get("countryCode"),e.attributes().get("subdivisionType"),e.attributes().get("parentCode"),account,account,tenant.toString(),e.code()});
    jdbc.batchUpdate("""
      INSERT INTO md_subdivision(id,tenant_id,code,name,country_code,subdivision_type,parent_code,created_by,created_at,updated_by,updated_at)
      SELECT UUID_TO_BIN(UUID()),UUID_TO_BIN(?),?,?,?,?,?,UUID_TO_BIN(?),CURRENT_TIMESTAMP(6),UUID_TO_BIN(?),CURRENT_TIMESTAMP(6)
      WHERE NOT EXISTS(SELECT 1 FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?) AND code=?)
      """,subdivisions);
  }
}
