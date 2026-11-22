package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.api.product.ProductMainData.*;

import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import com.appnexus.masterdata.domain.product.ProductLifecycle;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;
import java.util.UUID;

/** 主档创建与普通编辑应用用例；定义原子顺序，不依赖 JDBC、JSON、Spring 或原混合 Service。 */
public final class ProductMainService {
  private final ProductMainPort port;
  private final AccessAuthorization access;
  /** @param port 共享聚合事务的固定输出端口 @param access 实时功能授权 */
  public ProductMainService(ProductMainPort port,AccessAuthorization access){this.port=port;this.access=access;}
  /** @param c 可信主体 @param input 不含状态和审计的草稿参数 @return 初始 DRAFT 的真实快照；缺必填属性可保存草稿，已填值仍必须合法 */
  public Map<String,Object> create(VerifiedTenantContext c,Input input){
    require(c,"create");
    com.appnexus.masterdata.domain.product.ProductMainRules.create(input);
    String id=UUID.randomUUID().toString();
    return port.atomic(()->{port.references(c,id,input);port.insert(c,id,input);port.values(c,id,input.configId(),input.values());port.completeness(c,id);return port.finish(c,id,null);});
  }
  /** @param c 可信主体 @param id 商品 UUID @param input 普通字段及原版本；values=null 保留，空列表清空 @return 原子更新后的快照，启用商品完整性不足时全部回滚 */
  public Map<String,Object> update(VerifiedTenantContext c,String id,Update input){
    require(c,"update");com.appnexus.masterdata.domain.product.ProductMainRules.identifier(id);
    com.appnexus.masterdata.domain.product.ProductMainRules.update(input);
    return port.atomic(()->{
      var before=port.lock(c,id,input.version());port.unfrozen(c,id);
      ProductLifecycle.requireMutable(before.get("archivedAt")!=null,(String)before.get("structureState"));
      port.updateFields(c,id,input);
      if(input.values()!=null)port.values(c,id,(String)before.get("configId"),input.values());
      var issues=port.completeness(c,id);
      if("ENABLED".equals(before.get("status"))&&!issues.isEmpty())throw new Rejection(400,"PRODUCT_COMPLETION_REQUIRED");
      return port.finish(c,id,input.version());
    });
  }
  private void require(VerifiedTenantContext c,String action){
    if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.product:"+action))throw new Rejection(403,"MASTER_DATA_DENIED");
  }
}
