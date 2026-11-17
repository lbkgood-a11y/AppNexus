package db.migration;
import org.flywaydb.core.api.migration.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import com.appnexus.platform.persistence.configuration.WarehouseValueDefaults;
import java.util.UUID;
/** 为已有租户补仓库业务字典，不改变V89资源及校验和，不伪造迁移账号。 */
public final class V91__warehouse_value_defaults extends BaseJavaMigration {
 @Override public Integer getChecksum(){var crc=new java.util.zip.CRC32();crc.update(WarehouseValueDefaults.resource());return (int)crc.getValue();}
 @Override public void migrate(Context context){var jdbc=new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(),true));for(String tenant:jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM platform_tenant",String.class))WarehouseValueDefaults.seed(jdbc,UUID.fromString(tenant),null);}
}
