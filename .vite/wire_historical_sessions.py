from pathlib import Path
root=Path('backend/services/platform-service/adapters/persistence/src/main/java/db/migration')
for name in ['V89__business_value_defaults','V91__warehouse_value_defaults']:
 p=root/(name+'.java');s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('import org.springframework.jdbc.datasource.SingleConnectionDataSource;','')
 start=s.index(' @Override public void migrate(')
 body='BusinessValueDefaults.seed(mapper,id,null);BusinessValueDefaults.legacy(mapper,id);' if name.startswith('V89') else 'WarehouseValueDefaults.seed(mapper,id,null);'
 s=s[:start]+''' @Override public void migrate(Context context){try(var session=new com.appnexus.persistence.mybatis.MigrationSession(context.getConnection(),com.appnexus.platform.persistence.configuration.HistoricalDictionaryMapper.class)){var mapper=session.mapper(com.appnexus.platform.persistence.configuration.HistoricalDictionaryMapper.class);for(String tenant:mapper.tenants()){var id=UUID.fromString(tenant);'''+body+'''}}}
}
''';p.write_text(s,encoding='utf-8')
p=root/'V102__dictionary_value_standard.java';s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('import org.springframework.jdbc.datasource.SingleConnectionDataSource;','')
start=s.index('            var jdbc =');end=s.index('            if (ownTransaction) connection.commit();',start)
s=s[:start]+'''            try(var session=new com.appnexus.persistence.mybatis.MigrationSession(connection,com.appnexus.platform.persistence.configuration.HistoricalDictionaryMapper.class)){
                var mapper=session.mapper(com.appnexus.platform.persistence.configuration.HistoricalDictionaryMapper.class);
                for(String tenant:mapper.tenants())DictionaryStandardMigration.migrateTenant(mapper,UUID.fromString(tenant));
            }
'''+s[end:];p.write_text(s,encoding='utf-8')
p=Path('backend/services/master-data-service/src/main/java/db/migration/V96__china_administrative_divisions.java');s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('import org.springframework.jdbc.datasource.SingleConnectionDataSource;','');start=s.index('    var jdbc=');end=s.index('\n  }',start)
s=s[:start]+'''    try(var session=new com.appnexus.persistence.mybatis.MigrationSession(context.getConnection(),com.appnexus.masterdata.GeographyMapper.class,Tenants.class)){
      var mapper=session.mapper(com.appnexus.masterdata.GeographyMapper.class);
      for(String tenant:session.mapper(Tenants.class).all())GeographyDefaults.seed(mapper,UUID.fromString(tenant),null);
    }'''+s[end:]
s=s.rstrip()[:-1]+''' /** 当前迁移已拥有的固定租户目录，不注册运行期Mapper。 */
 public interface Tenants {@org.apache.ibatis.annotations.Select("SELECT BIN_TO_UUID(id) FROM platform_tenant") java.util.List<String> all();}
}\n''';p.write_text(s,encoding='utf-8')
