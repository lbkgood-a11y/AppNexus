package com.appnexus.masterdata;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.MasterData.*;

/** 客商管理固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/partner")
@BusinessObjectType(code="BusinessPartner",nameZh="客商管理",nameEn="Business partners",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class BusinessPartnerController {
  private final MasterDataService service;
  /**
   * @param service 负责真实授权和事务的主数据服务 */
  public BusinessPartnerController(MasterDataService service){this.service=service;}
  /** 查询客商管理；options 缺省 false，为 true 只读业务字典；否则 id 缺省查询列表，传 UUID 查询当前租户档案及明细；geography 默认 false，为 true 返回本租户地理摘要，仍复核当前业务读取权。真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="BusinessPartner.list",objectType="BusinessPartner",operationCode="list",pages={"master-data/partner"},nameZh="查询客商管理",nameEn="list Business partners",permissions={"masterdata.partner:read"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String id,@RequestParam(defaultValue="false") boolean options,@RequestParam(defaultValue="false") boolean geography) {if(geography)return service.geographyCatalog(context(r),Kind.PARTNER);if(options)return service.dictionaryOptions(context(r),Kind.PARTNER);return service.list(context(r),Kind.PARTNER,keyword,enabled,page,size,id);}
  /** 新增客商管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="BusinessPartner.create",objectType="BusinessPartner",operationCode="create",pages={"master-data/partner"},nameZh="新增客商管理",nameEn="create Business partners",permissions={"masterdata.partner:create"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.PARTNER,input);}
  /** 编辑客商管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="BusinessPartner.update",objectType="BusinessPartner",operationCode="update",pages={"master-data/partner"},nameZh="编辑客商管理",nameEn="update Business partners",permissions={"masterdata.partner:update"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.PARTNER,id,input);}
  /** 启停客商管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="BusinessPartner.setStatus",objectType="BusinessPartner",operationCode="setStatus",pages={"master-data/partner"},nameZh="启停客商管理",nameEn="setStatus Business partners",permissions={"masterdata.partner:set_status"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.PARTNER,id,input);}

  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
