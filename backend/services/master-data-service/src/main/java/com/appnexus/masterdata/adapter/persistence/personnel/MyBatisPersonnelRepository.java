package com.appnexus.masterdata.adapter.persistence.personnel;
import java.util.*;
import com.appnexus.masterdata.organization.port.personnel.PersonnelRepository;
import org.springframework.stereotype.Component;
/** 人员明细固定仓储；驱动时间转换为Instant，权限与完整用例事务由应用协调。 */
@Component
public final class MyBatisPersonnelRepository implements PersonnelRepository, com.appnexus.platform.authorization.DataAccessDeclaration.Executor {
 /** @return 由原权限用例和该适配器的固定数据边界共同约束的资源编码 */
 @Override public java.util.Set<String> enforcedResources(){return java.util.Set.of("masterdata.person.detail");}
 private final PersonnelMapper mapper;
 private final MyBatisPersonRepository personRecords;
 /**
  * @param mapper 固定人员明细 SQL 映射
  * @param personRecords 详情和列表复用的人员范围编译
  */
 public MyBatisPersonnelRepository(PersonnelMapper mapper,MyBatisPersonRepository personRecords){this.mapper=mapper;this.personRecords=personRecords;}
 /** {@inheritDoc} */
 @Override public void lockForWrite(com.appnexus.platform.tenant.VerifiedTenantContext context,String personId,String action){
   personRecords.lockForWrite(context,personId,action,true);
 }
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
 public List<Map<String,Object>> profile(com.appnexus.platform.tenant.VerifiedTenantContext context,String id){
   var predicate=personRecords.readPredicate(context);
   var rows=mapper.profile(context.tenantId().toString(),id,predicate);
   if(!rows.isEmpty())personRecords.authorizeProfileDetails(predicate,id);
   return PersonnelStorageValues.stable(rows);
 }
 /** {@inheritDoc} */
 public List<Map<String,Object>> audit(String tenant,String id){return PersonnelStorageValues.stable(mapper.audit(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> details(String tenant,String id){return PersonnelStorageValues.stable(mapper.details(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> assignments(String tenant,String id){return PersonnelStorageValues.stable(mapper.assignments(tenant,id));}
}
