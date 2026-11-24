package com.appnexus.masterdata.adapter.persistence;
import java.util.*;
import com.appnexus.platform.authorization.DataAccessDeclaration;
import com.appnexus.masterdata.application.product.ProductAttributeRepository;
import org.springframework.stereotype.Component;
/** 固定存储适配，端口类型和语义参数不携带技术位置绑定，驱动时间在边界转换。 */
@Component
public final class MyBatisProductAttributeRepository implements ProductAttributeRepository, DataAccessDeclaration.Executor {
 /** @return 由原用例授权和存储层固定租户条件保护的资源，未开放可配置行范围 */
 @Override public Set<String> enforcedResources(){return Set.of("masterdata.productattribute", "masterdata.productattribute.group", "masterdata.productattribute.revision", "masterdata.productattribute.option");}
 private final ProductAttributeMapper mapper;
 private final MasterStatusHistoryMapper statusHistory;
 /** @param mapper 原固定SQL映射，与父聚合共用事务连接 */
 public MyBatisProductAttributeRepository(ProductAttributeMapper mapper, MasterStatusHistoryMapper statusHistory){this.mapper=mapper;this.statusHistory=statusHistory;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> revisions(String tenant,String attribute){return ProductStorageValues.stable(mapper.revisions(tenant,attribute));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> options(String tenant,String revision){return ProductStorageValues.stable(mapper.options(tenant,revision));}
 /** {@inheritDoc} */
 public int createGroup(String id,String tenant,String code,String name,String creator,String updater){return mapper.createGroup(id,tenant,code,name,creator,updater);}
 /** {@inheritDoc} */
 public int createAttribute(String id,String tenant,String code,String group,String creator,String updater){return mapper.createAttribute(id,tenant,code,group,creator,updater);}
 /** {@inheritDoc} */
 public Integer enabledUnitCount(String tenant,String unit){return mapper.enabledUnitCount(tenant,unit);}
 /** {@inheritDoc} */
 public Long nextRevision(String tenant,String attribute){return mapper.nextRevision(tenant,attribute);}
 /** {@inheritDoc} */
 public int createRevision(String id,String tenant,String attribute,long revisionNumber,String name,String dataType,String unit,Integer scale,String rulesJson,String creator,String updater){return mapper.createRevision(id,tenant,attribute,revisionNumber,name,dataType,unit,scale,rulesJson,creator,updater);}
 /** {@inheritDoc} */
 public int createInitialOption(String id,String tenant,String revision,String code,String label,int sortOrder,String creator,String updater){return mapper.createInitialOption(id,tenant,revision,code,label,sortOrder,creator,updater);}
 /** {@inheritDoc} */
 public Integer optionCount(String tenant,String revision){return mapper.optionCount(tenant,revision);}
 /** {@inheritDoc} */
 public int publishRevision(String actor,String tenant,String revision){return mapper.publishRevision(actor,tenant,revision);}
 /** {@inheritDoc} */
 public int createOption(String id,String tenant,String revision,String code,String label,int sortOrder,String creator,String updater){return mapper.createOption(id,tenant,revision,code,label,sortOrder,creator,updater);}
 /** {@inheritDoc} */
 public int updateGroup(String name,String actor,String tenant,String group,String code,long version){return mapper.updateGroup(name,actor,tenant,group,code,version);}
 /** {@inheritDoc} */
 public int updateAttribute(String group,String actor,String tenant,String attribute,String code,long version){return mapper.updateAttribute(group,actor,tenant,attribute,code,version);}
 /** {@inheritDoc} */
 public List<String> revisionOwners(String tenant,String revision){return ProductStorageValues.stable(mapper.revisionOwners(tenant,revision));}
 /** {@inheritDoc} */
 public int retireRevision(String actor,String tenant,String revision){return mapper.retireRevision(actor,tenant,revision);}
 /** {@inheritDoc} */
 public Integer validUnitCount(String tenant,String unit){return mapper.validUnitCount(tenant,unit);}
 /** {@inheritDoc} */
 public int updateRevision(String name,String dataType,String unit,Integer scale,String rulesJson,String actor,String tenant,String revision){return mapper.updateRevision(name,dataType,unit,scale,rulesJson,actor,tenant,revision);}
 /** {@inheritDoc} */
 public int deleteOptions(String tenant,String revision){return mapper.deleteOptions(tenant,revision);}
 /** {@inheritDoc} */
 public int replaceOption(String id,String tenant,String revision,String code,String label,int sortOrder,String creator,String updater){return mapper.replaceOption(id,tenant,revision,code,label,sortOrder,creator,updater);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockAttribute(String tenant,String attribute){return ProductStorageValues.stable(mapper.lockAttribute(tenant,attribute));}
 /** {@inheritDoc} */
 public int advanceAttributeVersion(String actor,String tenant,String attribute){return mapper.advanceAttributeVersion(actor,tenant,attribute);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> revisionParent(String tenant,String revision){return ProductStorageValues.stable(mapper.revisionParent(tenant,revision));}
 /** {@inheritDoc} */
 public String lockRevisionState(String tenant,String revision){return mapper.lockRevisionState(tenant,revision);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> groups(String tenant){return ProductStorageValues.stable(mapper.groups(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> attributes(String tenant){return ProductStorageValues.stable(mapper.attributes(tenant));}
 /** {@inheritDoc} */
 public int status(boolean group, String tenant, String id, boolean enabled, String actor, long version){int changed = mapper.status(group,tenant,id,enabled,actor,version);
  if (changed == 1) statusHistory.append(tenant, group ? "ProductAttribute.group" : "ProductAttribute", id, version, enabled, actor);
  return changed;}
}
