from pathlib import Path
p=Path('backend/services/platform-service/adapters/persistence/src/main/java/com/appnexus/platform/persistence/businessobject/BusinessObjectPersistence.java')
s=p.read_text(encoding='utf-8').replace('public static BusinessObjectManagement create(JdbcTemplate jdbc,','public static BusinessObjectManagement create(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper mapper,').replace('return create(jdbc,manager,platform,workflows,providers,()->null);','return create(mapper,manager,platform,workflows,providers,()->null);').replace('configuration.JdbcConfigurationStore(jdbc)','configuration.MyBatisConfigurationStore(mapper)').replace('对象能力的 JDBC 装配入口','对象能力的持久化装配入口').replace('@param jdbc 对象配置数据源客户端，必须与 manager 共用数据源','@param mapper 对象配置固定 Mapper，必须与 manager 共用数据源').replace('@param jdbc 与事务共用数据源的固定 JDBC 客户端','@param mapper 与事务共用 SqlSession 的配置 Mapper')
p.write_text(s,encoding='utf-8')
p=Path('backend/applications/monolith-app/src/main/java/com/appnexus/platform/bootstrap/businessobject/BusinessObjectConfiguration.java')
s=p.read_text(encoding='utf-8').replace('businessObjectService(JdbcTemplate jdbc,','businessObjectService(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper mapper,').replace('BusinessObjectPersistence.create(jdbc,','BusinessObjectPersistence.create(mapper,')
p.write_text(s,encoding='utf-8')
p=Path('backend/architecture-tests/src/test/java/com/appnexus/platform/ServiceLayerBoundaryTest.java')
s=p.read_text(encoding='utf-8').replace('configuration.JdbcConfigurationStore','configuration.MyBatisConfigurationStore')
p.write_text(s,encoding='utf-8')
for name in ['LeavePocTest','BusinessObjectIntegrationTest']:
 p=Path('backend/services/leave-poc/src/test/java/com/appnexus/leave/'+name+'.java')
 s=p.read_text(encoding='utf-8').replace('BusinessObjectPersistence.create(jdbc,','BusinessObjectPersistence.create(configurationMapper(),')
 helper='''
  /** 隔离测试使用真实 MyBatis/Spring 事务；不使用直连 SqlSession 造成独立提交。 */
  private static com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper configurationMapper() {
    try {
      var factory = new org.mybatis.spring.SqlSessionFactoryBean();
      factory.setDataSource(java.util.Objects.requireNonNull(jdbc.getDataSource()));
      var configuration = new org.apache.ibatis.session.Configuration();
      configuration.setCallSettersOnNulls(true);
      configuration.addMapper(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper.class);
      factory.setConfiguration(configuration);
      return new org.mybatis.spring.SqlSessionTemplate(factory.getObject())
          .getMapper(com.appnexus.platform.persistence.businessobject.configuration.ConfigurationMapper.class);
    } catch (Exception error) { throw new IllegalStateException("Cannot assemble configuration mapper", error); }
  }
'''
 s=s.rstrip()[:-1]+helper+'}\n'
 p.write_text(s,encoding='utf-8')
