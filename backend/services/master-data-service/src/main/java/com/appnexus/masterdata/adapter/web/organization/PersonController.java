package com.appnexus.masterdata.adapter.web.organization;
import com.appnexus.contracts.operation.ResponseReferenceNames;
import com.appnexus.contracts.operation.ResponseReferenceNames.Binding;
import com.appnexus.masterdata.organization.PersonnelManagement;
import com.appnexus.masterdata.organization.PersonManagement;
import com.appnexus.masterdata.organization.PersonData;
import com.appnexus.masterdata.organization.*;

import com.appnexus.contracts.operation.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import static com.appnexus.masterdata.organization.PersonData.*;

/** 人员管理固定 API；复用宿主 Cookie/CSRF 链，禁止客户端指定身份。 */
@RestController
@RequestMapping("/api/master-data/person")

public final class PersonController {
  private final PersonManagement service;
  private final PersonnelManagement personnel;
  private final com.appnexus.platform.configuration.GeographyCatalog geography;
  /**
   * @param service 负责真实授权和事务的主数据服务
   * @param personnel 人员详情、十类明细和组织用户多对多关联服务
   * @param geography 地理目录公开端口；读取前复核人员权限 */
  public PersonController(PersonManagement service,PersonnelManagement personnel,com.appnexus.platform.configuration.GeographyCatalog geography){this.service=service;this.personnel=personnel;this.geography=geography;}
  /** 查询人员摘要；只从已完成字段保护的当前页批量回填在职状态名称。 */
  @OperationBinding(id="Person.list", reference=true)
  @ResponseReferenceNames(@Binding(path="rows",type="dictionary:employment_status",
      id="attributes.employmentStatus",name="employmentStatusName",uuid=false))
  @GetMapping("")
  public Object list(HttpServletRequest r,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return service.list(context(r),Kind.PERSON,keyword,enabled,page,size);}
  /** 查询当前成员人员档案的字段限制，不返回人员资料。 */
  @OperationBinding(id="Person.fieldRestrictions", reference=true)
  @GetMapping("/field-restrictions")
  public Object fieldRestrictions(HttpServletRequest request){return service.fieldRestrictions(context(request));}
  /** 新增人员管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Person.create", reference=true)
  @PostMapping("")
  public Object create(HttpServletRequest r,@RequestBody Input input) {return service.create(context(r),Kind.PERSON,input);}
  /** 编辑人员管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Person.update", reference=true)
  @PutMapping("/{id}")
  public void update(HttpServletRequest r,@PathVariable String id,@RequestBody Input input) {service.update(context(r),Kind.PERSON,id,input);}
  /** 启停人员管理；真实鉴权、租户与并发校验由同一业务执行器负责。 */
  @OperationBinding(id="Person.setStatus", reference=true)
  @PutMapping("/{id}/status")
  public void setStatus(HttpServletRequest r,@PathVariable String id,@RequestBody Status input) {service.setStatus(context(r),Kind.PERSON,id,input);}

  /** 读取人员详情；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.read", reference=true)
 @ResponseReferenceNames({
     @Binding(path="details",type="organization",id="values.organizationId",name="organizationName"),
     @Binding(path="details",type="position",id="values.positionId",name="positionName"),
     @Binding(path="person",type="geography",id="attributes.nativePlace",name="nativePlaceName",uuid=false),
     @Binding(path="person",type="geography",id="attributes.countryRegion",name="countryRegionName",uuid=false),
     @Binding(path="person",type="geography",id="attributes.province",name="provinceName",uuid=false),
     @Binding(path="person",type="geography",id="attributes.city",name="cityName",uuid=false),
     @Binding(path="person",type="dictionary:person_gender",id="attributes.gender",name="genderName",uuid=false),
     @Binding(path="person",type="dictionary:identity_type",id="attributes.identityType",name="identityTypeName",uuid=false),
     @Binding(path="person",type="dictionary:work_nature",id="attributes.workNature",name="workNatureName",uuid=false),
     @Binding(path="person",type="dictionary:blood_type",id="attributes.bloodType",name="bloodTypeName",uuid=false),
     @Binding(path="person",type="dictionary:marital_status",id="attributes.maritalStatus",name="maritalStatusName",uuid=false),
     @Binding(path="person",type="dictionary:employment_status",id="attributes.employmentStatus",name="employmentStatusName",uuid=false),
     @Binding(path="person",type="dictionary:ethnicity",id="attributes.ethnicity",name="ethnicityName",uuid=false),
     @Binding(path="person",type="dictionary:political_status",id="attributes.politicalStatus",name="politicalStatusName",uuid=false),
     @Binding(path="details",type="dictionary:certificate_status",id="values.certificateStatus",name="certificateStatusName",uuid=false),
     @Binding(path="details",type="dictionary:reward_penalty_type",id="values.recordType",name="recordTypeName",uuid=false),
     @Binding(path="details",type="dictionary:education_level",id="values.educationLevel",name="educationLevelName",uuid=false),
     @Binding(path="details",type="dictionary:skill_level",id="values.skillLevel",name="skillLevelName",uuid=false),
     @Binding(path="details",type="dictionary:certificate_type",id="values.certificateType",name="certificateTypeName",uuid=false),
     @Binding(path="details",type="dictionary:person_contact_type",id="values.contactType",name="contactTypeName",uuid=false),
     @Binding(path="details",type="dictionary:address_type",id="values.addressType",name="addressTypeName",uuid=false),
     @Binding(path="details",type="dictionary:academic_degree",id="values.degree",name="degreeName",uuid=false)})
  @GetMapping("/{id}")
  public Object read(HttpServletRequest r,@PathVariable String id){return personnel.read(context(r),id);}

  /** 读取人员关联目录；geography 默认 false，true 在相同人员权限下返回本租户地理摘要。复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.options", reference=true)
  @GetMapping("/options")
  public Object options(HttpServletRequest r,@RequestParam(defaultValue="false") boolean geography){var c=context(r);var options=personnel.options(c);return geography?this.geography.nodes(c.tenantId()):options;}

  /** 新增人员明细；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.createDetail", reference=true)
  @PostMapping("/{id}/details/{section}")
  public Object createDetail(HttpServletRequest r,@PathVariable String id,@PathVariable String section,@RequestBody com.appnexus.masterdata.organization.PersonnelData.DetailInput input){return personnel.saveDetail(context(r),id,section,null,input);}

  /** 编辑人员明细；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.updateDetail", reference=true)
  @PutMapping("/{id}/details/{section}/{detailId}")
  public Object updateDetail(HttpServletRequest r,@PathVariable String id,@PathVariable String section,@PathVariable String detailId,@RequestBody com.appnexus.masterdata.organization.PersonnelData.DetailInput input){return personnel.saveDetail(context(r),id,section,detailId,input);}

  /** 删除人员明细；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.deleteDetail", reference=true)
  @DeleteMapping("/{id}/details/{section}/{detailId}")
  public void deleteDetail(HttpServletRequest r,@PathVariable String id,@PathVariable String section,@PathVariable String detailId,@RequestParam long version){personnel.deleteDetail(context(r),id,section,detailId,version);}

  /** 分配人员用户；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.assign", reference=true)
  @PostMapping("/{id}/users")
  public Object assign(HttpServletRequest r,@PathVariable String id,@RequestBody com.appnexus.masterdata.organization.PersonnelData.AssignmentInput input){return personnel.assign(context(r),id,null,input);}

  /** 编辑人员用户关联；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.updateAssignment", reference=true)
  @PutMapping("/{id}/users/{assignmentId}")
  public Object updateAssignment(HttpServletRequest r,@PathVariable String id,@PathVariable String assignmentId,@RequestBody com.appnexus.masterdata.organization.PersonnelData.AssignmentInput input){return personnel.assign(context(r),id,assignmentId,input);}

  /** 解除人员用户关联；复用可信租户、独立权限和父档案版本，不扩大账号任职或业务权限。 */
  @OperationBinding(id="Person.unassign", reference=true)
  @DeleteMapping("/{id}/users/{assignmentId}")
  public void unassign(HttpServletRequest r,@PathVariable String id,@PathVariable String assignmentId,@RequestParam long version){personnel.unassign(context(r),id,assignmentId,version);}

  /** 本账号可代表的人员与组织，仅限自身关联，不需要人员管理权限。 */
  @OperationBinding(id="Person.myIdentities", reference=true, eligibility=OperationBinding.Eligibility.SELF)
  @GetMapping("/my-identities") public Object myIdentities(HttpServletRequest r){return personnel.identities(context(r));}
  /** 当前会话选择仅用于展示；返回前实时复核，关联失效时返回 204 并清除旧选择。 */
  @OperationBinding(id="Person.currentIdentity", reference=true, eligibility=OperationBinding.Eligibility.SELF)
  @GetMapping("/identity") public Object currentIdentity(HttpServletRequest r){var c=context(r);var session=r.getSession(false);if(session==null||!c.tenantId().toString().equals(session.getAttribute("appnexus.personTenant")))return org.springframework.http.ResponseEntity.noContent().build();String person=(String)session.getAttribute("appnexus.person"),organization=(String)session.getAttribute("appnexus.personOrganization");if(person==null||organization==null||!personnel.canRepresent(c,person,organization)){session.removeAttribute("appnexus.person");session.removeAttribute("appnexus.personOrganization");session.removeAttribute("appnexus.personTenant");return org.springframework.http.ResponseEntity.noContent().build();}return new com.appnexus.masterdata.organization.PersonnelData.IdentitySelection(person,organization);}
  /** 切换请求不写浏览器身份凭据；服务端重新校验关联、人员、组织及有效期。 */
  @OperationBinding(id="Person.selectIdentity", reference=true, eligibility=OperationBinding.Eligibility.SELF)
  @PostMapping("/identity") public Object selectIdentity(HttpServletRequest r,@RequestBody com.appnexus.masterdata.organization.PersonnelData.IdentitySelection input){
    var c=context(r);personnel.verifyIdentity(c,input);var session=r.getSession();session.setAttribute("appnexus.person",input.personId());session.setAttribute("appnexus.personOrganization",input.organizationId());session.setAttribute("appnexus.personTenant",c.tenantId().toString());return input;
  }
  /** 仅信任安全过滤器写入的上下文；响应禁止缓存含个人或合作方信息的档案。 */
  private VerifiedTenantContext context(HttpServletRequest r){
    Object value=r.getAttribute(VerifiedTenantContext.class.getName());
    if(!(value instanceof VerifiedTenantContext c))throw new Rejection(403,"MASTER_DATA_DENIED");
    return c;
  }
}
