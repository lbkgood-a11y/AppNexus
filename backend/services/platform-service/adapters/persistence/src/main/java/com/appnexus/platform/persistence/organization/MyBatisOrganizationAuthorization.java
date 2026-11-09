package com.appnexus.platform.persistence.organization;
import com.appnexus.platform.authorization.*;
import java.util.UUID;
import org.springframework.stereotype.Repository;
/** 授权判定适配器；任何数据访问异常向上失败关闭，不自动授权。 */
@Repository
public class MyBatisOrganizationAuthorization implements OrganizationAuthorization {
    private final OrganizationAuthorizationMapper mapper;
    public MyBatisOrganizationAuthorization(OrganizationAuthorizationMapper mapper){this.mapper=mapper;}
    public boolean allows(UUID tenantId,UUID memberId,OrganizationCapability capability){
        String tenant=tenantId.toString(),member=memberId.toString(),action=capability.name();
        return mapper.rbacExists(tenant,member,action)||mapper.exists(tenant,member,action)||mapper.legacyCapabilityExists(tenant,member,action);
    }
}
