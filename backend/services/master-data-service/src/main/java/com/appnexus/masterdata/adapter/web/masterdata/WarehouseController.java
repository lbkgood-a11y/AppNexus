package com.appnexus.masterdata.adapter.web.masterdata;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.*;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 仓库管理固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/warehouse")
@BusinessObjectType(code="Warehouse",nameZh="仓库管理",nameEn="Warehouses",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class WarehouseController {
  private final MasterDataManagement service;
  /**
   * @param service 负责真实授权和事务的主数据服务 */
  public WarehouseController(MasterDataManagement service){this.service=service;}
  /** 查询仓库管理；id缺省分页，传UUID查询已授权详情；options默认false，true只返回本租户字典，仍复核仓库读取权限。 */
  @OperationBinding(id="Warehouse.list",objectType="Warehouse",operationCode="list",pages={"master-data/warehouse"},nameZh="查询仓库管理",nameEn="list Warehouses",permissions={"masterdata.warehouse:read"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String id,@RequestParam(defaultValue="false") boolean options,@RequestParam(defaultValue="false") boolean geography) {if(geography)return service.geographyCatalog(context(r),Kind.WAREHOUSE);if(options)return service.warehouseOptions(context(r));return service.list(context(r),Kind.WAREHOUSE,keyword,enabled,page,size,id);}
  /** 新增仓库管理；geography 默认 false，为 true 返回本租户地理摘要，仍复核当前业务读取权。真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Warehouse.create",objectType="Warehouse",operationCode="create",pages={"master-data/warehouse"},nameZh="新增仓库管理",nameEn="create Warehouses",permissions={"masterdata.warehouse:create"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.WAREHOUSE,input);}
  /** 编辑仓库管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Warehouse.update",objectType="Warehouse",operationCode="update",pages={"master-data/warehouse"},nameZh="编辑仓库管理",nameEn="update Warehouses",permissions={"masterdata.warehouse:update"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.WAREHOUSE,id,input);}
  /** 启停仓库管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Warehouse.setStatus",objectType="Warehouse",operationCode="setStatus",pages={"master-data/warehouse"},nameZh="启停仓库管理",nameEn="setStatus Warehouses",permissions={"masterdata.warehouse:set_status"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.WAREHOUSE,id,input);}

  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
