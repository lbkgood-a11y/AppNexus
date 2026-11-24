package com.appnexus.masterdata.adapter.web.product;
import com.appnexus.masterdata.api.product.*;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.api.masterdata.MasterData;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;
/** 商品结构固定命令入口，身份来自可信会话，禁止通用执行器或任意表写入。 */
@RestController @RequestMapping("/api/master-data/product/{product}/structure-records")
public final class ProductStructureController {
 private final ProductStructureManagement service;
 /** @param service 负责父子归属、CAS、租户和事务的执行器 */
 public ProductStructureController(ProductStructureManagement service){this.service=service;}
 /** 查询商品结构；父子状态、可信租户和当前版本由领域端复核。 */
 @com.appnexus.contracts.operation.RecordAuditNames(paths={"specifications.audit","skus.audit","components.audit"})
 @GetMapping("") @OperationBinding(id="Product.readStructure",reference=true)
 public Object readStructure(HttpServletRequest r,@PathVariable String product){return service.read(c(r),product);}
 /** 新增商品规格；父子状态、可信租户和当前版本由领域端复核。 */
 @PostMapping("/specifications") @OperationBinding(id="Product.createSpecification",reference=true)
 public Object createSpecification(HttpServletRequest r,@PathVariable String product,@RequestBody com.appnexus.masterdata.api.product.ProductStructureCommands.Specification i){return service.createSpecification(c(r),product,i);}
 /** 新增商品SKU；父子状态、可信租户和当前版本由领域端复核。 */
 @PostMapping("/skus") @OperationBinding(id="Product.createSku",reference=true)
 public Object createSku(HttpServletRequest r,@PathVariable String product,@RequestBody com.appnexus.masterdata.api.product.ProductStructureCommands.Sku i){return service.createSku(c(r),product,i);}
 /** 新增组合明细；父子状态、可信租户和当前版本由领域端复核。 */
 @PostMapping("/components") @OperationBinding(id="Product.addComponent",reference=true)
 public Object addComponent(HttpServletRequest r,@PathVariable String product,@RequestBody com.appnexus.masterdata.api.product.ProductStructureCommands.Component i){return service.addComponent(c(r),product,i);}
 /** 启停结构记录；父子状态、可信租户和当前版本由领域端复核。 */
 @PutMapping("/{kind}/{id}/status") @OperationBinding(id="Product.setStructureState",reference=true)
 public Object setStructureState(HttpServletRequest r,@PathVariable String product,@PathVariable String kind,@PathVariable String id,@RequestBody com.appnexus.masterdata.api.product.ProductStructureCommands.State i){return service.state(c(r),product,kind,id,i);}
   /** 查询已授权商品的单位与包装。 */
 @GetMapping("/measurement") @OperationBinding(id="Product.readMeasurement",reference=true)
 public Object measurement(HttpServletRequest r,@PathVariable String product){return service.measurement(c(r),product);}
 /** 以父商品版本保存计量配置。 */
 @PutMapping("/measurement") @OperationBinding(id="Product.saveMeasurement",reference=true)
 public Object saveMeasurement(HttpServletRequest r,@PathVariable String product,@RequestBody ProductMeasurement.Change change){return service.saveMeasurement(c(r),product,change);}
 /** 以父子版本维护现有货号计量，不接受修改货号编码或所属规格。 */
 @PutMapping("/skus/{sku}/measurement") @OperationBinding(id="Product.saveSkuMeasurement",reference=true)
 public Object saveSkuMeasurement(HttpServletRequest r,@PathVariable String product,@PathVariable String sku,
     @RequestBody ProductMeasurement.SkuChange change){
  return service.saveSkuMeasurement(c(r),product,sku,change);
 }
 /** 确认商品生效规格轴模板。 */
 @PutMapping("/specification-template") @OperationBinding(id="Product.saveSpecificationTemplate",reference=true)
 public Object saveSpecificationTemplate(HttpServletRequest r,@PathVariable String product,
     @RequestBody ProductStructureCommands.SpecificationTemplate input){
  return service.saveSpecificationTemplate(c(r),product,input);
 }
 /** 保存 SPU 的生效模板，类目只提供默认候选。 */
 @PutMapping("/sku-attribute-template") @OperationBinding(id="Product.saveSkuAttributeTemplate",reference=true)
 public Object saveSkuAttributeTemplate(HttpServletRequest r,@PathVariable String product,
     @RequestBody ProductStructureCommands.SkuAttributeTemplate input){
  return service.saveSkuAttributeTemplate(c(r),product,input);
 }
 /** 读取授权货号普通属性工作区。 */
 @GetMapping("/skus/{sku}/attributes") @OperationBinding(id="Product.readSkuAttributes",reference=true)
 public Object skuAttributes(HttpServletRequest r,@PathVariable String product,@PathVariable String sku){
  return service.skuAttributes(c(r),product,sku);
 }

 /** 以父子版本保存已有货号的普通属性。 */
 @PutMapping("/skus/{sku}/attributes") @OperationBinding(id="Product.saveSkuAttributes",reference=true)
 public Object saveSkuAttributes(HttpServletRequest r,@PathVariable String product,@PathVariable String sku,
     @RequestBody ProductStructureCommands.SkuAttributes input){
  return service.saveSkuAttributes(c(r),product,sku,input);
 }

 /** 保存该货号的批次模板版本；模板归属和发布状态由服务端校验。 */
 @PutMapping("/skus/{sku}/batch-template") @OperationBinding(id="Product.saveSkuBatchTemplate",reference=true)
 public Object saveSkuBatchTemplate(HttpServletRequest r,@PathVariable String product,@PathVariable String sku,
     @RequestBody ProductStructureCommands.SkuBatchTemplate input){
  return service.saveSkuBatchTemplate(c(r),product,sku,input);
 }
 /** 根据当前商品已存规格试算。 */
 @GetMapping("/measurement/calculation") @OperationBinding(id="Product.calculatePackaging",reference=true)
 public Object calculatePackaging(HttpServletRequest r,@PathVariable String product,@RequestParam String code,@RequestParam String quantity){return service.calculatePackaging(c(r),product,code,quantity);}
 private static VerifiedTenantContext c(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");return c;}
}
