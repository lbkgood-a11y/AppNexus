package com.appnexus.masterdata.application;

import com.appnexus.masterdata.api.masterdata.MasterData.Input;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;

/** 客商用例的行范围、字段及归属准入端口；持久化明细与策略读取均在适配器中实现。 */
public interface PartnerAccess {
  /**
   * @param context 当前可信请求
   * @param action 固定 read、create 或 update；管理候选不能替代写入检查
   * @return 对应权限下的归属候选与字段限制
   */
  Map<String,Object> metadata(VerifiedTenantContext context,String action);
  /** 在当前事务锁定操作范围内父对象，返回原归属；无权与不存在统一拒绝。 */
  String lock(VerifiedTenantContext context,String id,String action);
  /** 合并受限字段；未提交保留，提交变化拒绝，不允许遮盖文本写回原文。 */
  Input prepare(VerifiedTenantContext context,String id,Input input,Map<String,String> previous);
  /**
   * @param context 当前可信请求
   * @param partnerId 修改时已经锁定、创建时由服务器分配的客商 UUID
   * @param organization 目标组织 UUID；历史未知且未变更时为空
   * @param action 真实创建或修改操作
   * @param creating 创建必须提供目标归属
   */
  void target(VerifiedTenantContext context,String partnerId,String organization,String action,boolean creating);
}
