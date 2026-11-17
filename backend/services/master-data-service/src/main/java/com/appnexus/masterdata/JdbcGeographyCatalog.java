package com.appnexus.masterdata;

import java.util.*;
import com.appnexus.platform.configuration.GeographyCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 已授权租户地理摘要；名称统一为中国仅中文、其他中文（原文），仅修改显示，不改变存储及引用。 */
@Component
public final class JdbcGeographyCatalog implements GeographyCatalog {
  private final JdbcTemplate jdbc;
  /** @param jdbc 主数据数据库访问器，查询始终绑定可信租户 */
  public JdbcGeographyCatalog(JdbcTemplate jdbc){this.jdbc=jdbc;}
  @Override public List<Node> nodes(UUID tenantId){
    Objects.requireNonNull(tenantId);
    var graph=new TenantGeography(jdbc,tenantId,false);
    return jdbc.query("""
      SELECT code,name,code country_code,'' parent_code,enabled FROM md_country_region WHERE tenant_id=UUID_TO_BIN(?)
      UNION ALL
      SELECT code,name,country_code,CASE WHEN parent_code='' THEN country_code ELSE parent_code END parent_code,enabled
      FROM md_subdivision WHERE tenant_id=UUID_TO_BIN(?) ORDER BY code
      """,(r,n)->new Node(r.getString("code"),graph.name(r.getString("code")),graph.country(r.getString("code")),r.getString("parent_code"),r.getBoolean("enabled")),tenantId.toString(),tenantId.toString());
  }
}
