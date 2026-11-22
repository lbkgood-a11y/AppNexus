package com.appnexus.platform.persistence.platformscope;

import com.appnexus.platform.authorization.*;
import com.appnexus.platform.configuration.BusinessValueCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** 原授权策略 JSON 的固定类型编解码与统一校验；不接收自由表达式，不把解析失败回退成全部。 */
public final class DataGrantPolicySupport {
    private static final ObjectMapper JSON=new ObjectMapper();
    private DataGrantPolicySupport() {}
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
     * 保存前在原管理事务中校验能力及主体，不让另一租户主体进入策略。
     * @param tenant 可信租户；平台域不支持组合授权
     * @param permission 已选操作权限码
     * @param policy 本分支策略
     * @param catalog 构建声明目录
     * @param mapper 已通过管理权门禁的原策略仓储
     * @param businessValues 本租户业务字典公开目录
     */
    public static void validate(UUID tenant,String permission,RolePermissionManagement.DataPolicy policy,DataAccessCatalog catalog,RolePermissionMapper mapper,BusinessValueCatalog businessValues) {
        if(policy.scopeType()!=DataScopeAuthorization.ScopeType.RULES)return;
        if(tenant==null)throw new IllegalArgumentException("DATA_GRANT_TENANT_REQUIRED");
        catalog.validateRules(permission,policy.resourceType(),policy.rules());
        // 同条策略跨规则重复主体只查验一次；所有条件仍完整保留，不能为优化而合并授权分支。
        var organizations=new TreeSet<UUID>();var members=new TreeSet<UUID>();
        for(var rule:policy.rules()) {
            if(rule.subjectType()==DataGrantRules.SubjectType.ORGANIZATION)organizations.addAll(rule.subjectIds());
            if(rule.subjectType()==DataGrantRules.SubjectType.MEMBER)members.addAll(rule.subjectIds());
        }
        var dimensions=catalog.dimensions(permission,policy.resourceType());
        var bindings=new TreeMap<String,String>();
        var values=new TreeMap<String,Set<String>>();
        for(var rule:policy.rules())for(var condition:rule.conditions()){
            if(condition.parameter()!=null)continue;
            var dimension=dimensions.stream().filter(d->d.field().equals(condition.field())).findFirst().orElseThrow();
            if("ORGANIZATION".equals(dimension.source())){
                condition.values().stream().map(UUID::fromString).forEach(organizations::add);
            }else if("DICTIONARY".equals(dimension.source())){
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
        validateSubjects(tenant,organizations,true,mapper);
        validateSubjects(tenant,members,false,mapper);
    }
    /** 仅在原管理事务内分批验证权威主体；空集合不发SQL，任何批次失败使整个保存回滚。 */
    private static void validateSubjects(UUID tenant,Set<UUID> subjects,boolean organization,RolePermissionMapper mapper) {
        var ids=subjects.stream().map(UUID::toString).sorted().toList();
        for(int offset=0;offset<ids.size();offset+=500) {
            var batch=ids.subList(offset,Math.min(offset+500,ids.size()));
            int found=organization?mapper.validOrganizations(tenant.toString(),batch):mapper.validMembers(tenant.toString(),batch);
            if(found!=batch.size())throw new IllegalArgumentException("DATA_GRANT_SUBJECT_INVALID");
        }
    }
}
