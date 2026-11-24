package com.appnexus.platform;

import com.appnexus.bulkwarehousing.application.inboundexecution.ExecuteReceiptService;
import com.appnexus.bulkwarehousing.application.inboundexecution.ReceiptExecutionRepository;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.DataScopeAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

/** 真实迁移回归，核对三单归属、有效槽位与事务幂等回执的存储约束。 */
class InboundExecutionMigrationTest {
    @Test
    void reversalReleasesSlotAndReceiptExecutionIsUniqueAndTransactional() {
        try (var mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(source).load().migrate();
            var jdbc = new JdbcTemplate(source);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            String tenant = id(), notice = id(), task = id(), template = id(), meter = id(), quality = id(), receipt = id();
            seed(jdbc, tenant, notice, task, template, meter, quality);
            insertReceipt(jdbc, tenant, notice, task, meter, quality, receipt, "R1");

            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                lockTask(jdbc, tenant, task);
                assertThat(post(jdbc, tenant, task, receipt)).isEqualTo(1);
                recordExecution(jdbc, tenant, task, receipt, "POST", "same-key", 1);
                throw new IllegalStateException("forced rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(countExecutions(jdbc, tenant, task)).isZero();
            assertThat(state(jdbc, tenant, receipt)).isEqualTo("DRAFT");

            transaction.executeWithoutResult(status -> {
                lockTask(jdbc, tenant, task);
                // 另一连接即使针对不同单据操作，也必须先获得同一作业锁。
                CompletableFuture.runAsync(() -> assertThatThrownBy(() -> jdbc.queryForObject("""
                        SELECT row_version FROM bw_inbound_task
                        WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE NOWAIT
                        """, Long.class, tenant, task))
                        .hasRootCauseInstanceOf(java.sql.SQLException.class)
                        .satisfies(error -> {
                            Throwable cause = error;
                            while (cause.getCause() != null) cause = cause.getCause();
                            assertThat(((java.sql.SQLException) cause).getErrorCode()).isEqualTo(3572);
                        })).orTimeout(15, TimeUnit.SECONDS).join();
                assertThat(post(jdbc, tenant, task, receipt)).isEqualTo(1);
                recordExecution(jdbc, tenant, task, receipt, "POST", "same-key", 1);
            });
            assertThat(post(jdbc, tenant, task, receipt)).isZero();
            assertThatThrownBy(() -> recordExecution(jdbc, tenant, task, receipt, "POST", "another-key", 1))
                    .isInstanceOf(DuplicateKeyException.class);
            assertThatThrownBy(() -> insertReceipt(jdbc, tenant, notice, task, meter, quality, id(), "R2"))
                    .isInstanceOf(DuplicateKeyException.class);

            transaction.executeWithoutResult(status -> {
                lockTask(jdbc, tenant, task);
                assertThat(jdbc.update("""
                        UPDATE bw_receipt_order SET status='REVERSED', row_version=row_version+1
                        WHERE tenant_id=UUID_TO_BIN(?) AND task_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                          AND status='POSTED' AND row_version=1
                        """, tenant, task, receipt)).isEqualTo(1);
                recordExecution(jdbc, tenant, task, receipt, "REVERSE", "reverse-key", 2);
            });
            String reopened = id();
            insertReceipt(jdbc, tenant, notice, task, meter, quality, reopened, "R2");
            assertThat(countExecutions(jdbc, tenant, task)).isEqualTo(2);
            assertThat(state(jdbc, tenant, receipt)).isEqualTo("REVERSED");
            verifyServiceReplay(jdbc, transaction, tenant, task, reopened);
        }
    }

    private static void verifyServiceReplay(JdbcTemplate jdbc, TransactionTemplate transaction, String tenant,
                                            String task, String receipt) {
        var context = new VerifiedTenantContext(UUID.randomUUID(), UUID.fromString(tenant), UUID.randomUUID(), 1);
        var permitted = new java.util.concurrent.atomic.AtomicBoolean(true);
        var repository = new ReceiptExecutionRepository() {
            @Override public <T> T transaction(Supplier<T> work) {
                return transaction.execute(ignored -> work.get());
            }

            @Override public Optional<LockedReceipt> lock(AccessPlan plan, UUID taskId, UUID receiptId) {
                String scopedTenant = plan.subject().tenantId().toString();
                lockTask(jdbc, scopedTenant, taskId.toString());
                return jdbc.query("""
                        SELECT row_version,status FROM bw_receipt_order
                        WHERE tenant_id=UUID_TO_BIN(?) AND task_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE
                        """, (row, index) -> new LockedReceipt(0, row.getLong("row_version"),
                        State.valueOf(row.getString("status"))), scopedTenant, taskId.toString(), receiptId.toString())
                        .stream().findFirst();
            }

            @Override public Optional<Execution> findExecution(VerifiedTenantContext subject, UUID taskId,
                                                               Action action, String key) {
                return jdbc.query("""
                        SELECT BIN_TO_UUID(receipt_id) receipt_id, LOWER(HEX(request_digest)) digest, resulting_version
                        FROM bw_receipt_execution
                        WHERE tenant_id=UUID_TO_BIN(?) AND task_id=UUID_TO_BIN(?) AND operation_code=? AND idempotency_key=?
                        """, (row, index) -> new Execution(UUID.fromString(row.getString("receipt_id")),
                        row.getString("digest"), row.getLong("resulting_version")), subject.tenantId().toString(),
                        taskId.toString(), action.name(), key).stream().findFirst();
            }

            @Override public void insertExecution(VerifiedTenantContext subject, UUID taskId, Action action,
                                                  String key, Execution execution) {
                jdbc.update("""
                        INSERT INTO bw_receipt_execution(tenant_id,task_id,receipt_id,operation_code,idempotency_key,
                          request_digest,resulting_version,created_by,updated_by)
                        VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UNHEX(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?))
                        """, subject.tenantId().toString(), taskId.toString(), execution.receiptId().toString(),
                        action.name(), key, execution.digest(), execution.resultingVersion(),
                        subject.accountId().toString(), subject.accountId().toString());
            }
        };
        // 严格使用测试授权和测试数据库效果，验证应用编排；不将其视为生产权限或库存适配器。
        var service = new ExecuteReceiptService(repository, (subject, permission, resource) -> {
            if (!permitted.get()) throw new SecurityException("revoked");
            return new AccessPlan(subject, permission, resource,
                    new DataScopeAuthorization.DataScope(DataScopeAuthorization.ScopeType.ALL, Set.of(), List.of()),
                    Map.of(), Instant.now());
        }, (plan, subject, action, command, nextVersion) ->
                assertThat(post(jdbc, tenant, task, receipt)).isEqualTo(1));
        var action = ReceiptExecutionRepository.Action.POST;
        UUID taskId = UUID.fromString(task), receiptId = UUID.fromString(receipt);
        var command = new ExecuteReceiptService.Command(taskId, receiptId, 0, 0, "service-key");
        assertThat(service.execute(context, action, command).receiptVersion()).isEqualTo(1);
        assertThat(service.execute(context, action, command).receiptVersion()).isEqualTo(1);
        assertThat(countExecutions(jdbc, tenant, task)).isEqualTo(3);
        assertThatThrownBy(() -> service.execute(context, action,
                new ExecuteReceiptService.Command(taskId, receiptId, 0, 1, "service-key")))
                .isInstanceOf(Rejection.class).hasMessage("INBOUND_EXECUTION_IDEMPOTENCY_CONFLICT");
        permitted.set(false);
        assertThatThrownBy(() -> service.execute(context, action, command)).isInstanceOf(SecurityException.class);
    }

    private static void seed(JdbcTemplate jdbc, String tenant, String notice, String task, String template,
                             String meter, String quality) {
        String ref = id();
        jdbc.update("""
                INSERT INTO bw_inbound_notice(tenant_id,id,notice_no,warehouse_id,owner_id,flow_version)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'N1',UUID_TO_BIN(?),UUID_TO_BIN(?),2)
                """, tenant, notice, ref, ref);
        jdbc.update("""
                INSERT INTO bw_commodity_template(tenant_id,id,template_code,version,status,
                  metering_strategy,quality_gate_strategy,deduction_strategy,stock_granularity,definition)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'TEST',1,'PUBLISHED','SCALE','SAMPLED','ZERO','BULK',JSON_OBJECT())
                """, tenant, template);
        jdbc.update("""
                INSERT INTO bw_inbound_task(tenant_id,id,task_no,notice_id,template_id,vehicle_identity_type,status)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'T1',UUID_TO_BIN(?),UUID_TO_BIN(?),'OTHER','READY_TO_RECEIVE')
                """, tenant, task, notice, template);
        jdbc.update("""
                INSERT INTO bw_quality_order(tenant_id,id,task_id,quality_no,version,is_active,status)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'Q1',1,1,'REGISTERED')
                """, tenant, quality, task);
        jdbc.update("""
                INSERT INTO bw_metering_order(tenant_id,id,task_id,metering_no,status,method,gross_kg,tare_kg,actual_net_kg)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'M1','CONFIRMED','SCALE_DIFFERENCE',10,2,8)
                """, tenant, meter, task);
    }

    private static void insertReceipt(JdbcTemplate jdbc, String tenant, String notice, String task, String meter,
                                      String quality, String receipt, String number) {
        jdbc.update("""
                INSERT INTO bw_receipt_order(tenant_id,id,task_id,notice_id,receipt_no,status,quality_state,
                  metering_id,quality_id,physical_net_kg,accounting_weight_kg,posting_snapshot)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,'DRAFT','PENDING_LAB',
                  UUID_TO_BIN(?),UUID_TO_BIN(?),8,8,JSON_OBJECT())
                """, tenant, receipt, task, notice, number, meter, quality);
    }

    private static void lockTask(JdbcTemplate jdbc, String tenant, String task) {
        assertThat(jdbc.queryForObject("""
                SELECT row_version FROM bw_inbound_task
                WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?) FOR UPDATE
                """, Long.class, tenant, task)).isZero();
    }

    private static int post(JdbcTemplate jdbc, String tenant, String task, String receipt) {
        return jdbc.update("""
                UPDATE bw_receipt_order SET status='POSTED', row_version=row_version+1, posted_at=CURRENT_TIMESTAMP(6)
                WHERE tenant_id=UUID_TO_BIN(?) AND task_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                  AND status='DRAFT' AND row_version=0
                """, tenant, task, receipt);
    }

    private static void recordExecution(JdbcTemplate jdbc, String tenant, String task, String receipt,
                                        String action, String key, long version) {
        jdbc.update("""
                INSERT INTO bw_receipt_execution(tenant_id,task_id,receipt_id,operation_code,idempotency_key,
                  request_digest,resulting_version)
                VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UNHEX(SHA2(?,256)),?)
                """, tenant, task, receipt, action, key, receipt, version);
    }

    private static int countExecutions(JdbcTemplate jdbc, String tenant, String task) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM bw_receipt_execution WHERE tenant_id=UUID_TO_BIN(?) AND task_id=UUID_TO_BIN(?)
                """, Integer.class, tenant, task);
    }

    private static String state(JdbcTemplate jdbc, String tenant, String receipt) {
        return jdbc.queryForObject("""
                SELECT status FROM bw_receipt_order WHERE tenant_id=UUID_TO_BIN(?) AND id=UUID_TO_BIN(?)
                """, String.class, tenant, receipt);
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
