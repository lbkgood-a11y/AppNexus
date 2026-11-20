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
@BusinessObjectType(code="ProductUnit",nameZh="计量单位",nameEn="Product unit",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class ProductUnitController {
  private final ProductCatalogManagement service;
  /** @param service 负责租户隔离、授权、事务与CAS的领域服务 */
  public ProductUnitController(ProductCatalogManagement service){this.service=service;}
  /** 查询分页；关键词最多200字符，页码默认1，每页默认20且范围1至100。 */
  @GetMapping
  @OperationBinding(id="ProductUnit.list",objectType="ProductUnit",operationCode="list",nameZh="查询计量单位",nameEn="List unit",pages={"master-data/product-unit"},permissions={"masterdata.productunit:read"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
  public Object list(HttpServletRequest request,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
    return service.units(context(request),keyword,page,size);
  }
  /** 新增档案，编码同租户唯一，不接受审计或身份。 */
  @PostMapping
  @OperationBinding(id="ProductUnit.create",objectType="ProductUnit",operationCode="create",nameZh="新增计量单位",nameEn="Create unit",pages={"master-data/product-unit"},permissions={"masterdata.productunit:create"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
  public Object create(HttpServletRequest request,@RequestBody UnitInput input){return service.createUnit(context(request),input);}
  /** 修改档案，使用当前version，编码不允许修改。 */
  @PutMapping("/{id}")
  @OperationBinding(id="ProductUnit.update",objectType="ProductUnit",operationCode="update",nameZh="编辑计量单位",nameEn="Update unit",pages={"master-data/product-unit"},permissions={"masterdata.productunit:update"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
  public Object update(HttpServletRequest request,@PathVariable String id,@RequestBody UnitInput input){return service.updateUnit(context(request),id,input);}
  /** 独立启停，CAS失败不推进正文或审计。 */
  @PutMapping("/{id}/status")
  @OperationBinding(id="ProductUnit.setStatus",objectType="ProductUnit",operationCode="setStatus",nameZh="启停计量单位",nameEn="Set status",pages={"master-data/product-unit"},permissions={"masterdata.productunit:set_status"},policy="可信租户有效成员、独立功能权限、领域校验及商品聚合版本")
  public Object status(HttpServletRequest request,@PathVariable String id,@RequestBody Status input){return service.statusUnit(context(request),id,input);}

  private static VerifiedTenantContext context(HttpServletRequest request){
    Object value=request.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");return c;
  }
}
