package db.migration;

import java.util.UUID;
import com.appnexus.masterdata.GeographyDefaults;
import org.flywaydb.core.api.migration.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 补齐已有租户的民政部地县级目录；与 ISO 省级关联，不覆盖已有业务数据。 */
public final class V96__china_administrative_divisions extends BaseJavaMigration {
  @Override public void migrate(Context context) {
    var jdbc=new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(),true));
    for(String tenant:jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM platform_tenant",String.class))
      GeographyDefaults.seed(jdbc,UUID.fromString(tenant),null);
  }
}
