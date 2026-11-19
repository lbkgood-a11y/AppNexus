package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import java.util.List;
import org.springframework.context.annotation.*;

/** 统一声明目录装配；业务模块只依赖公开契约，不依赖此安全适配层。 */
@Configuration(proxyBeanMethods=false)
public class DataAccessConfiguration {
    /** 当前构建的代码声明聚合为唯一只读目录。 */
    @Bean public DataAccessCatalog dataAccessCatalog(List<com.appnexus.contracts.businessobject.BusinessObjects.Provider> objects){return DataAccessCatalog.fromObjects(objects);}
}
