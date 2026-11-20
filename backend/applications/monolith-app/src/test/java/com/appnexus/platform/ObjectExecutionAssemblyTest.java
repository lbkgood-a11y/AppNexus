package com.appnexus.platform;

import com.appnexus.contracts.businessobject.*;
import com.appnexus.contracts.businessobject.BusinessObjects.*;
import com.appnexus.contracts.workflow.WorkflowBusiness;
import com.appnexus.workflow.adapter.flowable.WorkflowRuntime;
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
class ObjectExecutionAssemblyTest extends DatabaseTestSupport {
  @Autowired BusinessObjectExecution execution;
  @Autowired WorkflowRuntime workflow;
  @Autowired List<Provider> providers;
  @Autowired JdbcTemplate jdbc;
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
