from pathlib import Path
p=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/configuration/JdbcBusinessValueCatalog.java')
s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;','').replace('JdbcBusinessValueCatalog','MyBatisBusinessValueCatalog').replace('private final JdbcTemplate jdbc;','private final BusinessValueMapper mapper;').replace('@param jdbc 复用宿主事务与 UTC 数据源','@param mapper 固定字典引用 Mapper，复用宿主事务与 UTC 数据源').replace('public MyBatisBusinessValueCatalog(JdbcTemplate jdbc){this.jdbc=jdbc;}','public MyBatisBusinessValueCatalog(BusinessValueMapper mapper){this.mapper=Objects.requireNonNull(mapper);}')
start=s.index('jdbc.query("SELECT i.item_value');end=s.index('tenant.toString(),code)',start)+len('tenant.toString(),code)')
s=s[:start]+'''mapper.options(tenant.toString(),code).stream().map(row->new Option((String)row.get("value"),(String)row.get("name"),Boolean.TRUE.equals(row.get("enabled"))&&DictionaryValueStandard.validValue((String)row.get("value"))&&DictionaryValueStandard.validCode((String)row.get("code")))).toList()'''+s[end:]
start=s.index('jdbc.queryForList("SELECT BIN_TO_UUID(id) id,status FROM');end=s.index('tenant.toString(),code)',start)+len('tenant.toString(),code)')
s=s[:start]+'mapper.lockDictionary(tenant.toString(),code)'+s[end:]
start=s.index('jdbc.queryForList("SELECT BIN_TO_UUID(id) id,status,code');end=s.index('dictionary.get("id"),value)',start)+len('dictionary.get("id"),value)')
s=s[:start]+'mapper.items(tenant.toString(),(String)dictionary.get("id"),value)'+s[end:]
start=s.index('jdbc.update(');end=s.index(';',start)
s=s[:start]+'mapper.reference(tenant.toString(),(String)items.getFirst().get("id"),consumer,reference,actor==null?null:actor.toString())'+s[end:]
assert 'jdbc.' not in s
p.with_name('MyBatisBusinessValueCatalog.java').write_text(s,encoding='utf-8');p.unlink()
