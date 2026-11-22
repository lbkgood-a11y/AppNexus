package com.appnexus.bulkwarehousing.application;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;

/** 入仓主数据输出端口，每次查询沿用可信主体的数据访问边界。 */
public interface InboundReferenceSource {
  /** 商品身份的实物核算信息，来自主数据当前版本。 */
  record Identity(String specificationId, Long version, String baseUnitId) {}

  /** 货主档案的稳定引用。 */
  record Owner(String id, long version) {}

  /** 主数据计算返回的未舍入换算结果。 */
  record Calculation(String sourceUnitId, String targetUnitId, BigDecimal result,
                     BigDecimal factor, int scale) {}

  /** @param context 可信租户主体 @param id 商品 UUID，执行主数据现行记录范围 */
  void requireProduct(VerifiedTenantContext context,String id);

  /** @param context 可信租户主体 @param position 当前库位快照 @return 启用且版本匹配时可用于执行 */
  boolean usablePosition(VerifiedTenantContext context,Position position);

  /** @param context 可信租户主体 @param id 基础单位 UUID @param code 主数据单位编码 */
  void requireMassUnit(VerifiedTenantContext context,String id,String code);

  /** @param context 可信租户主体 @param id 新业务引用的启用仓库 UUID */
  void requireWarehouse(VerifiedTenantContext context, String id);

  /** @param context 可信租户主体 @param ids 新业务引用的仓库 UUID 集合，任一不可读或停用均拒绝 */
  void requireWarehouses(VerifiedTenantContext context,java.util.Collection<String> ids);

  /** @param context 可信租户主体 @param expected 已选位置及版本 @return 有效位置 */
  Position position(VerifiedTenantContext context, Position expected);

  /** @param context 可信租户主体 @param item 已选商品身份及版本 @return 有效身份 */
  Identity identity(VerifiedTenantContext context, Item item);

  /** @param context 可信租户主体 @param id 货主伙伴 UUID @return 可引用伙伴 */
  Owner owner(VerifiedTenantContext context, String id);

  /** @param context 可信租户主体 @param rule 换算规则及版本 @param quantity 正十进制数量 @return 换算依据 */
  Calculation unitConversion(VerifiedTenantContext context, UnitRule rule, String quantity);

  /** @param context 可信租户主体 @param item 父商品及版本 @param packaging 选用包装 @param quantity 正十进制数量 @return 包装换算依据 */
  Calculation packaging(VerifiedTenantContext context, Item item, Packaging packaging, String quantity);
}
