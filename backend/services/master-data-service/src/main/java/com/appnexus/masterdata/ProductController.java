package com.appnexus.masterdata;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.MasterData.*;

/** 商品管理固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/product")
@BusinessObjectType(code="Product",nameZh="商品管理",nameEn="Products",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class ProductController {
  private final MasterDataService service;
  /**
   * @param service 负责真实授权和事务的主数据服务 */
  public ProductController(MasterDataService service){this.service=service;}
  /** 查询商品管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Product.list",objectType="Product",operationCode="list",pages={"master-data/product"},nameZh="查询商品管理",nameEn="list Products",permissions={"masterdata.product:read"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return service.list(context(r),Kind.PRODUCT,keyword,enabled,page,size);}
  /** 新增商品管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Product.create",objectType="Product",operationCode="create",pages={"master-data/product"},nameZh="新增商品管理",nameEn="create Products",permissions={"masterdata.product:create"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.PRODUCT,input);}
  /** 编辑商品管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Product.update",objectType="Product",operationCode="update",pages={"master-data/product"},nameZh="编辑商品管理",nameEn="update Products",permissions={"masterdata.product:update"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.PRODUCT,id,input);}
  /** 启停商品管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Product.setStatus",objectType="Product",operationCode="setStatus",pages={"master-data/product"},nameZh="启停商品管理",nameEn="setStatus Products",permissions={"masterdata.product:set_status"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.PRODUCT,id,input);}

  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
