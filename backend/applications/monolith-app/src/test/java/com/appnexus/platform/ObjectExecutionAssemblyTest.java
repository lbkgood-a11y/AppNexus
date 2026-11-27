package com.appnexus.platform;
import com.appnexus.contracts.businessobject.BusinessObjects.*;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.workflow.WorkflowBusiness;
import com.appnexus.workflow.application.WorkflowRuntime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** 开启真实引擎验证公共执行装配，使用500条隔离记录检查对象锁定查询的主键执行计划。 */
@SpringBootTest(properties={"app.workflow.engine.enabled=true","app.workflow.worker.enabled=false"})
@ActiveProfiles("local")
class ObjectExecutionAssemblyTest extends WorkflowDatabaseTestSupport {
  @Autowired BusinessObjectExecution execution;
  @Autowired WorkflowRuntime workflow;
  @Autowired List<Provider> providers;
  @Autowired com.appnexus.platform.authorization.DataAccessCatalog dataAccess;
  @Autowired JdbcTemplate jdbc;
  /** 启用工作流的组合应用应同时装配全部对象，旧归属编码不会重复占据目录。 */
  @Test void completeProviderInventoryRegistersOneOwnerPerObject() {
    assertThat(providers).extracting(provider->provider.descriptor().objectType())
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrder(
            "Account","AuditEvent","AuthorizationTemplate","BusinessDictionary","BusinessObject","BusinessPartner",
            "CodingEncoder","CodingRule","CountryRegion","DictionaryItemReference","IntegrationClient","LeaveRequest",
            "NavigationMenu","OrganizationRelation","OrganizationType","OrganizationUnit",
            "PermissionCatalog","Person","PersonDirectory","PlatformRole","PlatformTask","Position","PositionAssignment",
            "PrintDataContract","PrintDevice","PrintDocument","PrintJob","PrintOutput","PrintScene","PrintTemplate",
            "Product","ProductApproval","ProductAttribute","ProductCategory","ProductTemplate","ProductUnit",
            "StorageArea","StorageLocation","Subdivision","Tenant","TenantInvitation","TenantMember","TenantParameter",
            "TenantRole","Warehouse","WorkflowDefinition","WorkflowInstance","WorkflowJob","WorkflowTask",
            "BulkInboundReceipt","BulkWarehouseStockBalance")
        .doesNotContain("AccountProfile","AccountMfa","AccountSession","AccountRoleAssignment",
                "MemberRoleAssignment","MemberAuthorization","OrganizationLeader","DictionaryItem","RoleAuthorization");
  }

  /** 本轮全部资源从唯一 Provider 派生；跨管理域对象分别进入平台和租户目录。 */
  @Test void allMigratedObjectsDeclareExecutedDataResources() {
    var expected=Set.of("TenantInvitation","TenantParameter","NavigationMenu","BusinessObject","AuditEvent",
        "PlatformRole","TenantRole","AuthorizationTemplate","PermissionCatalog",
        "Person","OrganizationType","OrganizationRelation","Position","PositionAssignment","BusinessDictionary",
        "Warehouse","ProductCategory","ProductAttribute","ProductTemplate","ProductUnit",
        "WorkflowDefinition","WorkflowInstance","WorkflowTask","WorkflowJob",
        "PrintDataContract","PrintDevice","PrintDocument","PrintJob","PrintOutput","PrintScene","PrintTemplate");
    var migrated=providers.stream().map(Provider::descriptor).filter(d->expected.contains(d.objectType())).toList();
    assertThat(migrated).hasSize(32).allSatisfy(d->assertThat(d.dataAccess()).isNotNull());
    var both=Set.of("BusinessObject","AuditEvent","AuthorizationTemplate","PermissionCatalog");
    for(var scope:com.appnexus.platform.authorization.PermissionCatalog.ScopeType.values()){
      assertThat(dataAccess.list(scope).stream().map(d->d.objectType()).toList()).containsAll(both);
    }
    assertThat(migrated.stream().filter(d->d.dataAccess().otherDomain()!=null).map(Descriptor::objectType).toList())
        .containsExactlyInAnyOrderElementsOf(both);
  }
  @Test void registersObjectRuntimeAndUsesPrimaryKeyForLockedReads(){
    assertThat(execution).isNotNull();assertThat(workflow).isNotNull();
    assertThat(providers.stream().filter(p->Set.of("LeaveRequest","ProductApproval").contains(p.descriptor().objectType())).toList())
        .hasSize(2).allSatisfy(p->{assertThat(p.runtime()).isNotNull();assertThat(p).isNotInstanceOf(WorkflowBusiness.class);});
    String tenant=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString(),organization=UUID.randomUUID().toString();
    var rows=new ArrayList<Object[]>();
    for(int index=0;index<500;index++)rows.add(new Object[]{tenant,UUID.randomUUID().toString(),actor,organization,UUID.randomUUID().toString()});
    try{
      jdbc.batchUpdate("INSERT INTO leave_poc_request(tenant_id,id,applicant,organization_id,leave_type,start_date,end_date,days,reason,create_key) VALUES(?,?,?,?,'PERSONAL','2026-11-01','2026-11-01',1,'isolated fixture',?)",rows);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_poc_request WHERE tenant_id=?",Integer.class,tenant)).isEqualTo(500);
      var plan=jdbc.queryForMap("EXPLAIN SELECT * FROM leave_poc_request WHERE tenant_id=? AND id=? FOR UPDATE",tenant,rows.getFirst()[1]);
      assertThat(plan.get("key")).isEqualTo("PRIMARY");
      assertThat(((Number)plan.get("rows")).longValue()).isEqualTo(1);
      assertThatThrownBy(()->execution.execute(new Context(tenant,actor,UUID.randomUUID().toString()),new BusinessObjectExecution.Request("Missing",UUID.randomUUID().toString(),"submit","Missing.submit",0)))
          .isInstanceOf(Rejection.class).hasMessage("BUSINESS_OBJECT_EXECUTION_UNAVAILABLE");
    }finally{jdbc.update("DELETE FROM leave_poc_request WHERE tenant_id=?",tenant);}
  }
}
