package com.appnexus.bulkwarehousing.adapter.integration;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences.*;
import com.appnexus.bulkwarehousing.application.InboundReferenceSource;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.api.product.ProductStructureManagement;
import com.appnexus.masterdata.api.product.ProductCatalogManagement;
import com.appnexus.masterdata.api.product.ProductManagement;
import com.appnexus.masterdata.api.product.UnitConversions;
import com.appnexus.platform.tenant.VerifiedTenantContext;

/** 通过主数据公开入口取得进仓引用，把可信事实转换为仓储自身的数据契约。 */
public final class MasterDataInboundReceiptReferences implements InboundReferenceSource {
  private final MasterDataManagement masterData;
  private final ProductStructureManagement products;
  private final UnitConversions conversions;
  private final ProductCatalogManagement catalog;
  private final ProductManagement productRead;

  /**
   * @param masterData 现有位置和业务伙伴的授权读取入口
   * @param products 现有商品身份和包装依据的授权读取入口
   * @param conversions 现有单位换算证据的授权读取入口
   * @param catalog 现有计量单位目录的授权读取入口
   * @param productRead 既有商品记录范围读取入口
   */
  public MasterDataInboundReceiptReferences(MasterDataManagement masterData,
      ProductStructureManagement products, UnitConversions conversions,ProductCatalogManagement catalog,ProductManagement productRead) {
    this.masterData = masterData;
    this.products = products;
    this.conversions = conversions;
    this.catalog = catalog;
    this.productRead = productRead;
  }

  /** {@inheritDoc} */
  @Override public void requireProduct(VerifiedTenantContext context,String id) {
    productRead.read(context,id);
  }

  /** {@inheritDoc} */
  @Override public boolean usablePosition(VerifiedTenantContext context,Position position) {
    try {
      position(context,position);
      return true;
    } catch (MasterData.Rejection error) {
      if (error.status==400 || error.status==409) return false;
      throw error;
    }
  }

  /** {@inheritDoc} */
  @Override public void requireMassUnit(VerifiedTenantContext context,String id,String code) {
    if (code==null || code.isBlank() || code.length()>64
        || catalog.units(context,code,1,100,"MASS").rows().stream().noneMatch(unit ->
            unit.id().equals(id) && unit.code().equals(code) && unit.enabled()
                && "MASS".equals(unit.dimensionCode()))) {
      throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(400,"OUTBOUND_WEIGHT_UNIT_REQUIRED");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void requireWarehouse(VerifiedTenantContext context, String id) {
    if (masterData.list(context, MasterData.Kind.WAREHOUSE, "", true, 1, 1, id).rows().size()!=1) {
      throw new com.appnexus.contracts.businessobject.BusinessObjects.Rejection(403, "INBOUND_ACCESS_DENIED");
    }
  }

  /** {@inheritDoc} */
  @Override
  public Position position(VerifiedTenantContext context, Position expected) {
    var stored = masterData.resolveStoragePosition(context,
        new MasterDataManagement.StoragePosition(expected.warehouseId(), expected.areaId(),
            expected.locationId(), expected.warehouseVersion(), expected.areaVersion(), expected.locationVersion()));
    return new Position(stored.warehouseId(), stored.areaId(), stored.locationId(),
        stored.warehouseVersion(), stored.areaVersion(), stored.locationVersion());
  }

  /** {@inheritDoc} */
  @Override
  public Identity identity(VerifiedTenantContext context, Item item) {
    var identity = products.resolveIdentity(context, item.productId(),
        ProductStructureManagement.IdentityKind.valueOf(item.kind().name()), item.identityId(), item.productVersion());
    return new Identity(identity.specificationId(), identity.identityVersion(), identity.baseUnitId());
  }

  /** {@inheritDoc} */
  @Override
  public Owner owner(VerifiedTenantContext context, String id) {
    var partners = masterData.list(context, MasterData.Kind.PARTNER, "", true, 1, 1, id).rows();
    if (partners.size() != 1) throw new IllegalArgumentException("INBOUND_OWNER_UNAVAILABLE");
    var owner = partners.getFirst();
    return new Owner(owner.id(), owner.version());
  }

  /** {@inheritDoc} */
  @Override
  public Calculation unitConversion(VerifiedTenantContext context, UnitRule rule, String quantity) {
    var evidence = conversions.evidence(context, rule.ruleId(), rule.version(), quantity);
    return new Calculation(evidence.sourceUnitId(), evidence.targetUnitId(),
        evidence.convertedQuantity(), evidence.factor(), evidence.targetScale());
  }

  /** {@inheritDoc} */
  @Override
  public Calculation packaging(VerifiedTenantContext context, Item item, Packaging packaging, String quantity) {
    var evidence = products.packagingEvidence(context, item.productId(), packaging.code(),
        item.productVersion(), quantity);
    return new Calculation(evidence.sourceUnitId(), evidence.targetUnitId(),
        evidence.convertedQuantity(), evidence.factor(), evidence.targetScale());
  }
}
