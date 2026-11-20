package com.appnexus.masterdata.adapter.jdbc;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;
/** 商品追加历史固定存储；快照、父CAS和历史插入在原事务内，失败必须回滚，不记录预检token。 */
public final class MyBatisProductHistory {
 private final ProductHistoryMapper mapper;
 private final ObjectMapper json;
 private final BiFunction<VerifiedTenantContext,String,Map<String,Object>> snapshot;
 /** @param mapper 原聚合事务固定历史映射 @param json 原快照序列化协议 @param snapshot 同事务授权快照读取协作 */
 public MyBatisProductHistory(ProductHistoryMapper mapper,ObjectMapper json,BiFunction<VerifiedTenantContext,String,Map<String,Object>> snapshot){this.mapper=mapper;this.json=json;this.snapshot=snapshot;}
 /** @param c 可信实际主体 @param id 已授权商品 @param type 固定业务事件编码 @param before 原快照，创建可空 @param reason 非敏感业务原因，可空 @return 新追加历史UUID；时间由数据库生成 */
 public String append(VerifiedTenantContext c,String id,String type,Map<String,Object> before,String reason){
  var after=snapshot.apply(c,id);if(reason!=null)after.put("changeReason",reason);String changeId=UUID.randomUUID().toString();
  mapper.append(tenantBytes(c),changeId,id,after.get("version"),type,before==null?null:json.writeValueAsString(before),json.writeValueAsString(after),actor(c));return changeId;
 }
 private static String actor(VerifiedTenantContext c){return c.accountId().toString();}
 private static byte[] tenantBytes(VerifiedTenantContext c){var id=c.tenantId();return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
