from pathlib import Path
p=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/configuration/StandardDictionaryDefaults.java');s=p.read_text(encoding='utf-8')
start=s.index('    /**\n     * 新租户开通同事务补预置');s=s[:start]+'}\n';s=s.replace('import org.springframework.jdbc.core.JdbcTemplate;\n','');p.write_text(s,encoding='utf-8')
for p in Path('backend/applications/monolith-app/src/test/java').rglob('*.java'):
 s=p.read_text(encoding='utf-8-sig');original=s;s=s.replace('com.appnexus.platform.persistence.configuration.StandardDictionaryDefaults.seed(','DictionaryFixtures.seed(').replace('StandardDictionaryDefaults.seed(','DictionaryFixtures.seed(')
 if s!=original:p.write_text(s,encoding='utf-8')
