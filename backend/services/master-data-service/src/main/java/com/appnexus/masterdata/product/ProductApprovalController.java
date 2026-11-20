package com.appnexus.masterdata.product;
import com.appnexus.contracts.operation.*;import com.appnexus.contracts.workflow.*;import com.appnexus.masterdata.MasterData;import com.appnexus.platform.tenant.VerifiedTenantContext;import jakarta.servlet.http.HttpServletRequest;import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** 商品审核入口仅在已有审批引擎启用时装配，不提供模拟审批。 */
@RestController @ConditionalOnProperty(name="app.workflow.engine.enabled",havingValue="true") @RequestMapping("/api/master-data/product")
public final class ProductApprovalController {
 private final ProductApprovalService service;
 /** @param service 既有审批契约适配器 */
 public ProductApprovalController(ProductApprovalService service){this.service=service;}
 /** 当前商品版本及申请组织；组织须为当前有效任职，不能伪造审批路由。 */
 public record Submit(Long version,String organizationId){}
 /** 提交行为由唯一业务Provider声明，接口只引用；Idempotency-Key必须规范UUID。 */
 @PostMapping("/{id}/approval") @OperationBinding(id="ProductApproval.submit",reference=true)
 public Object submit(HttpServletRequest r,@PathVariable String id,@RequestBody Submit i){if(i==null||i.version()==null)throw new MasterData.Rejection(400,"PRODUCT_INPUT_INVALID");return service.submit(tenant(r),workflow(r,true),id,i.version(),i.organizationId());}
 /** 原商品读取权限内审批轮次和四审计，不暴露任意对象申请。 */
 @GetMapping("/{id}/approvals") @RecordAuditSource(table="md_product_approval") @OperationBinding(id="Product.readApprovals",objectType="Product",operationCode="readApprovals",nameZh="查询商品审核记录",nameEn="Read product approvals",pages={"master-data/product"},permissions={"masterdata.product:read"},policy="可信租户原商品读取权")
 public Object list(HttpServletRequest r,@PathVariable String id){return service.list(tenant(r),id);}
 /** 审批组织候选沿用平台有效任职，不开放整个组织目录。 */
 @GetMapping("/approval-organizations") @OperationBinding(id="Product.readApprovalOrganizations",objectType="Product",operationCode="readApprovalOrganizations",nameZh="查询商品审核组织",nameEn="Read approval organizations",pages={"master-data/product"},permissions={"masterdata.product.approval:submit"},policy="可信租户有效任职范围")
 public Object organizations(HttpServletRequest r){return service.organizations(workflow(r,false));}
 /** 任务受限原文读取；领域按当前有效任务复核，不凭请求ID扩大商品读取权。 */
 @GetMapping("/approval-requests/{id}") @OperationBinding(id="Product.readApprovalSnapshot",objectType="Product",operationCode="readApprovalSnapshot",nameZh="查看商品审核快照",nameEn="Read product approval snapshot",pages={"master-data/product"},policy="原申请人或有效任务受限读取",eligibility=OperationBinding.Eligibility.OBJECT)
 public Object detail(HttpServletRequest r,@PathVariable String id,@RequestParam(required=false) String taskId){return service.detail(workflow(r,false),id,taskId);}
 private static VerifiedTenantContext tenant(HttpServletRequest r){if(!(r.getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext c))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");return c;}
 private static WorkflowContext workflow(HttpServletRequest r,boolean write){if(!(r.getAttribute(WorkflowContext.class.getName()) instanceof WorkflowContext c))throw new MasterData.Rejection(403,"MASTER_DATA_DENIED");String key=write?r.getHeader("Idempotency-Key"):c.requestId();try{UUID.fromString(key);}catch(Exception e){throw new MasterData.Rejection(400,"PRODUCT_INPUT_INVALID");}return new WorkflowContext(c.tenantId(),c.actorId(),key,c.personId(),c.organizationId());}
}
