package com.appnexus.masterdata.adapter.web.product;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.product.*;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import static com.appnexus.masterdata.api.product.ProductAttributeData.*;
/** 商品属性管理固定接口；修订发布后不可原地覆盖。 */
@RestController @RequestMapping("/api/master-data/product-attribute")
@BusinessObjectType(code="ProductAttribute",nameZh="商品属性",nameEn="Product attributes",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class ProductAttributeController {
 private final ProductAttributeManagement service; public ProductAttributeController(ProductAttributeManagement service){this.service=service;}
 @RecordAuditSource(table="md_product_attribute_group") @GetMapping("/groups") @OperationBinding(id="ProductAttribute.listGroups",objectType="ProductAttribute",operationCode="listGroups",nameZh="查询属性分组",nameEn="List attribute groups",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object groups(HttpServletRequest r){return service.groups(ctx(r));}
 @PostMapping("/groups") @OperationBinding(id="ProductAttribute.createGroup",objectType="ProductAttribute",operationCode="createGroup",nameZh="新增属性分组",nameEn="Create attribute group",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:create"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object createGroup(HttpServletRequest r,@RequestBody GroupInput i){return service.createGroup(ctx(r),i);}
 @RecordAuditSource(table="md_product_attribute") @GetMapping("") @OperationBinding(id="ProductAttribute.list",objectType="ProductAttribute",operationCode="list",nameZh="查询商品属性",nameEn="List product attributes",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object attributes(HttpServletRequest r){return service.attributes(ctx(r));}
 @PostMapping("") @OperationBinding(id="ProductAttribute.create",objectType="ProductAttribute",operationCode="create",nameZh="新增商品属性",nameEn="Create product attribute",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:create"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object create(HttpServletRequest r,@RequestBody AttributeInput i){return service.createAttribute(ctx(r),i);}
 @PostMapping("/{id}/revisions") @OperationBinding(id="ProductAttribute.createRevision",objectType="ProductAttribute",operationCode="createRevision",nameZh="新增属性修订",nameEn="Create attribute revision",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:update"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object revision(HttpServletRequest r,@PathVariable String id,@RequestBody RevisionInput i){return service.createRevision(ctx(r),id,i);}
 @PostMapping("/revisions/{id}/publish") @OperationBinding(id="ProductAttribute.publishRevision",objectType="ProductAttribute",operationCode="publishRevision",nameZh="发布属性修订",nameEn="Publish attribute revision",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:publish"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object publish(HttpServletRequest r,@PathVariable String id,@RequestParam long version){service.publishRevision(ctx(r),id,version);return java.util.Map.of("ok",true);}
 @PostMapping("/revisions/{id}/options") @OperationBinding(id="ProductAttribute.createOption",objectType="ProductAttribute",operationCode="createOption",nameZh="新增属性选项",nameEn="Create attribute option",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:update"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object option(HttpServletRequest r,@PathVariable String id,@RequestBody OptionInput i){return service.createOption(ctx(r),id,i);}

 /** 读取属性固定修订列表。 */
 @GetMapping("/{id}/revisions")
 @OperationBinding(id="ProductAttribute.readRevisions",objectType="ProductAttribute",operationCode="readRevisions",nameZh="查看属性修订",nameEn="Read attribute revisions",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:read"},policy="可信租户及独立读取权")
 public Object revisions(HttpServletRequest r,@PathVariable String id){return service.revisions(ctx(r),id);}
 /** 读取固定修订枚举选项。 */
 @GetMapping("/revisions/{id}/options")
 @OperationBinding(id="ProductAttribute.readOptions",objectType="ProductAttribute",operationCode="readOptions",nameZh="查看属性选项",nameEn="Read attribute options",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:read"},policy="可信租户及独立读取权")
 public Object options(HttpServletRequest r,@PathVariable String id){return service.options(ctx(r),id);}

 /** 固定属性配置维护命令，可信租户、独立权限、当前父CAS与四审计同事务。 */
 @PutMapping("/groups/{id}")
 @OperationBinding(id="ProductAttribute.updateGroup",objectType="ProductAttribute",operationCode="updateGroup",nameZh="维护商品属性配置",nameEn="Maintain product attribute configuration",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:update"},policy="可信租户、独立维护权限与CAS")
 public void updateGroup(HttpServletRequest r,@PathVariable String id,@RequestBody GroupInput input){service.updateGroup(ctx(r),id,input);}

 /** 固定属性配置维护命令，可信租户、独立权限、当前父CAS与四审计同事务。 */
 @PutMapping("/{id}")
 @OperationBinding(id="ProductAttribute.update",objectType="ProductAttribute",operationCode="update",nameZh="维护商品属性配置",nameEn="Maintain product attribute configuration",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:update"},policy="可信租户、独立维护权限与CAS")
 public void update(HttpServletRequest r,@PathVariable String id,@RequestBody AttributeInput input){service.updateAttribute(ctx(r),id,input);}

 /** 固定属性配置维护命令，可信租户、独立权限、当前父CAS与四审计同事务。 */
 @PutMapping("/groups/{id}/status")
 @OperationBinding(id="ProductAttribute.setGroupStatus",objectType="ProductAttribute",operationCode="setGroupStatus",nameZh="维护商品属性配置",nameEn="Maintain product attribute configuration",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:set_status"},policy="可信租户、独立维护权限与CAS")
 public void groupStatus(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.masterdata.api.product.ProductCatalogData.Status input){service.setStatus(ctx(r),id,input,true);}

 /** 固定属性配置维护命令，可信租户、独立权限、当前父CAS与四审计同事务。 */
 @PutMapping("/{id}/status")
 @OperationBinding(id="ProductAttribute.setStatus",objectType="ProductAttribute",operationCode="setStatus",nameZh="维护商品属性配置",nameEn="Maintain product attribute configuration",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:set_status"},policy="可信租户、独立维护权限与CAS")
 public void status(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.masterdata.api.product.ProductCatalogData.Status input){service.setStatus(ctx(r),id,input,false);}

 /** 退役保留发布正文，旧商品继续可读。 */
 @PostMapping("/revisions/{id}/retire")
 @OperationBinding(id="ProductAttribute.retireRevision",objectType="ProductAttribute",operationCode="retireRevision",nameZh="退役属性修订",nameEn="Retire attribute revision",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:publish"},policy="可信租户与父属性CAS，正文不改")
 public void retire(HttpServletRequest r,@PathVariable String id,@RequestParam long version){service.retireRevision(ctx(r),id,version);}

 /** 编辑未发布草稿；所有发布正文由服务端拒绝改写。 */
 @PutMapping("/revisions/{id}")
 @OperationBinding(id="ProductAttribute.updateRevision",objectType="ProductAttribute",operationCode="updateRevision",nameZh="编辑属性修订草稿",nameEn="Update attribute revision draft",pages={"master-data/product-attribute"},permissions={"masterdata.productattribute:update"},policy="可信租户、草稿状态、父属性CAS及同事务选项")
 public void updateRevision(HttpServletRequest r,@PathVariable String id,@RequestBody RevisionInput input){service.updateRevision(ctx(r),id,input);}
 private static VerifiedTenantContext ctx(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;}
}