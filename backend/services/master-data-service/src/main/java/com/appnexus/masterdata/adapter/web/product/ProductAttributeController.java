package com.appnexus.masterdata.adapter.web.product;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.product.*;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import static com.appnexus.masterdata.api.product.ProductAttributeData.*;
/** 商品属性管理固定接口；修订发布后不可原地覆盖。 */
@RestController @RequestMapping("/api/master-data/product-attribute")

public final class ProductAttributeController {
 private final ProductAttributeManagement service; public ProductAttributeController(ProductAttributeManagement service){this.service=service;}
 @RecordAuditSource(table="md_product_attribute_group") @GetMapping("/groups") @OperationBinding(id="ProductAttribute.listGroups", reference=true) public Object groups(HttpServletRequest r){return service.groups(ctx(r));}
 @PostMapping("/groups") @OperationBinding(id="ProductAttribute.createGroup", reference=true) public Object createGroup(HttpServletRequest r,@RequestBody GroupInput i){return service.createGroup(ctx(r),i);}
 @RecordAuditSource(table="md_product_attribute") @GetMapping("") @OperationBinding(id="ProductAttribute.list", reference=true) public Object attributes(HttpServletRequest r){return service.attributes(ctx(r));}
 @PostMapping("") @OperationBinding(id="ProductAttribute.create", reference=true) public Object create(HttpServletRequest r,@RequestBody AttributeInput i){return service.createAttribute(ctx(r),i);}
 @PostMapping("/{id}/revisions") @OperationBinding(id="ProductAttribute.createRevision", reference=true) public Object revision(HttpServletRequest r,@PathVariable String id,@RequestBody RevisionInput i){return service.createRevision(ctx(r),id,i);}
 @PostMapping("/revisions/{id}/publish") @OperationBinding(id="ProductAttribute.publishRevision", reference=true) public Object publish(HttpServletRequest r,@PathVariable String id,@RequestParam long version){service.publishRevision(ctx(r),id,version);return java.util.Map.of("ok",true);}
 @PostMapping("/revisions/{id}/options") @OperationBinding(id="ProductAttribute.createOption", reference=true) public Object option(HttpServletRequest r,@PathVariable String id,@RequestBody OptionInput i){return service.createOption(ctx(r),id,i);}

 /** 读取属性固定修订列表。 */
 @GetMapping("/{id}/revisions")
 @OperationBinding(id="ProductAttribute.readRevisions", reference=true)
 public Object revisions(HttpServletRequest r,@PathVariable String id){return service.revisions(ctx(r),id);}
 /** 读取固定修订枚举选项。 */
 @GetMapping("/revisions/{id}/options")
 @OperationBinding(id="ProductAttribute.readOptions", reference=true)
 public Object options(HttpServletRequest r,@PathVariable String id){return service.options(ctx(r),id);}

 /** 固定属性配置维护命令，可信租户、独立权限、当前父CAS与四审计同事务。 */
 @PutMapping("/groups/{id}")
 @OperationBinding(id="ProductAttribute.updateGroup", reference=true)
 public void updateGroup(HttpServletRequest r,@PathVariable String id,@RequestBody GroupInput input){service.updateGroup(ctx(r),id,input);}

 /** 固定属性配置维护命令，可信租户、独立权限、当前父CAS与四审计同事务。 */
 @PutMapping("/{id}")
 @OperationBinding(id="ProductAttribute.update", reference=true)
 public void update(HttpServletRequest r,@PathVariable String id,@RequestBody AttributeInput input){service.updateAttribute(ctx(r),id,input);}

 /** 固定属性配置维护命令，可信租户、独立权限、当前父CAS与四审计同事务。 */
 @PutMapping("/groups/{id}/status")
 @OperationBinding(id="ProductAttribute.setGroupStatus", reference=true)
 public void groupStatus(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.masterdata.api.product.ProductCatalogData.Status input){service.setStatus(ctx(r),id,input,true);}

 /** 固定属性配置维护命令，可信租户、独立权限、当前父CAS与四审计同事务。 */
 @PutMapping("/{id}/status")
 @OperationBinding(id="ProductAttribute.setStatus", reference=true)
 public void status(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.masterdata.api.product.ProductCatalogData.Status input){service.setStatus(ctx(r),id,input,false);}

 /** 退役保留发布正文，旧商品继续可读。 */
 @PostMapping("/revisions/{id}/retire")
 @OperationBinding(id="ProductAttribute.retireRevision", reference=true)
 public void retire(HttpServletRequest r,@PathVariable String id,@RequestParam long version){service.retireRevision(ctx(r),id,version);}

 /** 编辑未发布草稿；所有发布正文由服务端拒绝改写。 */
 @PutMapping("/revisions/{id}")
 @OperationBinding(id="ProductAttribute.updateRevision", reference=true)
 public void updateRevision(HttpServletRequest r,@PathVariable String id,@RequestBody RevisionInput input){service.updateRevision(ctx(r),id,input);}
 private static VerifiedTenantContext ctx(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;}
}