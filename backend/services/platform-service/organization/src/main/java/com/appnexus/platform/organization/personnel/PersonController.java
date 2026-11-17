package com.appnexus.platform.organization.personnel;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.platform.organization.personnel.PersonData.*;

/** 人员管理固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/person")
@BusinessObjectType(code="Person",nameZh="人员管理",nameEn="Personnel",category=BusinessObjectType.Category.MASTER_DATA,module="organization")
public final class PersonController {
  private final PersonDataService service;
  private final PersonnelService personnel;
  private final com.appnexus.platform.configuration.GeographyCatalog geography;
  /**
   * @param service 负责真实授权和事务的主数据服务
   * @param personnel 人员详情、十类明细和组织用户多对多关联服务
   * @param geography 地理目录公开端口；读取前复核人员权限 */
  public PersonController(PersonDataService service,PersonnelService personnel,com.appnexus.platform.configuration.GeographyCatalog geography){this.service=service;this.personnel=personnel;this.geography=geography;}
  /** 查询人员管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Person.list",objectType="Person",operationCode="list",pages={"organization/personnel"},nameZh="查询人员管理",nameEn="list Personnel",permissions={"masterdata.person:read"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return service.list(context(r),Kind.PERSON,keyword,enabled,page,size);}
  /** 新增人员管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Person.create",objectType="Person",operationCode="create",pages={"organization/personnel"},nameZh="新增人员管理",nameEn="create Personnel",permissions={"masterdata.person:create"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.PERSON,input);}
  /** 编辑人员管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Person.update",objectType="Person",operationCode="update",pages={"organization/personnel"},nameZh="编辑人员管理",nameEn="update Personnel",permissions={"masterdata.person:update"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.PERSON,id,input);}
  /** 启停人员管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Person.setStatus",objectType="Person",operationCode="setStatus",pages={"organization/personnel"},nameZh="启停人员管理",nameEn="setStatus Personnel",permissions={"masterdata.person:set_status"},policy="可信租户有效成员、独立功能权限、编码约束与版本校验")
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.PERSON,id,input);}

  /** 读取人员详情；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.read",objectType="Person",operationCode="read",pages={"organization/personnel"},nameZh="读取人员详情",nameEn="Read personnel profile",permissions={"masterdata.person:read"},policy="当前有效成员、独立人员权限、同租户档案及版本约束；账号关联每次复核可信租户有效成员")
  @GetMapping("/{id}")
  public Object read(HttpServletRequest r,@PathVariable String id){return personnel.read(context(r),id);}

  /** 读取人员关联目录；geography 默认 false，true 在相同人员权限下返回本租户地理摘要。复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.options",objectType="Person",operationCode="options",pages={"organization/personnel"},nameZh="读取人员关联目录",nameEn="Read personnel directory",permissions={"masterdata.person:read"},policy="当前有效成员、独立人员权限、同租户档案及版本约束；账号关联每次复核可信租户有效成员")
  @GetMapping("/options")
  public Object options(HttpServletRequest r,@RequestParam(defaultValue="false") boolean geography){var c=context(r);var options=personnel.options(c);return geography?this.geography.nodes(c.tenantId()):options;}

  /** 新增人员明细；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.createDetail",objectType="Person",operationCode="createDetail",pages={"organization/personnel"},nameZh="新增人员明细",nameEn="Create personnel detail",permissions={"masterdata.person:detail_create"},policy="当前有效成员、独立人员权限、同租户档案及版本约束；账号关联每次复核可信租户有效成员")
  @PostMapping("/{id}/details/{section}")
  public Object createDetail(HttpServletRequest r,@PathVariable String id,@PathVariable String section,@RequestBody PersonnelService.DetailInput input){return personnel.saveDetail(context(r),id,section,null,input);}

  /** 编辑人员明细；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.updateDetail",objectType="Person",operationCode="updateDetail",pages={"organization/personnel"},nameZh="编辑人员明细",nameEn="Edit personnel detail",permissions={"masterdata.person:detail_update"},policy="当前有效成员、独立人员权限、同租户档案及版本约束；账号关联每次复核可信租户有效成员")
  @PutMapping("/{id}/details/{section}/{detailId}")
  public Object updateDetail(HttpServletRequest r,@PathVariable String id,@PathVariable String section,@PathVariable String detailId,@RequestBody PersonnelService.DetailInput input){return personnel.saveDetail(context(r),id,section,detailId,input);}

  /** 删除人员明细；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.deleteDetail",objectType="Person",operationCode="deleteDetail",pages={"organization/personnel"},nameZh="删除人员明细",nameEn="Delete personnel detail",permissions={"masterdata.person:detail_delete"},policy="当前有效成员、独立人员权限、同租户档案及版本约束；账号关联每次复核可信租户有效成员")
  @DeleteMapping("/{id}/details/{section}/{detailId}")
  public void deleteDetail(HttpServletRequest r,@PathVariable String id,@PathVariable String section,@PathVariable String detailId,@RequestParam long version){personnel.deleteDetail(context(r),id,section,detailId,version);}

  /** 分配人员用户；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.assign",objectType="Person",operationCode="assign",pages={"organization/personnel"},nameZh="分配人员用户",nameEn="Assign personnel user",permissions={"masterdata.person:assign"},policy="当前有效成员、独立人员权限、同租户档案及版本约束；账号关联每次复核可信租户有效成员")
  @PostMapping("/{id}/users")
  public Object assign(HttpServletRequest r,@PathVariable String id,@RequestBody PersonnelService.AssignmentInput input){return personnel.assign(context(r),id,null,input);}

  /** 编辑人员用户关联；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.updateAssignment",objectType="Person",operationCode="updateAssignment",pages={"organization/personnel"},nameZh="编辑人员用户关联",nameEn="Edit personnel user assignment",permissions={"masterdata.person:assign"},policy="当前有效成员、独立人员权限、同租户档案及版本约束；账号关联每次复核可信租户有效成员")
  @PutMapping("/{id}/users/{assignmentId}")
  public Object updateAssignment(HttpServletRequest r,@PathVariable String id,@PathVariable String assignmentId,@RequestBody PersonnelService.AssignmentInput input){return personnel.assign(context(r),id,assignmentId,input);}

  /** 解除人员用户关联；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.unassign",objectType="Person",operationCode="unassign",pages={"organization/personnel"},nameZh="解除人员用户关联",nameEn="Remove personnel user assignment",permissions={"masterdata.person:assign"},policy="当前有效成员、独立人员权限、同租户档案及版本约束；账号关联每次复核可信租户有效成员")
  @DeleteMapping("/{id}/users/{assignmentId}")
  public void unassign(HttpServletRequest r,@PathVariable String id,@PathVariable String assignmentId,@RequestParam long version){personnel.unassign(context(r),id,assignmentId,version);}

  /** 本账号可代表的人员与组织，仅限自身关联，不需要人员管理权限。 */
  @OperationBinding(id="Person.myIdentities",objectType="Person",operationCode="myIdentities",pages={"organization/personnel"},nameZh="查看本人人员身份",nameEn="Read own personnel identities",eligibility=OperationBinding.Eligibility.SELF,policy="真实租户有效成员，仅返回自己的账号人员关联，不授予业务权限")
  @GetMapping("/my-identities") public Object myIdentities(HttpServletRequest r){return personnel.identities(context(r));}
  /** 当前会话选择仅用于展示；返回前实时复核，关联失效时返回 204 并清除旧选择。 */
  @OperationBinding(id="Person.currentIdentity",objectType="Person",operationCode="currentIdentity",pages={"organization/personnel"},nameZh="查看当前人员身份",nameEn="Read current personnel identity",eligibility=OperationBinding.Eligibility.SELF,policy="可信租户成员仅查看自己当前仍有效的人员选择")
  @GetMapping("/identity") public Object currentIdentity(HttpServletRequest r){var c=context(r);var session=r.getSession(false);if(session==null||!c.tenantId().toString().equals(session.getAttribute("appnexus.personTenant")))return org.springframework.http.ResponseEntity.noContent().build();String person=(String)session.getAttribute("appnexus.person"),organization=(String)session.getAttribute("appnexus.personOrganization");if(person==null||organization==null||!personnel.canRepresent(c,person,organization)){session.removeAttribute("appnexus.person");session.removeAttribute("appnexus.personOrganization");session.removeAttribute("appnexus.personTenant");return org.springframework.http.ResponseEntity.noContent().build();}return new PersonnelService.IdentitySelection(person,organization);}
  /** 切换请求不写浏览器身份凭据；服务端重新校验关联、人员、组织及有效期。 */
  @OperationBinding(id="Person.selectIdentity",objectType="Person",operationCode="selectIdentity",pages={"organization/personnel"},nameZh="选择本次人员身份",nameEn="Select personnel identity",eligibility=OperationBinding.Eligibility.SELF,policy="本账号有效人员及组织，Cookie CSRF 防护，不改变真实执行账号或权限")
  @PostMapping("/identity") public Object selectIdentity(HttpServletRequest r,@RequestBody PersonnelService.IdentitySelection input){
    var c=context(r);personnel.verifyIdentity(c,input);var session=r.getSession();session.setAttribute("appnexus.person",input.personId());session.setAttribute("appnexus.personOrganization",input.organizationId());session.setAttribute("appnexus.personTenant",c.tenantId().toString());return input;
  }
  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
