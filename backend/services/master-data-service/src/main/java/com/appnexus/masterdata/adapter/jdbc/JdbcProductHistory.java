package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;
/** 商品追加历史固定存储；快照、父CAS和历史插入在原事务内，失败必须回滚，不记录预检token。 */
public final class JdbcProductHistory {
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private final BiFunction<VerifiedTenantContext,String,Map<String,Object>> snapshot;
 /** @param jdbc 原聚合事务数据源 @param json 原快照序列化协议 @param snapshot 同事务授权快照读取协作 */
 public JdbcProductHistory(JdbcTemplate jdbc,ObjectMapper json,BiFunction<VerifiedTenantContext,String,Map<String,Object>> snapshot){this.jdbc=jdbc;this.json=json;this.snapshot=snapshot;}
 /** @param c 可信实际主体 @param id 已授权商品 @param type 固定业务事件编码 @param before 原快照，创建可空 @param reason 非敏感业务原因，可空 @return 新追加历史UUID；时间由数据库生成 */
 public String append(VerifiedTenantContext c,String id,String type,Map<String,Object> before,String reason){
  var after=snapshot.apply(c,id);if(reason!=null)after.put("changeReason",reason);String changeId=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO md_product_v1_change(id,tenant_id,product_id,to_version,event_type,before_snapshot,after_snapshot,created_by,updated_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?))",changeId,tenant(c),id,after.get("version"),type,before==null?null:json.writeValueAsString(before),json.writeValueAsString(after),actor(c),actor(c));return changeId;
 }
 private static String tenant(VerifiedTenantContext c){return c.tenantId().toString();}
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
}
