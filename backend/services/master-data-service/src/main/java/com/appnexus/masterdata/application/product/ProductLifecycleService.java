package com.appnexus.masterdata.application.product;

import com.appnexus.masterdata.MasterData.Rejection;
import com.appnexus.masterdata.domain.product.ProductLifecycle;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 商品主档启停及草稿归档应用协调；父锁、审批依据、完整性与事件始终在原事务执行。 */
public final class ProductLifecycleService {
  private final ProductLifecyclePort port;
  private final AccessAuthorization access;
  /** @param port 共享当前聚合事务的输出端口 @param access 实时功能授权，不信任页面预检结果 */
  public ProductLifecycleService(ProductLifecyclePort port,AccessAuthorization access){this.port=port;this.access=access;}
  /**
   * 检查入口参数及真实功能权限，必须在建立写事务前执行以保持原拒绝顺序。
   * @param context 服务端可信租户主体
   * @param id 目标 UUID，不存在与跨租户写入均按原冲突协议处理
   * @param version 必填原版本；负数由锁端口按原协议拒绝
   * @param target 启停目标，仅 ENABLED、DISABLED；归档检查时为 null
   * @param archive 是否归档用例，使用独立 manage_draft 权限
   */
  public void authorize(VerifiedTenantContext context,String id,Long version,String target,boolean archive){
    String permission="masterdata.product:"+(archive?"manage_draft":"set_status");
    if(context==null||!access.hasPermission(AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),permission))throw new Rejection(403,"MASTER_DATA_DENIED");
    try{if(!UUID.fromString(id).toString().equals(id))throw invalid();}catch(IllegalArgumentException|NullPointerException error){throw invalid();}
    if(version==null||!archive&&(target==null||!Set.of("ENABLED","DISABLED").contains(target)))throw invalid();
  }
  /** @param context 已授权可信主体 @param id 商品 UUID @param version 原版本 @param target 已验证目标状态 @return 当前聚合真实快照；由入站事务包裹 */
  public Map<String,Object> status(VerifiedTenantContext context,String id,long version,String target){
    var before=port.lock(context,id,version);port.requireUnfrozen(context,id);
    ProductLifecycle.requireTransition(before.status(),target,before.archived());
    if("ENABLED".equals(target)){
      port.requireApproved(context,id,version);
      if("LEGACY_PENDING".equals(before.structureState()))throw new Rejection(409,"PRODUCT_LEGACY_PENDING");
      port.requireComplete(context,id);
    }
    port.status(context,id,target);
    return port.commit(context,id,version,"STATUS");
  }
  /** @param context 已授权可信主体 @param id 商品 UUID @param version 原版本 @param archived 显式归档或恢复 @return 推进版本后的快照；只允许草稿，不删除历史或回收编码 */
  public Map<String,Object> archive(VerifiedTenantContext context,String id,long version,boolean archived){
    var before=port.lock(context,id,version);port.requireUnfrozen(context,id);
    ProductLifecycle.requireArchive(before.status(),before.archived(),archived);
    port.archive(context,id,archived);
    return port.commit(context,id,version,archived?"ARCHIVE":"RESTORE");
  }
  private static Rejection invalid(){return new Rejection(400,"PRODUCT_V1_INPUT_INVALID");}
}
