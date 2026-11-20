package com.appnexus.platform;

import com.appnexus.platform.organization.personnel.*;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import com.appnexus.platform.persistence.organization.OrganizationAdministratorBootstrap;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static com.appnexus.platform.organization.personnel.PersonData.*;

/** 真实 MySQL/Valkey/RBAC 验证全量人员档案、十类明细和组织用户多对多关系，隔离测试数据不进入开发库。 */
@SpringBootTest
@ActiveProfiles("local")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class PersonnelIntegrationTest extends DatabaseTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired PersonnelService personnel;
  @Autowired PersonDataService master;
  @Autowired OrganizationAdministratorBootstrap bootstrap;
  @Autowired com.appnexus.platform.organization.PersonnelExecution execution;
  /** 测试主体包含可信成员及同租户组织、岗位；所有账号为独立测试账号，不使用管理员真实凭据。 */
  record Fixture(VerifiedTenantContext admin,VerifiedTenantContext user,String org,String position){}
  private Fixture fixture(){
    UUID tenant=UUID.randomUUID(),account=UUID.randomUUID(),member=UUID.randomUUID(),userAccount=UUID.randomUUID(),userMember=UUID.randomUUID(),type=UUID.randomUUID(),org=UUID.randomUUID(),position=UUID.randomUUID();
    jdbc.update("INSERT INTO platform_tenant(id,name) VALUES(UUID_TO_BIN(?),'人员测试租户')",tenant.toString());
    DictionaryFixtures.seed(jdbc,tenant,null);
    for(var pair:List.of(Map.entry(account,member),Map.entry(userAccount,userMember))){
      jdbc.update("INSERT INTO identity_account(id,username,display_name,password_hash) VALUES(UUID_TO_BIN(?),?,'人员测试用户','unused')",pair.getKey().toString(),"person-"+pair.getKey());
      jdbc.update("INSERT INTO tenant_member(id,tenant_id,account_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",pair.getValue().toString(),tenant.toString(),pair.getKey().toString());
    }
    jdbc.update("INSERT INTO tenant_organization_type(tenant_id,id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BUSINESS','业务组织')",tenant.toString(),type.toString());
    jdbc.update("INSERT INTO tenant_organization_unit(tenant_id,id,code,type_id,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'ORG1',UUID_TO_BIN(?),'人员业务组织')",tenant.toString(),org.toString(),type.toString());
    jdbc.update("INSERT INTO tenant_organization_position(tenant_id,id,organization_id,code,name) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'POS1','人员业务岗位')",tenant.toString(),position.toString(),org.toString());
    bootstrap.initialize(tenant,member);
    return new Fixture(new VerifiedTenantContext(account,tenant,member,1),new VerifiedTenantContext(userAccount,tenant,userMember,1),org.toString(),position.toString());
  }
  private String person(Fixture f,String code){return master.create(f.admin(),Kind.PERSON,new Input(code,"测试业务人员",Map.of("birthDate","2000-01-01","employmentStatus","ACTIVE","identityNumber","TEST-DOCUMENT"),0)).get("id");}
  private long version(Fixture f,String id){return personnel.read(f.admin(),id).person().version();}
  /** 真实 Valkey Cookie 会话驱动安全过滤链，验证详情/目录映射、no-store、普通成员拒绝和 CSRF。 */
  @Test void httpRequestsKeepRealSessionTenantAndCsrfBoundaries() throws Exception {
    var f=fixture();String id=person(f,"HTTP-PERSON");
    var selected=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current").with(authenticated(f.admin().accountId().toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"tenantId\":\""+f.admin().tenantId()+"\"}" )).andReturn().getResponse();
    assertThat(selected.getStatus()).isEqualTo(200);var cookie=selected.getCookie("JSESSIONID");
    var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/person/"+id).cookie(cookie)).andReturn().getResponse();
    assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getHeader("Cache-Control")).contains("no-store");
    var accountOptions=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/access/member-options").cookie(cookie)).andReturn().getResponse();
    assertThat(accountOptions.getStatus()).isEqualTo(200);assertThat(accountOptions.getContentAsString()).doesNotContain("organizationId","organizationName");
    var body=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getContentAsString());
    assertThat(body.path("person").path("id").asText()).isEqualToIgnoringCase(id);
    assertThat(body.path("audit").path("createdBy").asText()).isEqualToIgnoringCase(f.admin().accountId().toString());
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/person/options").cookie(cookie)).andReturn().getResponse().getStatus()).isEqualTo(200);
    String input="{\"code\":\"HTTP-PERSON\",\"name\":\"篡改测试\",\"attributes\":{},\"version\":0}";
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/master-data/person/"+id).cookie(cookie).contentType("application/json").content(input)).andReturn().getResponse().getStatus()).isEqualTo(403);
    assertThat(personnel.read(f.admin(),id).person().name()).isEqualTo("测试业务人员");
    var ordinary=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current").with(authenticated(f.user().accountId().toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"tenantId\":\""+f.user().tenantId()+"\"}" )).andReturn().getResponse().getCookie("JSESSIONID");
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/person/"+id).cookie(ordinary)).andReturn().getResponse().getStatus()).isEqualTo(403);
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/person/"+id)).andReturn().getResponse().getStatus()).isEqualTo(401);
  }
  /** 组织页选择人员而非账号；任职和负责人经公开契约投影到关联账号，不修改账号本身的组织。 */
  @Test void organizationAppointmentUsesPersonAndResolvesLinkedAccounts() throws Exception {
    var f=fixture();String person=person(f,"POSITION-PERSON");
    personnel.assign(f.admin(),person,null,new PersonnelService.AssignmentInput(f.user().memberId().toString(),version(f,person)));
    var selected=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current").with(authenticated(f.admin().accountId().toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"tenantId\":\""+f.admin().tenantId()+"\"}")).andReturn().getResponse();
    var cookie=selected.getCookie("JSESSIONID");
    String input="{\"personId\":\""+person+"\",\"positionId\":\""+f.position()+"\",\"startsAt\":\"2020-01-01T00:00:00Z\"}";
    var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/organization/domain/assignments").cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(input)).andReturn().getResponse();
    assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
    var assignment=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getContentAsString()).path("id").asText();
    assertThat(execution.resolveAccounts(f.admin().tenantId(),UUID.fromString(f.org()),"POSITION",f.position())).anyMatch(account->account.equalsIgnoreCase(f.user().accountId().toString()));
    jdbc.update("INSERT INTO tenant_person_leader(tenant_id,organization_id,assignment_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",f.admin().tenantId().toString(),f.org(),assignment);
    assertThat(execution.resolveAccounts(f.admin().tenantId(),UUID.fromString(f.org()),"LEADER","")).hasSize(1);
    assertThat(personnel.read(f.admin(),person).details()).anyMatch(d->d.section().equals("positions")&&d.id().equalsIgnoreCase(assignment));
    assertThat(personnel.canRepresent(f.user(),person,f.org())).isTrue();
    assertThat(execution.routeCandidate(f.user().tenantId(),f.user().memberId(),UUID.fromString(person),UUID.fromString(f.org()),"POSITION",f.position())).isTrue();
    String another=person(f,"OTHER-IDENTITY");personnel.assign(f.admin(),another,null,new PersonnelService.AssignmentInput(f.user().memberId().toString(),version(f,another)));
    personnel.saveDetail(f.admin(),another,"organizations",null,new PersonnelService.DetailInput(version(f,another),Map.of("organizationId",f.org())));
    assertThat(execution.routeCandidate(f.user().tenantId(),f.user().memberId(),UUID.fromString(another),UUID.fromString(f.org()),"POSITION",f.position())).isFalse();
    jdbc.update("UPDATE tenant_member SET enabled=FALSE WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",f.user().tenantId().toString(),f.user().memberId().toString());
    assertThat(execution.resolveAccounts(f.admin().tenantId(),UUID.fromString(f.org()),"LEADER","")).isEmpty();
    assertThat(jdbc.queryForObject("SELECT enabled FROM tenant_person_position WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)",Boolean.class,f.admin().tenantId().toString(),assignment)).isTrue();
  }
  /** 多身份必须显式选择；人员组织独立于账号关系，选择失效立即阻断业务请求。 */
  @Test void identitySelectionIsExplicitTenantScopedAndDoesNotMergePeople() throws Exception {
    var f=fixture();var other=fixture();String first=person(f,"IDENTITY-1"),second=person(f,"IDENTITY-2");
    personnel.assign(f.admin(),first,null,new PersonnelService.AssignmentInput(f.user().memberId().toString(),version(f,first)));
    personnel.assign(f.admin(),second,null,new PersonnelService.AssignmentInput(f.user().memberId().toString(),version(f,second)));
    assertThat(personnel.identities(f.user())).isEmpty();
    personnel.saveDetail(f.admin(),first,"organizations",null,new PersonnelService.DetailInput(version(f,first),Map.of("organizationId",f.org(),"isPrimary","true")));
    assertThat(personnel.identities(f.user())).hasSize(1);
    assertThat(personnel.canRepresent(f.user(),second,f.org())).isFalse();
    var selected=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/bff/tenants/current").with(authenticated(f.user().accountId().toString(),0)).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"tenantId\":\""+f.user().tenantId()+"\"}")).andReturn().getResponse();
    var cookie=selected.getCookie("JSESSIONID");
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/leave-poc").cookie(cookie)).andReturn().getResponse().getStatus()).isEqualTo(409);
    String choice="{\"personId\":\""+first+"\",\"organizationId\":\""+f.org()+"\"}";
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/master-data/person/identity").cookie(cookie).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(choice)).andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/master-data/person/identity").cookie(cookie)).andReturn().getResponse().getContentAsString()).contains(first);
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/leave-poc").cookie(cookie).header("X-Person-Id",second)).andReturn().getResponse().getStatus()).isEqualTo(409);
    assertThatThrownBy(()->personnel.verifyIdentity(f.user(),new PersonnelService.IdentitySelection(first,other.org()))).hasMessage("PERSON_CONTEXT_REQUIRED");
    master.setStatus(f.admin(),Kind.PERSON,first,new Status(false,version(f,first)));
    assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/leave-poc").cookie(cookie)).andReturn().getResponse().getStatus()).isEqualTo(409);
    assertThat(personnel.identities(f.user())).isEmpty();
  }
  @Test void allTenDetailSectionsHaveRealCreateUpdateDeleteAndAudit(){
    var f=fixture();String id=person(f,"PERSON-1");
    assertThat(master.list(f.admin(),Kind.PERSON,"",null,1,20).rows().getFirst().attributes()).doesNotContainKey("identityNumber");
    var original=personnel.read(f.admin(),id);assertThat(original.audit().createdBy()).isEqualToIgnoringCase(f.admin().accountId().toString());assertThat(original.audit().createdByName()).isEqualTo("人员测试用户");
    for(var section:PersonnelSchema.Section.values()){
      var values=new LinkedHashMap<String,String>();
      for(String field:section.required)values.put(field,"测试明细");
      if(section.fields.contains("organizationId"))values.put("organizationId",f.org());
      if(section.fields.contains("positionId"))values.put("positionId",f.position());
      if(section==PersonnelSchema.Section.CERTIFICATES)values.put("certificateType","VOCATIONAL_QUALIFICATION");
      if(section==PersonnelSchema.Section.CONTACTS)values.put("contactType","PHONE");
      if(section==PersonnelSchema.Section.ADDRESSES)values.put("addressType","HOME");
      if(section==PersonnelSchema.Section.REWARDSPENALTIES)values.put("recordType","REWARD");
      if(section.fields.contains("startsOn")){values.put("startsOn","2020-01-01");values.put("endsOn","2021-01-01");}
      String detail=personnel.saveDetail(f.admin(),id,section.code,null,new PersonnelService.DetailInput(version(f,id),values)).get("id");
      assertThat(personnel.read(f.admin(),id).details()).anyMatch(d->d.id().equalsIgnoreCase(detail)&&d.section().equals(section.code));
      values.put("remarks","已更新");
      personnel.saveDetail(f.admin(),id,section.code,detail,new PersonnelService.DetailInput(version(f,id),values));
      assertThat(personnel.read(f.admin(),id).details().stream().filter(d->d.id().equalsIgnoreCase(detail)).findFirst().orElseThrow().values().get("remarks")).isEqualTo("已更新");
      long current=version(f,id);
      assertThatThrownBy(()->personnel.deleteDetail(f.admin(),id,section.code,detail,current-1)).hasMessage("MASTER_DATA_CHANGED");
      personnel.deleteDetail(f.admin(),id,section.code,detail,current);
    }
    assertThat(personnel.read(f.admin(),id).details()).isEmpty();
    assertThatThrownBy(()->master.update(f.admin(),Kind.PERSON,id,new Input("PERSON-1","测试",Map.of("birthDate","9999-01-01"),version(f,id)))).hasMessage("MASTER_DATA_INPUT_INVALID");
    long current=version(f,id);
    assertThatThrownBy(()->personnel.saveDetail(f.admin(),id,"contacts",null,new PersonnelService.DetailInput(current,Map.of("contactType","PHONE","contactValue","x","tenantId","forged")))).hasMessage("MASTER_DATA_INPUT_INVALID");
    assertThat(version(f,id)).isEqualTo(current);
    assertThatThrownBy(()->personnel.saveDetail(f.admin(),id,"training",null,new PersonnelService.DetailInput(current,Map.of("trainingName","测试","startsOn","2024-02-30")))).hasMessage("MASTER_DATA_INPUT_INVALID");
    assertThatThrownBy(()->personnel.saveDetail(f.admin(),id,"training",null,new PersonnelService.DetailInput(current,Map.of("trainingName","测试","startsOn","2024-05-01","endsOn","2024-01-01")))).hasMessage("MASTER_DATA_INPUT_INVALID");
    var other=fixture();
    assertThatThrownBy(()->personnel.read(other.admin(),id)).hasMessage("PERSON_NOT_FOUND");
    assertThatThrownBy(()->personnel.saveDetail(other.admin(),id,"contacts",null,new PersonnelService.DetailInput(current,Map.of("contactType","PHONE","contactValue","x")))).hasMessage("MASTER_DATA_CHANGED");
    assertThatThrownBy(()->personnel.saveDetail(f.admin(),id,"organizations",null,new PersonnelService.DetailInput(current,Map.of("organizationId",other.org())))).hasMessage("PERSON_ORGANIZATION_UNAVAILABLE");
    assertThat(personnel.read(f.admin(),id).audit().updatedBy()).isEqualToIgnoringCase(f.admin().accountId().toString());
  }
  @Test void oneAccountRepresentsMultiplePeopleOnlyInItsValidOrganizationsAndNeverGainsBusinessPermission(){
    var f=fixture();var other=fixture();String first=person(f,"FIRST"),second=person(f,"SECOND");
    assertThat(personnel.canRepresent(f.user(),first,f.org())).isFalse();
    personnel.saveDetail(f.admin(),first,"organizations",null,new PersonnelService.DetailInput(version(f,first),Map.of("organizationId",f.org(),"isPrimary","true")));
    personnel.saveDetail(f.admin(),second,"organizations",null,new PersonnelService.DetailInput(version(f,second),Map.of("organizationId",f.org())));
    String link=personnel.assign(f.admin(),first,null,new PersonnelService.AssignmentInput(f.user().memberId().toString(),version(f,first))).get("id");
    personnel.assign(f.admin(),first,null,new PersonnelService.AssignmentInput(f.admin().memberId().toString(),version(f,first)));
    assertThat(personnel.canRepresent(f.admin(),first,f.org())).isTrue();
    personnel.assign(f.admin(),second,null,new PersonnelService.AssignmentInput(f.user().memberId().toString(),version(f,second)));
    assertThat(personnel.canRepresent(f.user(),first,f.org())).isTrue();assertThat(personnel.canRepresent(f.user(),second,f.org())).isTrue();
    assertThat(personnel.read(f.admin(),first).assignments()).hasSize(2);
    assertThat(personnel.canRepresent(other.user(),first,f.org())).isFalse();
    assertThatThrownBy(()->personnel.read(f.user(),first)).hasMessage("MASTER_DATA_DENIED");
    assertThatThrownBy(()->personnel.assign(f.user(),first,null,new PersonnelService.AssignmentInput(f.user().memberId().toString(),version(f,first)))).hasMessage("MASTER_DATA_DENIED");
    long current=version(f,first);
    assertThatThrownBy(()->personnel.assign(f.admin(),first,null,new PersonnelService.AssignmentInput(f.user().memberId().toString(),current))).hasMessage("PERSON_USER_DUPLICATE");
    assertThat(version(f,first)).isEqualTo(current);
    assertThatThrownBy(()->personnel.assign(f.admin(),first,null,new PersonnelService.AssignmentInput(other.user().memberId().toString(),current))).hasMessage("PERSON_USER_UNAVAILABLE");
    personnel.assign(f.admin(),first,link,new PersonnelService.AssignmentInput(f.user().memberId().toString(),current));
    personnel.unassign(f.admin(),first,link,version(f,first));assertThat(personnel.canRepresent(f.user(),first,f.org())).isFalse();
    jdbc.update("UPDATE tenant_member SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",f.user().memberId().toString());
    assertThat(personnel.canRepresent(f.user(),second,f.org())).isFalse();
    assertThat(personnel.read(f.admin(),second).assignments().getFirst().active()).isFalse();
    jdbc.update("UPDATE tenant_member SET enabled=TRUE WHERE id=UUID_TO_BIN(?)",f.user().memberId().toString());
    master.setStatus(f.admin(),Kind.PERSON,second,new Status(false,version(f,second)));
    assertThat(personnel.canRepresent(f.user(),second,f.org())).isFalse();
  }
}
