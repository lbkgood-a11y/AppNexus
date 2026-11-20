package com.appnexus.platform;

import com.appnexus.platform.persistence.configuration.DictionaryProvisioningMapper;
import com.appnexus.platform.persistence.configuration.MyBatisDictionaryProvisioning;
import java.util.Objects;
import java.util.UUID;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 隔离测试使用生产MyBatis字典预置实现；不复制历史SQL或更改正式开通准入。 */
final class DictionaryFixtures {
    private DictionaryFixtures(){}
    /** @param jdbc 隔离测试库客户端，只用于取得同一数据源 @param tenant 已建立的测试租户UUID @param actor 真实测试账号UUID，系统场景可空 */
    static void seed(JdbcTemplate jdbc,UUID tenant,UUID actor){
        try{
            var source=Objects.requireNonNull(jdbc.getDataSource());
            var config=new Configuration();config.setLocalCacheScope(LocalCacheScope.STATEMENT);config.setCacheEnabled(false);
            config.addMapper(DictionaryProvisioningMapper.class);
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);
            var mapper=new SqlSessionTemplate(factory.getObject()).getMapper(DictionaryProvisioningMapper.class);
            new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status->new MyBatisDictionaryProvisioning(mapper).seed(tenant,actor));
        }catch(Exception error){throw new IllegalStateException("Cannot seed production dictionary fixture",error);}
    }
}
