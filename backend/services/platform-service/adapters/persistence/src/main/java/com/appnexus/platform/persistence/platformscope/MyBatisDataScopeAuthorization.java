package com.appnexus.platform.persistence.platformscope;
import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;
/** 多角色数据范围解析实现；不能无损表达的范围组合失败关闭，避免集合拼接产生意外扩权。 */
@Service
public final class MyBatisDataScopeAuthorization implements DataScopeAuthorization {
 private final DataScopeMapper mapper;
 public MyBatisDataScopeAuthorization(DataScopeMapper mapper){this.mapper=mapper;}
 @Override public DataScope resolveDataScope(AccessAuthorization.Subject subject,String code,String resource){
  if(subject==null||subject.accountId()==null||subject.tenantId()==null||subject.memberId()==null||code==null||resource==null||!resource.matches("[a-z][a-z0-9.]*"))return DataScope.deny();
  String account=subject.accountId().toString(),tenant=subject.tenantId().toString(),member=subject.memberId().toString();var rows=mapper.policies(account,tenant,member,code,resource);if(rows.isEmpty())return mapper.legacyAll(account,tenant,member,code)?new DataScope(ScopeType.ALL,Set.of()):DataScope.deny();
  var types=rows.stream().map(r->ScopeType.valueOf((String)r.get("scope_type"))).collect(java.util.stream.Collectors.toSet());
  if(types.contains(ScopeType.ALL))return new DataScope(ScopeType.ALL,Set.of());
  if(types.size()!=1)return DataScope.deny();
  var type=types.iterator().next();if(type==ScopeType.SELF)return new DataScope(type,Set.of());
  var ids=rows.stream().map(r->r.get("organization_id")).filter(Objects::nonNull).map(v->UUID.fromString((String)v)).collect(java.util.stream.Collectors.toUnmodifiableSet());
  return ids.isEmpty()?DataScope.deny():new DataScope(type,ids);
 }
}
