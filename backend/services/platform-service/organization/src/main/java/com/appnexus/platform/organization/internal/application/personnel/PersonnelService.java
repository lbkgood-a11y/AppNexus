package com.appnexus.platform.organization.internal.application.personnel;
import com.appnexus.platform.organization.internal.domain.personnel.PersonnelSchema;
import com.appnexus.platform.organization.PersonData;
import com.appnexus.platform.organization.port.personnel.PersonnelRepository;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.organization.PersonnelDirectory;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.platform.organization.PersonnelData.*;
import com.appnexus.platform.organization.PersonnelData.Audit;
import com.appnexus.platform.configuration.BusinessValueCatalog;

import static com.appnexus.platform.organization.PersonData.*;
import static com.appnexus.platform.organization.internal.domain.personnel.PersonnelSchema.*;

/** 人员详情、十类明细和账号人员关联；真实执行账号与被代表人员始终分别保存，不冒充登录身份。 */
public final class PersonnelService implements com.appnexus.platform.organization.PersonnelManagement {
  private final PersonFieldPolicy fieldPolicy;
 private final PersonnelRepository statements;

  private final BusinessValueCatalog dictionaries;
  
  private final AccessAuthorization access;
  private final PersonnelDirectory directory;
  private final com.appnexus.platform.organization.port.personnel.PersonnelTransaction tx;
  private final com.appnexus.platform.organization.port.personnel.PersonnelContent content;
 private final com.appnexus.platform.organization.PersonnelExecution identities;
  /** 人员或商品完整用例依赖装配；所有输出参与原业务安全与事务约束。
  * @param access 权威功能授权公开端口
  * @param directory 组织及任职公开目录
  * @param transaction 同数据源REQUIRED完整用例事务
  * @param dictionaries 字典及引用公开契约
  * @param statements 固定人员明细仓储，包含当前租户及父人员条件
  * @param content 固定明细存储内容编解码，不接受任意目标类型
  * @param identities 权威可代表身份端口，不在用例中构造技术适配器
  */
 public PersonnelService(AccessAuthorization access,PersonnelDirectory directory,com.appnexus.platform.organization.port.personnel.PersonnelTransaction transaction,BusinessValueCatalog dictionaries,PersonnelRepository statements,com.appnexus.platform.organization.port.personnel.PersonnelContent content,com.appnexus.platform.organization.PersonnelExecution identities,com.appnexus.platform.authorization.FieldProtection protection,com.appnexus.platform.authorization.DataAccessCatalog catalog){this.fieldPolicy=new PersonFieldPolicy(protection,catalog);this.content=content;this.identities=identities;this.statements=statements;this.dictionaries=dictionaries;this.access=access;this.directory=directory;this.tx=transaction;}
  
  
  
  
  
  
  /**
   * @param c 可信请求上下文
   * @return 组织、岗位及有效账号人员目录；仅供分配及明细选择 */
  public Options options(VerifiedTenantContext c){require(c,"read");var base=directory.catalog(c.tenantId());return new Options(base.organizations(),base.positions(),base.users(),dictionaryOptions(c));}
  /** 字典只控制状态名称及启用，不扩展真实在职/证书/奖惩状态机。 */
  private Map<String,List<BusinessValueCatalog.Option>> dictionaryOptions(VerifiedTenantContext c){
    var options=new HashMap<>(dictionaries.options(c.tenantId(),BusinessValueCatalog.PERSON));
    var fixed=Map.of("employmentStatus",Set.of("ACTIVE","PROBATION","LEAVE","RETIRED"),"certificateStatus",Set.of("VALID","EXPIRED","REVOKED","PENDING"),"recordType",Set.of("REWARD","PENALTY"));
    fixed.forEach((field,allowed)->options.computeIfPresent(field,(key,rows)->rows.stream().map(row->new BusinessValueCatalog.Option(row.value(),row.name(),row.enabled()&&allowed.contains(row.value()))).toList()));
    return options;
  }
  
  /**
   *
   * @param c 可信请求上下文
   *
   * @param id 当前租户人员 UUID
   *
   * @return 档案、审计信息及十类明细；未知或跨租户 ID 统一 404
   */
  public Profile read(VerifiedTenantContext c,String id){
    require(c,"read");uuid(id);
    return tx.required(()->{
    var people=statements.profile(c,id).stream().map(row->{var values=new LinkedHashMap<String,String>();for(String field:Kind.PERSON.fields)values.put(field,(String)row.get(field));return new Row((String)row.get("id"),(String)row.get("code"),(String)row.get("name"),values,Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("version")).longValue());}).toList();
    if(people.isEmpty())throw new Rejection(404,"PERSON_NOT_FOUND");
    var rawAudit=statements.audit(c.tenantId().toString(),id).getFirst();String creator=(String)rawAudit.get("created_by"),updater=(String)rawAudit.get("updated_by");
    var actorIds=java.util.stream.Stream.of(creator,updater).filter(Objects::nonNull).distinct().toList();
    var actorNames=directory.accountNames(c.tenantId(),actorIds);
    var audit=new Audit(creator,creator==null?"":actorNames.getOrDefault(creator,""),utc(rawAudit.get("created_at")),
        updater,updater==null?"":actorNames.getOrDefault(updater,""),utc(rawAudit.get("updated_at")));
    var details=new ArrayList<>(statements.details(c.tenantId().toString(),id).stream().map(row->new Detail((String)row.get("id"),(String)row.get("section_code"),decode((String)row.get("content")))).toList());
    for(var affiliation:directory.affiliations(c.tenantId(),uuid(id)))details.add(new Detail(affiliation.id(),affiliation.section(),affiliation.values()));
    var catalog=directory.catalog(c.tenantId());
    var assignments=statements.assignments(c.tenantId().toString(),id).stream().map(row->{String member=(String)row.get("member_text");var user=catalog.users().stream().filter(value->value.memberId().equalsIgnoreCase(member)).findFirst();return new Assignment((String)row.get("id"),member,user.map(PersonnelDirectory.User::name).orElse(""),user.isPresent());}).toList();
    var personModes=fieldPolicy.modes(c,PersonFieldPolicy.PERSON);
    var detailModes=fieldPolicy.modes(c,PersonFieldPolicy.DETAIL);
    var projected=details.stream().map(detail->new Detail(detail.id(),detail.section(),
        fieldPolicy.project(PersonFieldPolicy.DETAIL,detail.values(),detailModes))).toList();
    return new Profile(fieldPolicy.project(people.getFirst(),personModes),audit,projected,assignments,detailModes);
    });
  }
  /**
   * 创建或编辑固定分类明细；组织和岗位仅通过组织公开目录校验，不创建账号任职。
   *
   * @param c 可信主体
   * @param person 人员 UUID
   * @param sectionCode 固定明细分类
   * @param id 为空表示创建，否则编辑此明细
   *
   * @param input 当前人员版本与分类值；未知 ID、跨租户和陈旧版本失败，整个事务回滚
   *
   * @return 明细 UUID
   */
  public Map<String,String> saveDetail(VerifiedTenantContext c,String person,String sectionCode,String id,DetailInput input){
    String action=id==null?"detail_create":"detail_update";
    require(c,action);uuid(person);if(id!=null)uuid(id);
    var section=Section.parse(sectionCode);if(input==null)throw invalid();
    String key=id==null?UUID.randomUUID().toString():id;
    return tx.required(()->{
      statements.lockForWrite(c,person,action);
      bump(c,person,input.version());
      var previous=Map.<String,String>of();
      if(id!=null){var old=statements.readDetailContent(c.tenantId().toString(),person,id,section.code);if(!old.isEmpty())previous=decode(old.getFirst());}
      var values=validate(section,fieldPolicy.preserve(input.values(),previous,fieldPolicy.modes(c,PersonFieldPolicy.DETAIL)));
      validateReferences(c,section,values);
      if(!dictionaries.reference(c.tenantId(),c.accountId(),"PersonDetail",person+":"+key,BusinessValueCatalog.PERSON,values,previous))throw invalid();
      if(section==Section.ORGANIZATIONS||section==Section.POSITIONS){
        if(!directory.saveAffiliation(c.tenantId(),uuid(person),section.code,uuid(key),id==null,values))throw changed();
        return Map.of("id",key);
      }
      if(id==null)statements.createDetail(c.tenantId().toString(),key,person,section.code,content.encode(values));
      else if(statements.updateDetail(content.encode(values),c.tenantId().toString(),person,id,section.code)!=1)throw changed();
      return Map.of("id",key);
    });
  }
  /**
   * @param c 可信主体
   * @param person 人员 UUID
   * @param sectionCode 固定分类
   * @param id 明细 UUID
   * @param version 当前人员非负版本 */
  public void deleteDetail(VerifiedTenantContext c,String person,String sectionCode,String id,long version){
    require(c,"detail_delete");uuid(person);uuid(id);var section=Section.parse(sectionCode);
    tx.run(()->{statements.lockForWrite(c,person,"detail_delete");if(section.fields.stream().anyMatch(fieldPolicy.modes(c,PersonFieldPolicy.DETAIL)::containsKey))throw PersonFieldPolicy.denied();bump(c,person,version);if(section==Section.ORGANIZATIONS||section==Section.POSITIONS){if(!directory.deleteAffiliation(c.tenantId(),uuid(person),section.code,uuid(id)))throw changed();return;}if(statements.deleteDetail(c.tenantId().toString(),person,id,section.code)!=1)throw changed();});
  }
  /**
   * @param c 可信主体
   * @param person 人员 UUID
   * @param id 为空表示新增，否则替换此关联
   * @param input 租户账号及当前人员版本
   * @return 关联 UUID */
  public Map<String,String> assign(VerifiedTenantContext c,String person,String id,AssignmentInput input){
    require(c,"assign");uuid(person);if(id!=null)uuid(id);if(input==null)throw invalid();
    var member=uuid(input.memberId());
    String key=id==null?UUID.randomUUID().toString():id;
    try{return tx.required(()->{
      statements.lockForWrite(c,person,"assign");
      // 保存时重新核验，不信任浏览器此前读取的候选列表；关联不构成组织任职或权限授予。
      if(!directory.eligible(c.tenantId(),member))throw new Rejection(409,"PERSON_USER_UNAVAILABLE");
      bump(c,person,input.version());
      if(id==null)statements.createAssignment(c.tenantId().toString(),key,person,member.toString(),c.accountId().toString());
      else if(statements.updateAssignment(member.toString(),c.tenantId().toString(),person,id)!=1)throw changed();
      return Map.of("id",key);
    });}catch(com.appnexus.platform.organization.port.personnel.PersonnelTransaction.DuplicateRecord conflict){throw new Rejection(409,"PERSON_USER_DUPLICATE");}
  }
  /**
   * @param c 可信主体
   * @param person 人员 UUID
   * @param id 关联 UUID
   * @param version 当前人员版本；解除关联不删除人员、账号或任职 */
  public void unassign(VerifiedTenantContext c,String person,String id,long version){
    require(c,"assign");uuid(person);uuid(id);tx.run(()->{statements.lockForWrite(c,person,"assign");bump(c,person,version);if(statements.deleteAssignment(c.tenantId().toString(),person,id)!=1)throw changed();});
  }
  /**
   * 公开业务端口：仅验证真实执行用户可代表指定组织下的人员，不产生业务授权或替换登录身份。
   * 后续业务用例必须同时验证自身操作权限，并分别记录实际账号与被代表人员。
   *
   * @param c 真实执行用户可信上下文
   * @param person 被代表人员 UUID
   * @param organization 业务组织 UUID
   *
   * @return 档案启用且在职、关联存在、用户/组织/任职仍有效时为 true；其他均为 false
   */
  public boolean canRepresent(VerifiedTenantContext c,String person,String organization){
    if(c==null)return false;uuid(person);UUID org=uuid(organization);
    if(!directory.eligible(c.tenantId(),c.memberId())||!directory.personInOrganization(c.tenantId(),uuid(person),org))return false;
    return statements.countActiveIdentity(c.tenantId().toString(),person,c.memberId().toString())>0;
  }
  
  /** @return 自身可代表人员目录；管理权限不会让该账号代表未关联人员 */
  public java.util.List<com.appnexus.platform.organization.PersonnelExecution.Option> identities(VerifiedTenantContext c){return identities.options(c.tenantId(),c.memberId());}
  /** @throws Rejection 关系已撤销、人员离职、组织/成员失效或跨租户均拒绝 */
  public void verifyIdentity(VerifiedTenantContext c,IdentitySelection input){if(input==null||!canRepresent(c,input.personId(),input.organizationId()))throw new Rejection(409,"PERSON_CONTEXT_REQUIRED");}
  /** 所有明细写入和关系修改共享父版本；在同一事务内更新审计，失败回滚，不部分保存。 */
  private void bump(VerifiedTenantContext c,String id,long version){
    if(version<0)throw invalid();
    if(statements.advanceVersion(c.accountId().toString(),c.tenantId().toString(),id,version)!=1)throw changed();
  }
  private void require(VerifiedTenantContext c,String action){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.person:"+action))throw new Rejection(403,"MASTER_DATA_DENIED");}
  /** 人员组织/岗位是档案明细，不写入平台成员任职；仍须验证所选实体属于同一可信租户。 */
  private void validateReferences(VerifiedTenantContext c,Section section,Map<String,String> values){
    if(section!=Section.ORGANIZATIONS&&section!=Section.POSITIONS)return;
    var catalog=directory.catalog(c.tenantId());String org=values.get("organizationId");
    if(catalog.organizations().stream().noneMatch(o->o.id().equalsIgnoreCase(org)&&o.enabled()))throw new Rejection(409,"PERSON_ORGANIZATION_UNAVAILABLE");
    if(section==Section.POSITIONS&&catalog.positions().stream().noneMatch(p->p.id().equalsIgnoreCase(values.get("positionId"))&&p.organizationId().equalsIgnoreCase(org)&&p.enabled()))throw new Rejection(409,"PERSON_ORGANIZATION_UNAVAILABLE");
  }
  private Map<String,String> decode(String value){return content.decode(value);}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
  private static Rejection changed(){return new Rejection(409,"MASTER_DATA_CHANGED");}
 private static String utc(Object value){return value==null?null:((java.time.Instant)value).toString();}
}
