package com.appnexus.masterdata.application.product;
import com.appnexus.masterdata.api.product.ProductStructureData.Migration;
import com.appnexus.contracts.operation.OperationPreviewProof.Proof;
import java.util.List;
import java.util.Map;
/** 只读结构预检输出；调用方使用readOnly事务，禁止临时写入、幂等回执及写后回滚模拟。 */
public interface ProductPreviewPort {
  /** @return 当前可信租户内商品快照，不接受客户端事实 */
  Map<String,Object> source();
  /** @param target 已格式校验的全量目标；校验源CAS、引用及值，读取引用不加写锁 */
  void validate(Migration target);
  /** @param target 全量目标 @return 当前目标完整性问题，仅只读计算 */
  List<String> issues(Migration target);
  /** @param target 全量目标 @return 真实退出项，必须与客户端明确确认集合完全一致 */
  List<String> dropped(Migration target);
  /** @param target 全量目标 @return 无持久化签名及UTC过期点；不授予执行权限 */
  Proof proof(Migration target);
  /** @param target 全量目标 @return 原规范化载荷摘要，不含凭据 */
  String digest(Migration target);
}
