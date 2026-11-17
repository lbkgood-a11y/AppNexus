package db.migration;
import org.flywaydb.core.api.migration.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import com.appnexus.platform.persistence.configuration.BusinessValueDefaults;
import java.util.*;
/** 真实业务选项提炼：已有租户补预置及存量值，不覆盖定制名称/停用状态，不伪造迁移账号。 */
public final class V89__business_value_defaults extends BaseJavaMigration {
 @Override public Integer getChecksum(){var crc=new java.util.zip.CRC32();crc.update(BusinessValueDefaults.resource());return (int)crc.getValue();}
 @Override public void migrate(Context context){
  var jdbc=new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(),true));
  for(String tenant:jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM platform_tenant",String.class)){var id=UUID.fromString(tenant);BusinessValueDefaults.seed(jdbc,id,null);BusinessValueDefaults.legacy(jdbc,id);}
 }
}
