package com.appnexus.platform.persistence;

import org.apache.ibatis.session.LocalCacheScope;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis 的统一事务读取约束；迁移期同事务 JDBC 写入不能被会话缓存掩盖，权限查询不使用二级缓存。 */
@Configuration(proxyBeanMethods=false)
public class MyBatisExecutionConfiguration {
    /**
     * 每条语句重新读取当前事务可见数据，保持 JDBC/MyBatis 混合写入、状态校验及锁查询一致。
     * @return 配置定制器；STATEMENT 不改变数据库事务隔离级别，不提供跨请求授权缓存
     */
    @Bean public ConfigurationCustomizer authoritativeMapperReads(){
        return configuration->{
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            configuration.setCacheEnabled(false);
            // Map响应必须保留已选择但为NULL的字段，保持原JDBC响应契约；字段隐藏由FieldProtection独立执行。
            configuration.setCallSettersOnNulls(true);
        };
    }
}
