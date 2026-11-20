package com.appnexus.masterdata.adapter.web.masterdata;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.*;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 库区管理固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/storage-area")
@BusinessObjectType(code="StorageArea",nameZh="库区管理",nameEn="Storage areas",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class StorageAreaController {
  private final MasterDataManagement service;
  /**
   * @param service 负责真实授权和事务的主数据服务 */
  public StorageAreaController(MasterDataManagement service){this.service=service;}
  /** 查询本租户档案；scope 为直属上级编码，缺省不限；page 从1开始，size 为1–100条，默认20；id 缺省分页，指定UUID读取详情。 */
  @OperationBinding(id="StorageArea.list",objectType="StorageArea",operationCode="list",pages={"master-data/storage"},nameZh="查询库区管理",nameEn="list Storage areas",permissions={"masterdata.storagearea:read"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String id,@RequestParam(defaultValue="") String scope) {return service.list(context(r),Kind.STORAGE_AREA,keyword,enabled,page,size,id,scope);}
  /** 新增库区；上级必须为当前租户启用档案，编码创建后不可修改，初始启用。 */
  @OperationBinding(id="StorageArea.create",objectType="StorageArea",operationCode="create",pages={"master-data/storage"},nameZh="新增库区管理",nameEn="create Storage areas",permissions={"masterdata.storagearea:create"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.STORAGE_AREA,input);}
  /** 编辑库区管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="StorageArea.update",objectType="StorageArea",operationCode="update",pages={"master-data/storage"},nameZh="编辑库区管理",nameEn="update Storage areas",permissions={"masterdata.storagearea:update"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.STORAGE_AREA,id,input);}
  /** 启停库区管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="StorageArea.setStatus",objectType="StorageArea",operationCode="setStatus",pages={"master-data/storage"},nameZh="启停库区管理",nameEn="setStatus Storage areas",permissions={"masterdata.storagearea:set_status"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.STORAGE_AREA,id,input);}

  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
