package com.appnexus.masterdata.adapter.web.masterdata;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.api.masterdata.MasterData.*;

/** 行政区划固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/subdivision")
public final class SubdivisionController {
  private final MasterDataManagement service;
  /**
   * @param service 负责真实授权和事务的主数据服务 */
  public SubdivisionController(MasterDataManagement service){this.service=service;}
  /**
   * 查询行政区划；树、标准检索及业务列表沿用同一对象读取鉴权。
   * @param r 宿主可信会话请求，不接受客户端指定租户
   * @param keyword 编码、名称或层级编码关键词，默认空串
   * @param enabled 启停过滤，缺省表示全部，仅业务列表适用
   * @param page 页码，从 1 开始，默认 1
   * @param size 每页条数，1–100，默认 20
   * @param standards true 返回参考标准选项，默认 false
   * @param tree true 返回当前父级的参考树节点，默认 false；优先于 standards
   * @param parent 树父级编码，支持当前租户自定义节点；默认空串表示七洲，洲键使用 continent: 前缀
   * @param scope 业务列表范围，支持当前租户自定义节点；默认空串不限制，包含真实后代，仍强制租户边界
   * @return 当前分支对应分页结果；树与标准选项不代表已建立的租户业务记录
   */
  @OperationBinding(id="Subdivision.list",reference=true)
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="false") boolean standards,@RequestParam(defaultValue="false") boolean tree,@RequestParam(defaultValue="") String parent,@RequestParam(defaultValue="") String scope) {if(tree)return service.geographyTree(context(r),Kind.SUBDIVISION,parent,page,size);if(standards)return service.geographyOptions(context(r),Kind.SUBDIVISION,keyword,page,size);return service.list(context(r),Kind.SUBDIVISION,keyword,enabled,page,size,null,scope);}
  /** 新增行政区划；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Subdivision.create",reference=true)
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.SUBDIVISION,input);}
  /** 编辑行政区划；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Subdivision.update",reference=true)
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.SUBDIVISION,id,input);}
  /** 启停行政区划；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Subdivision.setStatus",reference=true)
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.SUBDIVISION,id,input);}

  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
