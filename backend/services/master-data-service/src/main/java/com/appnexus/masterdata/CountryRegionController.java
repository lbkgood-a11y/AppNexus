package com.appnexus.masterdata;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.MasterData.*;

/** 国别地区固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/country-region")
@BusinessObjectType(code="CountryRegion",nameZh="国别地区",nameEn="Countries and regions",category=BusinessObjectType.Category.MASTER_DATA,module="master-data")
public final class CountryRegionController {
  private final MasterDataService service;
  /**
   * @param service 负责真实授权和事务的主数据服务 */
  public CountryRegionController(MasterDataService service){this.service=service;}
  /** 查询国别地区；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="CountryRegion.list",objectType="CountryRegion",operationCode="list",pages={"master-data/country-region"},nameZh="查询国别地区",nameEn="list Countries and regions",permissions={"masterdata.countryregion:read"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return service.list(context(r),Kind.COUNTRY_REGION,keyword,enabled,page,size);}
  /** 新增国别地区；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="CountryRegion.create",objectType="CountryRegion",operationCode="create",pages={"master-data/country-region"},nameZh="新增国别地区",nameEn="create Countries and regions",permissions={"masterdata.countryregion:create"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.COUNTRY_REGION,input);}
  /** 编辑国别地区；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="CountryRegion.update",objectType="CountryRegion",operationCode="update",pages={"master-data/country-region"},nameZh="编辑国别地区",nameEn="update Countries and regions",permissions={"masterdata.countryregion:update"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.COUNTRY_REGION,id,input);}
  /** 启停国别地区；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="CountryRegion.setStatus",objectType="CountryRegion",operationCode="setStatus",pages={"master-data/country-region"},nameZh="启停国别地区",nameEn="setStatus Countries and regions",permissions={"masterdata.countryregion:set_status"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.COUNTRY_REGION,id,input);}

  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
