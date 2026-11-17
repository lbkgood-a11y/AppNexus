package com.appnexus.platform.organization.personnel;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.organization.PersonnelDirectory;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static com.appnexus.platform.organization.personnel.PersonData.*;
import static com.appnexus.platform.organization.personnel.PersonnelSchema.*;

/** 人员详情、十类明细和账号人员关联；真实执行账号与被代表人员始终分别保存，不冒充登录身份。 */
public final class PersonnelService {
  private final BusinessValueCatalog dictionaries;
  private final JdbcTemplate jdbc;
  private final AccessAuthorization access;
  private final PersonnelDirectory directory;
  private final TransactionTemplate tx;
  private final JsonMapper json=JsonMapper.builder().build();
  /**
   * @param jdbc 主数据源
   * @param access 公开授权端口
   * @param directory 组织公开目录
   * @param dictionaries 字典公开端口；不回退前端硬编码选项
   * @param manager 宿主事务管理器 */
  public PersonnelService(JdbcTemplate jdbc,AccessAuthorization access,PersonnelDirectory directory,PlatformTransactionManager manager,BusinessValueCatalog dictionaries){this.dictionaries=dictionaries;this.jdbc=jdbc;this.access=access;this.directory=directory;this.tx=new TransactionTemplate(manager);}
  /**
   * 全量详情输入仅保存固定字段；父档案版本用于所有明细写入，避免不同页签并发覆盖。
   *
   * @param version 当前人员档案非负版本，无默认值；成功递增 1
   *
   * @param values 固定明细白名单文本值；缺省空，必填规则由分类声明
   */
  public record DetailInput(long version,Map<String,String> values){}
  /**
   * 人员与租户账号关联，不创建组织任职，也不复制其角色权限。
   *
   *
   * @param memberId 当前租户启用账号的成员 UUID，必填；同用户可关联多个人员
   *
   * @param version 当前人员版本，非负，无默认值
   */
  public record AssignmentInput(String memberId,long version){}
  /** 已授权明细，id 不作为单独访问凭据，必须同时限定租户、人员及分类。 */
  public record Detail(String id,String section,Map<String,String> values){}
  /** 账号人员关联摘要；active 是当前组织目录复核结果，不等于业务授权。 */
  public record Assignment(String id,String memberId,String userName,boolean active){}
  /** 人员详情的真实审计信息；历史未记录字段为空，不伪造创建人或创建时间。 */
  public record Audit(String createdBy,String createdByName,String createdAt,String updatedBy,String updatedByName,String updatedAt){}
  /** 详情聚合只在具有人员读取权限时返回，不在普通列表加载身份证件、家庭及履历等正文。 */
  public record Profile(Row person,Audit audit,List<Detail> details,List<Assignment> assignments){}
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
  /** 只读人员目录和字典，不需要字典维护权限。 */
  public record Options(List<PersonnelDirectory.Organization> organizations,List<PersonnelDirectory.Position> positions,List<PersonnelDirectory.User> users,Map<String,List<BusinessValueCatalog.Option>> dictionaries){}
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
    var people=jdbc.query("SELECT BIN_TO_UUID(id) id,code,name,enabled,version,"+String.join(",",Kind.PERSON.fields.stream().map(Kind.PERSON::column).toList())+" FROM md_person WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",(rs,n)->{
      var values=new LinkedHashMap<String,String>();for(String field:Kind.PERSON.fields)values.put(field,rs.getString(Kind.PERSON.column(field)));
      return new Row(rs.getString("id"),rs.getString("code"),rs.getString("name"),values,rs.getBoolean("enabled"),rs.getLong("version"));
    },c.tenantId().toString(),id);
    if(people.isEmpty())throw new Rejection(404,"PERSON_NOT_FOUND");
    var audit=jdbc.query("SELECT BIN_TO_UUID(created_by) created_by,created_at,BIN_TO_UUID(updated_by) updated_by,updated_at FROM md_person WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",(rs,n)->{
      String creator=rs.getString("created_by"),updater=rs.getString("updated_by");
      return new Audit(creator,directory.accountName(c.tenantId(),creator),rs.getTimestamp("created_at")==null?null:rs.getTimestamp("created_at").toInstant().toString(),updater,directory.accountName(c.tenantId(),updater),rs.getTimestamp("updated_at")==null?null:rs.getTimestamp("updated_at").toInstant().toString());
    },c.tenantId().toString(),id).getFirst();
    var details=jdbc.query("SELECT BIN_TO_UUID(id) id,section_code,content FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND section_code NOT IN ('organizations','positions') ORDER BY section_code,id",(rs,n)->new Detail(rs.getString("id"),rs.getString("section_code"),decode(rs.getString("content"))),c.tenantId().toString(),id);
    details=new ArrayList<>(details);
    for(var affiliation:directory.affiliations(c.tenantId(),uuid(id)))details.add(new Detail(affiliation.id(),affiliation.section(),affiliation.values()));
    var catalog=directory.catalog(c.tenantId());
    var assignments=jdbc.query("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(member_id) member_text FROM md_person_account WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) ORDER BY created_at,id",(rs,n)->{
      String member=rs.getString("member_text");
      var user=catalog.users().stream().filter(u->u.memberId().equalsIgnoreCase(member)).findFirst();
      return new Assignment(rs.getString("id"),member,user.map(PersonnelDirectory.User::name).orElse(""),user.isPresent());
    },c.tenantId().toString(),id);
    return new Profile(people.getFirst(),audit,details,assignments);
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
    require(c,id==null?"detail_create":"detail_update");uuid(person);if(id!=null)uuid(id);
    var section=Section.parse(sectionCode);if(input==null)throw invalid();var values=validate(section,input.values());
    validateReferences(c,section,values);
    String key=id==null?UUID.randomUUID().toString():id;
    return tx.execute(status->{
      bump(c,person,input.version());
      var previous=Map.<String,String>of();
      if(id!=null){var old=jdbc.queryForList("SELECT content FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND section_code=?",String.class,c.tenantId().toString(),person,id,section.code);if(!old.isEmpty())previous=decode(old.getFirst());}
      if(!dictionaries.reference(c.tenantId(),c.accountId(),"PersonDetail",person+":"+key,BusinessValueCatalog.PERSON,values,previous))throw invalid();
      if(section==Section.ORGANIZATIONS||section==Section.POSITIONS){
        if(!directory.saveAffiliation(c.tenantId(),uuid(person),section.code,uuid(key),id==null,values))throw changed();
        return Map.of("id",key);
      }
      if(id==null)jdbc.update("INSERT INTO md_person_detail(tenant_id,id,person_id,section_code,content) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?)",c.tenantId().toString(),key,person,section.code,json.writeValueAsString(values));
      else if(jdbc.update("UPDATE md_person_detail SET content=? WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND section_code=?",json.writeValueAsString(values),c.tenantId().toString(),person,id,section.code)!=1)throw changed();
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
    tx.executeWithoutResult(status->{bump(c,person,version);if(section==Section.ORGANIZATIONS||section==Section.POSITIONS){if(!directory.deleteAffiliation(c.tenantId(),uuid(person),section.code,uuid(id)))throw changed();return;}if(jdbc.update("DELETE FROM md_person_detail WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND section_code=?",c.tenantId().toString(),person,id,section.code)!=1)throw changed();});
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
    try{return tx.execute(status->{
      // 保存时重新核验，不信任浏览器此前读取的候选列表；关联不构成组织任职或权限授予。
      if(!directory.eligible(c.tenantId(),member))throw new Rejection(409,"PERSON_USER_UNAVAILABLE");
      bump(c,person,input.version());
      if(id==null)jdbc.update("INSERT INTO md_person_account(tenant_id,id,person_id,member_id,created_by) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",c.tenantId().toString(),key,person,member.toString(),c.accountId().toString());
      else if(jdbc.update("UPDATE md_person_account SET member_id=UUID_TO_BIN(?) WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",member.toString(),c.tenantId().toString(),person,id)!=1)throw changed();
      return Map.of("id",key);
    });}catch(org.springframework.dao.DuplicateKeyException conflict){throw new Rejection(409,"PERSON_USER_DUPLICATE");}
  }
  /**
   * @param c 可信主体
   * @param person 人员 UUID
   * @param id 关联 UUID
   * @param version 当前人员版本；解除关联不删除人员、账号或任职 */
  public void unassign(VerifiedTenantContext c,String person,String id,long version){
    require(c,"assign");uuid(person);uuid(id);tx.executeWithoutResult(status->{bump(c,person,version);if(jdbc.update("DELETE FROM md_person_account WHERE tenant_id=UUID_TO_BIN(?) AND person_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",c.tenantId().toString(),person,id)!=1)throw changed();});
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
    return jdbc.queryForObject("SELECT COUNT(*) FROM md_person_account pu JOIN md_person p ON p.tenant_id=pu.tenant_id AND p.id=pu.person_id WHERE pu.tenant_id=UUID_TO_BIN(?) AND pu.person_id=UUID_TO_BIN(?) AND pu.member_id=UUID_TO_BIN(?) AND p.enabled=TRUE AND p.employment_status IN ('ACTIVE','PROBATION')",Integer.class,c.tenantId().toString(),person,c.memberId().toString())>0;
  }
  /** 选择本次人员与组织；均为规范 UUID，无缺省自动选择。 */
  public record IdentitySelection(String personId,String organizationId){}
  /** @return 自身可代表人员目录；管理权限不会让该账号代表未关联人员 */
  public java.util.List<com.appnexus.platform.organization.PersonnelExecution.Option> identities(VerifiedTenantContext c){return new PersonnelExecutionAdapter(jdbc,directory).options(c.tenantId(),c.memberId());}
  /** @throws Rejection 关系已撤销、人员离职、组织/成员失效或跨租户均拒绝 */
  public void verifyIdentity(VerifiedTenantContext c,IdentitySelection input){if(input==null||!canRepresent(c,input.personId(),input.organizationId()))throw new Rejection(409,"PERSON_CONTEXT_REQUIRED");}
  /** 所有明细写入和关系修改共享父版本；在同一事务内更新审计，失败回滚，不部分保存。 */
  private void bump(VerifiedTenantContext c,String id,long version){
    if(version<0)throw invalid();
    if(jdbc.update("UPDATE md_person SET version=version+1,updated_by=UUID_TO_BIN(?),updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND version=?",c.accountId().toString(),c.tenantId().toString(),id,version)!=1)throw changed();
  }
  private void require(VerifiedTenantContext c,String action){if(c==null||!access.hasPermission(AccessAuthorization.Subject.tenant(c.accountId(),c.tenantId(),c.memberId()),"masterdata.person:"+action))throw new Rejection(403,"MASTER_DATA_DENIED");}
  /** 人员组织/岗位是档案明细，不写入平台成员任职；仍须验证所选实体属于同一可信租户。 */
  private void validateReferences(VerifiedTenantContext c,Section section,Map<String,String> values){
    if(section!=Section.ORGANIZATIONS&&section!=Section.POSITIONS)return;
    var catalog=directory.catalog(c.tenantId());String org=values.get("organizationId");
    if(catalog.organizations().stream().noneMatch(o->o.id().equalsIgnoreCase(org)&&o.enabled()))throw new Rejection(409,"PERSON_ORGANIZATION_UNAVAILABLE");
    if(section==Section.POSITIONS&&catalog.positions().stream().noneMatch(p->p.id().equalsIgnoreCase(values.get("positionId"))&&p.organizationId().equalsIgnoreCase(org)&&p.enabled()))throw new Rejection(409,"PERSON_ORGANIZATION_UNAVAILABLE");
  }
  @SuppressWarnings("unchecked") private Map<String,String> decode(String value){return (Map<String,String>)json.readValue(value,Map.class);}
  private static Rejection invalid(){return new Rejection(400,"MASTER_DATA_INPUT_INVALID");}
  private static Rejection changed(){return new Rejection(409,"MASTER_DATA_CHANGED");}
}
