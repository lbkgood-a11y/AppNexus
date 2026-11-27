package com.appnexus.platform.managementapi.administration;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.identity.MultiFactorAuthentication;
import com.appnexus.platform.identity.PasswordRecovery;
import com.appnexus.platform.security.MfaController;
import com.appnexus.platform.security.PasswordRecoveryController;
import com.appnexus.platform.tenant.PlatformAdministration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 平台账号及成员代管命令，复用专用 API 的身份约束、近期 MFA 和原事务用例。 */
final class PlatformDirectoryCommandRuntime implements BusinessObjectCommandRuntime {
    private static final Map<String,Class<?>> ACCOUNT_INPUTS=Map.of(
        "list",DirectoryQuery.class,"create",PlatformAdministrationController.NewUser.class,
        "update",PlatformAdministrationController.UpdateUser.class,"setEnabled",PlatformAdministrationController.State.class,
        "revokeSessions",Empty.class,"issueRecovery",Empty.class);
    private static final Map<String,Class<?>> MEMBER_INPUTS=Map.of(
        "listManaged",MemberQuery.class,"createManaged",NewMember.class,
        "setEnabledManaged",MemberState.class,"removeManaged",MemberTarget.class);
    private final String objectType;
    private final PlatformAdministration service;
    private final PasswordEncoder encoder;
    private final MultiFactorAuthentication mfa;
    private final PasswordRecovery recovery;

    /** @param objectType 固定平台目录对象 @param service 原管理用例 @param encoder 原账号密码编码器
     * @param mfa 近期第二因子权威校验 @param recovery 原找回凭证签发用例 */
    PlatformDirectoryCommandRuntime(String objectType,PlatformAdministration service,PasswordEncoder encoder,
                                    MultiFactorAuthentication mfa,PasswordRecovery recovery){
        if(!"Account".equals(objectType)&&!"TenantMember".equals(objectType))throw new IllegalArgumentException();
        this.objectType=objectType;this.service=service;this.encoder=encoder;this.mfa=mfa;this.recovery=recovery;
    }

    /** 空输入；目标账号取自公共命令对象键。 */
    public record Empty() {}
    /** @param page 缺省 1 @param size 缺省 20，范围 1 至 100 @param query 关键词，缺省空 */
    public record DirectoryQuery(Integer page,Integer size,String query) {}
    /** @param targetTenantId 管理目标租户，不参与认证 @param page 缺省 1 @param size 缺省 20 @param query 关键词 */
    public record MemberQuery(UUID targetTenantId,Integer page,Integer size,String query) {}
    /** @param targetTenantId 管理目标租户 @param accountId 待加入账号 */
    public record NewMember(UUID targetTenantId,UUID accountId) {}
    /** @param targetTenantId 管理目标租户 @param enabled 目标状态必填 */
    public record MemberState(UUID targetTenantId,Boolean enabled) {}
    /** @param targetTenantId 管理目标租户 */
    public record MemberTarget(UUID targetTenantId) {}

    @Override public Map<String,Class<?>> inputTypes(){return "Account".equals(objectType)?ACCOUNT_INPUTS:MEMBER_INPUTS;}
    @Override public boolean platformOperation(String operation){return inputTypes().containsKey(operation);}

    @Override public Map<String,Object> execute(Context context,Request request,Object input){
        if(!objectType.equals(request.objectType())||!platformOperation(request.operationCode()))
            throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
        var http=currentRequest(context);
        UUID actor=UUID.fromString(context.actorId());
        try{
            if(request.basisVersion()!=0)throw new IllegalArgumentException();
            return "Account".equals(objectType)?account(actor,http,request,input):member(actor,request,input);
        }catch(DuplicateKeyException duplicate){
            throw new Rejection(409,"DIRECTORY_CONFLICT");
        }catch(IllegalArgumentException|NullPointerException invalid){
            throw new Rejection(400,"INVALID_DIRECTORY_INPUT");
        }
    }

    private Map<String,Object> account(UUID actor,HttpServletRequest http,Request request,Object input){
        return switch(request.operationCode()){
            case "list" -> {
                requireNoId(request);
                var value=(DirectoryQuery)input;
                yield result(service.users(actor,page(value.page()),size(value.size()),query(value.query())));
            }
            case "create" -> {
                requireNoId(request);
                yield result(createUser(service,encoder,actor,(PlatformAdministrationController.NewUser)input));
            }
            case "update" -> {
                var value=(PlatformAdministrationController.UpdateUser)input;
                yield result(service.updateUser(actor,id(request),value.displayName(),value.email(),value.phoneNumber()));
            }
            case "setEnabled" -> {
                service.setUserEnabled(actor,id(request),Objects.requireNonNull(((PlatformAdministrationController.State)input).enabled()));
                yield Map.of();
            }
            case "revokeSessions" -> {revoke(service,mfa,actor,id(request),http);yield Map.of();}
            case "issueRecovery" -> result(issueRecovery(recovery,mfa,actor,id(request),http));
            default -> throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
        };
    }

    private Map<String,Object> member(UUID actor,Request request,Object input){
        return switch(request.operationCode()){
            case "listManaged" -> {
                requireNoId(request);
                var value=(MemberQuery)input;
                yield result(service.members(actor,Objects.requireNonNull(value.targetTenantId()),
                    page(value.page()),size(value.size()),query(value.query())));
            }
            case "createManaged" -> {
                requireNoId(request);
                var value=(NewMember)input;
                service.addMember(actor,Objects.requireNonNull(value.targetTenantId()),Objects.requireNonNull(value.accountId()));
                yield Map.of();
            }
            case "setEnabledManaged" -> {
                var value=(MemberState)input;
                service.setMemberEnabled(actor,Objects.requireNonNull(value.targetTenantId()),id(request),
                    Objects.requireNonNull(value.enabled()));
                yield Map.of();
            }
            case "removeManaged" -> {
                service.removeMember(actor,Objects.requireNonNull(((MemberTarget)input).targetTenantId()),id(request));
                yield Map.of();
            }
            default -> throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
        };
    }

    /** 与原专用入口共用密码处理，业务服务继续执行创建权限、格式和事务校验。 */
    static PlatformAdministration.User createUser(PlatformAdministration service,PasswordEncoder encoder,UUID actor,
                                                   PlatformAdministrationController.NewUser input){
        if(!service.isAdministrator(actor))throw new SecurityException();
        if(input.password()==null||input.password().length()<12||input.password().length()>128)throw new IllegalArgumentException();
        return service.createUser(actor,com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate(),
            input.username(),input.displayName(),input.email(),input.phoneNumber(),encoder.encode(input.password()));
    }

    /** 两个 HTTP 入口都在原会话上检查近期 MFA 后撤销会话。 */
    static void revoke(PlatformAdministration service,MultiFactorAuthentication mfa,UUID actor,UUID target,HttpServletRequest request){
        MfaController.requireRecent(mfa,actor,request);
        service.revokeUserSessions(actor,target);
    }

    /** 两个 HTTP 入口都在原会话上检查近期 MFA 后签发凭证。 */
    static PasswordRecoveryController.Ticket issueRecovery(PasswordRecovery recovery,MultiFactorAuthentication mfa,
                                                           UUID actor,UUID target,HttpServletRequest request){
        MfaController.requireRecent(mfa,actor,request);
        return new PasswordRecoveryController.Ticket(recovery.issue(actor,target));
    }

    private static HttpServletRequest currentRequest(Context context){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(context.tenantId()!=null||auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken
            ||!auth.getName().equals(context.actorId())
            ||!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            throw new Rejection(403,"BUSINESS_OBJECT_ACCESS_DENIED");
        return attributes.getRequest();
    }

    private static void requireNoId(Request request){if(request.objectId()!=null)throw new IllegalArgumentException();}
    private static UUID id(Request request){return UUID.fromString(Objects.requireNonNull(request.objectId()));}
    private static int page(Integer value){return value==null?1:value;}
    private static int size(Integer value){return value==null?20:value;}
    private static String query(String value){return value==null?"":value;}
    private static Map<String,Object> result(Object value){return Map.of("result",value);}
}
