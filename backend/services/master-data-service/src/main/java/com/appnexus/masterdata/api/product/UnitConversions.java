package com.appnexus.masterdata.api.product;

import com.appnexus.platform.tenant.VerifiedTenantContext;

/** 商品同维度显式单位换算输入端口；不修改商品基础单位或动态属性保存单位。 */
public interface UnitConversions {
  /**
   * 新增固定换算关系，系数不可原地修改。
   * @param sourceUnitId 已授权租户内启用的源单位 UUID
   * @param targetUnitId 同维度目标单位 UUID，不能与源相同
   * @param factor 正数十进制文本，最多 18 位整数与 12 位小数
   */
  record Input(String sourceUnitId, String targetUnitId, String factor) {}
  /**
   * 状态写入参数。
   * @param enabled 必填目标状态，不能重复转换
   * @param version 读取的非负版本，冲突不覆盖
   */
  record Status(Boolean enabled, Long version) {}
  /** @param context 服务端可信主体与租户 @return 授权租户内换算列表，不产生写入 */
  Object list(VerifiedTenantContext context);
  /** @param context 可信主体与租户 @param input 完整换算输入 @return 新建标识；锁、关系与审计同事务 */
  Object create(VerifiedTenantContext context, Input input);
  /** @param context 可信主体与租户 @param id 当前租户关系 UUID @param input 目标状态与原版本 @return 成功结果，冲突拒绝 */
  Object status(VerifiedTenantContext context, String id, Status input);
  /** @param context 可信主体与租户 @param id 启用的直连关系 UUID @param quantity 最多18位整数与12位小数的精确文本 @return 原精确乘积和 HALF_UP 展示值，展示值不能替代业务原值 */
  Object calculate(VerifiedTenantContext context, String id, String quantity);
}
