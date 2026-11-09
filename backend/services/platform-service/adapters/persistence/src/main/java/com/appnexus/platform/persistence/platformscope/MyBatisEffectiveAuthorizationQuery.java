package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 有效权限解释聚合实现；每个角色分支必须同时满足角色、权限和所需数据策略才标记有效。 */
@Service
public final class MyBatisEffectiveAuthorizationQuery implements EffectiveAuthorizationQuery {
 private final EffectiveAuthorizationMapper mapper;
 public MyBatisEffectiveAuthorizationQuery(EffectiveAuthorizationMapper mapper){this.mapper=mapper;}
 @Override public List<Explanation> platformAccount(UUID accountId){return explain(mapper.platform(Objects.requireNonNull(accountId).toString()));}
 @Override public List<Explanation> tenantMember(UUID tenantId,UUID memberId){return explain(mapper.tenant(Objects.requireNonNull(tenantId).toString(),Objects.requireNonNull(memberId).toString()));}
 private List<Explanation> explain(List<Map<String,Object>> rows){
  record Key(String code,UUID role){} var organizations=new LinkedHashMap<Key,LinkedHashSet<UUID>>();var first=new LinkedHashMap<Key,Map<String,Object>>();
  for(var row:rows){var key=new Key((String)row.get("code"),UUID.fromString((String)row.get("role_id")));first.putIfAbsent(key,row);if(row.get("organization_id")!=null)organizations.computeIfAbsent(key,ignored->new LinkedHashSet<>()).add(UUID.fromString((String)row.get("organization_id")));}
  var grouped=new LinkedHashMap<String,List<Source>>();for(var entry:first.entrySet()){var row=entry.getValue();String reason=reason(row);if(reason==null&&("ORGANIZATION".equals(row.get("scope_type"))||"ORGANIZATION_SUBTREE".equals(row.get("scope_type")))&&organizations.getOrDefault(entry.getKey(),new LinkedHashSet<>()).isEmpty())reason="DATA_SCOPE_MISSING";var scope=row.get("scope_type")==null?null:DataScopeAuthorization.ScopeType.valueOf((String)row.get("scope_type"));var source=new Source(entry.getKey().role(),(String)row.get("role_name"),reason==null,reason,scope,Set.copyOf(organizations.getOrDefault(entry.getKey(),new LinkedHashSet<>())));grouped.computeIfAbsent(entry.getKey().code(),ignored->new ArrayList<>()).add(source);}
  return grouped.entrySet().stream().map(e->new Explanation(e.getKey(),e.getValue().stream().anyMatch(Source::effective),List.copyOf(e.getValue()))).toList();
 }
 private String reason(Map<String,Object> row){
 if(row.containsKey("subject_enabled")&&!flag(row.get("subject_enabled")))return "SUBJECT_DISABLED";
 if(row.containsKey("expired")&&flag(row.get("expired")))return "AUTHORIZATION_EXPIRED";
 if(row.containsKey("current_permission")&&!flag(row.get("current_permission")))return "ROLE_PERMISSION_REMOVED";
 if("organization.unit".equals(row.get("resource_type"))&&"SELF".equals(row.get("scope_type")))return "DATA_SCOPE_UNSUPPORTED";
if(row.get("deleted_at")!=null)return "ROLE_DELETED";if(!"ENABLED".equals(row.get("role_status")))return "ROLE_DISABLED";if(!flag(row.get("permission_enabled")))return "PERMISSION_DISABLED";if(flag(row.get("requires_scope"))&&row.get("scope_type")==null)return "DATA_SCOPE_MISSING";return null;}
 private boolean flag(Object value){return value instanceof Boolean b?b:((Number)value).intValue()!=0;}
}
