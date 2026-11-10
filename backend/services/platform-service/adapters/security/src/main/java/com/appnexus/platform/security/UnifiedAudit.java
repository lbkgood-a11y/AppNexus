package com.appnexus.platform.security;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.tenant.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 统一受权审计读模型，只暴露标识/动作/结果/追踪；不联接密码、Token、敏感原值或完整授权 JSON。 */
@Service
public class UnifiedAudit {
    /** 审计摘要，traceId 缺省 null 表示历史事件未采集，不伪造技术关联。 */
    public record Entry(String source,String eventId,String actor,String action,String target,String traceId,Integer status,Instant occurredAt){}
    /** 固定分页契约，items/total 使用完全一致的租户边界。 */
    public record Page(List<Entry> items,long total,int page,int pageSize){}
    private final JdbcTemplate jdbc;
    private final PlatformAdministration platform;
    private final AccessAuthorization authorization;
    private final TenantAccess tenants;
    public UnifiedAudit(JdbcTemplate jdbc,PlatformAdministration platform,AccessAuthorization authorization,TenantAccess tenants){this.jdbc=jdbc;this.platform=platform;this.authorization=authorization;this.tenants=tenants;}
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
        if(tenant!=null)return query(tenant,page,size);
        validate(page,size);
        var items=jdbc.query("SELECT id,BIN_TO_UUID(actor_account_id) actor,action,BIN_TO_UUID(target_id) target,occurred_at FROM platform_administration_audit WHERE tenant_id IS NULL ORDER BY id DESC LIMIT ? OFFSET ?",
            (row,index)->new Entry("platform",Long.toString(row.getLong("id")),row.getString("actor"),row.getString("action"),row.getString("target"),null,null,row.getTimestamp("occurred_at").toInstant()),size,(long)(page-1)*size);
        return new Page(items,Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM platform_administration_audit WHERE tenant_id IS NULL",Long.class)),page,size);
    }
    /** 当前租户授权管理员查询，不能传入目标租户覆盖可信上下文。 */
    public Page tenant(VerifiedTenantContext context,int page,int size){
        if(context==null||tenants.available(context.accountId()).stream().noneMatch(value->value.tenantId().equals(context.tenantId())&&value.memberId().equals(context.memberId()))||
                !authorization.hasPermission(AccessAuthorization.Subject.tenant(context.accountId(),context.tenantId(),context.memberId()),"organization.permission:grant"))throw denied();
        return query(context.tenantId(),page,size);
    }
    /** 固定 UNION 白名单，不接收自由表名、排序、过滤 SQL；每个分支独立绑定同一可信租户。 */
    private Page query(UUID tenant,int page,int size){
        validate(page,size);String id=tenant.toString();
        String union="SELECT 'platform' source,CAST(id AS CHAR) event_id,BIN_TO_UUID(actor_account_id) actor,action,BIN_TO_UUID(target_id) target,NULL trace_id,NULL status,occurred_at FROM platform_administration_audit WHERE tenant_id=UUID_TO_BIN(?)"
            +" UNION ALL SELECT 'dictionary',CAST(id AS CHAR),BIN_TO_UUID(actor_account_id),action,BIN_TO_UUID(dictionary_id),NULL,NULL,occurred_at FROM tenant_dictionary_audit WHERE tenant_id=UUID_TO_BIN(?)"
            +" UNION ALL SELECT 'parameter',CAST(id AS CHAR),BIN_TO_UUID(actor_id),'PARAMETER_UPDATED',parameter_key,NULL,NULL,occurred_at FROM tenant_parameter_audit WHERE tenant_id=UUID_TO_BIN(?)"
            +" UNION ALL SELECT 'integration',CAST(id AS CHAR),client_id,operation,NULL,trace_id,status,occurred_at FROM integration_call_audit WHERE tenant_id=UUID_TO_BIN(?)";
        var rows=jdbc.query("SELECT * FROM ("+union+") audit_rows ORDER BY occurred_at DESC,source,event_id DESC LIMIT ? OFFSET ?",
            (row,index)->new Entry(row.getString("source"),row.getString("event_id"),row.getString("actor"),row.getString("action"),row.getString("target"),row.getString("trace_id"),(Integer)row.getObject("status"),row.getTimestamp("occurred_at").toInstant()),id,id,id,id,size,(long)(page-1)*size);
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM ("+union+") audit_rows",Long.class,id,id,id,id));
        return new Page(rows,total,page,size);
    }
    private void validate(int page,int size){if(page<1||page>100000||size<1||size>100)throw AccountSettingsController.problem(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_AUDIT_QUERY","审计分页参数不正确");}
    private org.springframework.web.ErrorResponseException denied(){return AccountSettingsController.problem(org.springframework.http.HttpStatus.FORBIDDEN,"AUDIT_QUERY_DENIED","无审计查询权限");}
}
