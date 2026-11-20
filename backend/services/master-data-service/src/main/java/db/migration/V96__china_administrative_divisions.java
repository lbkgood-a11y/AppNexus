package db.migration;
import com.appnexus.masterdata.adapter.persistence.GeographyMapper;

import java.util.UUID;
import com.appnexus.masterdata.adapter.persistence.masterdata.GeographyDefaults;
import org.flywaydb.core.api.migration.*;



/** 补齐已有租户的民政部地县级目录；与 ISO 省级关联，不覆盖已有业务数据。 */
public final class V96__china_administrative_divisions extends BaseJavaMigration {
  @Override public void migrate(Context context) {
    try(var session=new com.appnexus.persistence.migration.MigrationSession(context.getConnection(),com.appnexus.masterdata.adapter.persistence.GeographyMapper.class,Tenants.class)){
      var mapper=session.mapper(com.appnexus.masterdata.adapter.persistence.GeographyMapper.class);
      for(String tenant:session.mapper(Tenants.class).all())GeographyDefaults.seed(mapper,UUID.fromString(tenant),null);
    }
  }
 /** 当前迁移已拥有的固定租户目录，不注册运行期Mapper。 */
 public interface Tenants {@org.apache.ibatis.annotations.Select("SELECT BIN_TO_UUID(id) FROM platform_tenant") java.util.List<String> all();}
}
