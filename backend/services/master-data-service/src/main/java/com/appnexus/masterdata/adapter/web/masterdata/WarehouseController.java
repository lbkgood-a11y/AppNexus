package com.appnexus.masterdata.adapter.web.masterdata;
import com.appnexus.contracts.operation.ResponseReferenceNames;
import com.appnexus.contracts.operation.ResponseReferenceNames.Binding;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 仓库管理固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/warehouse")

public final class WarehouseController {
  private final MasterDataManagement service;
  /** 返回本人仓库字段模式，即使本租户尚无仓库记录。 */
  @OperationBinding(id="Warehouse.fieldRestrictions", reference=true)
  @GetMapping("/field-restrictions")
  public Object fieldRestrictions(HttpServletRequest request){
    return service.warehouseFieldRestrictions(context(request));
  }
  /**
   * @param service 负责真实授权和事务的主数据服务 */
  public WarehouseController(MasterDataManagement service){this.service=service;}
  /** 查询仓库管理；id缺省分页，传UUID查询已授权详情；options默认false，true只返回本租户字典，仍复核仓库读取权限。 */
 @com.appnexus.contracts.operation.RecordAuditNames(paths={"audit","rows.audit"})
  @OperationBinding(id="Warehouse.list", reference=true)
 @ResponseReferenceNames({
     @Binding(path="rows",type="organization",id="organizationId",name="organizationName"),
     @Binding(path="rows",type="dictionary:warehouse_type",id="attributes.warehouseType",name="warehouseTypeName",uuid=false),
     @Binding(path="rows",type="dictionary:warehouse_operating_mode",id="attributes.operatingMode",name="operatingModeName",uuid=false),
     @Binding(path="rows",type="geography",id="attributes.region",name="regionName",uuid=false)})
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String id,@RequestParam(defaultValue="false") boolean options,@RequestParam(defaultValue="false") boolean geography,@RequestParam(required=false) String organizationAction) {if(organizationAction!=null)return service.warehouseOrganizations(context(r),organizationAction);if(geography)return service.geographyCatalog(context(r),Kind.WAREHOUSE);if(options)return service.warehouseOptions(context(r));return service.list(context(r),Kind.WAREHOUSE,keyword,enabled,page,size,id);}
  /** 新增仓库管理；geography 默认 false，为 true 返回本租户地理摘要，仍复核当前业务读取权。真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Warehouse.create", reference=true)
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.WAREHOUSE,input);}
  /** 编辑仓库管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Warehouse.update", reference=true)
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.WAREHOUSE,id,input);}
  /** 启停仓库管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Warehouse.setStatus", reference=true)
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.WAREHOUSE,id,input);}

  private static final String LOGIN_WAREHOUSE = WarehouseController.class.getName()+".loginWarehouse";

  /** @param warehouseId 待选择的启用仓库 UUID，必填 */
  public record LoginWarehouseSelection(UUID warehouseId) {}

  /** @param options 当前成员实时授权的候选 @param current 当前会话中仍有效的选仓，可为空 */
  public record LoginWarehouseView(List<MasterDataManagement.LoginWarehouse> options,
                                   MasterDataManagement.LoginWarehouse current) {}

  /** 读取登录候选和当前仓库，停用、撤权及身份切换后不返回旧选择。 */
  @OperationBinding(id="Warehouse.selectLogin", reference=true)
  @GetMapping("/login-selection")
  public ResponseEntity<LoginWarehouseView> loginSelection(HttpServletRequest request){
    var context=context(request);
    var options=service.loginWarehouses(context,null);
    var session=request.getSession(false);
    Object stored=session==null?null:session.getAttribute(LOGIN_WAREHOUSE);
    var current=options.stream()
        .filter(option->selectionKey(context,UUID.fromString(option.id())).equals(stored))
        .findFirst().orElse(null);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new LoginWarehouseView(options,current));
  }

  /** 通过 Cookie/CSRF 入口保存本次会话选择；每次提交均重新核验功能、行范围和仓库状态。 */
  @OperationBinding(id="Warehouse.selectLogin", reference=true)
  @PutMapping("/login-selection")
  public ResponseEntity<MasterDataManagement.LoginWarehouse> selectLoginWarehouse(HttpServletRequest request,
      @RequestBody LoginWarehouseSelection selection){
    var context=context(request);
    if(selection.warehouseId()==null)throw new Rejection(400,"MASTER_DATA_INVALID");
    var warehouse=service.loginWarehouses(context,selection.warehouseId()).stream().findFirst()
        .orElseThrow(()->new Rejection(403,"MASTER_DATA_DENIED"));
    // 会话选择绑定可信租户、成员和身份版本；迟到请求只会写入旧上下文的值。
    request.getSession().setAttribute(LOGIN_WAREHOUSE,selectionKey(context,selection.warehouseId()));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(warehouse);
  }

  private static String selectionKey(VerifiedTenantContext context,UUID warehouseId){
    return context.tenantId()+":"+context.memberId()+":"+context.version()+":"+context.personId()+":"
        +context.organizationId()+":"+warehouseId;
  }

  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
