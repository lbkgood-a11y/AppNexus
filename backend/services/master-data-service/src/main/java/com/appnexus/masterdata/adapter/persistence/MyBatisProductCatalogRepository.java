package com.appnexus.masterdata.adapter.persistence;
import java.util.*;
import com.appnexus.platform.authorization.DataAccessDeclaration;
import com.appnexus.masterdata.application.product.ProductCatalogRepository;
import org.springframework.stereotype.Component;
/** 固定存储适配，端口类型和语义参数不携带技术位置绑定，驱动时间在边界转换。 */
@Component
public final class MyBatisProductCatalogRepository implements ProductCatalogRepository, DataAccessDeclaration.Executor {
 /** @return 由原用例授权和存储层固定租户条件保护的资源，未开放可配置行范围 */
 @Override public Set<String> enforcedResources(){return Set.of("masterdata.productcategory", "masterdata.productunit", "masterdata.productunit.group", "masterdata.productcategory.configuration");}
 private final ProductCatalogMapper mapper;
 private final MasterStatusHistoryMapper statusHistory;
 /** @param mapper 原固定SQL映射，与父聚合共用事务连接 */
 public MyBatisProductCatalogRepository(ProductCatalogMapper mapper, MasterStatusHistoryMapper statusHistory){this.mapper=mapper;this.statusHistory=statusHistory;}
 /** {@inheritDoc} */
 public int createUnit(String id,String tenant,String code,String name,String dimension,int scale,String creator,String updater){return mapper.createUnit(id,tenant,code,name,dimension,scale,creator,updater);}
 /** {@inheritDoc} */
 public int renameUnit(String name,String actor,String tenant,String unit,String code,String dimension,int scale,long version){return mapper.renameUnit(name,actor,tenant,unit,code,dimension,scale,version);}
 /** {@inheritDoc} */
 public int setUnitStatus(boolean enabled,String actor,String tenant,String unit,long version){int changed = mapper.setUnitStatus(enabled,actor,tenant,unit,version);
  if (changed == 1) statusHistory.append(tenant, "ProductUnit", unit, version, enabled, actor);
  return changed;}
 /** {@inheritDoc} */
 public int createCategory(String id,String tenant,String code,String name,String parent,String creator,String updater){return mapper.createCategory(id,tenant,code,name,parent,creator,updater);}
 /** {@inheritDoc} */
 public int moveCategory(String name,String parent,String actor,String tenant,String category,long version){return mapper.moveCategory(name,parent,actor,tenant,category,version);}
 /** {@inheritDoc} */
 public int setCategoryStatus(boolean enabled,String actor,String tenant,String category,long version){int changed = mapper.setCategoryStatus(enabled,actor,tenant,category,version);
  if (changed == 1) statusHistory.append(tenant, "ProductCategory", category, version, enabled, actor);
  return changed;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> publishedTemplate(String tenant,String templateVersion){return ProductStorageValues.stable(mapper.publishedTemplate(tenant,templateVersion));}
 /** {@inheritDoc} */
 public List<String> templateAttributes(String tenant,String templateVersion){return ProductStorageValues.stable(mapper.templateAttributes(tenant,templateVersion));}
 /** {@inheritDoc} */
 public Long nextConfigurationRevision(String tenant,String category){return mapper.nextConfigurationRevision(tenant,category);}
 /** {@inheritDoc} */
 public int createConfiguration(String id,String tenant,String category,long revision,String path,boolean serviceUnitRequired,String creator,String updater){return mapper.createConfiguration(id,tenant,category,revision,path,serviceUnitRequired,creator,updater);}
 /** {@inheritDoc} */
 public int bindTemplate(String id,String tenant,String configuration,String purpose,String templateVersion,String creator,String updater){return mapper.bindTemplate(id,tenant,configuration,purpose,templateVersion,creator,updater);}
 /** {@inheritDoc} */
 public int freezeConfigurationItem(String creator,String updater,String tenant,String configuration){return mapper.freezeConfigurationItem(creator,updater,tenant,configuration);}
 /** {@inheritDoc} */
 public int activateConfiguration(String configuration,String actor,String tenant,String category,long version){return mapper.activateConfiguration(configuration,actor,tenant,category,version);}
 /** {@inheritDoc} */
 public int ensureCategoryGuard(String tenant,String creator,String updater){return mapper.ensureCategoryGuard(tenant,creator,updater);}
 /** {@inheritDoc} */
 public String lockCategoryGuard(String tenant){return mapper.lockCategoryGuard(tenant);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> units(String tenant, String keyword, String group, int size, int offset){return ProductStorageValues.stable(mapper.units(tenant,keyword,group,size,offset));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> unitGroups(String tenant){return mapper.unitGroups(tenant);}
 /** {@inheritDoc} */
 public void ensureUnitGroup(String tenant,String code,String actor){mapper.ensureUnitGroup(tenant,code,actor);}
 /** {@inheritDoc} */
 public void createUnitGroup(String tenant,String code,String name,String actor){mapper.createUnitGroup(tenant,code,name,actor);}
 /** {@inheritDoc} */
 public int renameUnitGroup(String tenant,String code,String name,String actor,long version){return mapper.renameUnitGroup(tenant,code,name,actor,version);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> unit(String tenant, String id){return ProductStorageValues.stable(mapper.unit(tenant,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> configuration(String tenant, String category, String id){return ProductStorageValues.stable(mapper.configuration(tenant,category,id));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> tree(String tenant){return ProductStorageValues.stable(mapper.tree(tenant));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> slots(String tenant, String id){return ProductStorageValues.stable(mapper.slots(tenant,id));}
 /** {@inheritDoc} */
 public long unitCount(String tenant, String keyword, String group){return mapper.unitCount(tenant,keyword,group);}
}
