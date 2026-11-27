package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.platform.identity.MultiFactorAuthentication;
import com.appnexus.platform.identity.PasswordRecovery;
import com.appnexus.platform.tenant.PlatformAdministration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 将平台管理运行端口连接到原对象声明和真实用例。 */
@Configuration(proxyBeanMethods=false)
public class PlatformDirectoryCommandConfiguration {
    /** @return 账号六项平台命令的固定运行端口 */
    @Bean
    public BusinessObjectCommandRuntime accountPlatformCommands(PlatformAdministration service,PasswordEncoder encoder,
                                                                 MultiFactorAuthentication mfa,PasswordRecovery recovery){
        return new PlatformDirectoryCommandRuntime("Account",service,encoder,mfa,recovery);
    }

    /** @return 租户成员四项平台代管命令的固定运行端口 */
    @Bean
    public BusinessObjectCommandRuntime managedMemberPlatformCommands(PlatformAdministration service,PasswordEncoder encoder,
                                                                       MultiFactorAuthentication mfa,PasswordRecovery recovery){
        return new PlatformDirectoryCommandRuntime("TenantMember",service,encoder,mfa,recovery);
    }
}
