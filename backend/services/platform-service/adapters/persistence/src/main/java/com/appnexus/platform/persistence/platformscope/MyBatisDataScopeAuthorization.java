package com.appnexus.platform.persistence.platformscope;
import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;
/** 同一操作的数据范围取并集；组织精确范围与子树分别计算后合并，不将根节点误解释为子树。 */
@Service
public final class MyBatisDataScopeAuthorization implements DataScopeAuthorization {
 private final DataScopeMapper mapper;
 private final com.appnexus.platform.organization.OrganizationMembership membership;
 private final DataAccessCatalog catalog;
 /**
  * @param mapper 原角色/成员分支的权威策略读取器
  * @param membership 组织上下文公开有效组织身份，不允许回退旧组织关系
  * @param catalog 同一业务对象声明派生的维度及操作能力目录
  */
 public MyBatisDataScopeAuthorization(DataScopeMapper mapper,com.appnexus.platform.organization.OrganizationMembership membership,DataAccessCatalog catalog){this.mapper=mapper;this.membership=membership;this.catalog=catalog;}
 @Override public DataScope resolveDataScope(AccessAuthorization.Subject subject,String code,String resource){
  if(subject==null||subject.accountId()==null||subject.tenantId()==null||subject.memberId()==null||code==null||resource==null||!resource.matches("[a-z][a-z0-9.]*"))return DataScope.deny();
  String account=subject.accountId().toString(),tenant=subject.tenantId().toString(),member=subject.memberId().toString();
  var rows=mapper.policies(account,tenant,member,code,resource);
  // 缺少有效角色数据分支直接拒绝，不能使用历史直授权回退扩大范围。
  if(rows.isEmpty())return DataScope.deny();
  var exact=new HashSet<UUID>();var roots=new HashSet<UUID>();
  var rules=new ArrayList<DataGrantRules.Rule>();var processed=new HashSet<String>();Set<UUID> ownOrganizations=null;
  // ALL 仍受可信租户和本分支功能权限前置约束；避免无须执行的规则引入额外复杂度。
  if(rows.stream().anyMatch(row->"ALL".equals(row.get("scope_type"))))return new DataScope(ScopeType.ALL,Set.of());
  for(var row:rows){
   String type=(String)row.get("scope_type");
   if("ALL".equals(type))return new DataScope(ScopeType.ALL,Set.of());
   if("RULES".equals(type)) {
    String json=Objects.toString(row.get("rules_json"),"");if(!processed.add(json))continue;
    var declared=DataGrantPolicySupport.decode(row.get("rules_json"));catalog.validateRules(code,resource,declared);
    for(var rule:declared) {
     if(rule.subjectType()==DataGrantRules.SubjectType.ORGANIZATION) {
      if(ownOrganizations==null)ownOrganizations=membership.activeOrganizations(subject.tenantId(),subject.memberId());
     }
     if(DataGrantRules.matchesSubject(rule,subject.memberId(),ownOrganizations)){
      var resolved=resolve(rule,subject);
      if(resolved!=null)rules.add(resolved);
     }
    }
    continue;
   }
   // organization.unit 没有创建人/负责人语义；旧 SELF 分支为空集，不抹除其他有效分支。
   if("SELF".equals(type))continue;
   if(!"ORGANIZATION".equals(type)&&!"ORGANIZATION_SUBTREE".equals(type))return DataScope.deny();
   if(row.get("organization_id")!=null)("ORGANIZATION".equals(type)?exact:roots).add(UUID.fromString((String)row.get("organization_id")));
  }
  if(!roots.isEmpty()){
   exact.addAll(mapper.descendants(tenant,roots.stream().map(UUID::toString).sorted().toList()).stream().map(UUID::fromString).toList());
  }
  if(!rules.isEmpty())return new DataScope(ScopeType.RULES,Set.copyOf(exact),rules);
  return exact.isEmpty()?DataScope.deny():new DataScope(ScopeType.ORGANIZATION,Set.copyOf(exact));
 }
 /** 缺少动态身份时丢弃整个 AND 分支；固定条件沿用旧策略。 */
 private DataGrantRules.Rule resolve(DataGrantRules.Rule rule,AccessAuthorization.Subject subject){
  var conditions=new ArrayList<DataGrantRules.Condition>();
  for(var condition:rule.conditions()){
   if(condition.parameter()==null){conditions.add(condition);continue;}
   UUID value=switch(condition.parameter()){
    case CURRENT_ACCOUNT -> subject.accountId();
    case CURRENT_PERSON -> subject.personId();
    case CURRENT_ORGANIZATION,CURRENT_ORGANIZATION_SUBTREE -> subject.organizationId();
    case CURRENT_WAREHOUSE -> null;
   };
   if(value==null)return null;
   if(condition.parameter()==DataGrantRules.Parameter.CURRENT_ORGANIZATION_SUBTREE){
    var descendants=mapper.descendants(subject.tenantId().toString(),List.of(value.toString()));
    if(descendants.isEmpty())return null;
    conditions.add(new DataGrantRules.Condition(condition.field(),DataGrantRules.Operator.RESOLVED_IN,descendants));
   }else conditions.add(new DataGrantRules.Condition(condition.field(),DataGrantRules.Operator.EQ,List.of(value.toString())));
  }
  return new DataGrantRules.Rule(rule.subjectType(),rule.subjectIds(),rule.recordIds(),conditions,rule.allRecords());
 }
}
