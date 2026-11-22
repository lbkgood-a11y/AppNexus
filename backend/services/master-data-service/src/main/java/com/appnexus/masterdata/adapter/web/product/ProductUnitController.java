package com.appnexus.masterdata.adapter.web.product;
import com.appnexus.masterdata.api.product.*;

import com.appnexus.contracts.operation.*;
import com.appnexus.masterdata.api.masterdata.MasterData.Rejection;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.api.product.ProductCatalogData.*;

/** 计量单位固定接口；身份只取宿主可信上下文，写入沿宿主Cookie/CSRF链。 */
@RestController
@RequestMapping("/api/master-data/product-unit")

public final class ProductUnitController {
  private final ProductCatalogManagement service;
  /** @param service 负责租户隔离、授权、事务与CAS的领域服务 */
  public ProductUnitController(ProductCatalogManagement service){this.service=service;}
  /** 查询分页；关键词最多200字符，页码默认1，每页默认20且范围1至100。 */
  @RecordAuditNames(paths={"rows.audit"})
  @GetMapping
  @OperationBinding(id="ProductUnit.list", reference=true)
  public Object list(HttpServletRequest request,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String groupCode){
    return service.units(context(request),keyword,page,size,groupCode);
  }
  /** 查询当前租户单位分组。 */
  @GetMapping("/groups")
  @OperationBinding(id="ProductUnit.listGroups", reference=true)
  public Object groups(HttpServletRequest request){return service.unitGroups(context(request));}

  /** 创建同租户分组，编码创建后固定。 */
  @PostMapping("/groups")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @OperationBinding(id="ProductUnit.createGroup", reference=true)
  public void createGroup(HttpServletRequest request,@RequestBody ProductCatalogManagement.UnitGroupInput input){
    service.createUnitGroup(context(request),input);
  }

  /** 按当前版本修改同租户分组名称。 */
  @PutMapping("/groups/{code}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @OperationBinding(id="ProductUnit.renameGroup", reference=true)
  public void renameGroup(HttpServletRequest request,@PathVariable String code,@RequestBody ProductCatalogManagement.UnitGroupInput input){
    service.renameUnitGroup(context(request),code,input);
  }
  /** 新增档案，编码同租户唯一，不接受审计或身份。 */
  @PostMapping
  @OperationBinding(id="ProductUnit.create", reference=true)
  public Object create(HttpServletRequest request,@RequestBody UnitInput input){return service.createUnit(context(request),input);}
  /** 修改档案，使用当前version，编码不允许修改。 */
  @PutMapping("/{id}")
  @OperationBinding(id="ProductUnit.update", reference=true)
  public Object update(HttpServletRequest request,@PathVariable String id,@RequestBody UnitInput input){return service.updateUnit(context(request),id,input);}
  /** 独立启停，CAS失败不推进正文或审计。 */
  @PutMapping("/{id}/status")
  @OperationBinding(id="ProductUnit.setStatus", reference=true)
  public Object status(HttpServletRequest request,@PathVariable String id,@RequestBody Status input){return service.statusUnit(context(request),id,input);}

  private static VerifiedTenantContext context(HttpServletRequest request){
    Object value=request.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;
  }
}
