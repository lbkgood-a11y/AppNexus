package com.appnexus.masterdata.adapter.persistence;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.appnexus.masterdata.application.product.ProductStructureRepository;
import org.springframework.stereotype.Component;
/** 结构固定存储适配；表映射、SQL绑定及驱动时间留在技术边界，不决定业务规则。 */
@Component
public final class MyBatisProductStructureRepository implements ProductStructureRepository {
 private final ProductStructureMapper mapper;
 /** @param mapper 原固定结构SQL映射 */
 public MyBatisProductStructureRepository(ProductStructureMapper mapper){this.mapper=mapper;}
 /** {@inheritDoc} */
 public List<Map<String,Object>> specifications(String tenant,String product){return ProductStorageValues.stable(mapper.specifications(tenant,product));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> skus(String tenant,String product){return ProductStorageValues.stable(mapper.skus(tenant,product));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> components(String tenant,String product){return ProductStorageValues.stable(mapper.components(tenant,product));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> standardDefinitions(String tenant,String configuration){return ProductStorageValues.stable(mapper.standardDefinitions(tenant,configuration));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> standardOption(String tenant,String revision,String option){return ProductStorageValues.stable(mapper.standardOption(tenant,revision,option));}
 /** {@inheritDoc} */
 public int createSpecification(String id,String tenant,String product,String configuration,String mode,String name,String description,String signature,byte[] digest,String creator,String updater){return mapper.createSpecification(id,tenant,product,configuration,mode,name,description,signature,digest,creator,updater);}
 /** {@inheritDoc} */
 public int createStandardValue(String id,String tenant,String specification,String configuration,String item,String revision,String type,Long integerValue,BigDecimal decimalValue,Boolean booleanValue,String option,String creator,String updater){return mapper.createStandardValue(id,tenant,specification,configuration,item,revision,type,integerValue,decimalValue,booleanValue,option,creator,updater);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> customDefinitions(String tenant,String configuration){return ProductStorageValues.stable(mapper.customDefinitions(tenant,configuration));}
 /** {@inheritDoc} */
 public Integer validOptionCount(String tenant,String revision,String option){return mapper.validOptionCount(tenant,revision,option);}
 /** {@inheritDoc} */
 public int createCustomValue(String id,String tenant,String specification,String configuration,String item,String revision,String type,String text,LocalDate dateValue,Long integerValue,BigDecimal decimalValue,Boolean booleanValue,String creator,String updater){return mapper.createCustomValue(id,tenant,specification,configuration,item,revision,type,text,dateValue,integerValue,decimalValue,booleanValue,creator,updater);}
 /** {@inheritDoc} */
 public int createChoice(String id,String tenant,String value,String revision,String type,String option,String customText,String creator,String updater){return mapper.createChoice(id,tenant,value,revision,type,option,customText,creator,updater);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> specificationValues(String tenant,String specification){return ProductStorageValues.stable(mapper.specificationValues(tenant,specification));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> customChoices(String tenant,String value){return ProductStorageValues.stable(mapper.customChoices(tenant,value));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> standardChoices(String tenant,String option){return ProductStorageValues.stable(mapper.standardChoices(tenant,option));}
 /** {@inheritDoc} */
 public Integer specificationCount(String tenant,String product,String specification){return mapper.specificationCount(tenant,product,specification);}
 /** {@inheritDoc} */
 public int createSku(String id,String tenant,String product,String specification,String code,String creator,String updater){return mapper.createSku(id,tenant,product,specification,code,creator,updater);}
 /** {@inheritDoc} */
 public Integer unitPrecision(String tenant,String unit){return mapper.unitPrecision(tenant,unit);}
 /** {@inheritDoc} */
 public int saveMeasurement(String tenant,String product,String configuration){return mapper.saveMeasurement(tenant,product,configuration);}
 /** {@inheritDoc} */
 public boolean hasMeasurementConversion(String tenant,String source,String target){return mapper.hasMeasurementConversion(tenant,source,target);}
 /** {@inheritDoc} */
 public List<String> componentChildren(String tenant,String product){return ProductStorageValues.stable(mapper.componentChildren(tenant,product));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> existingComponent(String tenant,String product,String component){return ProductStorageValues.stable(mapper.existingComponent(tenant,product,component));}
 /** {@inheritDoc} */
 public int updateComponent(BigDecimal quantity,long componentVersion,String actor,String tenant,String id){return mapper.updateComponent(quantity,componentVersion,actor,tenant,id);}
 /** {@inheritDoc} */
 public int createComponent(String id,String tenant,String product,String component,BigDecimal quantity,long componentVersion,String creator,String updater){return mapper.createComponent(id,tenant,product,component,quantity,componentVersion,creator,updater);}
 /** {@inheritDoc} */
 public int ensureGraphGuard(String tenant,String creator,String updater){return mapper.ensureGraphGuard(tenant,creator,updater);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> lockGraphGuard(String tenant){return ProductStorageValues.stable(mapper.lockGraphGuard(tenant));}
 /** {@inheritDoc} */
 public int advanceParentVersion(String actor,String tenant,String product,long version){return mapper.advanceParentVersion(actor,tenant,product,version);}
 /** {@inheritDoc} */
 public int state(String kind, boolean enabled, String actor, String tenant, String product, String id, long version){return mapper.state(kind,enabled,actor,tenant,product,id,version);}
 /** {@inheritDoc} */
 public List<Map<String,Object>> parent(String tenant, String id, boolean lock){return ProductStorageValues.stable(mapper.parent(tenant,id,lock));}
 /** {@inheritDoc} */
 public List<Map<String,Object>> audit(Resource resource,String tenant,String id){return ProductStorageValues.stable(mapper.audit(table(resource),tenant,id));}
 private String table(Resource resource){return switch(resource){case SPECIFICATION->"md_product_specification";case SKU->"md_product_sku";case COMPONENT->"md_product_component";};}
}
