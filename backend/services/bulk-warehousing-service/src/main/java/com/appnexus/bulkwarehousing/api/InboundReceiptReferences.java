package com.appnexus.bulkwarehousing.api;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;

/** 进仓确认所需的只读主数据核验阶段，供仓储应用用例在过账事务中调用。 */
public interface InboundReceiptReferences {
  /** 现有主数据位置 UUID 和调用方已复核的非负档案版本。 */
  record Position(String warehouseId, String areaId, String locationId,
                  long warehouseVersion, long areaVersion, long locationVersion) {}

  /** 本次库存核算所使用的商品身份粒度。 */
  enum IdentityKind { PRODUCT, SPECIFICATION, SKU }

  /** 现有商品与核算身份 UUID，以及选定结构时的非负父商品版本。 */
  record Item(String productId, IdentityKind kind, String identityId, long productVersion) {}

  /** 由批准的数量证据选定的换算依据。 */
  sealed interface Conversion permits SameUnit, UnitRule, Packaging {}

  /** 实收单位已经是商品基础单位。 */
  record SameUnit() implements Conversion {}

  /** 现有单位换算规则 UUID 与非负预期版本。 */
  record UnitRule(String ruleId, long version) implements Conversion {}

  /** 商品计量配置中的包装编码，版本沿用 Item 的父商品版本。 */
  record Packaging(String code) implements Conversion {}

  /** 已授权进仓操作通过服务端业务对象确定的引用与数量。 */
  record Request(Position position, Item item, String ownerPartnerId, String sourceUnitId,
                 String quantity, Conversion conversion) {}

  /** 当次核验的换算依据与未舍入数量，可由过账用例持久化。 */
  record QuantityEvidence(String sourceUnitId, String baseUnitId, BigDecimal sourceQuantity,
                          BigDecimal baseQuantity, BigDecimal factor, Integer targetScale,
                          Conversion conversion) {}

  /** 进仓用例可用于写入的引用快照。 */
  record Resolved(Position position, Item item, String specificationId, Long identityVersion,
                  String ownerPartnerId, long ownerPartnerVersion, QuantityEvidence quantity) {}

  /**
   * 按原主体的主数据权限解析进仓实物、货主与数量引用。
   * @param context 服务端可信租户及成员；宿主进仓命令应已完成自身业务准入
   * @param request 必填位置、商品、货主、来源单位 UUID、正十进制实收量和换算依据
   * @return 同一次调用使用的稳定引用和计算证据
   * @throws IllegalArgumentException 数量或引用与既有主数据证据不匹配
   */
  Resolved resolve(VerifiedTenantContext context, Request request);
}
