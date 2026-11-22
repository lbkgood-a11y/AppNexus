package com.appnexus.masterdata.adapter.web.product;
import com.appnexus.masterdata.api.product.ProductManagement;
import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.masterdata.api.product.ProductStructureData.*;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.product.*;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import static com.appnexus.masterdata.api.product.ProductCommandData.*;
/** 商品主档固定接口；不暴露规格、SKU、图片和下游业务能力。 */
@RestController @RequestMapping("/api/master-data/product")
public final class ProductController {
 private final ProductManagement service;
 /** 新增与修改各自独立准入，不要求获得商品读取或组织管理权限。 */
 @GetMapping("/organizations/create") @OperationBinding(id="Product.createOrganizations",reference=true)
 public Object createOrganizations(HttpServletRequest request){return service.organizations(ctx(request),"create");}
 /** 修改归属候选与保存时使用同一操作范围，不能默认选择首项。 */
 @GetMapping("/organizations/update") @OperationBinding(id="Product.updateOrganizations",reference=true)
 public Object updateOrganizations(HttpServletRequest request){return service.organizations(ctx(request),"update");}
 /** @param service 固定商品聚合服务，权限/租户/CAS/审计与事务由真实执行器复核 */
 public ProductController(ProductManagement service){this.service=service;}
 /** 分页查询仅当前可信租户；page从1起、size默认20最大100，动态条件仅单固定配置最多3项。
  * @param includeDescendants 默认false仅查所选分类，true包含下级；未传categoryId时此参数不生效。 */
 /** @param keyword 编码或名称的字面包含查询，最多200字符；默认空，不改变现有两个筛选项。 */
 @RecordAuditSource(table="md_product_v1",path="rows") @GetMapping @OperationBinding(id="Product.list",reference=true) public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String name,@RequestParam(defaultValue="") String code,@RequestParam(required=false) String categoryId,@RequestParam(required=false) String form,@RequestParam(required=false) String status,@RequestParam(defaultValue="false") boolean archived,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String configId,@RequestParam(required=false) String attributeFilters,@RequestParam(defaultValue="false") boolean includeDescendants,@RequestParam(defaultValue="") String keyword){return service.list(ctx(r),name,code,categoryId,form,status,archived,page,size,configId,attributeFilters,includeDescendants,keyword);}
 /** 创建只建DRAFT，编码/名称必填；已填属性必须合法，缺必填仅阻断后续独立启用。 */
 @PostMapping @OperationBinding(id="Product.create",reference=true) public Object create(HttpServletRequest r,@RequestBody Input i){return service.create(ctx(r),i);}
 /** 状态命令必须当前version；启用重验完整性，LEGACY_PENDING不得直接启用。 */
 @PutMapping("/{id}/status") @OperationBinding(id="Product.setStatus",reference=true) public Object status(HttpServletRequest r,@PathVariable String id,@RequestBody Status i){return service.updateStatus(ctx(r),id,i);}

 /** 已授权完整详情；只读用户无需编辑权。 */
 @GetMapping("/{id}")
 @OperationBinding(id="Product.read",reference=true)
 public Object read(HttpServletRequest r,@PathVariable String id){return service.read(ctx(r),id);}
 /** 普通修改不接收结构字段；使用父商品CAS。 */
 @PutMapping("/{id}")
 @OperationBinding(id="Product.update",reference=true)
 public Object update(HttpServletRequest r,@PathVariable String id,@RequestBody Update input){return service.update(ctx(r),id,input);}
 /** 草稿人工归档，保留编码与历史。 */
 @PutMapping("/{id}/draft-archive")
 @OperationBinding(id="Product.archiveDraft",reference=true)
 public Object archive(HttpServletRequest r,@PathVariable String id,@RequestBody Version input){return service.archive(ctx(r),id,input,true);}
 /** 恢复原草稿，不自动升级配置。 */
 @PutMapping("/{id}/draft-restore")
 @OperationBinding(id="Product.restoreDraft",reference=true)
 public Object restore(HttpServletRequest r,@PathVariable String id,@RequestBody Version input){return service.archive(ctx(r),id,input,false);}
 /** 历史采用授权原对象范围，页码1起每页默认20最大100。 */
 @GetMapping("/{id}/changes")
 @OperationBinding(id="Product.readChanges",reference=true)
 public Object changes(HttpServletRequest r,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.changes(ctx(r),id,page,size);}

 /** 只读预览：固定源版本/目标载荷，不创建任务或临时业务记录。 */
 @PostMapping("/{id}/structure-preview")
 @OperationBinding(id="Product.previewStructure",reference=true)
 public Object preview(HttpServletRequest r,@PathVariable String id,@RequestBody Migration change){return service.previewStructure(ctx(r),id,change);}
 /** 显式执行，幂等键作用域为可信账号/租户/商品；写超时不得自动重放。 */
 @PutMapping("/{id}/structure")
 @OperationBinding(id="Product.migrateStructure",reference=true)
 public Object migrate(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody MigrationCommand command){return service.migrateStructure(ctx(r),id,command,key);}

 /** 建档候选读取受商品读取权保护，页码和容量复用现有契约。 */
 @GetMapping("/options")
 @OperationBinding(id="Product.options",reference=true)
 public Object options(HttpServletRequest r,@RequestParam String kind,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.options(ctx(r),kind,keyword,page,size);}
 /** 动态表单固定版本读取，不按当前分类指针重解释旧商品。 */
 @GetMapping("/configurations/{id}")
 @OperationBinding(id="Product.readConfiguration",reference=true)
 public Object configuration(HttpServletRequest r,@PathVariable String id){return service.configuration(ctx(r),id);}

 /** 分类固定配置版本目录；仅用于显式选择，不改变当前商品绑定。 */
 @GetMapping("/categories/{id}/configurations")
 @OperationBinding(id="Product.listConfigurations",reference=true)
 public Object configurations(HttpServletRequest r,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.configurations(ctx(r),id,page,size);}

 /** 仅确认旧规格事实，保留原文，后续完整结构迁移仍独立执行。 */
 @PutMapping("/{id}/legacy-specification-review")
 @OperationBinding(id="Product.reviewLegacySpecification",reference=true)
 public Object reviewLegacy(HttpServletRequest r,@PathVariable String id,@RequestBody LegacyReview review){return service.reviewLegacySpecification(ctx(r),id,review);}

 /** 仅暂存单条旧商品，不切换旧入口；ID与审计逐列保留。 */
 @PostMapping("/legacy/{id}/stage")
 @OperationBinding(id="Product.stageLegacy",reference=true)
 public Object stage(HttpServletRequest r,@PathVariable String id){return service.stageLegacy(ctx(r),id);}
 /** 当前租户原文/版本/审计对账，差异不能自动覆盖新记录。 */
 @GetMapping("/legacy/reconciliation")
 @OperationBinding(id="Product.reconcileLegacy",reference=true)
 public Object reconcile(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.reconcileLegacy(ctx(r),page,size);}
 private static VerifiedTenantContext ctx(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;}
}
