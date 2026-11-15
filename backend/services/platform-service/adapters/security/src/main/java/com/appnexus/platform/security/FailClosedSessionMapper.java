package com.appnexus.platform.security;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.session.MapSession;
import org.springframework.session.data.redis.RedisSessionMapper;
/**
 * Redis 过期/并发删除后可能留下仅含增量属性的散列；缺少生命周期事实时必须重认证，不能重建权限或触发 500。
 * 仅检查生命周期结构，存储连接故障仍向上传播，禁止故障时回退旧会话或伪造创建时间。
 */
public final class FailClosedSessionMapper implements BiFunction<String,Map<String,Object>,MapSession> {
 private final RedisSessionMapper delegate=new RedisSessionMapper();
 /**
  * 保留完整会话原有解析规则，不记录会话 ID 或任何认证属性。
  * @param id 仓储读取的会话标识，仅交给 Spring 解析
  * @param values 仓储读取的散列，缺少任何必需生命周期字段时视为不存在
  * @return 完整会话，或 null 使认证链按会话失效处理
  */
 @Override public MapSession apply(String id,Map<String,Object> values){
  if(values==null||values.get("creationTime")==null||values.get("lastAccessedTime")==null||values.get("maxInactiveInterval")==null)return null;
  return delegate.apply(id,values);
 }
}
