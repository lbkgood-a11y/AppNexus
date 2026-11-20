package com.appnexus.leave;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

/** 真实 MySQL 验证旧交付表一次性迁移并删除，历史轮次不能污染已重提记录。 */
@Testcontainers
class ObjectReceiptMigrationTest {
  @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4");
  @Test void movesReceiptsAndDropsOldTable() throws Exception {
    var source=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
    var jdbc=new JdbcTemplate(source);
    try(var connection=source.getConnection()){
      ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V57__leave_poc.sql"));
      ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V60__leave_approval_cycles.sql"));
    }
    String id=UUID.randomUUID().toString();
    for(String tenant:new String[]{"tenant-a","tenant-b"}){
      String instance=tenant.equals("tenant-a")?"completed":"current";
      jdbc.update("INSERT INTO leave_poc_request(tenant_id,id,applicant,organization_id,leave_type,start_date,end_date,days,reason,state,basis_version,instance_id,create_key) VALUES(?,?,?,?,'PERSONAL','2026-11-01','2026-11-01',1,'test',?,0,?,?)",tenant,id,"owner","org",tenant.equals("tenant-a")?"APPROVED":"SUBMITTED",instance,UUID.randomUUID().toString());
      String historicalInstance=tenant.equals("tenant-a")?"completed":"old";
      jdbc.update("INSERT INTO leave_poc_approval_cycle(tenant_id,id,object_id,basis_version,instance_id,basis_snapshot) VALUES(?,?,?,0,?,'{}')",tenant,UUID.randomUUID().toString(),id,historicalInstance);
      jdbc.update("INSERT INTO leave_poc_delivery(tenant_id,invocation_id,object_id,instance_id,result) VALUES(?,?,?,?,?)",tenant,"delivery-"+tenant,id,historicalInstance,"APPROVED");
    }
    try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V129__object_approval_receipts.sql"));}
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='leave_poc_delivery'",Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT last_delivery_id FROM leave_poc_request WHERE tenant_id='tenant-a' AND id=?",String.class,id)).isEqualTo("delivery-tenant-a");
    assertThat(jdbc.queryForMap("SELECT last_delivery_id,state FROM leave_poc_request WHERE tenant_id='tenant-b' AND id=?",id)).containsEntry("last_delivery_id",null).containsEntry("state","SUBMITTED");
    assertThat(jdbc.queryForList("SELECT delivery_id,result,completed_at FROM leave_poc_approval_cycle")).allSatisfy(row->{assertThat(row.get("delivery_id")).isNotNull();assertThat(row.get("result")).isEqualTo("APPROVED");assertThat(row.get("completed_at")).isNull();});
  }
}
