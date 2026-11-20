from pathlib import Path
p=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/businessobject/BusinessObjectPersistence.java')
s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('coding(JdbcTemplate jdbc,','coding(com.appnexus.platform.persistence.businessobject.coding.CodingMapper mapper,').replace('coding.JdbcCodingStore(jdbc,manager)','coding.MyBatisCodingStore(mapper,manager)').replace('@param jdbc 与事务共用数据源的 JDBC 客户端','@param mapper 与事务共用数据源的固定编码 Mapper')
p.write_text(s,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/main/java/com/appnexus/platform/bootstrap/businessobject/BusinessObjectConfiguration.java')
s=p.read_text(encoding='utf-8').replace('import org.springframework.jdbc.core.JdbcTemplate;\n','').replace('codingService(JdbcTemplate jdbc,','codingService(com.appnexus.platform.persistence.businessobject.coding.CodingMapper mapper,').replace('BusinessObjectPersistence.coding(jdbc,','BusinessObjectPersistence.coding(mapper,')
p.write_text(s,encoding='utf-8')
p=Path('backend/architecture-tests/src/test/java/com/appnexus/platform/ServiceLayerBoundaryTest.java')
s=p.read_text(encoding='utf-8').replace('coding.JdbcCodingStore','coding.MyBatisCodingStore');p.write_text(s,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/test/java/com/appnexus/platform/businessobject/CodingMySqlTest.java')
s=p.read_text(encoding='utf-8').replace('BusinessObjectPersistence.coding(jdbc,','BusinessObjectPersistence.coding(codingMapper(),')
helper='''
  /** 隔离测试使用生产 Mapper 和 Spring 事务连接，流水不得独立提交。 */
  private com.appnexus.platform.persistence.businessobject.coding.CodingMapper codingMapper() {
    try {
      var factory=new org.mybatis.spring.SqlSessionFactoryBean();
      factory.setDataSource(java.util.Objects.requireNonNull(jdbc.getDataSource()));
      var config=new org.apache.ibatis.session.Configuration();
      config.setCallSettersOnNulls(true);
      config.addMapper(com.appnexus.platform.persistence.businessobject.coding.CodingMapper.class);
      factory.setConfiguration(config);
      return new org.mybatis.spring.SqlSessionTemplate(factory.getObject()).getMapper(com.appnexus.platform.persistence.businessobject.coding.CodingMapper.class);
    } catch(Exception error){throw new IllegalStateException("Cannot assemble coding mapper",error);}
  }
'''
s=s.rstrip()[:-1]+helper+'}\n';p.write_text(s,encoding='utf-8')
