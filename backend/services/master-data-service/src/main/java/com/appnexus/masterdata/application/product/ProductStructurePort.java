package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.api.product.ProductStructureData.Migration;
import java.util.List;
import java.util.Map;
/** 单次结构迁移输出会话；实例只在原十秒事务内使用，禁止跨请求共享锁定状态。 */
public interface ProductStructurePort {
  /** @return 先锁父商品、校验审批冻结再重读幂等结果；无历史返回null，重放不重验旧token */
  Map<String,Object> replay();
  /** @return 已锁商品真实快照，不能采用客户端快照 */
  Map<String,Object> source();
  /** @param target 全量目标；校验源CAS、源结构版本及固定配置一致性 */
  void sourceMatches(Migration target);
  /** @param target 全量目标；按原顺序锁引用并验证类型化值，不产生写入 */
  void validateTarget(Migration target);
  /** @param target 原预检目标 @return 当前主体、源、目标及权限摘要是否仍匹配有效签名 */
  boolean proof(Migration target);
  /** @param target 全量目标 @return 真实退出项，不能由客户端缩小 */
  List<String> dropped(Migration target);
  /** @param target 全量目标 @return 目标完整性问题，不替代源校验或授权 */
  List<String> issues(Migration target);
  /** @param target 已校验全量目标 @return 清旧值、改父配置、保存目标值与审计历史、CAS及幂等回执的原子结果 */
  Map<String,Object> apply(Migration target);
}
