package com.appnexus.masterdata.adapter.persistence;
import java.util.*;
import com.appnexus.masterdata.application.product.ProductCommandRepository;
import org.springframework.stereotype.Component;
/** 固定商品存储适配；类型与语义参数取自业务端口，Mapper位置绑定只存在技术边界。 */
@Component
public final class MyBatisProductCommandRepository implements ProductCommandRepository {
 private final ProductMainMapper mapper;
 /** @param mapper 固定SQL映射，与父聚合使用同一事务连接 */
 public MyBatisProductCommandRepository(ProductMainMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public int createProduct(String id,String tenant,String code,String name,String shortName,String category,String configuration,String form,String unit,String description,String brand,String origin,String barcode,String organization,String creator,String updater){return mapper.createProduct(id,tenant,code,name,shortName,category,configuration,form,unit,description,brand,origin,barcode,organization,creator,updater);}
 /** {@inheritDoc} */
 public int updateMainFields(String name,String shortName,String description,String brand,String origin,String barcode,String organization,String tenant,String id){return mapper.updateMainFields(name,shortName,description,brand,origin,barcode,organization,tenant,id);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> approvalStates(String tenant,String product){return ProductStorageValues.stable(mapper.approvalStates(tenant,product));}
 /** {@inheritDoc} */
 public int setProductStatus(String status,String tenant,String product){return mapper.setProductStatus(status,tenant,product);}
 /** {@inheritDoc} */
 public int changeStructureTarget(String category,String configuration,String form,String unit,String disposition,String tenant,String product){return mapper.changeStructureTarget(category,configuration,form,unit,disposition,tenant,product);}
 /** {@inheritDoc} */
 public int retireSpecification(String actor,String tenant,String product,String specification){return mapper.retireSpecification(actor,tenant,product,specification);}
 /** {@inheritDoc} */
 public int changeSpecificationMode(String mode,String tenant,String product){return mapper.changeSpecificationMode(mode,tenant,product);}
 /** {@inheritDoc} */
 public int disableComponents(String actor,String tenant,String product){return mapper.disableComponents(actor,tenant,product);}
 /** {@inheritDoc} */
 public int saveMigrationResult(String id,String tenant,String actor,String product,String requestKey,String digest,long productVersion,long structureRevision,String changeId,String resultJson,String creator,String updater){return mapper.saveMigrationResult(id,tenant,actor,product,requestKey,digest,productVersion,structureRevision,changeId,resultJson,creator,updater);}
 /** {@inheritDoc} */
 public Boolean serviceUnitRequired(String tenant,String configuration){return mapper.serviceUnitRequired(tenant,configuration);}
 /** {@inheritDoc} */
 public List<String> requiredItems(String tenant,String configuration){return ProductStorageValues.stable(mapper.requiredItems(tenant,configuration));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> categoryAdmission(String tenant,String category){return ProductStorageValues.stable(mapper.categoryAdmission(tenant,category));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> templateAdmission(String tenant,String configuration){return ProductStorageValues.stable(mapper.templateAdmission(tenant,configuration));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> unitAdmission(String tenant,String unit){return ProductStorageValues.stable(mapper.unitAdmission(tenant,unit));}
 /** {@inheritDoc} */
 public int deleteChoices(String tenant,String product){return mapper.deleteChoices(tenant,product);}
 /** {@inheritDoc} */
 public int deleteValues(String tenant,String product){return mapper.deleteValues(tenant,product);}
 /** {@inheritDoc} */
 public int reviewLegacyDisposition(String disposition,String tenant,String product){return mapper.reviewLegacyDisposition(disposition,tenant,product);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockLegacySource(String tenant,String product){return ProductStorageValues.stable(mapper.lockLegacySource(tenant,product));}
 /** {@inheritDoc} */
 public Integer stagedCount(String tenant,String product){return mapper.stagedCount(tenant,product);}
 /** {@inheritDoc} */
 public int stageLegacyProduct(String tenant,String product){return mapper.stageLegacyProduct(tenant,product);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> legacyReconciliation(String tenant,int size,long offset){return ProductStorageValues.stable(mapper.legacyReconciliation(tenant,size,offset));}
 /** {@inheritDoc} */
 public Long legacyCount(String tenant){return mapper.legacyCount(tenant);}
 /** {@inheritDoc} */
 public Integer pendingApprovalCount(String tenant,String product){return mapper.pendingApprovalCount(tenant,product);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> attributeDefinition(String tenant,String configuration,String item){return ProductStorageValues.stable(mapper.attributeDefinition(tenant,configuration,item));}
 /** {@inheritDoc} */
 public Integer validOptionCount(String tenant,String revision,String option){return mapper.validOptionCount(tenant,revision,option);}
 /** {@inheritDoc} */
 public int archive(String tenant, String id, boolean archived){return mapper.archive(tenant,id,archived);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> replay(String tenant, String actor, String id, String key, boolean lock){return ProductStorageValues.stable(mapper.replay(tenant,actor,id,key,lock));}
}
