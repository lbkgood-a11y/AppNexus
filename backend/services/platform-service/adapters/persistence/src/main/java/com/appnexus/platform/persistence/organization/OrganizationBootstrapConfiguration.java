package com.appnexus.platform.persistence.organization;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;

/** 装配显式管理员配置；默认关闭，参数缺失或非法时阻止应用启动。 */
@Configuration(proxyBeanMethods = false)
public class OrganizationBootstrapConfiguration {
    /**
     * @param tenant 租户 UUID，默认空；必须与 member 成对配置，仅部署初始化时使用
     * @param member 现有有效成员 UUID，默认空；首次成功后应移除配置，重启不补回撤权
     * @return 启动阶段执行的一次性初始化任务
     */
    @Bean
    ApplicationRunner organizationAdministrator(OrganizationAdministratorBootstrap bootstrap,
            @Value("${app.organization.bootstrap.tenant-id:}") String tenant,
            @Value("${app.organization.bootstrap.member-id:}") String member) {
        return args -> {
            if (tenant.isBlank() && member.isBlank()) return;
            if (tenant.isBlank() || member.isBlank())
                throw new IllegalArgumentException("Organization bootstrap requires both tenant-id and member-id");
            bootstrap.initialize(UUID.fromString(tenant), UUID.fromString(member));
        };
    }
}
