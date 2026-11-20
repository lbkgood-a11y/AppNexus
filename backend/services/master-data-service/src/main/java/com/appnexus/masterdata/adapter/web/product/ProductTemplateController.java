package com.appnexus.masterdata.adapter.web.product;
import com.appnexus.masterdata.api.product.ProductCatalogData;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.product.*;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import static com.appnexus.masterdata.api.product.ProductTemplateData.*;
/** 商品属性模板固定接口；发布版本后模板项不可编辑。 */
@RestController @RequestMapping("/api/master-data/product-template")
@BusinessObjectType(code="ProductTemplate",nameZh="属性模板",nameEn="Product templates",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class ProductTemplateController {
 private final ProductTemplateManagement service; public ProductTemplateController(ProductTemplateManagement service){this.service=service;}
 @RecordAuditSource(table="md_product_template") @GetMapping @OperationBinding(id="ProductTemplate.list",objectType="ProductTemplate",operationCode="list",nameZh="查询属性模板",nameEn="List product templates",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object list(HttpServletRequest r){return service.list(ctx(r));}
 @PostMapping @OperationBinding(id="ProductTemplate.create",objectType="ProductTemplate",operationCode="create",nameZh="新增属性模板",nameEn="Create product template",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:create"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object create(HttpServletRequest r,@RequestBody TemplateInput i){return service.create(ctx(r),i);}
 @PostMapping("/{id}/versions") @OperationBinding(id="ProductTemplate.createVersion",objectType="ProductTemplate",operationCode="createVersion",nameZh="新增模板版本",nameEn="Create template version",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:update"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object version(HttpServletRequest r,@PathVariable String id,@RequestBody VersionInput i){return service.createVersion(ctx(r),id,i);}
 @PostMapping("/versions/{id}/publish") @OperationBinding(id="ProductTemplate.publish",objectType="ProductTemplate",operationCode="publish",nameZh="发布属性模板",nameEn="Publish product template",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:publish"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object publish(HttpServletRequest r,@PathVariable String id,@RequestParam long version){service.publish(ctx(r),id,version);return java.util.Map.of("ok",true);}

 /** 读取模板历史版本。 */
 @GetMapping("/{id}/versions")
 @OperationBinding(id="ProductTemplate.readVersions",objectType="ProductTemplate",operationCode="readVersions",nameZh="查看模板版本",nameEn="Read template versions",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:read"},policy="可信租户及独立读取权")
 public Object versions(HttpServletRequest r,@PathVariable String id){return service.versions(ctx(r),id);}
 /** 读取固定版本模板项。 */
 @GetMapping("/versions/{id}/items")
 @OperationBinding(id="ProductTemplate.readItems",objectType="ProductTemplate",operationCode="readItems",nameZh="查看模板项",nameEn="Read template items",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:read"},policy="可信租户及独立读取权")
 public Object items(HttpServletRequest r,@PathVariable String id){return service.items(ctx(r),id);}

 /** 名称维护不改变已发布正文。 */
 @PutMapping("/{id}")
 @OperationBinding(id="ProductTemplate.update",objectType="ProductTemplate",operationCode="update",nameZh="编辑模板定义",nameEn="Update template definition",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:update"},policy="可信租户、固定编码/用途与CAS")
 public void update(HttpServletRequest r,@PathVariable String id,@RequestBody TemplateInput input){service.update(ctx(r),id,input);}
 /** 停用阻止新选，不破坏旧引用。 */
 @PutMapping("/{id}/status")
 @OperationBinding(id="ProductTemplate.setStatus",objectType="ProductTemplate",operationCode="setStatus",nameZh="启停模板",nameEn="Set template status",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:set_status"},policy="可信租户、独立启停权限与CAS")
 public void status(HttpServletRequest r,@PathVariable String id,@RequestBody ProductCatalogData.Status input){service.status(ctx(r),id,input);}
 /** 发布版本退役，保留原定义解释。 */
 @PostMapping("/versions/{id}/retire")
 @OperationBinding(id="ProductTemplate.retire",objectType="ProductTemplate",operationCode="retire",nameZh="退役模板版本",nameEn="Retire template version",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:publish"},policy="可信租户、发布权限与父模板CAS")
 public void retire(HttpServletRequest r,@PathVariable String id,@RequestParam long version){service.retire(ctx(r),id,version);}

 /** 全量编辑未发布版本；父CAS和草稿状态防止发布竞态。 */
 @PutMapping("/versions/{id}")
 @OperationBinding(id="ProductTemplate.updateVersion",objectType="ProductTemplate",operationCode="updateVersion",nameZh="编辑模板版本草稿",nameEn="Update template draft version",pages={"master-data/product-template"},permissions={"masterdata.producttemplate:update"},policy="可信租户、DRAFT及父模板CAS，正文发布后不可改")
 public void updateVersion(HttpServletRequest r,@PathVariable String id,@RequestBody VersionInput input){service.updateVersion(ctx(r),id,input);}
 private static VerifiedTenantContext ctx(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;}
}