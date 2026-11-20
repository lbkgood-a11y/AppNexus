package db.migration;
import org.flywaydb.core.api.migration.*;


import com.appnexus.platform.persistence.configuration.WarehouseValueDefaults;
import java.util.UUID;
/** 为已有租户补仓库业务字典，不改变V89资源及校验和，不伪造迁移账号。 */
public final class V91__warehouse_value_defaults extends BaseJavaMigration {
 @Override public Integer getChecksum(){var crc=new java.util.zip.CRC32();crc.update(WarehouseValueDefaults.resource());return (int)crc.getValue();}
 @Override public void migrate(Context context){try(var session=new com.appnexus.persistence.migration.MigrationSession(context.getConnection(),com.appnexus.platform.persistence.configuration.HistoricalDictionaryMapper.class)){var mapper=session.mapper(com.appnexus.platform.persistence.configuration.HistoricalDictionaryMapper.class);for(String tenant:mapper.tenants()){var id=UUID.fromString(tenant);WarehouseValueDefaults.seed(mapper,id,null);}}}
}
