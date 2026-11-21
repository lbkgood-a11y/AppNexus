package com.appnexus.platform.persistence.organization.personnel;
import com.appnexus.platform.organization.*;
import com.appnexus.platform.organization.port.personnel.*;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.authorization.FieldProtection;
import com.appnexus.platform.authorization.DataAccessCatalog;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import org.springframework.context.annotation.*;
/** 人员用例装配；通过公开工厂连接端口，禁止在此承担业务规则。 */
@Configuration(proxyBeanMethods=false)
@ComponentScan(basePackages={"com.appnexus.platform.persistence.organization.personnel","com.appnexus.platform.appapi.organization"})
public class PersonnelConfiguration {
 /** @return 真实权限及事务保护的人员基础公开用例 */
 @Bean public PersonManagement personDataService(PersonRepository records,AccessAuthorization access,PersonnelTransaction transaction,BusinessValueCatalog dictionaries,FieldProtection protection,DataAccessCatalog catalog){return PersonnelUseCases.persons(records,access,transaction,dictionaries,protection,catalog);}
 /** @return 人员明细及账号关联公开用例 */
 @Bean public PersonnelManagement personnelService(AccessAuthorization access,PersonnelDirectory directory,PersonnelTransaction transaction,BusinessValueCatalog dictionaries,PersonnelRepository records,PersonnelContent content,PersonnelExecution identities,FieldProtection protection,DataAccessCatalog catalog){return PersonnelUseCases.personnel(access,directory,transaction,dictionaries,records,content,identities,protection,catalog);}
}
