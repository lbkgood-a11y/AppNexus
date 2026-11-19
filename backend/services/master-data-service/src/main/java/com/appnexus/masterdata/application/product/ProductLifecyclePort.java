package com.appnexus.masterdata.application.product;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;

/** 商品主档生命周期输出；必须参与父行锁、扩展、版本和事件的同一聚合事务。 */
public interface ProductLifecyclePort {
  /** @param status 当前 DRAFT、ENABLED 或 DISABLED @param archived 是否归档 @param structureState 当前结构状态 */
  record State(String status,boolean archived,String structureState) {}
  /** @param context 可信主体 @param id 商品 UUID @param version 非负原版本 @return 锁父记录并核验版本后的状态；不泄露其他租户记录 */
  State lock(VerifiedTenantContext context,String id,long version);
  /** @param context 可信主体 @param id 商品 UUID；审批处理中必须拒绝写入 */
  void requireUnfrozen(VerifiedTenantContext context,String id);
  /** @param context 可信主体 @param id 已锁商品 @param version 启用所针对版本；只读取最新审批依据，不修改审批状态 */
  void requireApproved(VerifiedTenantContext context,String id,long version);
  /** @param context 可信主体 @param id 已锁商品；真实引用、启用状态与完整性必须在当前事务重读 */
  void requireComplete(VerifiedTenantContext context,String id);
  /** @param context 可信主体 @param id 已锁商品 @param target 允许的目标启停状态；不独立推进版本 */
  void status(VerifiedTenantContext context,String id,String target);
  /** @param context 可信主体 @param id 已锁草稿 @param archived true 由数据库生成 UTC 归档时间，false 清空；不覆盖创建信息 */
  void archive(VerifiedTenantContext context,String id,boolean archived);
  /** @param context 可信主体 @param id 已锁商品 @param version 原版本 @param event 固定 STATUS、ARCHIVE 或 RESTORE @return 推进父 CAS、修改审计和追加事件后的真实快照；失败回滚全部写入 */
  Map<String,Object> commit(VerifiedTenantContext context,String id,long version,String event);
}
