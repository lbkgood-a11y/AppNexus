package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 成员授权快照用例；成员行锁、角色行锁、快照替换、版本和审计处于同一事务。 */
@Service
public class MyBatisMemberAuthorizationManagement implements MemberAuthorizationManagement {
    private final SubjectRoleManagement subjects;
    private final RolePermissionManagement roles;
    private final SubjectRoleMapper subjectMapper;
    private final RolePermissionMapper roleMapper;
    private final MemberAuthorizationMapper mapper;

    public MyBatisMemberAuthorizationManagement(SubjectRoleManagement subjects,RolePermissionManagement roles,
            SubjectRoleMapper subjectMapper,RolePermissionMapper roleMapper,MemberAuthorizationMapper mapper) {
        this.subjects=subjects;this.roles=roles;this.subjectMapper=subjectMapper;this.roleMapper=roleMapper;this.mapper=mapper;
    }

    @Override @Transactional public Assignment get(RoleManagement.Context context,UUID memberId) {
        var assignment=subjects.memberRoles(context,memberId);
        return read(context.tenantId(),memberId,assignment.authorityVersion());
    }

    @Override @Transactional public Assignment replace(RoleManagement.Context context,UUID memberId,Grant grant,long expectedVersion,String reason) {
        Objects.requireNonNull(grant);Objects.requireNonNull(grant.roleId());Objects.requireNonNull(grant.permissionIds());Objects.requireNonNull(grant.dataPolicies());
        if(reason==null||reason.isBlank()||reason.trim().length()>500||expectedVersion<0)throw new IllegalArgumentException("授权版本或原因无效");
        var assigned=subjects.memberRoles(context,memberId);
        if(assigned.authorityVersion()!=expectedVersion)throw error("AUTHORITY_VERSION_CONFLICT","成员授权已变化，请重新读取");
        if(!assigned.roleIds().contains(grant.roleId()))throw error("AUTHORIZATION_NOT_FOUND","该成员未分配此角色");
        var role=roles.getAuthorization(context,grant.roleId());
        if(role.roleVersion()!=grant.roleVersion())throw error("ROLE_VERSION_CONFLICT","角色已变化，请重新读取");
        if(grant.permissionIds().size()>1000||!role.permissionIds().containsAll(grant.permissionIds()))throw error("PERMISSION_SCOPE_MISMATCH","授权不能超过角色当前功能集合");
        String tenant=context.tenantId().toString(),member=memberId.toString(),roleId=grant.roleId().toString();
        var required=grant.permissionIds().isEmpty()?Set.<UUID>of():roleMapper.scopedPermissions(strings(grant.permissionIds())).stream().map(UUID::fromString).collect(java.util.stream.Collectors.toSet());
        var policies=new HashMap<UUID,RolePermissionManagement.DataPolicy>();
        for(var policy:grant.dataPolicies()) {
            Objects.requireNonNull(policy);Objects.requireNonNull(policy.permissionId());Objects.requireNonNull(policy.scopeType());Objects.requireNonNull(policy.organizationIds());
            if(!required.contains(policy.permissionId())||policies.putIfAbsent(policy.permissionId(),policy)!=null)throw new IllegalArgumentException("数据策略必须唯一对应本分支已选权限");
            if(!roleMapper.supportsPolicy(policy.permissionId().toString(),policy.scopeType().name(),policy.resourceType())||!supported(policy))throw error("DATA_SCOPE_UNSUPPORTED","范围尚未支持执行");
            boolean organization=policy.scopeType()==DataScopeAuthorization.ScopeType.ORGANIZATION||policy.scopeType()==DataScopeAuthorization.ScopeType.ORGANIZATION_SUBTREE;
            if(organization) {
                if(policy.organizationIds().isEmpty()||policy.organizationIds().size()>500||roleMapper.validOrganizations(tenant,strings(policy.organizationIds()))!=policy.organizationIds().size())throw error("DATA_SCOPE_VALUE_INVALID","组织不存在或不属于当前租户");
            } else if(!policy.organizationIds().isEmpty())throw new IllegalArgumentException("非组织范围不能携带组织值");
        }
        if(!policies.keySet().equals(required))throw error("DATA_SCOPE_REQUIRED","请完成全部已选权限的数据范围");
        var before=read(context.tenantId(),memberId,expectedVersion);
        mapper.clear(tenant,member,roleId);
        for(UUID permission:grant.permissionIds()) {
            var policy=policies.get(permission);
            mapper.permission(tenant,member,roleId,permission.toString(),policy==null?null:policy.scopeType().name(),policy==null?null:policy.resourceType());
            if(policy!=null)for(UUID organization:policy.organizationIds())mapper.organization(tenant,member,roleId,permission.toString(),organization.toString());
        }
        mapper.update(tenant,member,roleId,role.roleVersion(),grant.expiresAt()==null?null:java.sql.Timestamp.from(grant.expiresAt()));
        if(subjectMapper.bumpMember(tenant,member,expectedVersion)!=1)throw error("AUTHORITY_VERSION_CONFLICT","成员授权版本冲突");
        var result=read(context.tenantId(),memberId,expectedVersion+1);
        subjectMapper.audit("TENANT",tenant,context.actorAccountId().toString(),"MEMBER_ROLES_REPLACED","MEMBER",member,reason.trim(),json(before),json(result));
        return result;
    }

    /** 历史组织 SELF 不能继续保存；创建操作仅可使用明确可执行的 ALL。 */
    private boolean supported(RolePermissionManagement.DataPolicy policy) {
        return policy.scopeType()!=DataScopeAuthorization.ScopeType.DENY
            && !("organization.unit".equals(policy.resourceType())&&policy.scopeType()==DataScopeAuthorization.ScopeType.SELF);
    }

    private Assignment read(UUID tenant,UUID member,long version) {
        var grouped=new LinkedHashMap<UUID,List<Map<String,Object>>>();
        for(var row:mapper.list(tenant.toString(),member.toString()))grouped.computeIfAbsent(UUID.fromString((String)row.get("role_id")),ignored->new ArrayList<>()).add(row);
        var grants=new ArrayList<Grant>();
        for(var entry:grouped.entrySet()) {
            var permissions=new HashSet<UUID>();var policies=new LinkedHashMap<UUID,RolePermissionManagement.DataPolicy>();
            for(var row:entry.getValue()) {
                if(row.get("permission_id")==null)continue;
                UUID id=UUID.fromString((String)row.get("permission_id"));permissions.add(id);
                if(row.get("scope_type")==null)continue;
                var organizations=new HashSet<UUID>();if(policies.containsKey(id))organizations.addAll(policies.get(id).organizationIds());
                if(row.get("organization_id")!=null)organizations.add(UUID.fromString((String)row.get("organization_id")));
                policies.put(id,new RolePermissionManagement.DataPolicy(id,DataScopeAuthorization.ScopeType.valueOf((String)row.get("scope_type")),(String)row.get("resource_type"),Set.copyOf(organizations)));
            }
            var first=entry.getValue().get(0);Object expires=first.get("expires_at");
            Instant instant=expires==null?null:expires instanceof java.sql.Timestamp timestamp?timestamp.toInstant():((java.time.LocalDateTime)expires).toInstant(java.time.ZoneOffset.UTC);
            grants.add(new Grant(entry.getKey(),Set.copyOf(permissions),Set.copyOf(policies.values()),instant,((Number)first.get("role_version")).longValue()));
        }
        return new Assignment(List.copyOf(grants),version);
    }

    private List<String> strings(Collection<UUID> ids){return ids.stream().map(UUID::toString).sorted().toList();}
    /** 快照只包含 UUID、受控枚举与时间；不记录主体资料、查询值或凭据。 */
    private String json(Assignment assignment){return "{\"version\":"+assignment.authorityVersion()+",\"grants\":["+assignment.grants().stream().map(g->"{\"roleId\":\""+g.roleId()+"\",\"roleVersion\":"+g.roleVersion()+",\"expiresAt\":"+(g.expiresAt()==null?"null":"\""+g.expiresAt()+"\"")+",\"permissions\":["+g.permissionIds().stream().sorted().map(id->"\""+id+"\"").collect(java.util.stream.Collectors.joining(","))+"],\"policies\":["+g.dataPolicies().stream().sorted(Comparator.comparing(RolePermissionManagement.DataPolicy::permissionId)).map(p->"{\"permissionId\":\""+p.permissionId()+"\",\"scopeType\":\""+p.scopeType()+"\",\"organizationIds\":["+p.organizationIds().stream().sorted().map(id->"\""+id+"\"").collect(java.util.stream.Collectors.joining(","))+"]}").collect(java.util.stream.Collectors.joining(","))+"]}").collect(java.util.stream.Collectors.joining(","))+"]}";}
    private RoleManagementException error(String code,String message){return new RoleManagementException(code,message);}
}
