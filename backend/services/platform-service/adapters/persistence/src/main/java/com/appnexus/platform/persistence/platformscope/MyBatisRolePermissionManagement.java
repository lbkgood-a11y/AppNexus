package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.RoleManagement;
import com.appnexus.platform.authorization.FieldSecurity;
import com.appnexus.platform.authorization.FieldCatalog;
import com.appnexus.platform.authorization.PermissionCatalog;
import com.appnexus.platform.authorization.RoleManagementException;
import com.appnexus.platform.authorization.RolePermissionManagement;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色权限全量覆盖实现；权威复核、关系替换、角色版本推进和审计在同一事务完成。 */
@Service
public class MyBatisRolePermissionManagement implements RolePermissionManagement {
    private static final String PLATFORM_OWNER="00000000-0000-0000-0000-000000000000";
    private final RolePermissionMapper mapper;
    private final FieldCatalog fieldCatalog;
    private final com.appnexus.platform.authorization.AuthorityEvents events;
    public MyBatisRolePermissionManagement(RolePermissionMapper mapper,FieldCatalog fieldCatalog,com.appnexus.platform.authorization.AuthorityEvents events){this.mapper=mapper;this.fieldCatalog=fieldCatalog;this.events=events;}

    @Override @Transactional public Assignment get(RoleManagement.Context context,UUID roleId){
        var c=context(context);requireAuthority(c);var role=role(c,roleId);return assignment(roleId,version(role));
    }
    @Override @Transactional public AuthorizationAssignment getAuthorization(RoleManagement.Context context,UUID roleId){var c=context(context);requireAuthority(c);var role=role(c,roleId);return new AuthorizationAssignment(assignment(roleId,version(role)).permissionIds(),readPolicies(roleId),version(role),readFields(roleId));}

    @Override @Transactional public Assignment replace(RoleManagement.Context context,UUID roleId,long expectedRoleVersion,
                                                         Set<UUID> permissionIds,String reason){
        var c=context(context);requireAuthority(c);if(expectedRoleVersion<0)throw new IllegalArgumentException("角色版本无效");requiredReason(reason);
        var role=role(c,roleId);long current=version(role);if(current!=expectedRoleVersion)conflict();
        var requested=normalize(permissionIds);var before=mapper.permissions(roleId.toString()).stream().map(UUID::fromString).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if(before.equals(new LinkedHashSet<>(requested)))return new Assignment(Set.copyOf(before),current);
        if(!requested.isEmpty()){
            var locked=mapper.lockPermissions(scope(c),requested.stream().map(UUID::toString).toList());
            if(locked.size()!=requested.size())throw error("PERMISSION_SCOPE_MISMATCH","权限不存在、已停用或不属于角色管理域");
            if(!mapper.scopedPermissions(strings(requested)).isEmpty())throw error("DATA_SCOPE_REQUIRED","所选权限需要同时配置数据范围");
        }
        mapper.deletePermissions(roleId.toString());
        if(!requested.isEmpty())mapper.insertPermissions(roleId.toString(),scope(c),c.actorAccountId().toString(),requested.stream().map(UUID::toString).toList());
        if(mapper.bumpVersion(roleId.toString(),current,c.actorAccountId().toString())!=1)conflict();
        events.invalidated(c.tenantId(),roleId);
        mapper.audit(scope(c),text(c.tenantId()),c.actorAccountId().toString(),roleId.toString(),reason.trim(),json(before),json(requested));
        return new Assignment(Set.copyOf(requested),current+1);
    }

    @Override @Transactional public AuthorizationAssignment replaceAuthorization(RoleManagement.Context context,UUID roleId,
            long expectedRoleVersion,Set<UUID> permissionIds,Set<DataPolicy> dataPolicies,String reason){
        return replaceAuthorization(context,roleId,expectedRoleVersion,permissionIds,dataPolicies,null,reason);
    }

    @Override @Transactional public AuthorizationAssignment replaceAuthorization(RoleManagement.Context context,UUID roleId,
            long expectedRoleVersion,Set<UUID> permissionIds,Set<DataPolicy> dataPolicies,Set<FieldSecurity.Policy> fieldPolicies,String reason){
        var c=context(context);requireAuthority(c);if(expectedRoleVersion<0)throw new IllegalArgumentException("角色版本无效");requiredReason(reason);
        var role=role(c,roleId);long current=version(role);if(current!=expectedRoleVersion)conflict();
        var requested=normalize(permissionIds);var requestedStrings=strings(requested);Objects.requireNonNull(dataPolicies);
        if(!requested.isEmpty()&&mapper.lockPermissions(scope(c),requestedStrings).size()!=requested.size())throw error("PERMISSION_SCOPE_MISMATCH","权限不存在、已停用或不属于角色管理域");
        var required=requested.isEmpty()?Set.<UUID>of():mapper.scopedPermissions(requestedStrings).stream().map(UUID::fromString).collect(java.util.stream.Collectors.toSet());
        var policies=normalizePolicies(c,requested,dataPolicies,required);
        var fieldsBefore=readFields(roleId);var readPoliciesForAudit=readPolicies(roleId);
        var fields=fieldPolicies==null?fieldsBefore:validateFields(c,fieldPolicies);
        var before=mapper.permissions(roleId.toString()).stream().map(UUID::fromString).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        mapper.deletePermissions(roleId.toString());
        if(!requested.isEmpty())mapper.insertPermissions(roleId.toString(),scope(c),c.actorAccountId().toString(),requestedStrings);
        for(var policy:policies){mapper.insertPolicy(roleId.toString(),policy.permissionId().toString(),policy.scopeType().name(),policy.resourceType(),c.actorAccountId().toString());if(!policy.organizationIds().isEmpty())mapper.insertPolicyOrganizations(roleId.toString(),policy.permissionId().toString(),c.tenantId().toString(),strings(policy.organizationIds()));}
        mapper.clearFields(roleId.toString());for(var field:fields)mapper.field(roleId.toString(),field.resource(),field.field(),field.mode().name());
        if(mapper.bumpVersion(roleId.toString(),current,c.actorAccountId().toString())!=1)conflict();
        events.invalidated(c.tenantId(),roleId);
        mapper.audit(scope(c),text(c.tenantId()),c.actorAccountId().toString(),roleId.toString(),reason.trim(),authorizationJson(before,readPoliciesForAudit,fieldsBefore),authorizationJson(requested,policies,fields));
        return new AuthorizationAssignment(Set.copyOf(requested),Set.copyOf(policies),current+1,Set.copyOf(fields));
    }

    /** 未接通字段不能保存限制，防止页面显示已配置但业务仍返回原文。 */
    private Set<FieldSecurity.Policy> validateFields(RoleManagement.Context c,Set<FieldSecurity.Policy> fields){
        if(fields.size()>100)throw new IllegalArgumentException("字段限制数量超过上限");
        var catalog=fieldCatalog.list(c.tenantId()==null?PermissionCatalog.ScopeType.PLATFORM:PermissionCatalog.ScopeType.TENANT);
        var seen=new HashSet<String>();
        for(var field:fields){Objects.requireNonNull(field);if(!seen.add(field.resource()+":"+field.field())||catalog.stream().noneMatch(item->item.resource().equals(field.resource())&&item.field().equals(field.field())&&item.modes().contains(field.mode())))throw new IllegalArgumentException("字段或展示模式尚未接通");}
        return Set.copyOf(fields);
    }
    private Set<FieldSecurity.Policy> readFields(UUID role){return mapper.fields(role.toString()).stream().map(row->new FieldSecurity.Policy((String)row.get("resource_type"),(String)row.get("field_code"),FieldSecurity.Mode.valueOf((String)row.get("display_mode")))).collect(java.util.stream.Collectors.toUnmodifiableSet());}

    private List<DataPolicy> normalizePolicies(RoleManagement.Context c,List<UUID> permissions,Set<DataPolicy> input,Set<UUID> required){
        if(input.size()>permissions.size())throw new IllegalArgumentException("数据策略数量不能超过功能权限数量");var result=new ArrayList<DataPolicy>();var seen=new HashSet<UUID>();
        for(var p:input){Objects.requireNonNull(p);Objects.requireNonNull(p.permissionId());Objects.requireNonNull(p.scopeType());if(!permissions.contains(p.permissionId())||!seen.add(p.permissionId()))throw new IllegalArgumentException("数据策略必须唯一对应已选权限");if(p.scopeType()==com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.DENY)throw new IllegalArgumentException("DENY 不能作为保存策略");if(p.resourceType()==null||!p.resourceType().matches("[a-z][a-z0-9.]*"))throw new IllegalArgumentException("资源类型无效");var orgs=p.organizationIds()==null?Set.<UUID>of():Set.copyOf(p.organizationIds());boolean organization=p.scopeType()==com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ORGANIZATION||p.scopeType()==com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.ORGANIZATION_SUBTREE;if(organization){if(c.tenantId()==null||orgs.isEmpty()||orgs.size()>500)throw new IllegalArgumentException("组织范围值无效");if(mapper.validOrganizations(c.tenantId().toString(),strings(orgs))!=orgs.size())throw error("DATA_SCOPE_VALUE_INVALID","组织范围值不存在或不属于当前租户");}else if(!orgs.isEmpty())throw new IllegalArgumentException("该范围类型不能包含组织值");if(!mapper.supportsPolicy(p.permissionId().toString(),p.scopeType().name(),p.resourceType()))throw error("DATA_SCOPE_UNSUPPORTED","权限不支持所选数据范围");result.add(new DataPolicy(p.permissionId(),p.scopeType(),p.resourceType(),orgs));}
        if(!seen.equals(required))throw error("DATA_SCOPE_REQUIRED","需要数据范围的权限必须且只能配置一项策略");return result.stream().sorted(java.util.Comparator.comparing(DataPolicy::permissionId)).toList();
    }

    private Assignment assignment(UUID roleId,long version){return new Assignment(mapper.permissions(roleId.toString()).stream().map(UUID::fromString).collect(java.util.stream.Collectors.toUnmodifiableSet()),version);}
    private Set<DataPolicy> readPolicies(UUID roleId){var grouped=new LinkedHashMap<UUID,DataPolicy>();for(var row:mapper.policies(roleId.toString())){var id=UUID.fromString((String)row.get("permission_id"));var current=grouped.get(id);var organizations=new LinkedHashSet<UUID>();if(current!=null)organizations.addAll(current.organizationIds());if(row.get("organization_id")!=null)organizations.add(UUID.fromString((String)row.get("organization_id")));grouped.put(id,new DataPolicy(id,com.appnexus.platform.authorization.DataScopeAuthorization.ScopeType.valueOf((String)row.get("scope_type")),(String)row.get("resource_type"),Set.copyOf(organizations)));}return Set.copyOf(grouped.values());}
    private Map<String,Object> role(RoleManagement.Context c,UUID id){Objects.requireNonNull(id);var row=mapper.lockRole(owner(c),id.toString());if(row==null)throw error("ROLE_SCOPE_NOT_FOUND","角色不存在");return row;}
    private void requireAuthority(RoleManagement.Context c){boolean allowed=c.actorMemberId()==null?mapper.platformAdministrator(c.actorAccountId().toString()):mapper.tenantAdministrator(c.actorAccountId().toString(),c.tenantId().toString(),c.actorMemberId().toString());if(!allowed)throw new SecurityException("Role permission authority required");}
    private RoleManagement.Context context(RoleManagement.Context c){Objects.requireNonNull(c);Objects.requireNonNull(c.actorAccountId());if(c.tenantId()==null&&c.actorMemberId()!=null)throw new IllegalArgumentException("平台域不能包含租户成员");return c;}
    private List<UUID> normalize(Set<UUID> ids){Objects.requireNonNull(ids);if(ids.size()>1000)throw new IllegalArgumentException("单角色权限数量不能超过 1000");if(ids.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("权限 ID 不能为空");return ids.stream().sorted().toList();}
    private List<String> strings(Collection<UUID> ids){return ids.stream().map(UUID::toString).sorted().toList();}
    private void requiredReason(String value){if(value==null||value.isBlank()||value.trim().length()>500)throw new IllegalArgumentException("原因须为 1–500 字符");}
    private long version(Map<String,Object> row){return ((Number)row.get("version")).longValue();}
    private String scope(RoleManagement.Context c){return c.tenantId()==null?"PLATFORM":"TENANT";}
    private String owner(RoleManagement.Context c){return c.tenantId()==null?PLATFORM_OWNER:c.tenantId().toString();}
    private String text(UUID id){return id==null?null:id.toString();}
    /** UUID 没有 JSON 转义字符，固定格式序列化避免授权基础设施额外依赖 Web JSON 组件。 */
    private String json(Collection<UUID> ids){return ids.stream().map(id->"\""+id+"\"").collect(java.util.stream.Collectors.joining(",","[","]"));}
    private String authorizationJson(Collection<UUID> ids,Collection<DataPolicy> policies,Collection<FieldSecurity.Policy> fields){return "{\"permissionIds\":"+json(ids)+",\"dataPolicies\":["+policies.stream().map(p->"{\"permissionId\":\""+p.permissionId()+"\",\"scopeType\":\""+p.scopeType()+"\",\"resourceType\":\""+p.resourceType()+"\",\"organizationIds\":"+json(p.organizationIds())+"}").collect(java.util.stream.Collectors.joining(","))+"],\"fieldPolicies\":["+fields.stream().map(f->"{\"resource\":\""+f.resource()+"\",\"field\":\""+f.field()+"\",\"mode\":\""+f.mode()+"\"}").collect(java.util.stream.Collectors.joining(","))+"]}";}
    private void conflict(){throw error("ROLE_VERSION_CONFLICT","角色已被其他管理员修改");}
    private RoleManagementException error(String code,String message){return new RoleManagementException(code,message);}
}
