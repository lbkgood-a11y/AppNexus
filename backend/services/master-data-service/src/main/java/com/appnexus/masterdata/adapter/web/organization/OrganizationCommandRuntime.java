package com.appnexus.masterdata.adapter.web.organization;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.masterdata.organization.OrganizationStructure;
import com.appnexus.masterdata.organization.OrganizationSummary;
import com.appnexus.masterdata.organization.OrganizationUnit;
import com.appnexus.masterdata.organization.port.OrganizationUnitRepository;
import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 组织命令复用原组织端口及可信请求租户；保留固定范围、事务、组织版本与关系约束。 */
@Component
public final class OrganizationCommandRuntime implements BusinessObjectCommandRuntime {
    private static final Map<String,Class<?>> INPUTS=Map.of(
        "listFlat",Empty.class,"createFlat",Name.class,"rename",Name.class,"delete",Empty.class,
        "list",Empty.class,"create",CreateUnit.class,"update",UpdateUnit.class,"move",Move.class,
        "inspectImpact",Empty.class,"summary",Empty.class);
    private final OrganizationUnitRepository units;
    private final OrganizationStructure structure;
    private final OrganizationSummary summary;

    /** @param units 原平面组织端口 @param structure 原领域组织端口 @param summary 原受控摘要端口 */
    public OrganizationCommandRuntime(OrganizationUnitRepository units,OrganizationStructure structure,OrganizationSummary summary){
        this.units=units;this.structure=structure;this.summary=summary;
    }

    /** 无业务参数的查询或按对象键执行的操作。 */
    public record Empty() {}
    /** @param name 组织名称，1 至 100 字符，不能为空白 */
    public record Name(String name) {}
    /** @param code 组织编码，1 至 50 个规范字符 @param typeId 启用类型 UUID @param parentId 可空父组织 @param name 组织名称 */
    public record CreateUnit(String code,UUID typeId,UUID parentId,String name) {}
    /** @param name 新名称 @param enabled 启停，必填 @param version 原记录版本，非负 */
    public record UpdateUnit(String name,Boolean enabled,Long version) {}
    /** @param parentId 新父组织，空表示移到根 @param version 原记录版本，非负 */
    public record Move(UUID parentId,Long version) {}

    @Override public Map<String,Class<?>> inputTypes(){return INPUTS;}

    /** @return 同一组织用例的任职人员目录命令，使用原自动字段保护响应类型 */
    public BusinessObjectCommandRuntime directory(){
        return new BusinessObjectCommandRuntime(){
            @Override public Map<String,Class<?>> inputTypes(){return Map.of("list",Empty.class);}

            @Override public Map<String,Object> execute(Context context,Request request,Object payload){
                verified(context);
                if(!"PersonDirectory".equals(request.objectType())||!"list".equals(request.operationCode()))
                    throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
                requireNoId(request);
                return result(structure.persons());
            }
        };
    }

    @Override public Map<String,Object> execute(Context context,Request request,Object payload){
        var trusted=verified(context);
        if(!"OrganizationUnit".equals(request.objectType())||!INPUTS.containsKey(request.operationCode()))
            throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
        try{return invoke(trusted,request,payload);}
        catch(java.util.NoSuchElementException error){throw new Rejection(404,"ORGANIZATION_OBJECT_NOT_FOUND");}
        catch(org.springframework.dao.DuplicateKeyException error){throw new Rejection(409,"ORGANIZATION_CODE_CONFLICT");}
        catch(org.springframework.dao.DataIntegrityViolationException error){throw new Rejection(409,"ORGANIZATION_REFERENCE_CONFLICT");}
        catch(IllegalStateException error){
            throw new Rejection(409,"CONCURRENT_MODIFICATION".equals(error.getMessage())
                ?"ORGANIZATION_CHANGED":"ORGANIZATION_STATE_CONFLICT");
        }catch(IllegalArgumentException|NullPointerException error){
            throw new Rejection(400,"INVALID_ORGANIZATION_INPUT");
        }
    }

    private Map<String,Object> invoke(VerifiedTenantContext trusted,Request request,Object payload){
        return switch(request.operationCode()){
            case "listFlat" -> {requireNoId(request);yield result(units.findAll());}
            case "list" -> {requireNoId(request);yield result(structure.units());}
            case "summary" -> {
                requireNoId(request);
                yield result(summary.summarize(AccessAuthorization.Subject.tenant(
                    trusted.accountId(),trusted.tenantId(),trusted.memberId())));
            }
            case "createFlat" -> {
                requireNoId(request);
                yield result(createFlat(structure,name(((Name)payload).name(),false)));
            }
            case "create" -> {
                requireNoId(request);
                var input=(CreateUnit)payload;
                if(input.code()==null||!input.code().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,49}")||input.typeId()==null)
                    throw invalid();
                yield result(structure.createUnit(input.code(),input.typeId(),input.parentId(),name(input.name(),true)));
            }
            case "rename" -> {
                if(!units.rename(id(request),name(((Name)payload).name(),false)))throw missing();
                yield Map.of();
            }
            case "delete" -> {
                try{if(!units.delete(id(request)))throw missing();}
                catch(IllegalStateException assigned){throw new Rejection(409,"ORGANIZATION_HAS_MEMBERS");}
                yield Map.of();
            }
            case "update" -> {
                var input=(UpdateUnit)payload;
                if(input.enabled()==null)throw invalid();
                yield result(structure.updateUnit(id(request),name(input.name(),true),input.enabled(),version(request,input.version())));
            }
            case "move" -> {
                var input=(Move)payload;
                yield result(structure.moveUnit(id(request),input.parentId(),version(request,input.version())));
            }
            case "inspectImpact" -> result(structure.impact(id(request)));
            default -> throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
        };
    }

    /** 使用原平面创建流程，保留默认业务类型及服务端生成编码。 */
    static OrganizationUnit createFlat(OrganizationStructure structure,String name){
        var type=structure.types().stream().filter(value->value.code().equals("BUSINESS")).findFirst()
            .orElseGet(()->structure.createType("BUSINESS","业务组织"));
        var created=structure.createUnit("ORG-"+UUID.randomUUID().toString().replace("-","")
            .substring(0,12).toUpperCase(Locale.ROOT),type.id(),null,name);
        return new OrganizationUnit(created.id(),created.name());
    }

    private static VerifiedTenantContext verified(Context context){
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
            &&attributes.getRequest().getAttribute(VerifiedTenantContext.class.getName()) instanceof VerifiedTenantContext trusted
            &&trusted.tenantId().toString().equals(context.tenantId())&&trusted.accountId().toString().equals(context.actorId()))
            return trusted;
        throw new Rejection(403,"BUSINESS_OBJECT_ACCESS_DENIED");
    }
    private static void requireNoId(Request request){
        if(request.objectId()!=null||request.basisVersion()!=0)throw invalid();
    }
    private static UUID id(Request request){
        try{if(request.objectId()!=null)return UUID.fromString(request.objectId());}
        catch(IllegalArgumentException e){throw invalid();}
        throw invalid();
    }
    private static long version(Request request,Long version){
        if(version==null||version<0||request.basisVersion()!=version)throw invalid();
        return version;
    }
    private static String name(String value,boolean trim){
        if(value==null||value.isBlank()||value.length()>100)throw invalid();
        return trim?value.trim():value;
    }
    private static Map<String,Object> result(Object value){return Map.of("result",value);}
    private static Rejection invalid(){return new Rejection(400,"ORGANIZATION_COMMAND_INVALID");}
    private static Rejection missing(){return new Rejection(404,"ORGANIZATION_NOT_FOUND");}
}
