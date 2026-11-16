package com.appnexus.masterdata;

import com.appnexus.platform.authorization.AccessAuthorization;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 主数据由组合应用显式装配；不依赖审批开关，不创建模拟档案或登录账号。 */
@Configuration(proxyBeanMethods=false)
@ComponentScan(basePackageClasses=MasterDataConfiguration.class)
public class MasterDataConfiguration {
  /**
   * @return 复用宿主事务、可信租户和公开授权端口的主数据执行器 */
  @Bean public MasterDataService masterDataService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager){return new MasterDataService(jdbc,access,manager);}
}
