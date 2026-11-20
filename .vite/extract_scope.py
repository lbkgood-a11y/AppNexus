from pathlib import Path
root=Path('backend');old=root/'services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence';new=root/'building-blocks/mybatis-scope/src/main/java/com/appnexus/persistence/mybatis'
for name in ['PlatformScoped','TenantScoped','TenantAggregateScoped']:
 p=old/(name+'.java');s=p.read_text(encoding='utf-8').replace('package com.appnexus.platform.persistence;','package com.appnexus.persistence.mybatis;');(new/p.name).write_text(s,encoding='utf-8');p.unlink()
for p in root.rglob('*.java'):
 if any(part.startswith('target') for part in p.parts):continue
 s=p.read_text(encoding='utf-8-sig');before=s
 for name in ['PlatformScoped','TenantScoped','TenantAggregateScoped']:s=s.replace('com.appnexus.platform.persistence.'+name,'com.appnexus.persistence.mybatis.'+name)
 if p.name=='TenantSqlInterceptor.java':s=s.replace('import com.appnexus.platform.tenant.VerifiedTenantContext;','import com.appnexus.platform.tenant.VerifiedTenantContext;\nimport com.appnexus.persistence.mybatis.*;')
 if s!=before:p.write_text(s,encoding='utf-8')
p=root/'building-blocks/pom.xml';s=p.read_text(encoding='utf-8').replace('</project>','<modules><module>mybatis-scope</module></modules></project>');p.write_text(s,encoding='utf-8')
for rel in ['services/platform-service/adapters/persistence/pom.xml','services/master-data-service/pom.xml','services/print-service/adapters/jdbc/pom.xml','services/workflow-service/adapters/flowable/pom.xml','services/leave-poc/pom.xml']:
 p=root/rel;s=p.read_text(encoding='utf-8');s=s.replace('<dependencies>','''<dependencies>
    <!-- 固定Mapper的中立范围元数据，不引入平台持久化或其他业务实现。 -->
    <dependency><groupId>com.appnexus</groupId><artifactId>mybatis-scope</artifactId><version>${project.version}</version></dependency>''',1)
 if rel!='services/platform-service/adapters/persistence/pom.xml':
  s=s.replace('<dependencies>','''<dependencies>
    <!-- 沿用全仓MyBatis版本；存储通过宿主SqlSession和Spring事务执行。 -->
    <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${mybatis-spring-boot.version}</version></dependency>''',1)
 p.write_text(s,encoding='utf-8')
