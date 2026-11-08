package com.appnexus.platform.persistence.platformscope;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;

/** 首个登录账号同步获得平台管理身份；默认关闭，执行顺序晚于认证账号初始化。 */
@Configuration(proxyBeanMethods=false)
public class PlatformBootstrapConfiguration {
    /**
     * @param username 首个登录账号名，默认空表示关闭；与账号初始化共用配置，避免两个用户名不一致
     * @return 一次性平台身份初始化任务，失败阻止应用启动
     */
    @Bean
    @Order(100)
    ApplicationRunner platformAdministrator(MyBatisPlatformAdministration service,
            @Value("${app.bootstrap.username:}") String username) {
        return args -> { if(!username.isBlank()) service.initializeAdministrator(username); };
    }
}
