package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** MySQL 数据策略元数据目录；定义由迁移受控发布，不提供运行时写接口。 */
@Service
public final class MyBatisDataPolicyMetadataCatalog implements DataPolicyMetadataCatalog {
    private final PermissionMapper mapper;
    public MyBatisDataPolicyMetadataCatalog(PermissionMapper mapper){this.mapper=mapper;}
    @Override public List<ScopeSupport> list(PermissionCatalog.ScopeType scopeType){
        Objects.requireNonNull(scopeType,"scopeType");
        return mapper.dataPolicyMetadata(scopeType.name()).stream().filter(row ->
            !"organization.unit".equals(row.get("resource_type")) ||
            (!"SELF".equals(row.get("scope_type")) &&
             (!"organization.unit:create".equals(row.get("code")) || "ALL".equals(row.get("scope_type")))))
            .map(row->new ScopeSupport(
            UUID.fromString((String)row.get("permission_id")),(String)row.get("code"),
            DataScopeAuthorization.ScopeType.valueOf((String)row.get("scope_type")),
            (String)row.get("resource_type"))).toList();
    }
}
