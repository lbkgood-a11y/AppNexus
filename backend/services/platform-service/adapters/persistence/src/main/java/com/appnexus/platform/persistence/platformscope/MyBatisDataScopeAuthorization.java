package com.appnexus.platform.persistence.platformscope;
import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;
/** 同一操作的数据范围取并集；组织精确范围与子树分别计算后合并，不将根节点误解释为子树。 */
@Service
public final class MyBatisDataScopeAuthorization implements DataScopeAuthorization {
 private final DataScopeMapper mapper;
 public MyBatisDataScopeAuthorization(DataScopeMapper mapper){this.mapper=mapper;}
 @Override public DataScope resolveDataScope(AccessAuthorization.Subject subject,String code,String resource){
  if(subject==null||subject.accountId()==null||subject.tenantId()==null||subject.memberId()==null||code==null||resource==null||!resource.matches("[a-z][a-z0-9.]*"))return DataScope.deny();
  String account=subject.accountId().toString(),tenant=subject.tenantId().toString(),member=subject.memberId().toString();
  var rows=mapper.policies(account,tenant,member,code,resource);
  // 兼容来源只在没有角色数据分支时回退，不能把已配置的窄范围静默扩大为 ALL。
  if(rows.isEmpty())return LegacyOrganizationPermissions.supportsData(code,resource)&&mapper.legacyAll(account,tenant,member,code)?new DataScope(ScopeType.ALL,Set.of()):DataScope.deny();
  var exact=new HashSet<UUID>();var roots=new HashSet<UUID>();
  for(var row:rows){
   String type=(String)row.get("scope_type");
   if("ALL".equals(type))return new DataScope(ScopeType.ALL,Set.of());
   // organization.unit 没有创建人/负责人语义；旧 SELF 分支为空集，不抹除其他有效分支。
   if("SELF".equals(type))continue;
   if(!"ORGANIZATION".equals(type)&&!"ORGANIZATION_SUBTREE".equals(type))return DataScope.deny();
   if(row.get("organization_id")!=null)("ORGANIZATION".equals(type)?exact:roots).add(UUID.fromString((String)row.get("organization_id")));
  }
  if(!roots.isEmpty()){
   if(!"organization.unit".equals(resource))return DataScope.deny();
   exact.addAll(mapper.descendants(tenant,roots.stream().map(UUID::toString).sorted().toList()).stream().map(UUID::fromString).toList());
  }
  return exact.isEmpty()?DataScope.deny():new DataScope(ScopeType.ORGANIZATION,Set.copyOf(exact));
 }
}
