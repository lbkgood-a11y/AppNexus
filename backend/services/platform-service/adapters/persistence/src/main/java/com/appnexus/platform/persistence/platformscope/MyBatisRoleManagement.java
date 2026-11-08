package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.RoleManagement;
import com.appnexus.platform.authorization.RoleManagementException;
import com.fasterxml.uuid.Generators;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色生命周期实现；管理权威锁、条件更新与追加审计在同一 MySQL 事务完成。 */
@Service
public class MyBatisRoleManagement implements RoleManagement {
    private static final String PLATFORM_OWNER="00000000-0000-0000-0000-000000000000";
    private final RoleMapper mapper;
    public MyBatisRoleManagement(RoleMapper mapper){this.mapper=mapper;}

    @Override public Page list(Context context,Query input){
        var c=context(context);var q=query(input);requireRead(c);
        String owner=owner(c),status=q.status()==null?null:q.status().name(),type=q.roleType()==null?null:q.roleType().name();
        var items=mapper.list(owner,q.query().trim(),status,type,q.pageSize(),(q.page()-1)*q.pageSize()).stream().map(this::role).toList();
        return new Page(items,mapper.count(owner,q.query().trim(),status,type),q.page(),q.pageSize());
    }
    @Override @Transactional public Role create(Context context,Create input){
        var c=context(context);lockAuthority(c);validate(input);
        UUID id=Generators.timeBasedEpochGenerator().generate();
        mapper.create(id.toString(),scope(c).name(),text(c.tenantId()),input.code().trim(),input.name().trim(),blank(input.description()),input.sortOrder(),c.actorAccountId().toString());
        mapper.audit(id.toString(),scope(c).name(),text(c.tenantId()),c.actorAccountId().toString(),"ROLE_CREATED",null);
        return role(Objects.requireNonNull(mapper.find(owner(c),id.toString())));
    }
    @Override @Transactional public Role update(Context context,UUID roleId,Update input){
        var c=context(context);lockAuthority(c);Objects.requireNonNull(roleId);validate(input);
        var current=locked(c,roleId);
        String name=input.name().trim(),description=blank(input.description());
        if(name.equals(current.name())&&Objects.equals(description,current.description())&&input.sortOrder()==current.sortOrder())return current;
        if(mapper.update(owner(c),roleId.toString(),input.version(),name,description,input.sortOrder(),c.actorAccountId().toString())!=1)conflict();
        mapper.audit(roleId.toString(),scope(c).name(),text(c.tenantId()),c.actorAccountId().toString(),"ROLE_UPDATED",null);
        return role(Objects.requireNonNull(mapper.find(owner(c),roleId.toString())));
    }
    @Override @Transactional public Role changeStatus(Context context,UUID roleId,long version,Status status,String reason){
        var c=context(context);lockAuthority(c);Objects.requireNonNull(roleId);Objects.requireNonNull(status);validVersion(version);
        var current=locked(c,roleId);
        if(current.version()!=version)conflict();
        if(current.status()==status)return current;
        if(status==Status.DISABLED){requiredReason(reason);if(protectedSystem(current))throw error("ROLE_SYSTEM_DISABLE_FORBIDDEN","受保护系统角色不能禁用");}
        if(mapper.status(owner(c),roleId.toString(),version,status.name(),c.actorAccountId().toString())!=1)conflict();
        mapper.audit(roleId.toString(),scope(c).name(),text(c.tenantId()),c.actorAccountId().toString(),status==Status.ENABLED?"ROLE_ENABLED":"ROLE_DISABLED",blank(reason));
        return role(Objects.requireNonNull(mapper.find(owner(c),roleId.toString())));
    }
    @Override @Transactional public void delete(Context context,UUID roleId,long version,String confirmationCode,String reason){
        var c=context(context);lockAuthority(c);Objects.requireNonNull(roleId);validVersion(version);requiredReason(reason);
        var current=locked(c,roleId);
        if(current.version()!=version)conflict();
        if(current.roleType()==RoleType.SYSTEM)throw error("ROLE_SYSTEM_DELETE_FORBIDDEN","系统角色不能删除");
        if(current.status()!=Status.DISABLED)throw error("ROLE_NOT_DISABLED","请先禁用角色");
        if(!current.code().equals(confirmationCode))throw error("ROLE_CONFIRMATION_MISMATCH","确认编码不匹配");
        if(mapper.delete(owner(c),roleId.toString(),version,reason.trim(),c.actorAccountId().toString())!=1)conflict();
        mapper.audit(roleId.toString(),scope(c).name(),text(c.tenantId()),c.actorAccountId().toString(),"ROLE_DELETED",reason.trim());
    }

    /** 平台代管和租户自助走不同权威锁；任何缺失上下文均默认拒绝。 */
    private void lockAuthority(Context c){
        if(c.tenantId()==null){if(mapper.lockPlatformAdministrator(text(c.actorAccountId()))==null)denied();return;}
        if(c.actorMemberId()==null){if(mapper.lockPlatformAdministrator(text(c.actorAccountId()))==null||mapper.lockTenant(text(c.tenantId()))==null)denied();return;}
        if(mapper.lockTenantAdministrator(text(c.actorAccountId()),text(c.tenantId()),text(c.actorMemberId()))==null)denied();
    }
    private void requireRead(Context c){
        boolean allowed=c.actorMemberId()==null?mapper.platformAdministrator(text(c.actorAccountId())):mapper.tenantAdministrator(text(c.actorAccountId()),text(c.tenantId()),text(c.actorMemberId()));
        if(!allowed)denied();
    }
    private Role locked(Context c,UUID id){var row=mapper.lock(owner(c),id.toString());if(row==null)throw error("ROLE_SCOPE_NOT_FOUND","角色不存在");return role(row);}
    private Context context(Context c){Objects.requireNonNull(c);Objects.requireNonNull(c.actorAccountId());if(c.tenantId()==null&&c.actorMemberId()!=null)throw new IllegalArgumentException("平台域不能包含租户成员");return c;}
    private Query query(Query q){Objects.requireNonNull(q);if(q.page()<1||q.pageSize()<1||q.pageSize()>100)throw new IllegalArgumentException("分页范围无效");String text=q.query()==null?"":q.query();if(text.length()>100)throw new IllegalArgumentException("关键词过长");return new Query(text,q.status(),q.roleType(),q.page(),q.pageSize());}
    private void validate(Create i){Objects.requireNonNull(i);if(i.code()==null||!i.code().matches("[a-z][a-z0-9_]{1,63}")||i.code().startsWith("sys_"))throw new IllegalArgumentException("角色编码无效");common(i.name(),i.description(),i.sortOrder());}
    private void validate(Update i){Objects.requireNonNull(i);validVersion(i.version());common(i.name(),i.description(),i.sortOrder());}
    private void common(String name,String description,int sort){if(name==null||name.isBlank()||name.length()>100||description!=null&&description.trim().length()>500||sort<0||sort>9999)throw new IllegalArgumentException("角色字段无效");}
    private void validVersion(long version){if(version<0)throw new IllegalArgumentException("版本无效");}
    private void requiredReason(String value){if(value==null||value.isBlank()||value.trim().length()>500)throw new IllegalArgumentException("原因须为 1–500 字符");}
    private boolean protectedSystem(Role role){return role.roleType()==RoleType.SYSTEM&&("PLATFORM_SUPER_ADMIN".equals(role.systemKey())||"TENANT_ADMIN".equals(role.systemKey()));}
    private ScopeType scope(Context c){return c.tenantId()==null?ScopeType.PLATFORM:ScopeType.TENANT;}
    private String owner(Context c){return c.tenantId()==null?PLATFORM_OWNER:c.tenantId().toString();}
    private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    private String text(UUID v){return v==null?null:v.toString();}
    private void denied(){throw new SecurityException("Role management authority required");}
    private void conflict(){throw error("ROLE_VERSION_CONFLICT","角色已被其他管理员修改");}
    private RoleManagementException error(String code,String message){return new RoleManagementException(code,message);}
    private Role role(Map<String,Object> r){return new Role(uuid(r,"id"),ScopeType.valueOf((String)r.get("scope_type")),uuid(r,"tenant_id"),RoleType.valueOf((String)r.get("role_type")),(String)r.get("system_key"),(String)r.get("code"),(String)r.get("name"),(String)r.get("description"),Status.valueOf((String)r.get("status")),((Number)r.get("sort_order")).intValue(),((Number)r.get("version")).longValue(),instant(r.get("updated_at")));}
    private UUID uuid(Map<String,Object> r,String key){Object value=r.get(key);return value==null?null:UUID.fromString((String)value);}
    private Instant instant(Object value){if(value instanceof Instant i)return i;if(value instanceof Timestamp t)return t.toInstant();if(value instanceof LocalDateTime t)return t.toInstant(ZoneOffset.UTC);throw new IllegalStateException("Unsupported timestamp");}
}
