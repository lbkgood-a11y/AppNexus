package com.appnexus.platform.persistence;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
@MapperScan("com.appnexus.platform.persistence")
public class PersistenceConfiguration {
    /**
     * 租户绑定发生在 SQL prepare 阶段，因此禁用跨语句/二级结果缓存和语句复用。
     * 避免相同业务参数的缓存或 PreparedStatement 越过新的租户绑定；批量执行另行设计。
     */
    @org.springframework.context.annotation.Bean
    org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer tenantSafeExecution() {
        return configuration -> {
            configuration.setCacheEnabled(false);
            configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
            configuration.setDefaultExecutorType(org.apache.ibatis.session.ExecutorType.SIMPLE);
        };
    }
}
