from pathlib import Path
for rel in ['backend/services/master-data-service/src/test/java/com/appnexus/masterdata/product/ProductCatalogMySqlTest.java','backend/services/leave-poc/src/test/java/com/appnexus/leave/LeavePocTest.java','backend/services/leave-poc/src/test/java/com/appnexus/leave/BusinessObjectIntegrationTest.java','backend/applications/monolith-app/src/test/java/com/appnexus/platform/businessobject/CodingMySqlTest.java']:
 p=Path(rel);s=p.read_text(encoding='utf-8-sig')
 for name in ['mapperConfig','configuration','config']:
  original='var '+name+'=new org.apache.ibatis.session.Configuration();'
  if original in s:s=s.replace(original,original+'\n      '+name+'.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);\n      '+name+'.setCacheEnabled(false);')
  original='var '+name+' = new org.apache.ibatis.session.Configuration();'
  if original in s:s=s.replace(original,original+'\n      '+name+'.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);\n      '+name+'.setCacheEnabled(false);')
 p.write_text(s.rstrip()+'\n',encoding='utf-8')
