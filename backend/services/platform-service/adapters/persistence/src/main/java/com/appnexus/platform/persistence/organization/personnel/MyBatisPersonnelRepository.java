package com.appnexus.platform.persistence.organization.personnel;
import java.util.*;
import com.appnexus.platform.organization.port.personnel.PersonnelRepository;
import org.springframework.stereotype.Component;
/** 人员明细固定仓储；驱动时间转换为Instant，权限与完整用例事务由应用协调。 */
@Component
public final class MyBatisPersonnelRepository implements PersonnelRepository {
 private final PersonnelMapper mapper;
 /** @param mapper 固定人员明细SQL映射 */
 public MyBatisPersonnelRepository(PersonnelMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<String> readDetailContent(String tenant,String person,String id,String section){return mapper.readDetailContent(tenant,person,id,section);}
 /** {@inheritDoc} */
 public int createDetail(String tenant,String id,String person,String section,String content){return mapper.createDetail(tenant,id,person,section,content);}
 /** {@inheritDoc} */
 public int updateDetail(String content,String tenant,String person,String id,String section){return mapper.updateDetail(content,tenant,person,id,section);}
 /** {@inheritDoc} */
 public int deleteDetail(String tenant,String person,String id,String section){return mapper.deleteDetail(tenant,person,id,section);}
 /** {@inheritDoc} */
 public int createAssignment(String tenant,String id,String person,String member,String actor){return mapper.createAssignment(tenant,id,person,member,actor);}
 /** {@inheritDoc} */
 public int updateAssignment(String member,String tenant,String person,String id){return mapper.updateAssignment(member,tenant,person,id);}
 /** {@inheritDoc} */
 public int deleteAssignment(String tenant,String person,String id){return mapper.deleteAssignment(tenant,person,id);}
 /** {@inheritDoc} */
 public Integer countActiveIdentity(String tenant,String person,String member){return mapper.countActiveIdentity(tenant,person,member);}
 /** {@inheritDoc} */
 public int advanceVersion(String actor,String tenant,String person,long version){return mapper.advanceVersion(actor,tenant,person,version);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> profile(String tenant,String id){return PersonnelStorageValues.stable(mapper.profile(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> audit(String tenant,String id){return PersonnelStorageValues.stable(mapper.audit(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> details(String tenant,String id){return PersonnelStorageValues.stable(mapper.details(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> assignments(String tenant,String id){return PersonnelStorageValues.stable(mapper.assignments(tenant,id));}
}
