package com.appnexus.masterdata.adapter.web.product;
import com.appnexus.masterdata.api.product.*;

import com.appnexus.contracts.operation.*;
import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.api.product.ProductCatalogData.*;

/** 商品分类固定接口；身份只取宿主可信上下文，写入沿宿主Cookie/CSRF链。 */
@RestController
@RequestMapping("/api/master-data/product-category")

public final class ProductCategoryController {
  private final ProductCatalogManagement service;
  /** @param service 负责租户隔离、授权、事务与CAS的领域服务 */
  public ProductCategoryController(ProductCatalogManagement service){this.service=service;}
  /** 查询分页；关键词最多200字符，页码默认1，每页默认20且范围1至100。 */
  @GetMapping
  @OperationBinding(id="ProductCategory.list", reference=true)
  public Object list(HttpServletRequest request,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String parentId){
    return service.categories(context(request),parentId,keyword,page,size);
  }
  /** 新增档案，编码同租户唯一，不接受审计或身份。 */
  @PostMapping
  @OperationBinding(id="ProductCategory.create", reference=true)
  public Object create(HttpServletRequest request,@RequestBody CategoryInput input){return service.createCategory(context(request),input);}
  /** 修改档案，使用当前version，编码不允许修改。 */
  @PutMapping("/{id}")
  @OperationBinding(id="ProductCategory.update", reference=true)
  public Object update(HttpServletRequest request,@PathVariable String id,@RequestBody CategoryInput input){return service.updateCategory(context(request),id,input);}
  /** 独立启停，CAS失败不推进正文或审计。 */
  @PutMapping("/{id}/status")
  @OperationBinding(id="ProductCategory.setStatus", reference=true)
  public Object status(HttpServletRequest request,@PathVariable String id,@RequestBody Status input){return service.statusCategory(context(request),id,input);}

  /** 发布四槽明确完整的不可变配置，SPEC为空；服务单位规则必填。 */
  @PostMapping("/{id}/configurations")
  @OperationBinding(id="ProductCategory.publishConfiguration", reference=true)
  public Object publish(HttpServletRequest request,@PathVariable String id,@RequestBody Publish input){return service.publish(context(request),id,input);}
  /** 固定版本读取，不重新解析当前指针。 */
  @GetMapping("/{id}/configurations/{configId}")
  @OperationBinding(id="ProductCategory.readConfiguration", reference=true)
  public Object configuration(HttpServletRequest request,@PathVariable String id,@PathVariable String configId){return service.configuration(context(request),id,configId);}

  private static VerifiedTenantContext context(HttpServletRequest request){
    Object value=request.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;
  }
}
