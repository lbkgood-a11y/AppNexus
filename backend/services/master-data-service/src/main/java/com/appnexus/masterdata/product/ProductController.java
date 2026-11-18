package com.appnexus.masterdata.product;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.MasterData.Rejection;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import static com.appnexus.masterdata.product.ProductV1Service.*;
/** 商品主档固定接口；不暴露规格、SKU、图片和下游业务能力。 */
@RestController @RequestMapping("/api/master-data/product")
@BusinessObjectType(code="Product",nameZh="商品档案",nameEn="Products",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class ProductController {
 private final ProductV1Service service;
 /** @param service 固定商品聚合服务，权限/租户/CAS/审计与事务由真实执行器复核 */
 public ProductController(ProductV1Service service){this.service=service;}
 /** 分页查询仅当前可信租户；page从1起、size默认20最大100，动态条件仅单固定配置最多3项。 */
 @RecordAuditSource(table="md_product_v1",path="rows") @GetMapping @OperationBinding(id="Product.list",objectType="Product",operationCode="list",nameZh="查询商品",nameEn="List V1 products",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String name,@RequestParam(defaultValue="") String code,@RequestParam(required=false) String categoryId,@RequestParam(required=false) String form,@RequestParam(required=false) String status,@RequestParam(defaultValue="false") boolean archived,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String configId,@RequestParam(required=false) String attributeFilters){return service.list(ctx(r),name,code,categoryId,form,status,archived,page,size,configId,attributeFilters);}
 /** 创建只建DRAFT，编码/名称必填；已填属性必须合法，缺必填仅阻断后续独立启用。 */
 @PostMapping @OperationBinding(id="Product.create",objectType="Product",operationCode="create",nameZh="新增商品",nameEn="Create V1 product",pages={"master-data/product"},permissions={"masterdata.product:create"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object create(HttpServletRequest r,@RequestBody Input i){return service.create(ctx(r),i);}
 /** 状态命令必须当前version；启用重验完整性，LEGACY_PENDING不得直接启用。 */
 @PutMapping("/{id}/status") @OperationBinding(id="Product.setStatus",objectType="Product",operationCode="setStatus",nameZh="启停商品",nameEn="Set V1 product status",pages={"master-data/product"},permissions={"masterdata.product:set_status"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object status(HttpServletRequest r,@PathVariable String id,@RequestBody Status i){return service.updateStatus(ctx(r),id,i);}

 /** 已授权完整详情；只读用户无需编辑权。 */
 @GetMapping("/{id}")
 @OperationBinding(id="Product.read",objectType="Product",operationCode="read",nameZh="查看商品",nameEn="Read V1 product",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object read(HttpServletRequest r,@PathVariable String id){return service.read(ctx(r),id);}
 /** 普通修改不接收结构字段；使用父商品CAS。 */
 @PutMapping("/{id}")
 @OperationBinding(id="Product.update",objectType="Product",operationCode="update",nameZh="编辑商品",nameEn="Update V1 product",pages={"master-data/product"},permissions={"masterdata.product:update"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object update(HttpServletRequest r,@PathVariable String id,@RequestBody Update input){return service.update(ctx(r),id,input);}
 /** 草稿人工归档，保留编码与历史。 */
 @PutMapping("/{id}/draft-archive")
 @OperationBinding(id="Product.archiveDraft",objectType="Product",operationCode="archiveDraft",nameZh="归档商品草稿",nameEn="Archive V1 draft",pages={"master-data/product"},permissions={"masterdata.product:manage_draft"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object archive(HttpServletRequest r,@PathVariable String id,@RequestBody Version input){return service.archive(ctx(r),id,input,true);}
 /** 恢复原草稿，不自动升级配置。 */
 @PutMapping("/{id}/draft-restore")
 @OperationBinding(id="Product.restoreDraft",objectType="Product",operationCode="restoreDraft",nameZh="恢复商品草稿",nameEn="Restore V1 draft",pages={"master-data/product"},permissions={"masterdata.product:manage_draft"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object restore(HttpServletRequest r,@PathVariable String id,@RequestBody Version input){return service.archive(ctx(r),id,input,false);}
 /** 历史采用授权原对象范围，页码1起每页默认20最大100。 */
 @GetMapping("/{id}/changes")
 @OperationBinding(id="Product.readChanges",objectType="Product",operationCode="readChanges",nameZh="查看商品历史",nameEn="Read V1 product changes",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object changes(HttpServletRequest r,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.changes(ctx(r),id,page,size);}

 /** 只读预览：固定源版本/目标载荷，不创建任务或临时业务记录。 */
 @PostMapping("/{id}/structure-preview")
 @OperationBinding(id="Product.previewStructure",objectType="Product",operationCode="previewStructure",nameZh="预览商品结构迁移",nameEn="Preview V1 product structure",pages={"master-data/product"},permissions={"masterdata.product:migrate_structure&masterdata.product:read"},policy="可信租户、原对象读取与迁移权；预览不授予执行成功")
 public Object preview(HttpServletRequest r,@PathVariable String id,@RequestBody Migration change){return service.previewStructure(ctx(r),id,change);}
 /** 显式执行，幂等键作用域为可信账号/租户/商品；写超时不得自动重放。 */
 @PutMapping("/{id}/structure")
 @OperationBinding(id="Product.migrateStructure",objectType="Product",operationCode="migrateStructure",nameZh="迁移商品结构",nameEn="Migrate V1 product structure",pages={"master-data/product"},permissions={"masterdata.product:migrate_structure&masterdata.product:read"},policy="停用或草稿、固定源CAS与有效预览、幂等成功结果同事务，重放重新鉴权")
 public Object migrate(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody MigrationCommand command){return service.migrateStructure(ctx(r),id,command,key);}

 /** 建档候选读取受商品读取权保护，页码和容量复用现有契约。 */
 @GetMapping("/options")
 @OperationBinding(id="Product.options",objectType="Product",operationCode="options",nameZh="读取商品配置候选",nameEn="Read product options",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="可信租户、商品读取权，仅可读必要配置候选")
 public Object options(HttpServletRequest r,@RequestParam String kind,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.options(ctx(r),kind,keyword,page,size);}
 /** 动态表单固定版本读取，不按当前分类指针重解释旧商品。 */
 @GetMapping("/configurations/{id}")
 @OperationBinding(id="Product.readConfiguration",objectType="Product",operationCode="readConfiguration",nameZh="读取商品固定配置",nameEn="Read product fixed configuration",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="可信租户与原商品读取权，固定模板/属性修订只读")
 public Object configuration(HttpServletRequest r,@PathVariable String id){return service.configuration(ctx(r),id);}

 /** 分类固定配置版本目录；仅用于显式选择，不改变当前商品绑定。 */
 @GetMapping("/categories/{id}/configurations")
 @OperationBinding(id="Product.listConfigurations",objectType="Product",operationCode="listConfigurations",nameZh="查询商品配置版本",nameEn="List product configuration versions",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="可信租户商品读取权，仅固定分类版本目录")
 public Object configurations(HttpServletRequest r,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.configurations(ctx(r),id,page,size);}

 /** 仅确认旧规格事实，保留原文，后续完整结构迁移仍独立执行。 */
 @PutMapping("/{id}/legacy-specification-review")
 @OperationBinding(id="Product.reviewLegacySpecification",objectType="Product",operationCode="reviewLegacySpecification",nameZh="确认旧商品规格原文",nameEn="Review legacy product specification",pages={"master-data/product"},permissions={"masterdata.product:migrate_structure&masterdata.product:read"},policy="可信租户、旧档LEGACY_PENDING、商品CAS、审查不等于结构完成")
 public Object reviewLegacy(HttpServletRequest r,@PathVariable String id,@RequestBody LegacyReview review){return service.reviewLegacySpecification(ctx(r),id,review);}

 /** 仅暂存单条旧商品，不切换旧入口；ID与审计逐列保留。 */
 @PostMapping("/legacy/{id}/stage")
 @OperationBinding(id="Product.stageLegacy",objectType="Product",operationCode="stageLegacy",nameZh="暂存旧商品",nameEn="Stage legacy product",pages={"master-data/product"},permissions={"masterdata.product:stage_legacy&masterdata.product:read"},policy="可信租户、独立受控暂存权，不覆盖已有目标或切换写入口")
 public Object stage(HttpServletRequest r,@PathVariable String id){return service.stageLegacy(ctx(r),id);}
 /** 当前租户原文/版本/审计对账，差异不能自动覆盖新记录。 */
 @GetMapping("/legacy/reconciliation")
 @OperationBinding(id="Product.reconcileLegacy",objectType="Product",operationCode="reconcileLegacy",nameZh="核对旧商品",nameEn="Reconcile legacy products",pages={"master-data/product"},permissions={"masterdata.product:stage_legacy&masterdata.product:read"},policy="可信租户、独立受控对账权，不写业务")
 public Object reconcile(HttpServletRequest r,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.reconcileLegacy(ctx(r),page,size);}
 private static VerifiedTenantContext ctx(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;}
}