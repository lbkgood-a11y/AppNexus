package com.appnexus.masterdata.adapter.persistence;
import java.util.*;
import com.appnexus.platform.authorization.DataAccessDeclaration;
import com.appnexus.masterdata.application.product.ProductTemplateRepository;
import org.springframework.stereotype.Component;
/** 固定存储适配，端口类型和语义参数不携带技术位置绑定，驱动时间在边界转换。 */
@Component
public final class MyBatisProductTemplateRepository implements ProductTemplateRepository, DataAccessDeclaration.Executor {
 /** @return 由原用例授权和存储层固定租户条件保护的资源，未开放可配置行范围 */
 @Override public Set<String> enforcedResources(){return Set.of("masterdata.producttemplate", "masterdata.producttemplate.version", "masterdata.producttemplate.item");}
 private final ProductTemplateMapper mapper;
 private final MasterStatusHistoryMapper statusHistory;
 /** @param mapper 原固定SQL映射，与父聚合共用事务连接 */
 public MyBatisProductTemplateRepository(ProductTemplateMapper mapper, MasterStatusHistoryMapper statusHistory){this.mapper=mapper;this.statusHistory=statusHistory;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> versions(String tenant,String template){return ProductStorageValues.stable(mapper.versions(tenant,template));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> items(String tenant,String templateVersion){return ProductStorageValues.stable(mapper.items(tenant,templateVersion));}
 /** {@inheritDoc} */
 public int createTemplate(String id,String tenant,String code,String name,String purpose,String creator,String updater){return mapper.createTemplate(id,tenant,code,name,purpose,creator,updater);}
 /** {@inheritDoc} */
 public String purpose(String tenant,String template){return mapper.purpose(tenant,template);}
 /** {@inheritDoc} */
 public Long nextVersion(String tenant,String template){return mapper.nextVersion(tenant,template);}
 /** {@inheritDoc} */
 public int createVersion(String id,String tenant,String template,long revision,String purpose,String description,String creator,String updater){return mapper.createVersion(id,tenant,template,revision,purpose,description,creator,updater);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> versionOwnerAndState(String tenant,String templateVersion){return ProductStorageValues.stable(mapper.versionOwnerAndState(tenant,templateVersion));}
 /** {@inheritDoc} */
 public String lockVersionState(String tenant,String templateVersion){return mapper.lockVersionState(tenant,templateVersion);}
 /** {@inheritDoc} */
 public int deleteVersionItems(String tenant,String templateVersion){return mapper.deleteVersionItems(tenant,templateVersion);}
 /** {@inheritDoc} */
 public int updateDescription(String description,String actor,String tenant,String templateVersion){return mapper.updateDescription(description,actor,tenant,templateVersion);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> attributeRevision(String tenant,String revision,String attribute){return ProductStorageValues.stable(mapper.attributeRevision(tenant,revision,attribute));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> attributeGroup(String tenant,String attribute){return ProductStorageValues.stable(mapper.attributeGroup(tenant,attribute));}
 /** {@inheritDoc} */
 public int createItem(String id,String tenant,String templateVersion,String attribute,String revision,String dataType,String groupCode,String groupName,boolean required,boolean searchable,boolean allowCustom,int sortOrder,String creator,String updater){return mapper.createItem(id,tenant,templateVersion,attribute,revision,dataType,groupCode,groupName,required,searchable,allowCustom,sortOrder,creator,updater);}
 /** {@inheritDoc} */
 public List<String> versionOwners(String tenant,String templateVersion){return ProductStorageValues.stable(mapper.versionOwners(tenant,templateVersion));}
 /** {@inheritDoc} */
 public Integer itemCount(String tenant,String templateVersion){return mapper.itemCount(tenant,templateVersion);}
 /** {@inheritDoc} */
 public Integer unavailableItemCount(String tenant,String templateVersion){return mapper.unavailableItemCount(tenant,templateVersion);}
 /** {@inheritDoc} */
 public int publishVersion(String actor,String tenant,String templateVersion){return mapper.publishVersion(actor,tenant,templateVersion);}
 /** {@inheritDoc} */
 public int updateTemplate(String name,String actor,String tenant,String template,String code,String purpose,long version){return mapper.updateTemplate(name,actor,tenant,template,code,purpose,version);}
 /** {@inheritDoc} */
 public int setTemplateStatus(boolean enabled,String actor,String tenant,String template,long version){int changed = mapper.setTemplateStatus(enabled,actor,tenant,template,version);
  if (changed == 1) statusHistory.append(tenant, "ProductTemplate", template, version, enabled, actor);
  return changed;}
 /** {@inheritDoc} */
 public List<String> retirementOwners(String tenant,String templateVersion){return ProductStorageValues.stable(mapper.retirementOwners(tenant,templateVersion));}
 /** {@inheritDoc} */
 public int retireVersion(String actor,String tenant,String templateVersion){return mapper.retireVersion(actor,tenant,templateVersion);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockTemplate(String tenant,String template){return ProductStorageValues.stable(mapper.lockTemplate(tenant,template));}
 /** {@inheritDoc} */
 public int advanceTemplateVersion(String actor,String tenant,String template){return mapper.advanceTemplateVersion(actor,tenant,template);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> summaries(String tenant){return ProductStorageValues.stable(mapper.summaries(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> versionState(String tenant, String version){return ProductStorageValues.stable(mapper.versionState(tenant,version));}
}
