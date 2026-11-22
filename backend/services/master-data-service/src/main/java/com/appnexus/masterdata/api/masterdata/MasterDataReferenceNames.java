package com.appnexus.masterdata.api.masterdata;

import com.appnexus.contracts.operation.ReferenceNames;
import com.appnexus.platform.tenant.VerifiedTenantContext;

/** 主数据批量名称公开端口；远程实现须复核同一租户、主体、行范围与字段可见性。 */
public interface MasterDataReferenceNames extends ReferenceNames.Source<VerifiedTenantContext> {
    String WAREHOUSE="warehouse";
    String PARTNER="partner";
    String ACTIVE_WAREHOUSE="active-warehouse";
    String ACTIVE_PARTNER="active-partner";
    String PRODUCT="product";
    String AREA="storage-area";
    String LOCATION="storage-location";
    String PRODUCT_UNIT="product-unit";
    String SPECIFICATION="product-specification";
    String SKU="product-sku";
    String SKU_SPECIFICATION="product-sku-specification";
}
