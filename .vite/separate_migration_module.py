from pathlib import Path
p=Path('backend/building-blocks/mybatis-scope/src/main/java/com/appnexus/persistence/mybatis/MigrationSession.java');s=p.read_text(encoding='utf-8').replace('package com.appnexus.persistence.mybatis;','package com.appnexus.persistence.migration;');out=Path('backend/building-blocks/mybatis-migration/src/main/java/com/appnexus/persistence/migration/MigrationSession.java');out.write_text(s,encoding='utf-8');p.unlink()
p=Path('backend/building-blocks/mybatis-scope/pom.xml');s=p.read_text(encoding='utf-8');start=s.index('  <!-- 历史迁移会话');s=s[:start]+'</project>\n';p.write_text(s,encoding='utf-8')
p=Path('backend/building-blocks/pom.xml');s=p.read_text(encoding='utf-8').replace('</modules>','<module>mybatis-migration</module></modules>');p.write_text(s,encoding='utf-8')
for rel in ['backend/services/platform-service/adapters/persistence/pom.xml','backend/services/master-data-service/pom.xml']:
 p=Path(rel);s=p.read_text(encoding='utf-8').replace('<dependencies>','''<dependencies>
    <!-- 历史Java迁移复用Flyway连接，不改变事务所有权或迁移资源。 -->
    <dependency><groupId>com.appnexus</groupId><artifactId>mybatis-migration</artifactId><version>${project.version}</version></dependency>''',1);p.write_text(s,encoding='utf-8')
for p in Path('backend/services').rglob('*.java'):
 if any(x.startswith('target') for x in p.parts):continue
 s=p.read_text(encoding='utf-8-sig')
 if 'com.appnexus.persistence.mybatis.MigrationSession' in s:p.write_text(s.replace('com.appnexus.persistence.mybatis.MigrationSession','com.appnexus.persistence.migration.MigrationSession'),encoding='utf-8')
