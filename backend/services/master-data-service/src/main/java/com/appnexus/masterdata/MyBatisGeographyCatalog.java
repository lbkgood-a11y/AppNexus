package com.appnexus.masterdata;

import java.util.*;
import com.appnexus.platform.configuration.GeographyCatalog;

import org.springframework.stereotype.Component;

/** 已授权租户地理摘要；名称统一为中国仅中文、其他中文（原文），仅修改显示，不改变存储及引用。 */
@Component
public final class MyBatisGeographyCatalog implements GeographyCatalog {
  private final GeographyMapper mapper;
  /** @param mapper 主数据数据库访问器，查询始终绑定可信租户 */
  public MyBatisGeographyCatalog(GeographyMapper mapper){this.mapper=mapper;}
  @Override public List<Node> nodes(UUID tenantId){
    Objects.requireNonNull(tenantId);
    var graph=new TenantGeography(mapper,tenantId,false);
    return mapper.catalog(tenantId.toString()).stream().map(row->new Node((String)row.get("code"),graph.name((String)row.get("code")),graph.country((String)row.get("code")),(String)row.get("parent_code"),Boolean.TRUE.equals(row.get("enabled")))).toList();
  }
}
