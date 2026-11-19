package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** 字段目录只从统一业务对象声明派生，策略保存与响应投影共用相同字段能力。 */
@Component
public final class DeclaredFieldCatalog implements FieldCatalog {
    private final DataAccessCatalog catalog;
    public DeclaredFieldCatalog(DataAccessCatalog catalog){this.catalog=catalog;}
    @Override public List<FieldSecurity.Descriptor> list(PermissionCatalog.ScopeType scope){return catalog.fields(scope);}
}
