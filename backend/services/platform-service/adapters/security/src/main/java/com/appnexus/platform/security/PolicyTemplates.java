package com.appnexus.platform.security;
import com.appnexus.platform.persistence.authorization.AuthorizationTemplateMapper;

import com.appnexus.platform.authorization.*;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 角色策略快照模板；复用正式授权保存，模板不能绕过当前权限目录、组织归属或字段执行支持。 */
@Service
public class PolicyTemplates {
    private final AuthorizationTemplateMapper statements;

    private final RolePermissionManagement roles;
    
    private final DataAccessCatalog dataAccess;
    private final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    public PolicyTemplates(RolePermissionManagement roles,DataAccessCatalog dataAccess,AuthorizationTemplateMapper statements){this.statements=statements;this.dataAccess=dataAccess;this.roles=roles;}
    /** 模板摘要，不返回授权快照给无管理权主体。 */
    public record View(UUID id,String name){}
    /**
     * 保存已审核角色的完整策略快照，含字段强制限制及结构化数据范围。
     * @param context 可信管理上下文，模板固定在该域内
     * @param role 当前管理域源角色 UUID
     * @param name 模板名，1–100 字符，不含控制字符
     * @return 新模板标识，不自动应用到任何角色
     */
    @Transactional
    public View save(RoleManagement.Context context,UUID role,String name){
        if(name==null||name.isBlank()||name.length()>100||name.codePoints().anyMatch(Character::isISOControl))throw invalid();
        var assignment=roles.getAuthorization(context,role);UUID id=UUID.randomUUID();
        statements.statement1(id.toString(),owner(context).toString(),name.strip(),json.writeValueAsString(assignment),context.actorAccountId().toString());
        return new View(id,name.strip());
    }
    /**
     * 列出当前管理域模板；通过角色读取复核管理权，不以知道模板 UUID 代替授权。
     * @param context 可信管理上下文
     * @param role 本域可管理角色，用于权威能力复核
     * @return 最近 100 个模板，不包含其他域模板
     */
    public List<View> list(RoleManagement.Context context,UUID role){
        roles.getAuthorization(context,role);
        return statements.summaries(owner(context).toString()).stream().map(row->new View(UUID.fromString((String)row.get("id")),(String)row.get("name"))).toList();
    }
    /**
     * 使用当前目标角色版本覆盖保存模板；所有旧权限和组织字段再次校验，失败不部分应用。
     * @param context 可信管理上下文，不能跨租户套用
     * @param role 目标角色 UUID
     * @param template 同域模板 UUID
     * @param version 当前目标角色版本，不使用模板来源版本
     * @param reason 1–500 字符变更原因，由正式用例校验并审计
     * @return 保存后的完整授权快照
     */
    @Transactional
    public RolePermissionManagement.AuthorizationAssignment apply(RoleManagement.Context context,UUID role,UUID template,long version,String reason){
        roles.getAuthorization(context,role);
        var values=statements.statement2(template.toString(),owner(context).toString());
        if(values.size()!=1)throw invalid();
        var value=json.readValue(values.getFirst(),RolePermissionManagement.AuthorizationAssignment.class);
        if(value.dataPolicies().stream().anyMatch(p->!p.resourceType().equals("organization.unit"))&&!Objects.equals(value.dataAccessVersion(),dataAccess.version()))throw invalid();
        return roles.replaceAuthorization(context,role,version,value.permissionIds(),value.dataPolicies(),value.fieldPolicies(),reason);
    }
    private UUID owner(RoleManagement.Context context){return context.tenantId()==null?new UUID(0,0):context.tenantId();}
    private org.springframework.web.ErrorResponseException invalid(){return AccountSettingsController.problem(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_POLICY_TEMPLATE","策略模板不存在、不属于当前域或参数无效");}
}
