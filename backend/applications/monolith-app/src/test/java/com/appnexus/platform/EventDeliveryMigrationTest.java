package com.appnexus.platform;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

/** 真实 MySQL 从完整旧迁移链升级，保留四类事件的兼容投递和原回执。 */
class EventDeliveryMigrationTest {
    @Test void migratesLegacyOutboxWithoutInventingSuccessOrHistory() {
        try(var mysql=new MySQLContainer("mysql:8.4")) {
            mysql.start();
            var source=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            verifyUpgrade(source);
        }
    }

    /** 在空 MySQL schema 上验证完整升级，便于独立运行同一组断言。 */
    void verifyUpgrade(javax.sql.DataSource source) {
        var jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("202").load().migrate();

        String authority=legacy(jdbc,"AUTHORITY_INVALIDATED","DONE",1);
        String integration=legacy(jdbc,"INTEGRATION_AUTHORITY_CHANGED","DONE",2);
        String receipt=legacy(jdbc,"INBOUND_RECEIPT_POSTED","PROCESSING",3);
        String quality=legacy(jdbc,"INBOUND_QUALITY_DECIDED","PROCESSING",5);
        jdbc.update("""
            INSERT INTO platform_event_consumption(tenant_id,event_id,consumer)
            SELECT tenant_id,id,'authority-invalidation' FROM platform_outbox WHERE id=UUID_TO_BIN(?)
            """,authority);

        var deliveryMigration=Flyway.configure().dataSource(source).target("203").load();
        assertThat(deliveryMigration.migrate().migrationsExecuted).isEqualTo(1);
        var flyway=Flyway.configure().dataSource(source).load();
        flyway.migrate();
        flyway.validate();
        assertThat(delivery(jdbc,authority)).containsEntry("status","DONE").containsEntry("consumer_id","authority-invalidation");
        assertThat(delivery(jdbc,integration)).containsEntry("status","MANUAL").containsEntry("failure_code","LEGACY_RECEIPT_MISSING");
        assertThat(delivery(jdbc,receipt)).containsEntry("status","PENDING").containsEntry("consumer_id","inbound-batch-quality").containsEntry("attempts",3);
        assertThat(delivery(jdbc,quality)).containsEntry("status","MANUAL").containsEntry("failure_code","ATTEMPTS_EXHAUSTED").containsEntry("total_attempts",5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_event_delivery_attempt",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_event_consumption WHERE event_id=UUID_TO_BIN(?)",Integer.class,authority)).isEqualTo(1);
    }

    private String legacy(JdbcTemplate jdbc,String type,String status,int attempts) {
        String id=UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO platform_outbox(id,tenant_id,event_type,aggregate_id,status,attempts)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?)
            """,id,UUID.randomUUID().toString(),type,id,status,attempts);
        return id;
    }

    private java.util.Map<String,Object> delivery(JdbcTemplate jdbc,String event) {
        return jdbc.queryForMap("""
            SELECT status,consumer_id,attempts,total_attempts,failure_code
            FROM platform_event_delivery WHERE event_id=UUID_TO_BIN(?)
            """,event);
    }
}
