package com.appnexus.platform.persistence.organization.personnel;
import com.appnexus.platform.organization.personnel.*;
import com.appnexus.platform.organization.port.personnel.PersonnelIdentityQueries;
import com.appnexus.platform.organization.PersonnelCatalog;
import java.util.*;

import org.springframework.stereotype.Component;
/** 人员主数据目录适配，不授予档案读取权限；调用方先检查组织目录操作权限。 */
@Component
public final class PersonnelCatalogAdapter implements PersonnelCatalog {
 private final PersonnelIdentityQueries mapper;
 /**
 * @param mapper 主数据源，SQL 固定当前可信租户 */
 public PersonnelCatalogAdapter(PersonnelIdentityQueries mapper){this.mapper=mapper;}
 public List<Person> active(UUID tenant){return mapper.active(tenant.toString()).stream().map(row->new Person(UUID.fromString((String)row.get("id")),(String)row.get("name"))).toList();}
 public void touch(UUID tenant,UUID person,UUID actor){if(mapper.touch(tenant.toString(),person.toString(),actor.toString())!=1)throw new IllegalArgumentException("PERSON_UNAVAILABLE");}
}
