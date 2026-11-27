package com.appnexus.bulkwarehousing.adapter.web.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CommodityTemplates;
import com.appnexus.bulkwarehousing.api.inboundexecution.CommodityTemplates.*;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.operation.OperationBinding;
import com.appnexus.contracts.operation.RecordAuditSource;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 现有模板表的真实配置和发布入口。 */
@RestController
@RequestMapping(CommodityTemplates.PATH)
public final class CommodityTemplateController {
    private final CommodityTemplates templates;
    /** @param templates 受控发布用例 */
    public CommodityTemplateController(CommodityTemplates templates){this.templates=templates;}
    /** @return 加载已授权分页及总数 */
    @GetMapping
    @RecordAuditSource(table="bw_commodity_template",path="rows")
    @OperationBinding(id="BulkCommodityTemplate.list",reference=true)
    public ResponseEntity<CommodityTemplatePage> listCommodityTemplates(HttpServletRequest request,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(templates.list(context(request),page,pageSize));
    }
    /** @return 新草稿 */
    @PostMapping
    @OperationBinding(id="BulkCommodityTemplate.save",reference=true)
    public CommodityTemplateVersion createCommodityTemplate(HttpServletRequest request,@RequestBody CommodityTemplateDraft input){return templates.create(context(request),input);}
    /** @return 更新后的未发布草稿 */
    @PutMapping("/{id}")
    @OperationBinding(id="BulkCommodityTemplate.save",reference=true)
    public CommodityTemplateVersion updateCommodityTemplate(HttpServletRequest request,@PathVariable UUID id,@RequestBody CommodityTemplateEdit input){
        return templates.update(context(request),id,input);
    }
    /** @return 发布后的当前版本 */
    @PostMapping("/{id}:publish")
    @OperationBinding(id="BulkCommodityTemplate.publish",reference=true)
    public CommodityTemplateVersion publishCommodityTemplate(HttpServletRequest request,@PathVariable UUID id,@RequestBody CommodityTemplatePublication input){
        return templates.publish(context(request),id,input);
    }
    private static VerifiedTenantContext context(HttpServletRequest request){
        if(request.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c)return c;
        throw new Rejection(403,"INBOUND_TEMPLATE_ACCESS_DENIED");
    }
    /** @return 统一查询、编辑或发布权限不足 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetail> denied(){
        return rejected(new Rejection(403,"INBOUND_TEMPLATE_ACCESS_DENIED"));
    }
    /** @return 不回显配置原文的稳定错误 */
    @ExceptionHandler(Rejection.class)
    public ResponseEntity<ProblemDetail> rejected(Rejection ex){
        var problem=ProblemDetail.forStatus(ex.status());
        problem.setProperty("code",ex.getMessage());
        return ResponseEntity.status(ex.status()).cacheControl(CacheControl.noStore()).body(problem);
    }
}
