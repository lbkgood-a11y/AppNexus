package com.appnexus.masterdata.adapter.web.product;
import com.appnexus.masterdata.api.product.ProductCatalogData;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.product.*;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import static com.appnexus.masterdata.api.product.ProductTemplateData.*;
/** 商品属性模板固定接口；发布版本后模板项不可编辑。 */
@RestController @RequestMapping("/api/master-data/product-template")

public final class ProductTemplateController {
 private final ProductTemplateManagement service; public ProductTemplateController(ProductTemplateManagement service){this.service=service;}
 @RecordAuditSource(table="md_product_template") @GetMapping @OperationBinding(id="ProductTemplate.list", reference=true) public Object list(HttpServletRequest r){return service.list(ctx(r));}
 @PostMapping @OperationBinding(id="ProductTemplate.create", reference=true) public Object create(HttpServletRequest r,@RequestBody TemplateInput i){return service.create(ctx(r),i);}
 @PostMapping("/{id}/versions") @OperationBinding(id="ProductTemplate.createVersion", reference=true) public Object version(HttpServletRequest r,@PathVariable String id,@RequestBody VersionInput i){return service.createVersion(ctx(r),id,i);}
 @PostMapping("/versions/{id}/publish") @OperationBinding(id="ProductTemplate.publish", reference=true) public Object publish(HttpServletRequest r,@PathVariable String id,@RequestParam long version){service.publish(ctx(r),id,version);return java.util.Map.of("ok",true);}

 /** 读取模板历史版本。 */
 @GetMapping("/{id}/versions")
 @OperationBinding(id="ProductTemplate.readVersions", reference=true)
 public Object versions(HttpServletRequest r,@PathVariable String id){return service.versions(ctx(r),id);}
 /** 读取固定版本模板项。 */
 @GetMapping("/versions/{id}/items")
 @OperationBinding(id="ProductTemplate.readItems", reference=true)
 public Object items(HttpServletRequest r,@PathVariable String id){return service.items(ctx(r),id);}

 /** 名称维护不改变已发布正文。 */
 @PutMapping("/{id}")
 @OperationBinding(id="ProductTemplate.update", reference=true)
 public void update(HttpServletRequest r,@PathVariable String id,@RequestBody TemplateInput input){service.update(ctx(r),id,input);}
 /** 停用阻止新选，不破坏旧引用。 */
 @PutMapping("/{id}/status")
 @OperationBinding(id="ProductTemplate.setStatus", reference=true)
 public void status(HttpServletRequest r,@PathVariable String id,@RequestBody ProductCatalogData.Status input){service.status(ctx(r),id,input);}
 /** 发布版本退役，保留原定义解释。 */
 @PostMapping("/versions/{id}/retire")
 @OperationBinding(id="ProductTemplate.retire", reference=true)
 public void retire(HttpServletRequest r,@PathVariable String id,@RequestParam long version){service.retire(ctx(r),id,version);}

 /** 全量编辑未发布版本；父CAS和草稿状态防止发布竞态。 */
 @PutMapping("/versions/{id}")
 @OperationBinding(id="ProductTemplate.updateVersion", reference=true)
 public void updateVersion(HttpServletRequest r,@PathVariable String id,@RequestBody VersionInput input){service.updateVersion(ctx(r),id,input);}
 private static VerifiedTenantContext ctx(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;}
}