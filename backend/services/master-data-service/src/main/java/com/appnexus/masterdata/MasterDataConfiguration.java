package com.appnexus.masterdata;

import com.appnexus.platform.authorization.AccessAuthorization;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 主数据由组合应用显式装配；不依赖审批开关，不创建模拟档案或登录账号。 */
@Configuration(proxyBeanMethods=false)
@ComponentScan(basePackageClasses=MasterDataConfiguration.class)
public class MasterDataConfiguration {
  /** 同步订阅开通事件，复用发布方事务；本模块缺省装配时不建立跨模块数据库访问。 */
  @Bean public GeographyProvisioning geographyProvisioning(JdbcTemplate jdbc){return new GeographyProvisioning(jdbc);}

  /** 开通事务内写入当前版本的地理目录；普通查询无初始化副作用。 */
  public record GeographyProvisioning(JdbcTemplate jdbc) {
    /** @param event 可信平台开通事件，账号来自已授权开通执行器 */
    @org.springframework.context.event.EventListener
    public void provision(com.appnexus.platform.tenant.TenantProvisioned event){GeographyDefaults.seed(jdbc,event.tenantId(),event.actorId());}
  }
  /**
   * @return 复用宿主事务、可信租户和公开授权端口的主数据执行器 */
  @Bean public MasterDataService masterDataService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager,com.appnexus.platform.configuration.BusinessValueCatalog dictionaries){return new MasterDataService(jdbc,access,manager,dictionaries);}
}
