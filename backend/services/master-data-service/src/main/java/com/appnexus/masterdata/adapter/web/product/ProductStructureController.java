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
 private static VerifiedTenantContext c(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");return c;}
}
