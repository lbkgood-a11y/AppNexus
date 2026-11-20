package com.appnexus.platform.persistence.organization.personnel;
import com.appnexus.platform.organization.port.personnel.PersonnelIdentityQueries;
import com.appnexus.platform.organization.port.personnel.PersonnelExecutionRecords;
import com.appnexus.platform.organization.*;
import java.util.*;

import org.springframework.stereotype.Component;
/** 主数据提供账号人员身份；跨模块只暴露 PersonnelExecution，真实业务权限另行判定。 */
@Component
public final class PersonnelExecutionAdapter implements PersonnelExecution {
 private final PersonnelExecutionRecords statements;

 private final PersonnelIdentityQueries identity;private final PersonnelDirectory directory;
 /**
 * @param jdbc 主数据源
 * @param directory 组织公开目录 */
 public PersonnelExecutionAdapter(PersonnelIdentityQueries identity,PersonnelDirectory directory,PersonnelExecutionRecords statements){this.statements=statements;this.identity=identity;this.directory=directory;}
 public List<Option> options(UUID tenant,UUID member){
  if(!directory.eligible(tenant,member))return List.of();var catalog=directory.catalog(tenant);var result=new ArrayList<Option>();
  var people=identity.people(tenant.toString(),member.toString()).stream().map(row->Map.entry((String)row.get("id"),(String)row.get("name"))).toList();
  for(var person:people)for(var org:catalog.organizations())if(org.enabled()&&directory.personInOrganization(tenant,UUID.fromString(person.getKey()),UUID.fromString(org.id())))result.add(new Option(person.getKey(),person.getValue(),org.id(),org.name()));
  return List.copyOf(result);
 }
 public boolean allowed(UUID tenant,UUID member,UUID person,UUID organization){return options(tenant,member).stream().anyMatch(o->o.personId().equalsIgnoreCase(person.toString())&&o.organizationId().equalsIgnoreCase(organization.toString()));}
 public List<String> resolveAccounts(UUID tenant,UUID organization,String kind,String reference){
  var people=directory.resolvePersons(tenant,organization,kind,reference);var accounts=new TreeSet<String>();
  for(String person:people){
   var rows=identity.members(tenant.toString(),person);
   var catalog=directory.catalog(tenant);for(String member:rows)catalog.users().stream().filter(u->u.memberId().equalsIgnoreCase(member)).map(PersonnelDirectory.User::accountId).forEach(accounts::add);
  }
  return List.copyOf(accounts);
 }

 public void unlinkMember(UUID tenant,UUID member,UUID actor){statements.statement1(actor.toString(),tenant.toString(),member.toString());statements.statement2(tenant.toString(),member.toString());}
 public boolean routeCandidate(UUID tenant,UUID member,UUID person,UUID organization,String kind,String reference){return allowed(tenant,member,person,organization)&&directory.resolvePersons(tenant,organization,kind,reference).stream().anyMatch(value->value.equalsIgnoreCase(person.toString()));}
}
