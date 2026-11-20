package com.appnexus.platform.persistence.organization;

import com.appnexus.platform.organization.PersonnelDirectory;
import java.util.*;

import org.springframework.stereotype.Service;

/** 组织拥有的目录适配器；跨模块只公开契约，主数据服务不读取组织、成员或账号表。 */
@Service
public final class MyBatisPersonnelDirectory implements PersonnelDirectory {
  private final PersonnelDirectoryMapper mapper;
  /** @param mapper 宿主 JDBC，只查询组织领域拥有的关系及公开账号名称 */
  public MyBatisPersonnelDirectory(PersonnelDirectoryMapper mapper){this.mapper=mapper;}
  /** 租户账号目录与组织无直接关系；仅保留有效成员，人员关联由主数据独立管理。 */
  private static final String USERS="SELECT BIN_TO_UUID(m.id) member_id,BIN_TO_UUID(a.id) account_id,a.display_name FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(?) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE";
  @Override public Catalog catalog(UUID tenant){
    String id=Objects.requireNonNull(tenant).toString();
    var organizations=mapper.statement0(id).stream().map(row->new Organization(text(row,"id"),text(row,"name"),flag(row,"enabled"))).toList();
    var positions=mapper.statement1(id).stream().map(row->new Position(text(row,"id"),text(row,"organization_id"),text(row,"name"),flag(row,"enabled"))).toList();
    var users=mapper.statement2(id).stream().map(row->new User(text(row,"member_id"),text(row,"account_id"),text(row,"display_name"))).toList();
    return new Catalog(organizations,positions,users);
  }
  @Override public boolean eligible(UUID tenant,UUID member){
    return !mapper.statement3(tenant.toString(),member.toString()).stream().map(row->text(row,"member_id")).toList().isEmpty();
  }
  /** 业务归属只从人员关系及任职解析；日期、状态逐次校验，旧用户组织表不参与。 */
  @Override public boolean personInOrganization(UUID tenant,UUID person,UUID org){
    return Boolean.TRUE.equals(mapper.statement4(tenant.toString(),org.toString(),person.toString(),person.toString()));
  }
  @Override public List<Affiliation> affiliations(UUID tenant,UUID person){
    var result=new ArrayList<Affiliation>();
    result.addAll(mapper.statement5(tenant.toString(),person.toString()).stream().map(row->new Affiliation(text(row,"id"),"organizations",Map.of("organizationId",text(row,"org"),"isPrimary",String.valueOf(flag(row,"is_primary")),"enabled",String.valueOf(flag(row,"enabled")),"startsOn",Objects.toString(text(row,"starts_on"),""),"endsOn",Objects.toString(text(row,"ends_on"),""),"remarks",text(row,"remarks")))).toList());
    result.addAll(mapper.statement6(tenant.toString(),person.toString()).stream().map(row->new Affiliation(text(row,"id"),"positions",Map.of("organizationId",text(row,"org"),"positionId",text(row,"pos"),"enabled",String.valueOf(flag(row,"enabled")),"startsOn",day(row.get("starts_at")),"endsOn",day(row.get("ends_at")),"remarks",text(row,"remarks")))).toList());
    return List.copyOf(result);
  }
  @Override public boolean saveAffiliation(UUID tenant,UUID person,String section,UUID id,boolean create,Map<String,String> values){
    String org=values.get("organizationId");var catalog=catalog(tenant);
    if(catalog.organizations().stream().noneMatch(o->o.id().equalsIgnoreCase(org)&&o.enabled()))throw new IllegalArgumentException("PERSON_ORGANIZATION_UNAVAILABLE");
    if(section.equals("organizations")){
      boolean primary=Boolean.parseBoolean(values.getOrDefault("isPrimary","false")),enabled=!values.getOrDefault("enabled","true").equals("false");
      if(primary)mapper.statement7(tenant.toString(),person.toString());
      if(create)return mapper.statement8(tenant.toString(),id.toString(),person.toString(),org,primary,values.get("startsOn"),values.get("endsOn"),enabled,values.getOrDefault("remarks",""))==1;
      return mapper.statement9(org,primary,values.get("startsOn"),values.get("endsOn"),enabled,values.getOrDefault("remarks",""),tenant.toString(),person.toString(),id.toString())==1;
    }
    if(!section.equals("positions"))throw new IllegalArgumentException("INVALID_PERSON_AFFILIATION");
    String pos=values.get("positionId");if(catalog.positions().stream().noneMatch(p->p.id().equalsIgnoreCase(pos)&&p.organizationId().equalsIgnoreCase(org)&&p.enabled()))throw new IllegalArgumentException("PERSON_ORGANIZATION_UNAVAILABLE");
    String starts=values.getOrDefault("startsOn","").isBlank()?"1970-01-02":values.get("startsOn");String ends=values.getOrDefault("endsOn","");boolean enabled=!values.getOrDefault("enabled","true").equals("false");
    if(create)return mapper.statement10(tenant.toString(),id.toString(),person.toString(),pos,starts,ends,enabled,values.getOrDefault("remarks",""))==1;
    if(!enabled)mapper.statement11(tenant.toString(),id.toString());
    return mapper.statement12(pos,starts,ends,enabled,values.getOrDefault("remarks",""),tenant.toString(),person.toString(),id.toString())==1;
  }
  @Override public boolean deleteAffiliation(UUID tenant,UUID person,String section,UUID id){
    if(section.equals("positions"))mapper.statement13(tenant.toString(),person.toString(),id.toString());
    String table=switch(section){case "organizations"->"tenant_person_organization";case "positions"->"tenant_person_position";default->throw new IllegalArgumentException("INVALID_PERSON_AFFILIATION");};
    return mapper.statement14(tenant.toString(),person.toString(),id.toString())==1;
  }
  @Override public List<String> resolvePersons(UUID tenant,UUID organization,String kind,String reference){
    if(!Set.of("POSITION","LEADER").contains(kind))return List.of();
    if(kind.equals("POSITION"))UUID.fromString(reference);
    return mapper.resolve(tenant.toString(),organization.toString(),kind,reference);
  }
  @Override public String accountName(UUID tenant,String account){
    if(account==null)return "";
    return mapper.statement15(tenant.toString(),account).stream().map(row->text(row,"display_name")).toList().stream().findFirst().orElse("");
  }
 private static String text(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
 private static boolean flag(Map<String,Object> row,String key){Object value=row.get(key);return Boolean.TRUE.equals(value)||value instanceof Number number&&number.intValue()!=0;}
 private static String day(Object value){if(value==null)return "";if(value instanceof java.sql.Timestamp time)return time.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();return value.toString().substring(0,10);}
}
