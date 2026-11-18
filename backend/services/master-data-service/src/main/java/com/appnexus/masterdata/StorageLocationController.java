package com.appnexus.masterdata;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.MasterData.*;

/** 库位管理固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/storage-location")
@BusinessObjectType(code="StorageLocation",nameZh="库位管理",nameEn="Storage locations",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class StorageLocationController {
  private final MasterDataService service;
  /**
   * @param service 负责真实授权和事务的主数据服务 */
  public StorageLocationController(MasterDataService service){this.service=service;}
  /** 查询本租户档案；scope 为直属上级编码，缺省不限；page 从1开始，size 为1–100条，默认20；id 缺省分页，指定UUID读取详情。 */
  @OperationBinding(id="StorageLocation.list",objectType="StorageLocation",operationCode="list",pages={"master-data/storage"},nameZh="查询库位管理",nameEn="list Storage locations",permissions={"masterdata.storagelocation:read"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String id,@RequestParam(defaultValue="") String scope) {return service.list(context(r),Kind.STORAGE_LOCATION,keyword,enabled,page,size,id,scope);}
  /** 新增库位；上级必须为当前租户启用档案，编码创建后不可修改，初始启用。 */
  @OperationBinding(id="StorageLocation.create",objectType="StorageLocation",operationCode="create",pages={"master-data/storage"},nameZh="新增库位管理",nameEn="create Storage locations",permissions={"masterdata.storagelocation:create"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.STORAGE_LOCATION,input);}
  /** 编辑库位管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="StorageLocation.update",objectType="StorageLocation",operationCode="update",pages={"master-data/storage"},nameZh="编辑库位管理",nameEn="update Storage locations",permissions={"masterdata.storagelocation:update"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.STORAGE_LOCATION,id,input);}
  /** 启停库位管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="StorageLocation.setStatus",objectType="StorageLocation",operationCode="setStatus",pages={"master-data/storage"},nameZh="启停库位管理",nameEn="setStatus Storage locations",permissions={"masterdata.storagelocation:set_status"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.STORAGE_LOCATION,id,input);}

  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
