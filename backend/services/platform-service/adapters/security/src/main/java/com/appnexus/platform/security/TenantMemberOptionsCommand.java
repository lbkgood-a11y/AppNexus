package com.appnexus.platform.security;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.RoleManagement;
import com.appnexus.platform.authorization.SubjectRoleManagement;
import com.appnexus.platform.tenant.TenantAccess;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 租户授权账号候选命令；使用原成员授权复核与目录端口。 */
@Component
public final class TenantMemberOptionsCommand implements BusinessObjectCommandRuntime {
    private final TenantAccess members;
    private final SubjectRoleManagement roles;

    /** @param members 权威成员目录 @param roles 原角色授权公开端口 */
    public TenantMemberOptionsCommand(TenantAccess members,SubjectRoleManagement roles){
        this.members=members;this.roles=roles;
    }

    /** 授权候选仅使用当前可信租户，无客户端身份或过滤参数。 */
    public record Empty() {}

    @Override public Map<String,Class<?>> inputTypes(){return Map.of("authorizationOptions",Empty.class);}

    @Override public Map<String,Object> execute(Context context,Request request,Object input){
        if(!"TenantMember".equals(request.objectType())||!"authorizationOptions".equals(request.operationCode()))
            throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
        if(request.objectId()!=null||request.basisVersion()!=0)
            throw new Rejection(400,"BUSINESS_OBJECT_COMMAND_INVALID");
        UUID actor=UUID.fromString(context.actorId()),tenant=UUID.fromString(context.tenantId());
        var member=members.available(actor).stream().filter(value->tenant.equals(value.tenantId())).findFirst()
            .orElseThrow(()->new Rejection(403,"AUTHORIZATION_MANAGEMENT_DENIED"));
        return Map.of("result",read(members,roles,new RoleManagement.Context(actor,tenant,member.memberId())));
    }

    /**
     * @param members 权威目录
     * @param roles 既有授权检查端口
     * @param context 从当前会话得到的操作者、租户及成员
     * @return 同租户已授权候选，复用原读接口的授权条件
     */
    public static List<TenantAccess.MemberOption> read(TenantAccess members,SubjectRoleManagement roles,RoleManagement.Context context){
        roles.memberRoles(context,context.actorMemberId());
        return members.memberOptions(context.tenantId());
    }
}
