package com.appnexus.platform.persistence.organization;
import com.appnexus.platform.organization.*;
import com.appnexus.platform.persistence.TenantSqlInterceptor;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.*;

/** 组织单元仓储适配器；租户 UUID 只取自请求级服务端上下文。 */
@Repository
public class MyBatisOrganizationUnitRepository implements OrganizationUnitRepository {
    private final OrganizationUnitMapper mapper;
    private final OrganizationAuthorizationMapper grants;
    private final OrganizationStructure structure;
    public MyBatisOrganizationUnitRepository(OrganizationUnitMapper mapper,OrganizationAuthorizationMapper grants,OrganizationStructure structure){this.mapper=mapper;this.grants=grants;this.structure=structure;}
    public List<OrganizationUnit> findAll(){
        byte[] tenant=tenantBytes();
        return mapper.findAll(tenant).stream()
            .map(r -> new OrganizationUnit(UUID.fromString((String)r.get("id")),(String)r.get("name"))).toList();
    }
    public void create(OrganizationUnit unit){if(structure.types().stream().noneMatch(type->type.code().equals("BUSINESS")&&type.enabled()))structure.createType("BUSINESS","业务组织");if(mapper.create(tenantBytes(),unit.id().toString(),unit.name())!=1)throw new IllegalStateException("Default organization type unavailable");}
    public boolean rename(UUID id,String name){return mapper.rename(name,tenantBytes(),id.toString())==1;}
    /** 与成员归属写入共用租户锁；存在归属时先解除，禁止留下悬空关联。 */
    @org.springframework.transaction.annotation.Transactional
    public boolean delete(UUID id){
        byte[] bytes=tenantBytes();var buffer=java.nio.ByteBuffer.wrap(bytes);
        String tenant=new UUID(buffer.getLong(),buffer.getLong()).toString();
        grants.lockTenant(tenant);
        if(grants.organizationAssigned(tenant,id.toString()))throw new IllegalStateException("Organization has members");
        return mapper.delete(bytes,id.toString())==1;
    }
    private byte[] tenantBytes(){
        var attrs=RequestContextHolder.getRequestAttributes();
        if(!(attrs instanceof ServletRequestAttributes servlet) ||
            !(servlet.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext context))
            throw new IllegalStateException("Verified tenant context required");
        return java.nio.ByteBuffer.allocate(16).putLong(context.tenantId().getMostSignificantBits())
            .putLong(context.tenantId().getLeastSignificantBits()).array();
    }
}
