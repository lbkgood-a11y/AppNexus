package com.appnexus.platform;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 在隔离库内准备商品创建所需的启用归属组织。 */
final class ProductOrganizationFixture {
    private ProductOrganizationFixture() {}

    /**
     * @param jdbc 隔离库连接
     * @param tenant 已初始化的测试租户
     * @return 可作为商品归属的组织标识
     */
    static String create(JdbcTemplate jdbc, UUID tenant) {
        String type = UUID.randomUUID().toString();
        String organization = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PRODUCT-QA','商品验收类型')", tenant.toString(), type);
        jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'PRODUCT-QA',UUID_TO_BIN(?),'商品验收组织')", tenant.toString(), organization, type);
        return organization;
    }
}
