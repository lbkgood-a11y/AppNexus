package com.appnexus.bulkwarehousing.application;

import com.appnexus.contracts.operation.ReferenceNames;
import com.appnexus.platform.tenant.VerifiedTenantContext;

/** 库存总览名称输出端口；应用只描述本侧展示引用，集成适配器负责所属服务调用。 */
public interface InventoryOverviewReferences extends ReferenceNames.Source<VerifiedTenantContext> {
    String WAREHOUSE="warehouse";
    String PARTNER="partner";
    String ACTIVE_WAREHOUSE="active-warehouse";
    String ACTIVE_PARTNER="active-partner";
    String PRODUCT="product";
    String PRODUCT_UNIT="product-unit";
    String SPECIFICATION="product-specification";
    String SKU="product-sku";
    String SKU_SPECIFICATION="product-sku-specification";
}
