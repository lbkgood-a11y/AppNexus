package com.appnexus.platform.organization.personnel;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.organization.PersonnelDirectory;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
/** 组织与人员模块的人员子域装配；只读公开目录与身份端口不依赖客商、仓库或商品服务。 */
@Configuration(proxyBeanMethods=false)
@ComponentScan(basePackageClasses=PersonnelConfiguration.class)
public class PersonnelConfiguration {
 /** @return 人员基础档案服务，复用真实权限、租户及事务 */
 @Bean public PersonDataService personDataService(JdbcTemplate jdbc,AccessAuthorization access,PlatformTransactionManager manager){return new PersonDataService(jdbc,access,manager);}
 /** @return 人员明细及账号关联服务，和组织任职使用同一公开关系契约 */
 @Bean public PersonnelService personnelService(JdbcTemplate jdbc,AccessAuthorization access,PersonnelDirectory directory,PlatformTransactionManager manager){return new PersonnelService(jdbc,access,directory,manager);}
}
