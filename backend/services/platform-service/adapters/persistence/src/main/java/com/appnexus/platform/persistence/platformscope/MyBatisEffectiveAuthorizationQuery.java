package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 有效权限解释聚合实现；每个角色分支必须同时满足角色、权限和所需数据策略才标记有效。 */
@Service
public class MyBatisEffectiveAuthorizationQuery implements EffectiveAuthorizationQuery {
 private final EffectiveAuthorizationMapper mapper;
 private final ScopedWriteGate sqlGate;
 private final DataAccessCatalog catalog;
 public MyBatisEffectiveAuthorizationQuery(EffectiveAuthorizationMapper mapper,ScopedWriteGate sqlGate,DataAccessCatalog catalog){
  this.mapper=mapper;this.sqlGate=sqlGate;this.catalog=catalog;
 }
 @Override public List<Explanation> platformAccount(UUID accountId){return explain(mapper.platform(Objects.requireNonNull(accountId).toString()),null);}
 @Override @org.springframework.transaction.annotation.Transactional(readOnly=true)
 public List<Explanation> tenantMember(UUID tenantId,UUID memberId){
  // 管理入口已复核目标成员。许可仅绑定本次解释查询，不签发写许可或业务记录范围。
  sqlGate.authorizeServiceRead(Objects.requireNonNull(tenantId),"access.scope:explain",
      List.of(Objects.requireNonNull(memberId).toString()),()->true);
  return explain(mapper.tenant(tenantId.toString(),memberId.toString()),memberId);
 }
 private List<Explanation> explain(List<Map<String,Object>> rows,UUID memberId){
  record Key(String code,UUID role){} var organizations=new LinkedHashMap<Key,LinkedHashSet<UUID>>();var first=new LinkedHashMap<Key,Map<String,Object>>();
  for(var row:rows){var key=new Key((String)row.get("code"),UUID.fromString((String)row.get("role_id")));first.putIfAbsent(key,row);if(row.get("organization_id")!=null)organizations.computeIfAbsent(key,ignored->new LinkedHashSet<>()).add(UUID.fromString((String)row.get("organization_id")));}
  var grouped=new LinkedHashMap<String,List<Source>>();for(var entry:first.entrySet()){var row=entry.getValue();String reason=reason(row,memberId);var scope=row.get("scope_type")==null?null:DataScopeAuthorization.ScopeType.valueOf((String)row.get("scope_type"));var source=new Source(entry.getKey().role(),(String)row.get("role_name"),reason==null,reason,scope,Set.copyOf(organizations.getOrDefault(entry.getKey(),new LinkedHashSet<>())),source(row));grouped.computeIfAbsent(entry.getKey().code(),ignored->new ArrayList<>()).add(source);}
  return grouped.entrySet().stream().map(e->new Explanation(e.getKey(),e.getValue().stream().anyMatch(Source::effective),List.copyOf(e.getValue()))).toList();
 }
 private ScopeSource source(Map<String,Object> row) {
  return row.get("scope_source") instanceof String value ? ScopeSource.valueOf(value) : null;
 }

 /** 默认范围必须匹配当前声明及成员主体，避免解释把失效分支展示成可用范围。 */
 private String defaultReason(Map<String,Object> row, UUID memberId, DataScopeAuthorization.ScopeType scope) {
  var source = source(row);
  if (source != ScopeSource.ROLE_DEFAULT && source != ScopeSource.MEMBER_DEFAULT) return null;
  String code=(String)row.get("code"), resource=(String)row.get("resource_type");
  if (!catalog.supports(code,resource,scope)) return "DATA_SCOPE_UNSUPPORTED";
  if (scope != DataScopeAuthorization.ScopeType.RULES) return null;
  var rules=DataGrantPolicySupport.decode(row.get("default_rules"));
  try { catalog.validateRules(code,resource,rules); }
  catch (IllegalArgumentException unsupported) { return "DATA_SCOPE_UNSUPPORTED"; }
  var dimensions=catalog.dimensions(code,resource);
  return rules.stream().anyMatch(rule -> !DataGrantPolicySupport.retiredRule(rule,dimensions)
      && DataGrantRules.matchesSubject(rule,memberId,Set.of())) ? null : "DATA_SCOPE_MISSING";
 }

 private String reason(Map<String,Object> row,UUID memberId){
 if(row.containsKey("subject_enabled")&&!flag(row.get("subject_enabled")))return "SUBJECT_DISABLED";
 if(row.containsKey("expired")&&flag(row.get("expired")))return "AUTHORIZATION_EXPIRED";
 if(row.containsKey("current_permission")&&!flag(row.get("current_permission")))return "ROLE_PERMISSION_REMOVED";
 // 运行时忽略退役范围，权限解释也不能把该分支标为有效。
 if(row.get("scope_type") instanceof String scope&&!DataScopeAuthorization.ScopeType.valueOf(scope).configurable())return "DATA_SCOPE_UNSUPPORTED";
if(row.get("deleted_at")!=null)return "ROLE_DELETED";if(!"ENABLED".equals(row.get("role_status")))return "ROLE_DISABLED";if(!flag(row.get("permission_enabled")))return "PERMISSION_DISABLED";if(flag(row.get("requires_scope"))&&row.get("scope_type")==null)return "DATA_SCOPE_MISSING";return row.get("scope_type") instanceof String scope ? defaultReason(row,memberId,DataScopeAuthorization.ScopeType.valueOf(scope)) : null;}
 private boolean flag(Object value){return value instanceof Boolean b?b:((Number)value).intValue()!=0;}
}
