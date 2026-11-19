package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** MySQL 数据策略元数据目录；代码声明为权威来源，数据库只读投影和既有外键约束共同校验，不提供管理员写目录接口。 */
@Service
public final class MyBatisDataPolicyMetadataCatalog implements DataPolicyMetadataCatalog {
    private final PermissionMapper mapper;
    private final DataAccessCatalog catalog;
    public MyBatisDataPolicyMetadataCatalog(PermissionMapper mapper,DataAccessCatalog catalog){this.mapper=mapper;this.catalog=catalog;}
    @Override public List<ScopeSupport> list(PermissionCatalog.ScopeType scopeType){
        Objects.requireNonNull(scopeType,"scopeType");
        return mapper.dataPolicyMetadata(scopeType.name()).stream().filter(row ->
            !"organization.unit".equals(row.get("resource_type")) ||
            (!"SELF".equals(row.get("scope_type")) &&
             (!"organization.unit:create".equals(row.get("code")) || "ALL".equals(row.get("scope_type")))))
            .filter(row->catalog.supports((String)row.get("code"),(String)row.get("resource_type"),DataScopeAuthorization.ScopeType.valueOf((String)row.get("scope_type"))))
            .map(row->new ScopeSupport(
            UUID.fromString((String)row.get("permission_id")),(String)row.get("code"),
            DataScopeAuthorization.ScopeType.valueOf((String)row.get("scope_type")),
            (String)row.get("resource_type"))).toList();
    }
}
