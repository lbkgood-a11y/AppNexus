package com.appnexus.masterdata.adapter.integration.product;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.*;
import com.appnexus.platform.configuration.NavigationPages;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
/** 商品唯一声明；主档、规格SKU、图片与历史继承父商品组织范围，查询、修改和启停分别按自己声明的归属组织范围执行。 */
@Component
public final class ProductDeclaration implements BusinessObjects.Provider {
 // 审核读取契约与现有审核 Controller 共用开关；关闭引擎时不宣告不存在的 HTTP 入口。
 @org.springframework.beans.factory.annotation.Value("${app.workflow.engine.enabled:false}")
 private boolean approvalEnabled;
 private final BusinessObjectRuntime runtime;
 /**
  * 目录构建只读取声明；延迟连接运行端口，避免目录初始化反向创建依赖目录的授权执行器。
  * 代理仅在真实运行调用时解析原 Bean，保留同一用例及实时授权，不缓存权限或放宽循环依赖设置。
  * @param products 原商品主档公开用例
  * @param structures 原商品结构公开用例
  * @param images 原商品图片公开用例
  * @param authorization 原统一商品记录授权端口
  * @param records 原商品父记录仓储端口
  * @param tenants 权威租户成员公开端口
  */
 public ProductDeclaration(@Lazy com.appnexus.masterdata.api.product.ProductManagement products,@Lazy com.appnexus.masterdata.api.product.ProductStructureManagement structures,@Lazy com.appnexus.masterdata.api.product.ProductImageManagement images,@Lazy com.appnexus.masterdata.application.product.ProductAuthorization authorization,@Lazy com.appnexus.masterdata.application.product.ProductRepository records,@Lazy com.appnexus.platform.tenant.TenantAccess tenants){
  var actions=new HashMap<String,String>();
  for(var operation:descriptor().operations())if(ProductRuntime.OPERATIONS.contains(operation.operationCode()))actions.put(operation.operationCode(),operation.permissionCode().substring("masterdata.product:".length()));
  runtime=new ProductRuntime(products,structures,images,authorization,records,tenants,actions);
 }
 @Override public BusinessObjectRuntime runtime(){return runtime;}

 @Override public Descriptor descriptor(){
  var operations=List.of(
      operation("list","查询商品","List V1 products","masterdata.product:read","GET","/api/master-data/product",List.of("master-data/product","master-data/product-sku")),
      operation("create","新增商品","Create V1 product","masterdata.product:create","POST","/api/master-data/product",List.of("master-data/product")),
      operation("setStatus","启停商品","Set V1 product status","masterdata.product:set_status","PUT","/api/master-data/product/{id}/status",List.of("master-data/product")),
      operation("read","查看商品","Read V1 product","masterdata.product:read","GET","/api/master-data/product/{id}",List.of("master-data/product","master-data/product-sku")),
      operation("update","编辑商品","Update V1 product","masterdata.product:update","PUT","/api/master-data/product/{id}",List.of("master-data/product")),
      operation("archiveDraft","归档商品草稿","Archive V1 draft","masterdata.product:manage_draft","PUT","/api/master-data/product/{id}/draft-archive",List.of("master-data/product")),
      operation("restoreDraft","恢复商品草稿","Restore V1 draft","masterdata.product:manage_draft","PUT","/api/master-data/product/{id}/draft-restore",List.of("master-data/product")),
      operation("readChanges","查看商品历史","Read V1 product changes","masterdata.product:read","GET","/api/master-data/product/{id}/changes",List.of("master-data/product")),
      operation("previewStructure","预览商品结构迁移","Preview V1 product structure","masterdata.product:migrate_structure","POST","/api/master-data/product/{id}/structure-preview",List.of("master-data/product")),
      operation("migrateStructure","迁移商品结构","Migrate V1 product structure","masterdata.product:migrate_structure","PUT","/api/master-data/product/{id}/structure",List.of("master-data/product")),
      operation("options","读取商品配置候选","Read product options","masterdata.product:read","GET","/api/master-data/product/options",List.of("master-data/product")),
      operation("readConfiguration","读取商品固定配置","Read product fixed configuration","masterdata.product:read","GET","/api/master-data/product/configurations/{id}",List.of("master-data/product","master-data/product-sku")),
      operation("listConfigurations","查询商品配置版本","List product configuration versions","masterdata.product:read","GET","/api/master-data/product/categories/{id}/configurations",List.of("master-data/product")),
      operation("reviewLegacySpecification","确认旧商品规格原文","Review legacy product specification","masterdata.product:migrate_structure","PUT","/api/master-data/product/{id}/legacy-specification-review",List.of("master-data/product")),
      operation("stageLegacy","暂存旧商品","Stage legacy product","masterdata.product:stage_legacy","POST","/api/master-data/product/legacy/{id}/stage",List.of("master-data/product")),
      operation("reconcileLegacy","核对旧商品","Reconcile legacy products","masterdata.product:stage_legacy","GET","/api/master-data/product/legacy/reconciliation",List.of("master-data/product")),
      operation("readMeasurement","查询商品单位与包装","Read product measurement","masterdata.product:read","GET","/api/master-data/product/{product}/structure-records/measurement",List.of("master-data/product","master-data/product-sku")),
      operation("saveMeasurement","维护商品单位与包装","Manage product measurement","masterdata.product:manage_structure","PUT","/api/master-data/product/{product}/structure-records/measurement",List.of("master-data/product")),
      operation("saveSpecificationTemplate","确认规格轴模板","Confirm specification template","masterdata.product:manage_structure","PUT","/api/master-data/product/{product}/structure-records/specification-template",List.of("master-data/product")),
      operation("saveSkuAttributeTemplate","确认普通货号属性模板","Confirm SKU attribute template","masterdata.product:manage_structure","PUT","/api/master-data/product/{product}/structure-records/sku-attribute-template",List.of("master-data/product")),
      operation("readSkuAttributes","读取普通货号属性","Read SKU attributes","masterdata.product:read","GET","/api/master-data/product/{product}/structure-records/skus/{sku}/attributes",List.of("master-data/product-sku")),
      operation("saveSkuAttributes","维护普通货号属性","Manage SKU attributes","masterdata.product:manage_structure","PUT","/api/master-data/product/{product}/structure-records/skus/{sku}/attributes",List.of("master-data/product-sku")),
      operation("saveSkuBatchTemplate","维护货号批次规则","Manage SKU batch template","masterdata.product:manage_structure","PUT","/api/master-data/product/{product}/structure-records/skus/{sku}/batch-template",List.of("master-data/product-sku")),
      operation("saveSkuMeasurement","维护货号计量","Manage SKU measurement","masterdata.product:manage_structure","PUT","/api/master-data/product/{product}/structure-records/skus/{sku}/measurement",List.of("master-data/product-sku")),
      operation("calculatePackaging","试算商品包装","Calculate product packaging","masterdata.product:read","GET","/api/master-data/product/{product}/structure-records/measurement/calculation",List.of("master-data/product")),
      operation("readStructure","查询商品结构","Read structure","masterdata.product:read","GET","/api/master-data/product/{product}/structure-records",List.of("master-data/product","master-data/product-sku")),
      operation("createSpecification","新增商品规格","Create specification","masterdata.product:manage_structure","POST","/api/master-data/product/{product}/structure-records/specifications",List.of("master-data/product","master-data/product-sku")),
      operation("createSku","新增商品SKU","Create SKU","masterdata.product:manage_structure","POST","/api/master-data/product/{product}/structure-records/skus",List.of("master-data/product","master-data/product-sku")),
      operation("addComponent","新增组合明细","Add component","masterdata.product:manage_structure","POST","/api/master-data/product/{product}/structure-records/components",List.of("master-data/product","master-data/product-sku")),
      operation("setStructureState","启停结构记录","Set structure state","masterdata.product:manage_structure","PUT","/api/master-data/product/{product}/structure-records/{kind}/{id}/status",List.of("master-data/product","master-data/product-sku")),
      operation("listImages","查询商品图片","List images","masterdata.product:read","GET","/api/master-data/product/{product}/images",List.of("master-data/product")),
      operation("uploadImage","上传商品图片","Upload image","masterdata.product:update","POST","/api/master-data/product/{product}/images",List.of("master-data/product")),
      operation("readImage","查看商品图片","Read image","masterdata.product:read","GET","/api/master-data/product/{product}/images/{id}/content",List.of("master-data/product")),
      operation("detachImage","移除商品图片","Detach image","masterdata.product:update","PUT","/api/master-data/product/{product}/images/{id}/detach",List.of("master-data/product")),
      operation("createOrganizations","查询新增商品归属组织","Read create product organizations","masterdata.product:create","GET","/api/master-data/product/organizations/create",List.of("master-data/product")),
      operation("updateOrganizations","查询修改商品归属组织","Read update product organizations","masterdata.product:update","GET","/api/master-data/product/organizations/update",List.of("master-data/product")));
  var scopes=new TreeMap<String,Set<String>>();
  operations.forEach(op->scopes.put(op.operationCode(),Set.of("ALL")));
  if(approvalEnabled){
   var withApproval=new ArrayList<>(operations);
   withApproval.addAll(List.of(
     operation("readApprovals","查询商品审核记录","Read product approvals","masterdata.product:read","GET","/api/master-data/product/{id}/approvals",List.of("master-data/product")),
     operation("readApprovalOrganizations","查询商品审核组织","Read approval organizations","masterdata.product.approval:submit","GET","/api/master-data/product/approval-organizations",List.of("master-data/product")),
     operation("readApprovalSnapshot","查看商品审核快照","Read product approval snapshot","","GET","/api/master-data/product/approval-requests/{id}",List.of("master-data/product"))));
   operations=List.copyOf(withApproval);
  }
  var scopedOperations=new TreeSet<String>();
  var scopedPermissions=Set.of("masterdata.product:read","masterdata.product:create","masterdata.product:update","masterdata.product:set_status");
  for(var operation:operations)if(scopedPermissions.contains(operation.permissionCode())){
   scopedOperations.add(operation.operationCode());
   scopes.put(operation.operationCode(),Set.of("ALL","RULES"));
  }
  return new Descriptor("Product","商品档案","Products","master-data",7,Category.MASTER_DATA,List.of(),operations,
   new DataAccess("TENANT",List.of(new DataResource("masterdata.product","","商品档案","Products",List.of())),scopes,
    List.of(new DataDimension("organizationId","归属组织","Owning organization","UUID",Set.of("EQ","IN"),
     "ORGANIZATION","",scopedOperations,Set.of("CURRENT_ORGANIZATION","CURRENT_ORGANIZATION_SUBTREE"))))
   .withStandardCrud("organizationId",Set.of("list","read","create","update","setStatus")));
 }
 /** 固定页面和真实API来自代码；预览等额外读取前置仍由原用例复核，不以声明替代执行。 */
 private static Operation operation(String code,String zh,String en,String permission,String method,String path,List<String> pages){
  String id="Product."+code;
  var views=pages.stream().map(key->{var page=Objects.requireNonNull(NavigationPages.find(key));return new PageOperation(key,page.path(),page.titleZh(),page.titleEn(),method,path);}).toList();
  // 公共请求不携带命令数据；不能把创建、表单修改、上传等原API操作虚假登记为可零参数执行。
  var capabilities=ProductRuntime.OPERATIONS.contains(code)?List.of(new InteractionContract.Capability(InteractionContract.Service.BUSINESS,InteractionContract.Completion.SYNCHRONOUS,id,"",List.of(),Set.of(),"product."+code,2)):List.<InteractionContract.Capability>of();
  var interaction=new InteractionContract(id,2,"Product",code,zh,en,InteractionContract.Trigger.USER_ACTION,capabilities);
  // 同一业务操作的页面别名使用页面键区分，避免与主操作标识重复。
  return new Operation(code,zh,en,permission,id,views.getFirst(),views.stream().skip(1).map(v->new OperationAlias(id+"@"+v.pageKey(),v)).toList(),interaction);
 }
}
