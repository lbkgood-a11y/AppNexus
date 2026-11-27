package com.appnexus.masterdata.adapter.integration.product;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.masterdata.api.product.*;
import com.appnexus.masterdata.application.product.ProductAuthorization;
import com.appnexus.masterdata.application.product.ProductRepository;
import com.appnexus.masterdata.api.product.ProductCommandData.Version;
import com.appnexus.platform.tenant.*;
import java.util.*;

/**
 * Product统一运行适配；复用原用例、统一授权及父记录端口，不复制商品业务规则。
 * <p>公共请求只携带记录与版本，因此仅开放无需额外命令的操作；有表单、子资源或上传输入的操作仍使用原受控API。
 */
public final class ProductRuntime implements BusinessObjectRuntime {
  /** 公共记录执行能够完整表达的操作；唯一声明据此派生能力，不能登记后静默拒绝。 */
  public static final Set<String> OPERATIONS=Set.of("read","archiveDraft","restoreDraft","readChanges","readStructure","listImages");
  private final ProductManagement products;
  private final ProductStructureManagement structures;
  private final ProductImageManagement images;
  private final ProductAuthorization authorization;
  private final ProductRepository records;
  private final TenantAccess tenants;
  private final Map<String,String> actions;

  /**
   * @param products 原主档公开用例，保留权限、状态、CAS及历史
   * @param structures 原规格/SKU/组成查询公开用例
   * @param images 原图片目录公开用例，不回传图片二进制或未声明字段
   * @param authorization 原统一记录范围执行适配器，不另建授权算法
   * @param records 原父记录端口，写入前在公共执行事务中锁定版本
   * @param tenants 权威租户成员公开端口，每次重查真实成员，不从客户端推断
   * @param actions 由同一Provider的操作权限码派生的固定动作，不复制第二份权限目录
   */
  public ProductRuntime(ProductManagement products,ProductStructureManagement structures,ProductImageManagement images,ProductAuthorization authorization,ProductRepository records,TenantAccess tenants,Map<String,String> actions){
    this.products=products;this.structures=structures;this.images=images;this.authorization=authorization;this.records=records;this.tenants=tenants;this.actions=Map.copyOf(actions);
  }

  @Override public Snapshot read(BusinessObjects.Context context,String objectType,String objectId,String operation,long basisVersion,Set<String> fields){
    check(objectType,operation,basisVersion);
    if(fields==null||!fields.isEmpty())throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_FIELD_UNDECLARED");
    var trusted=context(context);authorization.authorize(trusted,objectId,actions.get(operation),true);
    var row=records.load(trusted,objectId,true,readScope(trusted,operation));long current=((Number)row.get("version")).longValue();
    if(current!=basisVersion)throw new BusinessObjects.Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
    // Product当前没有声明通用事实字段；只返回服务器归属和版本，原文不绕过公共字段投影。
    return new Snapshot(current,(String)row.get("organizationId"),Map.of(),(String)row.get("id"));
  }

  @Override public Result execute(BusinessObjects.Context context,String objectType,String objectId,String operation,long basisVersion,Map<String,?> values){
    check(objectType,operation,basisVersion);
    if(values==null||!values.isEmpty())throw new BusinessObjects.Rejection(400,"PRODUCT_RUNTIME_INPUT_UNDECLARED");
    var trusted=context(context);
    // 不信任此前预检或浏览器事实；真实执行时再次锁定同一记录、复核对应操作和CAS。
    read(context,objectType,objectId,operation,basisVersion,Set.of());
    switch(operation){
      case "read" -> products.read(trusted,objectId);
      case "archiveDraft" -> products.archive(trusted,objectId,new Version(basisVersion),true);
      case "restoreDraft" -> products.archive(trusted,objectId,new Version(basisVersion),false);
      case "readChanges" -> products.changes(trusted,objectId,1,20);
      case "readStructure" -> structures.read(trusted,objectId);
      case "listImages" -> images.list(trusted,objectId);
      default -> throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_OPERATION_UNAVAILABLE");
    }
    var row=records.load(trusted,objectId,false,readScope(trusted,operation));
    return new Result(((Number)row.get("version")).longValue(),Map.of());
  }

  /** 只读操作携带父商品读取范围，写入继续消耗同事务记录许可。 */
  private com.appnexus.platform.authorization.RowAccessControl.Predicate readScope(VerifiedTenantContext c,String operation){
    return "read".equals(actions.get(operation))?authorization.predicate(c,"read"):null;
  }

  private void check(String type,String operation,long version){
    if(!"Product".equals(type)||!OPERATIONS.contains(operation)||!actions.containsKey(operation))throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_OPERATION_UNAVAILABLE");
    if(version<0)throw new BusinessObjects.Rejection(400,"BUSINESS_OBJECT_VERSION_INVALID");
  }
  private VerifiedTenantContext context(BusinessObjects.Context context){
    try{
      UUID actor=UUID.fromString(context.actorId()),tenant=UUID.fromString(context.tenantId());
      var member=tenants.available(actor).stream().filter(value->value.tenantId().equals(tenant)).findFirst().orElseThrow();
      if(!tenants.isActiveMember(tenant,member.memberId()))throw new NoSuchElementException();
      return new VerifiedTenantContext(actor,tenant,member.memberId(),1);
    }catch(IllegalArgumentException|NoSuchElementException invalid){throw new BusinessObjects.Rejection(403,"BUSINESS_OBJECT_ACCESS_DENIED");}
  }
}
