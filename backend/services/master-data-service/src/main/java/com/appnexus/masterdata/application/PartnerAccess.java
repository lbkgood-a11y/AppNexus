package com.appnexus.masterdata.application;

import com.appnexus.masterdata.MasterData.Input;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;

/** 客商用例的行范围、字段及归属准入端口；持久化明细与策略读取均在适配器中实现。 */
public interface PartnerAccess {
  /** 原读取权下的归属候选与字段限制；元数据不替代实际写入检查。 */
  Map<String,Object> metadata(VerifiedTenantContext context);
  /** 在当前事务锁定操作范围内父对象，返回原归属；无权与不存在统一拒绝。 */
  String lock(VerifiedTenantContext context,String id,String action);
  /** 合并受限字段；未提交保留，提交变化拒绝，不允许遮盖文本写回原文。 */
  Input prepare(VerifiedTenantContext context,String id,Input input,Map<String,String> previous);
  /** 验证目标租户组织、启用及操作范围；creating 为 true 时组织必填，禁止伪造未知历史归属。 */
  void target(VerifiedTenantContext context,String organization,String action,boolean creating);
}
