package com.appnexus.masterdata.application.product;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;

/** 商品父记录端口；聚合扩展仍在同一宿主事务内执行，禁止跨租户读取或独立提交父版本。 */
public interface ProductRecords {
  /**
   * 读取父记录与只读审计，缺失和跨租户统一返回冲突，避免泄露存在性。
   * @param context 可信租户主体，不能从请求正文构造
   * @param id 商品 UUID，调用方先验证格式及读取或写入权限
   * @param lock true 时必须在写事务中锁定父行，直到扩展与审计完成
   * @return 当前父记录投影；不包含其他租户数据
   */
  Map<String,Object> load(VerifiedTenantContext context, String id, boolean lock);
  /** CAS 推进父版本及最后修改审计；expectedVersion 为读取的非负版本，失败整笔聚合回滚。 */
  void advance(VerifiedTenantContext context, String id, long expectedVersion);
}
