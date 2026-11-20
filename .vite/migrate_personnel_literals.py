from pathlib import Path
import re
source=Path('.vite/extract_master_literals.py').read_text(encoding='utf-8')
source=source.replace("root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product')","root=Path('backend/services/platform-service/organization/src/main/java/com/appnexus/platform/organization/personnel')")
source=re.sub(r"for name in \['ProductV1Service'.*?\]:", "for name in ['PersonnelService','PersonnelExecutionAdapter']:",source)
source=source.replace("out=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc')/(mapper+'.java')","out=root/(mapper+'.java')").replace("'package com.appnexus.masterdata.product;'","'package com.appnexus.platform.organization.personnel;'").replace("'package com.appnexus.masterdata.product;\\nimport com.appnexus.masterdata.adapter.jdbc.'","'package com.appnexus.platform.organization.personnel;\\nimport com.appnexus.platform.organization.personnel.'").replace('package com.appnexus.masterdata.adapter.jdbc;','package com.appnexus.platform.organization.personnel;')
exec(source)
p=Path('backend/services/platform-service/organization/pom.xml');s=p.read_text(encoding='utf-8').replace('<dependencies>','''<dependencies>
<!-- 人员固定Mapper使用中立SQL范围元数据，不依赖平台存储实现。 -->
<dependency><groupId>com.appnexus</groupId><artifactId>mybatis-scope</artifactId><version>${project.version}</version></dependency>
<dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>${mybatis-spring-boot.version}</version></dependency>''',1);p.write_text(s,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/main/java/com/appnexus/platform/PlatformApplication.java');s=p.read_text(encoding='utf-8').replace('"com.appnexus.workflow.adapter.flowable"}','"com.appnexus.workflow.adapter.flowable","com.appnexus.platform.organization.personnel"}');p.write_text(s,encoding='utf-8')
