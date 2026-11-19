package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 本租户数据归属候选；只接受已登记且当前有权的业务操作，不接受任意目录绕行。 */
@Service
public final class MyBatisDataAccessReferences implements DataAccessReferences {
    private final DataScopeMapper mapper;private final AccessAuthorization access;private final DataAccessCatalog catalog;
    public MyBatisDataAccessReferences(DataScopeMapper mapper,AccessAuthorization access,DataAccessCatalog catalog){this.mapper=mapper;this.access=access;this.catalog=catalog;}
    @Override public List<Organization> organizations(AccessAuthorization.Subject subject,String permissionCode){
        if(subject==null||subject.tenantId()==null||permissionCode==null||!permissionCode.contains(":")||(!"access.tenant.role:grant".equals(permissionCode)&&!catalog.declared(permissionCode,permissionCode.substring(0,permissionCode.indexOf(':'))))||!access.hasPermission(subject,permissionCode))throw new SecurityException("DATA_ACCESS_REFERENCE_DENIED");
        return mapper.organizationOptions(subject.tenantId().toString()).stream().map(row->new Organization(UUID.fromString((String)row.get("id")),(String)row.get("name"))).toList();
    }
}
