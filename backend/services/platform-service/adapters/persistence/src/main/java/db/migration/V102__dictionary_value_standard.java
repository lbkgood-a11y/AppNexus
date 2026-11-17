package db.migration;

import com.appnexus.platform.persistence.configuration.DictionaryStandardMigration;
import com.appnexus.platform.persistence.configuration.StandardDictionaryDefaults;
import java.util.UUID;
import org.flywaydb.core.api.migration.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 统一全部租户字典标准及业务引用；仅 DML，整批事务失败回滚，保留历史迁移及其资源校验和。 */
public final class V102__dictionary_value_standard extends BaseJavaMigration {
    @Override public Integer getChecksum() {
        var crc = new java.util.zip.CRC32();
        crc.update(StandardDictionaryDefaults.resource());
        return (int) crc.getValue();
    }

    /** MySQL 不保证迁移整体 DDL 事务；本版本显式保证全部 DML 原子性，不提交 Flyway 已接管的事务。 */
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        boolean ownTransaction = connection.getAutoCommit();
        if (ownTransaction) connection.setAutoCommit(false);
        try {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            for (String tenant : jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM platform_tenant ORDER BY id", String.class))
                DictionaryStandardMigration.migrateTenant(jdbc, UUID.fromString(tenant));
            if (ownTransaction) connection.commit();
        } catch (Exception error) {
            if (ownTransaction) connection.rollback();
            throw error;
        } finally {
            if (ownTransaction) connection.setAutoCommit(true);
        }
    }
}
