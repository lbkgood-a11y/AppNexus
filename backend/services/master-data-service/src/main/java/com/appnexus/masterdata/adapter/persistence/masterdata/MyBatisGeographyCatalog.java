package com.appnexus.masterdata.adapter.persistence.masterdata;
import com.appnexus.masterdata.adapter.persistence.masterdata.TenantGeography;
import com.appnexus.masterdata.adapter.persistence.GeographyMapper;

import java.util.*;
import com.appnexus.platform.configuration.GeographyCatalog;

import org.springframework.stereotype.Component;

/** 已授权租户地理摘要；名称统一为中国仅中文、其他中文（原文），仅修改显示，不改变存储及引用。 */
@Component
public final class MyBatisGeographyCatalog implements GeographyCatalog {
  private final GeographyMapper mapper;
  private final com.appnexus.platform.authorization.ScopedWriteGate gate;
  private final org.springframework.transaction.support.TransactionTemplate transaction;
  private static final Map<String,String> STANDARDS=standardNames();
  private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();

  /** @param mapper 可信租户地理读取 @param gate 限定批次授权 @param manager 宿主数据库事务管理器 */
  public MyBatisGeographyCatalog(GeographyMapper mapper,com.appnexus.platform.authorization.ScopedWriteGate gate,
      org.springframework.transaction.PlatformTransactionManager manager){
    this.mapper=mapper;
    this.gate=gate;
    this.transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
  }

  /** {@inheritDoc} */
  @Override public Map<String,String> names(UUID tenantId,List<String> codes){
    Objects.requireNonNull(tenantId);
    if(codes.isEmpty())return Map.of();
    if(codes.size()>200)throw new IllegalArgumentException("GEOGRAPHY_NAME_BATCH_TOO_LARGE");
    String batch=json.writeValueAsString(codes.stream().distinct().toList());
    return transaction.execute(status->{
      gate.authorizeServiceOperation(tenantId,"geography-reference:resolve-names",List.of(batch),()->true);
      var result=new HashMap<String,String>();
      for(var row:mapper.names(tenantId.toString(),batch)){
        String code=(String)row.get("code"),name=(String)row.get("name");
        if(name.equals(STANDARDS.get(code)))name=com.appnexus.masterdata.domain.geography.GeographyHierarchy.displayName(code,name);
        result.put(code,name);
      }
      for(var continent:com.appnexus.masterdata.domain.geography.GeographyHierarchy.children("",1,100).rows()){
        if(codes.contains(continent.code()))result.putIfAbsent(continent.code(),continent.name());
      }
      return Map.copyOf(result);
    });
  }

  /** 启动时建立官方原名索引，对租户自行修改的名称保留原文。 */
  private static Map<String,String> standardNames(){
    var names=new HashMap<String,String>();
    for(boolean subdivision:List.of(false,true)){
      for(var entry:com.appnexus.masterdata.domain.geography.GeographyStandards.entries(subdivision))names.put(entry.code(),entry.name());
    }
    return Map.copyOf(names);
  }

  @Override public List<Node> nodes(UUID tenantId){return nodes(mapper,tenantId);}

  /** @param mapper 当前数据源的租户地理投影 @param tenantId 可信租户 @return 选择器完整目录 */
  public static List<Node> nodes(GeographyMapper mapper,UUID tenantId){
    Objects.requireNonNull(tenantId);
    var graph=new TenantGeography(mapper,tenantId,false);
    return mapper.catalog(tenantId.toString()).stream().map(row->new Node((String)row.get("code"),graph.name((String)row.get("code")),graph.country((String)row.get("code")),(String)row.get("parent_code"),Boolean.TRUE.equals(row.get("enabled")))).toList();
  }
}
