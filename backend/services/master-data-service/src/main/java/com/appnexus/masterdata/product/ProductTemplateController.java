package com.appnexus.masterdata.product;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.MasterData.Rejection;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import static com.appnexus.masterdata.product.ProductTemplateService.*;
/** 商品属性模板固定接口；发布版本后模板项不可编辑。 */
@RestController @RequestMapping("/api/master-data/product-template")
@BusinessObjectType(code="ProductTemplate",nameZh="属性模板",nameEn="Product templates",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class ProductTemplateController {
 private final ProductTemplateService service; public ProductTemplateController(ProductTemplateService service){this.service=service;}
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
 private static VerifiedTenantContext ctx(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;}
}