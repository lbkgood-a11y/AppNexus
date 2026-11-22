package com.appnexus.platform;

import java.math.BigDecimal;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

/** 使用旧 Flyway 版本种入通知、接管和确认，核对真实升级保留原行、库存关联及历史摘要。 */
class InboundNoticeLineMigrationTest {
  @Test void migratesPostedAndPendingCustodiesWithoutChangingLegacyKeys() {
    try (var mysql=new MySQLContainer("mysql:8.4")) {
      mysql.start();
      var source=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
      var jdbc=new JdbcTemplate(source);
      Flyway.configure().dataSource(source).target("166").load().migrate();
      String tenant=id(),notice=id(),warehouse=id(),owner=id(),product=id(),unit=id();
      jdbc.update("""
          INSERT INTO bw_inbound_notice(tenant_id,id,warehouse_id,owner_id,product_id,identity_kind,
            identity_id,product_version,base_unit_id,approved_quantity,received_quantity,flow_version)
          VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'PRODUCT',
            UUID_TO_BIN(?),3,UUID_TO_BIN(?),100,40,1)
          """,tenant,notice,warehouse,owner,product,product,unit);
      String prior=id(),pending=id(),area=id(),location=id();
      for(String custody:java.util.List.of(prior,pending)) {
        jdbc.update("""
            INSERT INTO bw_inbound_custody(tenant_id,id,notice_id,warehouse_id,area_id,location_id,
              warehouse_version,area_version,location_version,evidence_id)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),
              UUID_TO_BIN(?),0,0,0,UUID_TO_BIN(?))
            """,tenant,custody,notice,warehouse,area,location,id());
      }
      String receipt=id(),digest="5".repeat(64);
      jdbc.update("""
          INSERT INTO bw_inbound_receipt(tenant_id,id,custody_id,measurement_id,quantity,unit_id,
            request_digest,quantity_evidence,receipt_batch_no)
          VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),40,UUID_TO_BIN(?),?,'legacy','LOT-01')
          """,tenant,receipt,prior,id(),unit,digest);
      jdbc.update("""
          INSERT INTO bw_stock_receipt(tenant_id,receipt_id,inbound_quantity,pending_quantity)
          VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),40,40)
          """,tenant,receipt);
      assertThat(Flyway.configure().dataSource(source).target("167").load().migrate().migrationsExecuted).isEqualTo(1);
      var head=jdbc.queryForMap("""
          SELECT notice_no,status,flow_version FROM bw_inbound_notice WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
          """,tenant,notice);
      assertThat(head).containsEntry("notice_no",notice).containsEntry("flow_version",1).containsEntry("status","RECEIVING");
      assertThat(jdbc.queryForObject("""
          SELECT received_quantity FROM bw_inbound_notice_line
          WHERE tenant_id=UUID_TO_BIN(?) AND notice_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) AND line_no=1
          """,BigDecimal.class,tenant,notice,notice)).isEqualByComparingTo("40");
      assertThat(jdbc.queryForList("""
          SELECT BIN_TO_UUID(id) FROM bw_inbound_custody
          WHERE tenant_id=UUID_TO_BIN(?) AND notice_id=UUID_TO_BIN(?) AND notice_line_id=UUID_TO_BIN(?)
          """,String.class,tenant,notice,notice)).containsExactlyInAnyOrder(prior,pending);
      assertThat(jdbc.queryForMap("""
          SELECT r.request_digest,r.receipt_batch_no,s.inbound_quantity FROM bw_inbound_receipt r
          JOIN bw_stock_receipt s ON s.tenant_id=r.tenant_id AND s.receipt_id=r.id
          WHERE r.tenant_id=UUID_TO_BIN(?) AND r.id=UUID_TO_BIN(?)
          """,tenant,receipt)).containsEntry("request_digest",digest).containsEntry("receipt_batch_no","LOT-01");
      assertThat(Flyway.configure().dataSource(source).target("167").load().migrate().migrationsExecuted).isZero();
    }
  }

  private static String id() { return UUID.randomUUID().toString(); }
}
