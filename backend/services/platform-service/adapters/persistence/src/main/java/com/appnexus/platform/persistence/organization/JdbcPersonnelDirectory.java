package com.appnexus.platform.persistence.organization;

import com.appnexus.platform.organization.PersonnelDirectory;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 组织拥有的目录适配器；跨模块只公开契约，主数据服务不读取组织、成员或账号表。 */
@Service
public final class JdbcPersonnelDirectory implements PersonnelDirectory {
  private final JdbcTemplate jdbc;
  /** @param jdbc 宿主 JDBC，只查询组织领域拥有的关系及公开账号名称 */
  public JdbcPersonnelDirectory(JdbcTemplate jdbc){this.jdbc=jdbc;}
  /** 租户账号目录与组织无直接关系；仅保留有效成员，人员关联由主数据独立管理。 */
  private static final String USERS="SELECT BIN_TO_UUID(m.id) member_id,BIN_TO_UUID(a.id) account_id,a.display_name FROM tenant_member m JOIN identity_account a ON a.id=m.account_id WHERE m.tenant_id=UUID_TO_BIN(?) AND m.enabled=TRUE AND m.removed_at IS NULL AND a.enabled=TRUE";
  @Override public Catalog catalog(UUID tenant){
    String id=Objects.requireNonNull(tenant).toString();
    var organizations=jdbc.query("SELECT BIN_TO_UUID(id) id,name,enabled FROM tenant_organization_unit WHERE tenant_id=UUID_TO_BIN(?) ORDER BY code",(rs,n)->new Organization(rs.getString("id"),rs.getString("name"),rs.getBoolean("enabled")),id);
    var positions=jdbc.query("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(organization_id) organization_id,name,enabled FROM tenant_organization_position WHERE tenant_id=UUID_TO_BIN(?) ORDER BY code",(rs,n)->new Position(rs.getString("id"),rs.getString("organization_id"),rs.getString("name"),rs.getBoolean("enabled")),id);
    var users=jdbc.query(USERS+" ORDER BY member_id",(rs,n)->new User(rs.getString("member_id"),rs.getString("account_id"),rs.getString("display_name")),id);
    return new Catalog(organizations,positions,users);
  }
  @Override public boolean eligible(UUID tenant,UUID member){
    return !jdbc.query(USERS+" AND m.id=UUID_TO_BIN(?)",(rs,n)->rs.getString("member_id"),tenant.toString(),member.toString()).isEmpty();
  }
  /** 业务归属只从人员关系及任职解析；日期、状态逐次校验，旧用户组织表不参与。 */
  @Override public boolean personInOrganization(UUID tenant,UUID person,UUID org){
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tenant_organization_unit u WHERE u.tenant_id=UUID_TO_BIN(?) AND u.id=UUID_TO_BIN(?) AND u.enabled=TRUE AND (EXISTS(SELECT 1 FROM tenant_person_organization po WHERE po.tenant_id=u.tenant_id AND po.organization_id=u.id AND po.person_id=UUID_TO_BIN(?) AND po.enabled=TRUE AND (po.starts_on IS NULL OR po.starts_on<=CURRENT_DATE) AND (po.ends_on IS NULL OR po.ends_on>=CURRENT_DATE)) OR EXISTS(SELECT 1 FROM tenant_person_position a JOIN tenant_organization_position p ON p.tenant_id=a.tenant_id AND p.id=a.position_id WHERE a.tenant_id=u.tenant_id AND a.person_id=UUID_TO_BIN(?) AND p.organization_id=u.id AND p.enabled=TRUE AND a.enabled=TRUE AND a.starts_at<=CURRENT_TIMESTAMP(6) AND (a.ends_at IS NULL OR a.ends_at>CURRENT_TIMESTAMP(6)))))",Boolean.class,tenant.toString(),org.toString(),person.toString(),person.toString()));
  }
  @Override public List<Affiliation> affiliations(UUID tenant,UUID person){
    var result=new ArrayList<Affiliation>();
    result.addAll(jdbc.query("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(organization_id) org,is_primary,starts_on,ends_on,enabled,remarks FROM tenant_person_organization WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?)",(rs,n)->new Affiliation(rs.getString("id"),"organizations",Map.of("organizationId",rs.getString("org"),"isPrimary",String.valueOf(rs.getBoolean("is_primary")),"enabled",String.valueOf(rs.getBoolean("enabled")),"startsOn",Objects.toString(rs.getString("starts_on"),""),"endsOn",Objects.toString(rs.getString("ends_on"),""),"remarks",rs.getString("remarks"))),tenant.toString(),person.toString()));
    result.addAll(jdbc.query("SELECT BIN_TO_UUID(a.id) id,BIN_TO_UUID(a.position_id) pos,BIN_TO_UUID(p.organization_id) org,a.starts_at,a.ends_at,a.enabled,a.remarks FROM tenant_person_position a JOIN tenant_organization_position p ON p.tenant_id=a.tenant_id AND p.id=a.position_id WHERE a.tenant_id=UUID_TO_BIN(?) AND a.person_id=UUID_TO_BIN(?)",(rs,n)->new Affiliation(rs.getString("id"),"positions",Map.of("organizationId",rs.getString("org"),"positionId",rs.getString("pos"),"enabled",String.valueOf(rs.getBoolean("enabled")),"startsOn",rs.getTimestamp("starts_at").toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString(),"endsOn",rs.getTimestamp("ends_at")==null?"":rs.getTimestamp("ends_at").toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString(),"remarks",rs.getString("remarks"))),tenant.toString(),person.toString()));
    return List.copyOf(result);
  }
  @Override public boolean saveAffiliation(UUID tenant,UUID person,String section,UUID id,boolean create,Map<String,String> values){
    String org=values.get("organizationId");var catalog=catalog(tenant);
    if(catalog.organizations().stream().noneMatch(o->o.id().equalsIgnoreCase(org)&&o.enabled()))throw new IllegalArgumentException("PERSON_ORGANIZATION_UNAVAILABLE");
    if(section.equals("organizations")){
      boolean primary=Boolean.parseBoolean(values.getOrDefault("isPrimary","false")),enabled=!values.getOrDefault("enabled","true").equals("false");
      if(primary)jdbc.update("UPDATE tenant_person_organization SET is_primary=FALSE,version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND is_primary=TRUE",tenant.toString(),person.toString());
      if(create)return jdbc.update("INSERT INTO tenant_person_organization(tenant_id,id,person_id,organization_id,is_primary,starts_on,ends_on,enabled,remarks) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,NULLIF(?,''),NULLIF(?,''),?,?)",tenant.toString(),id.toString(),person.toString(),org,primary,values.get("startsOn"),values.get("endsOn"),enabled,values.getOrDefault("remarks",""))==1;
      return jdbc.update("UPDATE tenant_person_organization SET organization_id=UUID_TO_BIN(?),is_primary=?,starts_on=NULLIF(?,''),ends_on=NULLIF(?,''),enabled=?,remarks=?,version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",org,primary,values.get("startsOn"),values.get("endsOn"),enabled,values.getOrDefault("remarks",""),tenant.toString(),person.toString(),id.toString())==1;
    }
    if(!section.equals("positions"))throw new IllegalArgumentException("INVALID_PERSON_AFFILIATION");
    String pos=values.get("positionId");if(catalog.positions().stream().noneMatch(p->p.id().equalsIgnoreCase(pos)&&p.organizationId().equalsIgnoreCase(org)&&p.enabled()))throw new IllegalArgumentException("PERSON_ORGANIZATION_UNAVAILABLE");
    String starts=values.getOrDefault("startsOn","").isBlank()?"1970-01-02":values.get("startsOn");String ends=values.getOrDefault("endsOn","");boolean enabled=!values.getOrDefault("enabled","true").equals("false");
    if(create)return jdbc.update("INSERT INTO tenant_person_position(tenant_id,id,person_id,position_id,starts_at,ends_at,enabled,remarks) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,NULLIF(?,''),?,?)",tenant.toString(),id.toString(),person.toString(),pos,starts,ends,enabled,values.getOrDefault("remarks",""))==1;
    if(!enabled)jdbc.update("DELETE FROM tenant_person_leader WHERE tenant_id=UUID_TO_BIN(?) AND assignment_id=UUID_TO_BIN(?)",tenant.toString(),id.toString());
    return jdbc.update("UPDATE tenant_person_position SET position_id=UUID_TO_BIN(?),starts_at=?,ends_at=NULLIF(?,''),enabled=?,remarks=?,version=version+1 WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",pos,starts,ends,enabled,values.getOrDefault("remarks",""),tenant.toString(),person.toString(),id.toString())==1;
  }
  @Override public boolean deleteAffiliation(UUID tenant,UUID person,String section,UUID id){
    if(section.equals("positions"))jdbc.update("DELETE l FROM tenant_person_leader l JOIN tenant_person_position a ON a.tenant_id=l.tenant_id AND a.id=l.assignment_id WHERE a.tenant_id=UUID_TO_BIN(?) AND a.person_id=UUID_TO_BIN(?) AND a.id=UUID_TO_BIN(?)",tenant.toString(),person.toString(),id.toString());
    String table=switch(section){case "organizations"->"tenant_person_organization";case "positions"->"tenant_person_position";default->throw new IllegalArgumentException("INVALID_PERSON_AFFILIATION");};
    return jdbc.update("DELETE FROM "+table+" WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",tenant.toString(),person.toString(),id.toString())==1;
  }
  @Override public List<String> resolvePersons(UUID tenant,UUID organization,String kind,String reference){
    if(!Set.of("POSITION","LEADER").contains(kind))return List.of();
    String sql="SELECT DISTINCT BIN_TO_UUID(a.person_id) person_id FROM tenant_person_position a JOIN tenant_organization_position p ON p.tenant_id=a.tenant_id AND p.id=a.position_id JOIN tenant_organization_unit u ON u.tenant_id=p.tenant_id AND u.id=p.organization_id "+(kind.equals("LEADER")?"JOIN tenant_person_leader l ON l.tenant_id=a.tenant_id AND l.assignment_id=a.id AND l.organization_id=u.id ":"")+"WHERE a.tenant_id=UUID_TO_BIN(?) AND u.id=UUID_TO_BIN(?) AND a.enabled=TRUE AND p.enabled=TRUE AND u.enabled=TRUE AND a.starts_at<=CURRENT_TIMESTAMP(6) AND (a.ends_at IS NULL OR a.ends_at>CURRENT_TIMESTAMP(6))";
    if(kind.equals("POSITION")){UUID.fromString(reference);return jdbc.queryForList(sql+" AND p.id=UUID_TO_BIN(?)",String.class,tenant.toString(),organization.toString(),reference);}
    return jdbc.queryForList(sql,String.class,tenant.toString(),organization.toString());
  }
  @Override public String accountName(UUID tenant,String account){
    if(account==null)return "";
    return jdbc.query("SELECT a.display_name FROM identity_account a JOIN tenant_member m ON m.account_id=a.id WHERE m.tenant_id=UUID_TO_BIN(?) AND a.id=UUID_TO_BIN(?)",(rs,n)->rs.getString(1),tenant.toString(),account).stream().findFirst().orElse("");
  }
}
