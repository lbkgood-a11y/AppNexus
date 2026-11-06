package com.appnexus.platform.persistence.identity;
import com.appnexus.platform.tenant.TenantAccess;
import java.util.*;
import org.springframework.stereotype.Repository;
/** 租户公开查询端口的 MyBatis 实现，不引用其他领域内部代码。 */
@Repository
public class MyBatisTenantAccess implements TenantAccess {
    private final TenantAccessMapper mapper;
    public MyBatisTenantAccess(TenantAccessMapper mapper) { this.mapper=mapper; }
    public boolean isActiveMember(UUID tenantId,UUID memberId) {
        return mapper.activeMember(tenantId.toString(),memberId.toString());
    }
    public List<Membership> available(UUID accountId) {
        return mapper.available(accountId.toString()).stream().map(row -> new Membership(
            UUID.fromString((String)row.get("tenant_id")),UUID.fromString((String)row.get("member_id")),(String)row.get("name"))).toList();
    }
}
