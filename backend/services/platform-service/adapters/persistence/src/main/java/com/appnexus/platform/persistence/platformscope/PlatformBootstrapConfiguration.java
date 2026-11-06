package com.appnexus.platform.persistence.platformscope;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;

/** 平台管理身份只能由部署显式指定；默认关闭，执行顺序晚于认证账号初始化。 */
@Configuration(proxyBeanMethods=false)
public class PlatformBootstrapConfiguration {
    /**
     * @param username 现有账号登录名，默认空表示关闭；首次成功后移除配置，不恢复后续撤权
     * @return 一次性平台身份初始化任务，失败阻止应用启动
     */
    @Bean
    @Order(100)
    ApplicationRunner platformAdministrator(MyBatisPlatformAdministration service,
            @Value("${app.platform.bootstrap.username:}") String username) {
        return args -> { if(!username.isBlank()) service.initializeAdministrator(username); };
    }
}
