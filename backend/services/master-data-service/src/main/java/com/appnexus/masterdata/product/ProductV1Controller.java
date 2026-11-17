package com.appnexus.masterdata.product;
import com.appnexus.contracts.operation.*;import com.appnexus.masterdata.MasterData.Rejection;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import org.springframework.web.bind.annotation.*;import static com.appnexus.masterdata.product.ProductV1Service.*;
/** V1商品主档接口；不暴露规格、SKU、图片和下游业务能力。 */
@RestController @RequestMapping("/api/master-data/product-v1")
@BusinessObjectType(code="ProductV1",nameZh="V1商品档案",nameEn="V1 products",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class ProductV1Controller {
 private final ProductV1Service service;public ProductV1Controller(ProductV1Service service){this.service=service;}
 @GetMapping @OperationBinding(id="ProductV1.list",objectType="ProductV1",operationCode="list",nameZh="查询V1商品",nameEn="List V1 products",pages={"master-data/product-v1"},permissions={"masterdata.product:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String name,@RequestParam(defaultValue="") String code,@RequestParam(required=false) String categoryId,@RequestParam(required=false) String form,@RequestParam(required=false) String status,@RequestParam(defaultValue="false") boolean archived,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.list(ctx(r),name,code,categoryId,form,status,archived,page,size);}
 @PostMapping @OperationBinding(id="ProductV1.create",objectType="ProductV1",operationCode="create",nameZh="新增V1商品",nameEn="Create V1 product",pages={"master-data/product-v1"},permissions={"masterdata.product:create"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object create(HttpServletRequest r,@RequestBody Input i){return service.create(ctx(r),i);}
 @PutMapping("/{id}/status") @OperationBinding(id="ProductV1.setStatus",objectType="ProductV1",operationCode="setStatus",nameZh="启停V1商品",nameEn="Set V1 product status",pages={"master-data/product-v1"},permissions={"masterdata.product:set_status"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本") public Object status(HttpServletRequest r,@PathVariable String id,@RequestBody Status i){return service.updateStatus(ctx(r),id,i);}

 /** 已授权完整详情；只读用户无需编辑权。 */
 @GetMapping("/{id}")
 @OperationBinding(id="ProductV1.read",objectType="ProductV1",operationCode="read",nameZh="查看V1商品",nameEn="Read V1 product",pages={"master-data/product-v1"},permissions={"masterdata.product:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object read(HttpServletRequest r,@PathVariable String id){return service.read(ctx(r),id);}
 /** 普通修改不接收结构字段；使用父商品CAS。 */
 @PutMapping("/{id}")
 @OperationBinding(id="ProductV1.update",objectType="ProductV1",operationCode="update",nameZh="编辑V1商品",nameEn="Update V1 product",pages={"master-data/product-v1"},permissions={"masterdata.product:update"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object update(HttpServletRequest r,@PathVariable String id,@RequestBody Update input){return service.update(ctx(r),id,input);}
 /** 草稿人工归档，保留编码与历史。 */
 @PutMapping("/{id}/draft-archive")
 @OperationBinding(id="ProductV1.archiveDraft",objectType="ProductV1",operationCode="archiveDraft",nameZh="归档V1草稿",nameEn="Archive V1 draft",pages={"master-data/product-v1"},permissions={"masterdata.product:manage_draft"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object archive(HttpServletRequest r,@PathVariable String id,@RequestBody Version input){return service.archive(ctx(r),id,input,true);}
 /** 恢复原草稿，不自动升级配置。 */
 @PutMapping("/{id}/draft-restore")
 @OperationBinding(id="ProductV1.restoreDraft",objectType="ProductV1",operationCode="restoreDraft",nameZh="恢复V1草稿",nameEn="Restore V1 draft",pages={"master-data/product-v1"},permissions={"masterdata.product:manage_draft"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object restore(HttpServletRequest r,@PathVariable String id,@RequestBody Version input){return service.archive(ctx(r),id,input,false);}
 /** 历史采用授权原对象范围，页码1起每页默认20最大100。 */
 @GetMapping("/{id}/changes")
 @OperationBinding(id="ProductV1.readChanges",objectType="ProductV1",operationCode="readChanges",nameZh="查看V1商品历史",nameEn="Read V1 product changes",pages={"master-data/product-v1"},permissions={"masterdata.product:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
 public Object changes(HttpServletRequest r,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.changes(ctx(r),id,page,size);}

 /** 只读预览：固定源版本/目标载荷，不创建任务或临时业务记录。 */
 @PostMapping("/{id}/structure-preview")
 @OperationBinding(id="ProductV1.previewStructure",objectType="ProductV1",operationCode="previewStructure",nameZh="预览V1商品结构迁移",nameEn="Preview V1 product structure",pages={"master-data/product-v1"},permissions={"masterdata.product:migrate_structure&masterdata.product:read"},policy="可信租户、原对象读取与迁移权；预览不授予执行成功")
 public Object preview(HttpServletRequest r,@PathVariable String id,@RequestBody Migration change){return service.previewStructure(ctx(r),id,change);}
 /** 显式执行，幂等键作用域为可信账号/租户/商品；写超时不得自动重放。 */
 @PutMapping("/{id}/structure")
 @OperationBinding(id="ProductV1.migrateStructure",objectType="ProductV1",operationCode="migrateStructure",nameZh="迁移V1商品结构",nameEn="Migrate V1 product structure",pages={"master-data/product-v1"},permissions={"masterdata.product:migrate_structure&masterdata.product:read"},policy="停用或草稿、固定源CAS与有效预览、幂等成功结果同事务，重放重新鉴权")
 public Object migrate(HttpServletRequest r,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody MigrationCommand command){return service.migrateStructure(ctx(r),id,command,key);}

 /** 建档候选读取受商品读取权保护，页码和容量复用现有契约。 */
 @GetMapping("/options")
 @OperationBinding(id="ProductV1.options",objectType="ProductV1",operationCode="options",nameZh="读取商品配置候选",nameEn="Read product options",pages={"master-data/product-v1"},permissions={"masterdata.product:read"},policy="可信租户、商品读取权，仅可读必要配置候选")
 public Object options(HttpServletRequest r,@RequestParam String kind,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.options(ctx(r),kind,keyword,page,size);}
 /** 动态表单固定版本读取，不按当前分类指针重解释旧商品。 */
 @GetMapping("/configurations/{id}")
 @OperationBinding(id="ProductV1.readConfiguration",objectType="ProductV1",operationCode="readConfiguration",nameZh="读取商品固定配置",nameEn="Read product fixed configuration",pages={"master-data/product-v1"},permissions={"masterdata.product:read"},policy="可信租户与原商品读取权，固定模板/属性修订只读")
 public Object configuration(HttpServletRequest r,@PathVariable String id){return service.configuration(ctx(r),id);}

 /** 分类固定配置版本目录；仅用于显式选择，不改变当前商品绑定。 */
 @GetMapping("/categories/{id}/configurations")
 @OperationBinding(id="ProductV1.listConfigurations",objectType="ProductV1",operationCode="listConfigurations",nameZh="查询商品配置版本",nameEn="List product configuration versions",pages={"master-data/product-v1"},permissions={"masterdata.product:read"},policy="可信租户商品读取权，仅固定分类版本目录")
 public Object configurations(HttpServletRequest r,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return service.configurations(ctx(r),id,page,size);}
 private static VerifiedTenantContext ctx(HttpServletRequest r){Object v=r.getAttribute(VerifiedTenantContext.class.getName());if(!(v instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;}
}