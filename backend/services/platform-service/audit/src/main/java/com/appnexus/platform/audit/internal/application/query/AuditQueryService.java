package com.appnexus.platform.audit.internal.application.query;
import com.appnexus.platform.audit.AuditQueries;
import com.appnexus.platform.audit.AuditQueries.*;
import com.appnexus.platform.audit.port.AuditRecords;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.*;
import java.time.Instant;
import java.util.*;

/** 统一受权审计读模型，只暴露标识/动作/结果/追踪；不联接密码、Token、敏感原值或完整授权 JSON。 */
public final class AuditQueryService implements AuditQueries {
    private final AuditRecords mapper;
    private final PlatformAdministration platform;
    private final AccessAuthorization authorization;
    private final TenantAccess tenants;
    private final FieldProtection fields;
    /** @param fields 统一实时字段保护；摘要投影与原审计存储隔离 */
    public AuditQueryService(AuditRecords mapper,PlatformAdministration platform,AccessAuthorization authorization,
            TenantAccess tenants,FieldProtection fields){
        this.mapper=mapper;this.platform=platform;this.authorization=authorization;this.tenants=tenants;
        this.fields=Objects.requireNonNull(fields);
    }
    /**
     * 平台查询可选目标租户，不读取全局个人安全日志或凭据。
     * @param actor 权威平台操作者 UUID
     * @param tenant 指定租户时仅该租户；null 为平台操作审计
     * @param page 从 1 开始，最大 100000
     * @param size 每页 1–100，缺省入口 20
     * @return 脱敏元数据摘要，不含联系方式、姓名或业务原值
     */
    public Page platform(UUID actor,UUID tenant,int page,int size){
        if(!platform.isAdministrator(actor))throw denied();
        boolean hidden=fields.restrictions(AccessAuthorization.Subject.platform(actor),"audit.platform").containsKey("target");
        if(tenant!=null)return query(tenant,page,size,hidden);
        validate(page,size);
        var items=mapper.platform(size,(long)(page-1)*size).stream().map(row->entry(row,"platform",hidden)).toList();
        return new Page(items,mapper.platformCount(),page,size);
    }
    /** 当前租户授权管理员查询，不能传入目标租户覆盖可信上下文。 */
    public Page tenant(VerifiedTenantContext context,int page,int size){
        if(context==null||tenants.available(context.accountId()).stream().noneMatch(value->value.tenantId().equals(context.tenantId())&&value.memberId().equals(context.memberId()))||
                !authorization.hasPermission(AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),"organization.permission:grant"))throw denied();
        var subject=AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId());
        return query(context.tenantId(),page,size,fields.restrictions(subject,"audit.tenant").containsKey("target"));
    }
    /** 固定 UNION 白名单，不接收自由表名、排序、过滤 SQL；每个分支独立绑定同一可信租户。 */
    private Page query(UUID tenant,int page,int size,boolean hidden){
        validate(page,size);String id=tenant.toString();
        return new Page(mapper.tenant(id,size,(long)(page-1)*size).stream().map(row->entry(row,(String)row.get("source"),hidden)).toList(),mapper.tenantCount(id),page,size);
    }
    /** 固定查询别名转UTC摘要；NULL历史值保持未知，不生成敏感正文。 */
    private Entry entry(Map<String,Object> row,String source,boolean hidden){Object at=row.get("occurred_at");Instant occurred=(Instant)at;return new Entry(source,Objects.toString(row.get("event_id"),null),(String)row.get("actor"),(String)row.get("action"),hidden?null:(String)row.get("target"),(String)row.get("trace_id"),row.get("status")==null?null:((Number)row.get("status")).intValue(),occurred);}
    private void validate(int page,int size){if(page<1||page>100000||size<1||size>100)throw new Rejection(400,"INVALID_AUDIT_QUERY");}
    private Rejection denied(){return new Rejection(403,"AUDIT_QUERY_DENIED");}
}
