package com.appnexus.platform.organization.personnel;
import com.appnexus.platform.organization.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** 主数据提供账号人员身份；跨模块只暴露 PersonnelExecution，真实业务权限另行判定。 */
@Component
public final class PersonnelExecutionAdapter implements PersonnelExecution {
 private final JdbcTemplate jdbc;private final PersonnelDirectory directory;
 /**
 * @param jdbc 主数据源
 * @param directory 组织公开目录 */
 public PersonnelExecutionAdapter(JdbcTemplate jdbc,PersonnelDirectory directory){this.jdbc=jdbc;this.directory=directory;}
 public List<Option> options(UUID tenant,UUID member){
  if(!directory.eligible(tenant,member))return List.of();var catalog=directory.catalog(tenant);var result=new ArrayList<Option>();
  var people=jdbc.query("SELECT BIN_TO_UUID(p.id) id,p.name FROM md_person_account a JOIN md_person p ON p.tenant_id=a.tenant_id AND p.id=a.person_id WHERE a.tenant_id=UUID_TO_BIN(?) AND a.member_id=UUID_TO_BIN(?) AND p.enabled=TRUE AND p.employment_status IN ('ACTIVE','PROBATION') ORDER BY p.name,p.id",(rs,n)->Map.entry(rs.getString("id"),rs.getString("name")),tenant.toString(),member.toString());
  for(var person:people)for(var org:catalog.organizations())if(org.enabled()&&directory.personInOrganization(tenant,UUID.fromString(person.getKey()),UUID.fromString(org.id())))result.add(new Option(person.getKey(),person.getValue(),org.id(),org.name()));
  return List.copyOf(result);
 }
 public boolean allowed(UUID tenant,UUID member,UUID person,UUID organization){return options(tenant,member).stream().anyMatch(o->o.personId().equalsIgnoreCase(person.toString())&&o.organizationId().equalsIgnoreCase(organization.toString()));}
 public List<String> resolveAccounts(UUID tenant,UUID organization,String kind,String reference){
  var people=directory.resolvePersons(tenant,organization,kind,reference);var accounts=new TreeSet<String>();
  for(String person:people){
   var rows=jdbc.query("SELECT BIN_TO_UUID(a.member_id) member_id FROM md_person_account a JOIN md_person p ON p.tenant_id=a.tenant_id AND p.id=a.person_id WHERE a.tenant_id=UUID_TO_BIN(?) AND p.id=UUID_TO_BIN(?) AND p.enabled=TRUE AND p.employment_status IN ('ACTIVE','PROBATION')",(rs,n)->rs.getString("member_id"),tenant.toString(),person);
   var catalog=directory.catalog(tenant);for(String member:rows)catalog.users().stream().filter(u->u.memberId().equalsIgnoreCase(member)).map(PersonnelDirectory.User::accountId).forEach(accounts::add);
  }
  return List.copyOf(accounts);
 }

 public void unlinkMember(UUID tenant,UUID member,UUID actor){jdbc.update("UPDATE md_person p JOIN md_person_account a ON a.tenant_id=p.tenant_id AND a.person_id=p.id SET p.version=p.version+1,p.updated_by=UUID_TO_BIN(?),p.updated_at=CURRENT_TIMESTAMP(6) WHERE a.tenant_id=UUID_TO_BIN(?) AND a.member_id=UUID_TO_BIN(?)",actor.toString(),tenant.toString(),member.toString());jdbc.update("DELETE FROM md_person_account WHERE tenant_id=UUID_TO_BIN(?) AND member_id=UUID_TO_BIN(?)",tenant.toString(),member.toString());}
 public boolean routeCandidate(UUID tenant,UUID member,UUID person,UUID organization,String kind,String reference){return allowed(tenant,member,person,organization)&&directory.resolvePersons(tenant,organization,kind,reference).stream().anyMatch(value->value.equalsIgnoreCase(person.toString()));}
}
