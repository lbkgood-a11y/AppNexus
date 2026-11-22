package com.appnexus.platform.persistence.organization;

import com.appnexus.platform.organization.OrganizationSummary;
import com.appnexus.platform.authorization.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 固定 B 级联表和 C 级分组安全样例；两表显式租户谓词，聚合前应用 AccessPlan。 */
@Service
public class ScopedOrganizationSummary implements OrganizationSummary {
    private final OrganizationSummaryMapper mapper;
    private final AccessPlanning planning;
    private final RowAccessControl rows;
    /** @param mapper 固定联表 Mapper，与只读事务共用数据源 @param planning 每次读取的权威权限计划 */
    public ScopedOrganizationSummary(OrganizationSummaryMapper mapper,AccessPlanning planning,RowAccessControl rows){this.mapper=mapper;this.planning=planning;this.rows=rows;}
    @Transactional(readOnly=true)
    public List<Group> summarize(AccessAuthorization.Subject subject){
        // Mapper 的租户参数由请求重新绑定；必须先确保权限计划主体与该可信请求相同，避免以 A 的 ALL 读取 B。
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes request)
            || !(request.getRequest().getAttribute(com.appnexus.platform.tenant.VerifiedTenantContext.class.getName()) instanceof com.appnexus.platform.tenant.VerifiedTenantContext context)
            || subject==null||!context.accountId().equals(subject.accountId())||!context.tenantId().equals(subject.tenantId())||!context.memberId().equals(subject.memberId()))
            throw new SecurityException("ORGANIZATION_SUMMARY_SUBJECT_MISMATCH");
        var verified=new AccessAuthorization.Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        var range=rows.organizationRange(planning.plan(verified,"organization.unit:read","organization.unit"),"organizationId");
        if(!range.all()&&range.ids().isEmpty())return List.of();
        var tenant=java.nio.ByteBuffer.allocate(16).putLong(subject.tenantId().getMostSignificantBits()).putLong(subject.tenantId().getLeastSignificantBits()).array();
        return mapper.summarize(tenant,range.all(),range.ids()).stream()
            .map(row->new Group((String)row.get("code"),(String)row.get("name"),Boolean.TRUE.equals(row.get("enabled")),((Number)row.get("total")).longValue())).toList();
    }
}
