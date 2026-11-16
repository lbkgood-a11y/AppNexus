package com.appnexus.platform.organization.personnel;
import com.appnexus.platform.organization.PersonnelCatalog;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** 人员主数据目录适配，不授予档案读取权限；调用方先检查组织目录操作权限。 */
@Component
public final class PersonnelCatalogAdapter implements PersonnelCatalog {
 private final JdbcTemplate jdbc;
 /**
 * @param jdbc 主数据源，SQL 固定当前可信租户 */
 public PersonnelCatalogAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Person> active(UUID tenant){return jdbc.query("SELECT BIN_TO_UUID(id) id,name FROM md_person WHERE tenant_id=UUID_TO_BIN(?) AND enabled=TRUE AND employment_status IN ('ACTIVE','PROBATION') ORDER BY name,id",(rs,n)->new Person(UUID.fromString(rs.getString("id")),rs.getString("name")),tenant.toString());}
 public void touch(UUID tenant,UUID person,UUID actor){if(jdbc.update("UPDATE md_person SET version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND enabled=TRUE",actor.toString(),tenant.toString(),person.toString())!=1)throw new IllegalArgumentException("PERSON_UNAVAILABLE");}
}
