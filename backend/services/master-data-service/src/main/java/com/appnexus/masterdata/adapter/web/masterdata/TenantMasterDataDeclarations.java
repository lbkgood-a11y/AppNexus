package com.appnexus.masterdata.adapter.web.masterdata;

import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.businessobject.InteractionContract;
import com.appnexus.masterdata.api.masterdata.MasterData.Kind;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 绑定固定主数据专用 API 到唯一对象声明；标准和层级查询沿用各对象读取权限。 */
@Configuration(proxyBeanMethods = false)
public class TenantMasterDataDeclarations {
    /** @return 国别地区四个专用操作及租户资源声明 */
    @Bean
    public Provider countryRegionDeclaration() {
        return () -> descriptor(Kind.COUNTRY_REGION, "国别地区", "Countries and regions", "master-data/country-region");
    }

    /** @return 行政区划四个专用操作及租户资源声明 */
    @Bean
    public Provider subdivisionDeclaration() {
        return () -> descriptor(Kind.SUBDIVISION, "行政区划", "Subdivisions", "master-data/subdivision");
    }

    /** @return 库区功能权限及租户资源；读与维护分别继承归属仓库的查询与修改范围 */
    @Bean
    public Provider storageAreaDeclaration() {
        return () -> descriptor(Kind.STORAGE_AREA, "库区管理", "Storage areas", "master-data/storage");
    }

    /** @return 库位功能权限及租户资源；读与维护分别继承祖先仓库的查询与修改范围 */
    @Bean
    public Provider storageLocationDeclaration() {
        return () -> descriptor(Kind.STORAGE_LOCATION, "库位管理", "Storage locations", "master-data/storage");
    }

    private Descriptor descriptor(Kind kind, String nameZh, String nameEn, String page) {
        var access = new DataAccess("TENANT", List.of(
                new DataResource(kind.resource(), "", nameZh, nameEn, List.of())), Map.of());
        return new Descriptor(kind.objectType, nameZh, nameEn, "master-data", kind==Kind.STORAGE_AREA||kind==Kind.STORAGE_LOCATION?2:1, Category.MASTER_DATA,
                List.of(), List.of(
                        operation(kind, page, "list", "查询" + nameZh, "Read " + nameEn, "read", "GET", ""),
                        operation(kind, page, "create", "新增" + nameZh, "Create " + nameEn, "create", "POST", ""),
                        operation(kind, page, "update", "编辑" + nameZh, "Update " + nameEn, "update", "PUT", "/{id}"),
                        operation(kind, page, "setStatus", "启停" + nameZh, "Set status of " + nameEn,
                                "set_status", "PUT", "/{id}/status")), access);
    }

    private Operation operation(Kind kind, String page, String code, String nameZh, String nameEn, String action,
            String method, String suffix) {
        String id = kind.objectType + "." + code;
        var location = new PageOperation(page, "/" + page, nameZh, nameEn, method, "/api/master-data/" + kind.path + suffix);
        var interaction = new InteractionContract(id, 1, kind.objectType, code, nameZh, nameEn,
                InteractionContract.Trigger.USER_ACTION, List.of());
        return new Operation(code, nameZh, nameEn, kind.resource() + ":" + action, id, location,
                List.of(), interaction);
    }
}
