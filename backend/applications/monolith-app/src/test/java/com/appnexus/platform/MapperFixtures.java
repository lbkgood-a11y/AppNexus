package com.appnexus.platform;

import java.util.Objects;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 组合测试的固定 Mapper 装配；沿用传入 JDBC 客户端的数据源及 Spring 事务连接。 */
final class MapperFixtures {
  private MapperFixtures() {}

  /**
   * 创建生产 Mapper 的测试代理，不提供任意 SQL 或生产兼容构造入口。
   * @param jdbc 隔离测试数据库客户端，仅用于取得同一数据源
   * @param type 构建内固定 Mapper 接口
   * @param <T> Mapper 接口类型
   * @return 参与原 Spring 事务的 Mapper
   * @throws IllegalStateException Mapper 元数据或会话工厂无法初始化
   */
  static <T> T mapper(JdbcTemplate jdbc, Class<T> type) {
    try {
      var config = new Configuration();
      config.setLocalCacheScope(LocalCacheScope.STATEMENT);
      config.setCacheEnabled(false);
      config.setDefaultScriptingLanguage(com.appnexus.platform.persistence.ScopedQueryLanguageDriver.class);
      config.setCallSettersOnNulls(true);
      config.addMapper(type);
      var factory = new SqlSessionFactoryBean();
      factory.setDataSource(Objects.requireNonNull(jdbc.getDataSource()));
      factory.setConfiguration(config);
      return new SqlSessionTemplate(factory.getObject()).getMapper(type);
    } catch (Exception failure) {
      throw new IllegalStateException("TEST_MAPPER_ASSEMBLY_FAILED", failure);
    }
  }
}
