package com.appnexus.masterdata.application.product;

import com.appnexus.masterdata.api.product.ProductMainData.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** 商品主档写入输出端口；固定父记录、属性、完整性与事件操作，共享同一聚合事务。 */
public interface ProductMainPort {
  /** @param <T> 业务返回类型 @param work 同线程聚合回调 @return 原子结果；存储冲突映射原 PRODUCT_V1_CONFLICT，全部回滚 */
  <T>T atomic(Supplier<T> work);
  /** @param c 可信主体 @param input 草稿输入；检查真实租户引用和固定发布版本 */
  void references(VerifiedTenantContext c,Input input);
  /** @param c 可信主体 @param id 服务端新 UUID @param input 已验证草稿；初始状态由数据库固定 DRAFT，创建审计来自可信账号及数据库 UTC */
  void insert(VerifiedTenantContext c,String id,Input input);
  /** @param c 可信主体 @param id 商品 UUID @param version 原非负版本 @return 锁父行后的原快照，仅当前租户 */
  Map<String,Object> lock(VerifiedTenantContext c,String id,long version);
  /** @param c 可信主体 @param id 已锁商品；审批冻结时拒绝普通写 */
  void unfrozen(VerifiedTenantContext c,String id);
  /** @param c 可信主体 @param id 已锁商品 @param input 普通字段；不能变更分类、结构或基础单位 */
  void updateFields(VerifiedTenantContext c,String id,Update input);
  /** @param c 可信主体 @param id 当前商品 @param config 固定配置版本 UUID，可空 @param values 类型化属性；由原校验器验证后同事务保存 */
  void values(VerifiedTenantContext c,String id,String config,List<Value> values);
  /** @param c 可信主体 @param id 当前商品 @return 重新计算并保存完整性后的问题集合，不自动启用 */
  List<String> completeness(VerifiedTenantContext c,String id);
  /** @param c 可信主体 @param id 当前商品 @param version 修改原版本；创建时为 null @return CAS、审计及追加事件全部完成后的快照 */
  Map<String,Object> finish(VerifiedTenantContext c,String id,Long version);
}
