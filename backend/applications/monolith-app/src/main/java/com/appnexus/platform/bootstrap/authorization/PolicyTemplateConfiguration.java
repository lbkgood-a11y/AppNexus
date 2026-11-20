package com.appnexus.platform.bootstrap.authorization;
import com.appnexus.platform.authorization.*;
import com.appnexus.platform.authorization.port.PolicyTemplateStorage;
import org.springframework.context.annotation.*;
/** 策略模板装配，不承担授权决策。 */
@Configuration(proxyBeanMethods=false)
public class PolicyTemplateConfiguration {
 /** @param roles 正式授权保存 @param catalog 当前目录 @param storage 固定存储 @return 模板公开用例 */
 @Bean public PolicyTemplates policyTemplates(RolePermissionManagement roles,DataAccessCatalog catalog,PolicyTemplateStorage storage){return PolicyTemplateUseCases.create(roles,catalog,storage);}
}
