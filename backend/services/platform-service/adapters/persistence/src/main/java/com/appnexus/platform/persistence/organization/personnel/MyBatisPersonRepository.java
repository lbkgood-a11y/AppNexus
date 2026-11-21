package com.appnexus.platform.persistence.organization.personnel;
import com.appnexus.platform.organization.PersonData;
import java.util.*;
import com.appnexus.platform.organization.PersonData.Input;
import com.appnexus.platform.organization.port.personnel.PersonRepository;
import org.springframework.stereotype.Component;
/** 人员固定存储适配；SQL留在Mapper，驱动时间在边界转换，写入参与原用例事务。 */
@Component
public final class MyBatisPersonRepository implements PersonRepository, com.appnexus.platform.authorization.DataAccessDeclaration.Executor {
 /** @return 由原权限用例和该适配器的固定数据边界共同约束的资源编码 */
 @Override public java.util.Set<String> enforcedResources(){return java.util.Set.of("masterdata.person");}
 private final PersonRecordMapper mapper;
 /** @param mapper 固定人员SQL映射，不接收自由SQL */
 public MyBatisPersonRepository(PersonRecordMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> list( String tenant, String keyword, Boolean enabled, int size, int offset){return PersonnelStorageValues.stable(mapper.list(tenant,keyword,enabled,size,offset));}
 /** {@inheritDoc} */
 public long count( String tenant, String keyword, Boolean enabled){return mapper.count(tenant,keyword,enabled);}
 /** {@inheritDoc} */
 public int create( String tenant, String id, Input input, Map<String,String> values, String actor){return mapper.create(tenant,id,input,values,actor);}
 /** {@inheritDoc} */
 public int update( String tenant, String id, Input input, Map<String,String> values){return mapper.update(tenant,id,input,values);}
 /** {@inheritDoc} */
 public int status( String tenant, String id, boolean enabled, long version){return mapper.status(tenant,id,enabled,version);}
 /** {@inheritDoc} */
 public List<Map<String,String>> previous( String tenant, String id){return PersonnelStorageValues.stable(mapper.previous(tenant,id));}
 /** {@inheritDoc} */
 public int audit( String tenant, String id, String actor){return mapper.audit(tenant,id,actor);}
}
