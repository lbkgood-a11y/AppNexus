package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.api.product.ProductStructureData.Migration;
import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import java.util.Map;
import java.util.TreeSet;
/** 结构迁移应用协调；调用方先授权并建立原幂等范围、十秒事务及技术冲突映射。 */
public final class ProductStructureMigrationService {
  private final ProductStructurePort port;
  /** @param port 当前事务独占的锁定输出会话，不允许缓存或共享 */
  public ProductStructureMigrationService(ProductStructurePort port){this.port=port;}
  /** @param target 已验证格式的全量目标 @return 首次原子结果或原幂等重放；预检不能替代此处重读校验 */
  public Map<String,Object> migrate(Migration target){
    var replay=port.replay();if(replay!=null)return replay;
    var source=port.source();port.sourceMatches(target);port.validateTarget(target);
    if(!port.proof(target))throw new Rejection(409,"PRODUCT_PREVIEW_CHANGED_OR_EXPIRED");
    if(!new TreeSet<>(target.droppedItemIds()).equals(new TreeSet<>(port.dropped(target))))throw new Rejection(409,"PRODUCT_DROPPED_VALUES_UNCONFIRMED");
    var issues=port.issues(target);
    if(!"DRAFT".equals(source.get("status"))&&!issues.isEmpty())throw new Rejection(400,"PRODUCT_COMPLETION_REQUIRED");
    return port.apply(target);
  }
}
