package com.appnexus.platform.organization.internal.application.personnel;

import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.DataAccessCatalog;
import com.appnexus.platform.authorization.FieldProtection;
import com.appnexus.platform.authorization.FieldSecurity;
import com.appnexus.platform.authorization.PermissionCatalog.ScopeType;
import com.appnexus.platform.organization.PersonData;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.*;

/** 人员字段策略执行；策略、字段目录和遮盖算法均来自统一授权模块。 */
final class PersonFieldPolicy {
    static final String PERSON = "masterdata.person";
    static final String DETAIL = "masterdata.person.detail";
    private final FieldProtection protection;
    private final List<FieldSecurity.Descriptor> fields;

    PersonFieldPolicy(FieldProtection protection, DataAccessCatalog catalog) {
        this.protection = protection;
        this.fields = catalog == null ? List.of() : catalog.fields(ScopeType.TENANT);
    }

    Map<String,FieldSecurity.Mode> modes(VerifiedTenantContext context,String resource) {
        return protection == null ? Map.of() : protection.restrictions(
                Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),resource);
    }

    Map<String,String> project(String resource,Map<String,String> original,Map<String,FieldSecurity.Mode> modes) {
        var result = new LinkedHashMap<>(original);
        for(var field : fields) {
            if(!resource.equals(field.resource()) || !result.containsKey(field.field()))continue;
            var mode = modes.get(field.field());
            if(mode==null)continue;
            if(!field.modes().contains(mode))throw new SecurityException("DATA_FIELD_MODE_UNSUPPORTED");
            if(mode==FieldSecurity.Mode.HIDDEN)result.remove(field.field());
            else result.put(field.field(),FieldSecurity.display(field.kind(),result.get(field.field()),mode));
        }
        return Collections.unmodifiableMap(result);
    }

    /** 即使提交的受限值与原值相同也拒绝，以免通过成功与失败猜测原文。 */
    Map<String,String> preserve(Map<String,String> supplied,Map<String,String> old,Map<String,FieldSecurity.Mode> modes) {
        var result = supplied==null ? new LinkedHashMap<String,String>() : new LinkedHashMap<>(supplied);
        for(var field : modes.keySet()) {
            if(result.containsKey(field))throw denied();
            if(old.containsKey(field))result.put(field,old.get(field));
        }
        return result;
    }

    PersonData.Row project(PersonData.Row row,Map<String,FieldSecurity.Mode> modes) {
        return new PersonData.Row(row.id(),row.code(),row.name(),project(PERSON,row.attributes(),modes),
                row.enabled(),row.version(),row.audit(),modes);
    }

    static PersonData.Rejection denied(){return new PersonData.Rejection(403,"DATA_FIELD_WRITE_DENIED");}
}
