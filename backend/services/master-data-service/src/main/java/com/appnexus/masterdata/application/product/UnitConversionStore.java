package com.appnexus.masterdata.application.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 单位换算输出端口；固定所属表操作，锁和写入必须参与用例事务，不接收 SQL 或表名。 */
public interface UnitConversionStore {
  /** @param dimension 单位维度编码 @param enabled 当前启用状态 */
  record Unit(String dimension, boolean enabled) {}
  /** @param source 源单位 UUID @param target 目标单位 UUID @param factor 原始精确系数 */
  record Relation(String source, String target, BigDecimal factor) {}
  /** @param factor 原始精确系数 @param scale 目标单位展示小数位数，来自授权存储记录 */
  record Calculation(BigDecimal factor, int scale) {}
  /** @param tenant 可信租户 UUID @return 原授权读取协议的列表投影 */
  List<Map<String, Object>> list(String tenant);
  /** @param tenant 可信租户 UUID @param id 单位 UUID @return 锁定单位事实；不存在返回 null，调用方按 UUID 顺序加锁 */
  Unit lockUnit(String tenant, String id);
  /** @param tenant 可信租户 UUID @param source 源 UUID @param target 目标 UUID @return 已启用反向关系系数，不存在返回 null */
  BigDecimal reverse(String tenant, String source, String target);
  /** @param tenant 可信租户 UUID @param id 新关系 UUID @param source 源 UUID @param target 目标 UUID @param factor 精确正数系数 @param actor 可信实际账号 UUID；时间由数据库生成 */
  void create(String tenant, String id, String source, String target, BigDecimal factor, String actor);
  /** @param tenant 可信租户 UUID @param id 关系 UUID @return 当前关系事实，不存在返回 null */
  Relation relation(String tenant, String id);
  /** @param tenant 可信租户 UUID @param id 关系 UUID @param enabled 目标状态 @param version 原非负版本 @param actor 可信账号 @return CAS 影响行数，零必须拒绝；不覆盖创建审计 */
  int status(String tenant, String id, boolean enabled, long version, String actor);
  /** @param tenant 可信租户 UUID @param id 启用关系 UUID @return 关系和两端均启用时的计算事实，否则 null */
  Calculation calculation(String tenant, String id);
}
