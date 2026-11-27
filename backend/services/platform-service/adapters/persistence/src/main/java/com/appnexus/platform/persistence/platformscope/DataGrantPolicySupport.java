package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.authorization.RolePermissionManagement.DataPolicy;
import com.appnexus.platform.authorization.RolePermissionManagement.ObjectScope;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** 原授权策略 JSON 的固定类型编解码与统一校验；不接收自由表达式，不把解析失败回退成全部。 */
public final class DataGrantPolicySupport {
    private static final ObjectMapper JSON=new ObjectMapper();
    private DataGrantPolicySupport() {}
    /** 按业务对象主资源键保存角色默认范围；空映射表示没有默认配置。 */
    static String encodeObjectScopes(Map<String, RolePermissionManagement.ObjectScope> scopes) {
        try {
            return JSON.writeValueAsString(new TreeMap<>(scopes));
        } catch (Exception error) {
            throw new IllegalArgumentException("DATA_GRANT_SERIALIZATION_FAILED", error);
        }
    }

    /** 历史 NULL 表示没有默认范围，损坏的策略不能按空配置处理。 */
    static Map<String, RolePermissionManagement.ObjectScope> decodeObjectScopes(Object value) {
        if (value == null) return Map.of();
        try {
            return Map.copyOf(JSON.readValue(value.toString(),
                    new TypeReference<Map<String, RolePermissionManagement.ObjectScope>>() {}));
        } catch (Exception error) {
            throw new IllegalStateException("DATA_GRANT_POLICY_INVALID", error);
        }
    }

    /** @param rules 经过统一模型校验的规则 @return 原策略表保存与审计共用的完整规则 JSON */
    public static String encode(List<DataGrantRules.Rule> rules) {
        try{return JSON.writeValueAsString(rules);}catch(Exception e){throw new IllegalArgumentException("DATA_GRANT_SERIALIZATION_FAILED",e);}
    }
    /** @param value 数据库规则文本，历史 NULL 缺省空列表；非空非法值拒绝 @return 不可变规则列表 */
    public static List<DataGrantRules.Rule> decode(Object value) {
        if(value==null)return List.of();
        try{return List.copyOf(JSON.readValue(value.toString(),new TypeReference<List<DataGrantRules.Rule>>(){}));}
        catch(Exception e){throw new IllegalStateException("DATA_GRANT_POLICY_INVALID",e);}
    }
    /**
     * 校验管理域内已选权限的范围，并复用统一规则校验。持久化投影与业务对象声明须同时支持该范围。
     * @param tenant 可信租户；平台域传 null
     * @param policy 本次提交的操作策略，调用方已校验权限属于当前授权分支
     * @param catalog 唯一业务对象声明派生的权限目录
     * @param mapper 授权管理事务内读取权限投影及规则引用的 Mapper
     * @param businessValues 用于校验规则取值的租户字典目录
     * @throws RoleManagementException 权限与范围不匹配或选择了已退役历史范围
     */
    static void validateOperationPolicy(UUID tenant, RolePermissionManagement.DataPolicy policy,
            DataAccessCatalog catalog, RolePermissionMapper mapper, BusinessValueCatalog businessValues) {
        if (!policy.organizationIds().isEmpty()) {
            throw new RoleManagementException("DATA_SCOPE_UNSUPPORTED", "历史组织范围已退役");
        }
        String permissionId = policy.permissionId().toString();
        String domain = tenant == null ? "PLATFORM" : "TENANT";
        var permission = mapper.permissionDetail(domain, permissionId);
        if (!policy.scopeType().configurable() || permission == null
                || !catalog.supports((String) permission.get("code"), policy.resourceType(), policy.scopeType())
                || !mapper.supportsPolicy(permissionId, policy.scopeType().name(), policy.resourceType())) {
            throw new RoleManagementException("DATA_SCOPE_UNSUPPORTED", "权限不支持所选数据范围");
        }
        validate(tenant, (String) permission.get("code"), policy, catalog, mapper, businessValues);
    }

    /**
     * 保存前在原管理事务中校验能力及主体，不让另一租户主体进入策略。
     * @param tenant 可信租户；平台域不支持组合授权
     * @param permission 已选操作权限码
     * @param policy 本分支策略
     * @param catalog 构建声明目录
     * @param mapper 已通过管理权门禁的原策略仓储
     * @param businessValues 本租户业务字典公开目录
     */
    public static void validate(UUID tenant,String permission,RolePermissionManagement.DataPolicy policy,DataAccessCatalog catalog,RolePermissionMapper mapper,BusinessValueCatalog businessValues) {
        if (!policy.scopeType().configurable()) {
            throw new IllegalArgumentException("DATA_SCOPE_UNSUPPORTED");
        }
        if(policy.scopeType()!=DataScopeAuthorization.ScopeType.RULES)return;
        if(tenant==null)throw new IllegalArgumentException("DATA_GRANT_TENANT_REQUIRED");
        catalog.validateRules(permission,policy.resourceType(),policy.rules());
        // 同条策略跨规则重复主体只查验一次；所有条件仍完整保留，不能为优化而合并授权分支。
        var members=new TreeSet<UUID>();
        for(var rule:policy.rules()) {
            if(rule.subjectType()==DataGrantRules.SubjectType.MEMBER)members.addAll(rule.subjectIds());
        }
        var dimensions=catalog.dimensions(permission,policy.resourceType());
        if(policy.rules().stream().anyMatch(rule -> retiredRule(rule, dimensions)))throw new IllegalArgumentException("DATA_SCOPE_UNSUPPORTED");
        var bindings=new TreeMap<String,String>();
        var values=new TreeMap<String,Set<String>>();
        for(var rule:policy.rules())for(var condition:rule.conditions()){
            if(condition.parameter()!=null)continue;
            var dimension=dimensions.stream().filter(d->d.field().equals(condition.field())).findFirst().orElseThrow();
            if("DICTIONARY".equals(dimension.source())){
                bindings.put(dimension.dictionary(),dimension.dictionary());
                values.computeIfAbsent(dimension.dictionary(),key->new HashSet<>()).addAll(condition.values());
            }
        }
        if(!bindings.isEmpty()){
            var candidates=businessValues.options(tenant,bindings);
            values.forEach((key,selected)->{
                // 停用值仍是已有记录的业务编码，保留历史授权；未知或其他租户字典值拒绝。
                var known=candidates.getOrDefault(key,List.of()).stream().map(BusinessValueCatalog.Option::value).toList();
                if(!known.containsAll(selected))throw new IllegalArgumentException("DATA_GRANT_DICTIONARY_VALUE_INVALID");
            });
        }
        // 每规则500个主体，策略最多20条；分批上限500，避免不同规则合计形成超长IN查询。
        validateSubjects(tenant,members,mapper);
    }
    /** 仅在原管理事务内分批验证权威主体；空集合不发SQL，任何批次失败使整个保存回滚。 */
    private static void validateSubjects(UUID tenant,Set<UUID> subjects,RolePermissionMapper mapper) {
        var ids=subjects.stream().map(UUID::toString).sorted().toList();
        for(int offset=0;offset<ids.size();offset+=500) {
            var batch=ids.subList(offset,Math.min(offset+500,ids.size()));
            int found=mapper.validMembers(tenant.toString(),batch);
            if(found!=batch.size())throw new IllegalArgumentException("DATA_GRANT_SUBJECT_INVALID");
        }
    }
    /**
     * @param rule 历史或待保存的完整授权分支
     * @param dimensions 操作声明的维度
     * @return 分支是否引用退役主体、动态参数或组织范围
     */
    static boolean retiredRule(DataGrantRules.Rule rule,
            List<com.appnexus.contracts.businessobject.BusinessObjects.DataDimension> dimensions) {
        if (rule.subjectType() == DataGrantRules.SubjectType.ORGANIZATION) return true;
        return rule.conditions().stream().anyMatch(condition ->
                (condition.parameter() != null && condition.parameter() != DataGrantRules.Parameter.CURRENT_ACCOUNT)
                || dimensions.stream().anyMatch(dimension -> dimension.field().equals(condition.field())
                        && Set.of("ORGANIZATION", "PERSON").contains(dimension.source())));
    }
    /** 新写入的默认即使暂未生效，也须关联至少一个已授权操作并验证主体和条件。 */
    static void validateObjectDefaults(RoleManagement.Context context, Set<UUID> required,
            Map<String,ObjectScope> defaults, Map<String,ObjectScope> previous, DataAccessCatalog dataAccess, RolePermissionMapper mapper, BusinessValueCatalog businessValues) {
        if (defaults.isEmpty()) return;
        if (context.tenantId() == null) throw new RoleManagementException("DATA_SCOPE_UNSUPPORTED", "对象默认范围仅适用于租户角色");
        for (var entry : defaults.entrySet()) {
            String resource = entry.getKey();
            var scope = entry.getValue();
            if (scope.equals(previous.get(resource))) continue;
            boolean valid = false;
            for (UUID id : required) {
                var detail = mapper.permissionDetail("TENANT", id.toString());
                if (detail == null) continue;
                String code = (String) detail.get("code");
                if (!dataAccess.supports(code, resource, scope.scopeType())) continue;
                if (scope.scopeType() == DataScopeAuthorization.ScopeType.RULES) {
                    try { dataAccess.validateRules(code, resource, scope.rules()); }
                    catch (IllegalArgumentException unsupported) { continue; }
                }
                var policy = new DataPolicy(id, scope.scopeType(), resource, Set.of(), scope.rules());
                DataGrantPolicySupport.validateOperationPolicy(context.tenantId(), policy, dataAccess, mapper, businessValues);
                valid = true;
                break;
            }
            if (!valid) throw new RoleManagementException("DATA_SCOPE_UNSUPPORTED", "对象默认范围须适用于至少一个已授权操作");
        }
    }

    /** 仅解析同域、已选功能、无覆盖且由对象声明支持的范围；保存时对每个实际继承操作验证规则。 */
    static Map<UUID,DataPolicy> inheritedPolicies(RoleManagement.Context context, Set<UUID> permissions,
            Set<DataPolicy> overrides, Map<String,ObjectScope> defaults, boolean validate, DataAccessCatalog dataAccess, RolePermissionMapper mapper, BusinessValueCatalog businessValues) {
        if (defaults.isEmpty()) return Map.of();
        if (context.tenantId() == null) throw new RoleManagementException("DATA_SCOPE_UNSUPPORTED", "对象默认范围仅适用于租户角色");
        var descriptors = dataAccess.list(PermissionCatalog.ScopeType.TENANT);
        if (defaults.keySet().stream().anyMatch(resource -> descriptors.stream()
                .noneMatch(object -> object.resources().getFirst().resource().equals(resource)))) {
            throw new RoleManagementException("DATA_SCOPE_UNSUPPORTED", "对象默认范围必须引用已声明主资源");
        }
        var covered = overrides.stream().map(DataPolicy::permissionId).collect(java.util.stream.Collectors.toSet());
        var result = new HashMap<UUID,DataPolicy>();
        for (UUID id : permissions) {
            if (covered.contains(id)) continue;
            var permission = mapper.permissionDetail("TENANT", id.toString());
            if (permission == null) continue;
            var enabled = permission.get("enabled");
            if (!Boolean.TRUE.equals(enabled) && !(enabled instanceof Number flag && flag.intValue() == 1)) continue;
            String code = (String) permission.get("code");
            for (var entry : defaults.entrySet()) {
                var resource = entry.getKey();
                var value = entry.getValue();
                if (!dataAccess.supports(code, resource, value.scopeType())) continue;
                var policy = new DataPolicy(id, value.scopeType(), resource, Set.of(), value.rules());
                if (validate) {
                    DataGrantPolicySupport.validateOperationPolicy(context.tenantId(), policy, dataAccess, mapper, businessValues);
                } else if (value.scopeType() == DataScopeAuthorization.ScopeType.RULES) {
                    // 目录升级后失去适用条件时拒绝使用旧规则，等待管理员修订。
                    try { dataAccess.validateRules(code, resource, value.rules()); }
                    catch (IllegalArgumentException unsupported) { continue; }
                }
                result.put(id, policy);
            }
        }
        return result;
    }

}
