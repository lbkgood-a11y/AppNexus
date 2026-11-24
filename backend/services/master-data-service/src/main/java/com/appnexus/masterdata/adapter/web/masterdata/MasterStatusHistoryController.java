package com.appnexus.masterdata.adapter.web.masterdata;

import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.masterdata.api.masterdata.StatusHistory.Page;
import com.appnexus.masterdata.api.masterdata.StatusHistory.Subject;
import com.appnexus.masterdata.application.masterdata.StatusHistoryQuery;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** 主数据启停历史只读入口；路由绑定对象声明，不接受客户端提交对象类型或租户。 */
@RestController
@RequestMapping("/api/master-data")
public final class MasterStatusHistoryController {
    private final StatusHistoryQuery query;

    /** @param query 同一主档授权下的历史查询 */
    public MasterStatusHistoryController(StatusHistoryQuery query) {
        this.query = query;
    }

    private Page list(HttpServletRequest request, Subject subject, String id, int page, int size) {
        var context = (VerifiedTenantContext) request.getAttribute(VerifiedTenantContext.class.getName());
        return query.list(context, subject, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/partner/{id}/status-history")
    @OperationBinding(id="BusinessPartner.readStatusHistory", reference=true)
    public Page partner(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.PARTNER, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/warehouse/{id}/status-history")
    @OperationBinding(id="Warehouse.readStatusHistory", reference=true)
    public Page warehouse(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.WAREHOUSE, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/storage-area/{id}/status-history")
    @OperationBinding(id="StorageArea.readStatusHistory", reference=true)
    public Page storage_area(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.STORAGE_AREA, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/storage-location/{id}/status-history")
    @OperationBinding(id="StorageLocation.readStatusHistory", reference=true)
    public Page storage_location(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.STORAGE_LOCATION, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/country-region/{id}/status-history")
    @OperationBinding(id="CountryRegion.readStatusHistory", reference=true)
    public Page country_region(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.COUNTRY_REGION, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/subdivision/{id}/status-history")
    @OperationBinding(id="Subdivision.readStatusHistory", reference=true)
    public Page subdivision(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.SUBDIVISION, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/product-category/{id}/status-history")
    @OperationBinding(id="ProductCategory.readStatusHistory", reference=true)
    public Page product_category(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.CATEGORY, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/product-unit/{id}/status-history")
    @OperationBinding(id="ProductUnit.readStatusHistory", reference=true)
    public Page product_unit(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.UNIT, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/product-attribute/groups/{id}/status-history")
    @OperationBinding(id="ProductAttribute.readGroupStatusHistory", reference=true)
    public Page groups(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.ATTRIBUTE_GROUP, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/product-attribute/{id}/status-history")
    @OperationBinding(id="ProductAttribute.readStatusHistory", reference=true)
    public Page product_attribute(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.ATTRIBUTE, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/product-template/{id}/status-history")
    @OperationBinding(id="ProductTemplate.readStatusHistory", reference=true)
    public Page product_template(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.TEMPLATE, id, page, size);
    }

    /** 读取目标的启停历史；page 从 1 开始，size 为每页 1–100 条。 */
    @GetMapping("/product-unit/conversions/{id}/status-history")
    @OperationBinding(id="ProductUnit.readConversionStatusHistory", reference=true)
    public Page conversions(HttpServletRequest request, @PathVariable String id,
                         @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return list(request, Subject.CONVERSION, id, page, size);
    }
}
