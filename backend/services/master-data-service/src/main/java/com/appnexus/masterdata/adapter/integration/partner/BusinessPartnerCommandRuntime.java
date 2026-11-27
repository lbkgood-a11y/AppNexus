package com.appnexus.masterdata.adapter.integration.partner;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.masterdata.api.masterdata.MasterData;
import com.appnexus.masterdata.api.masterdata.MasterDataManagement;
import com.appnexus.masterdata.api.masterdata.StatusHistory;
import com.appnexus.masterdata.application.masterdata.StatusHistoryQuery;
import com.appnexus.platform.tenant.TenantAccess;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

import static com.appnexus.masterdata.api.masterdata.MasterData.Kind.PARTNER;

/** 客商命令接入原应用端口；范围、字段、版本及事务继续由原用例执行。 */
@Component
public final class BusinessPartnerCommandRuntime implements BusinessObjectCommandRuntime {
    private static final Map<String,Class<?>> INPUTS=Map.of(
        "list",ListInput.class,"create",MasterData.Input.class,"update",MasterData.Input.class,
        "setStatus",MasterData.Status.class,"readStatusHistory",HistoryInput.class);
    private final MasterDataManagement records;
    private final StatusHistoryQuery history;
    private final TenantAccess tenants;

    /** @param records 原客商用例 @param history 原启停历史用例 @param tenants 当前成员权威目录 */
    public BusinessPartnerCommandRuntime(MasterDataManagement records,StatusHistoryQuery history,TenantAccess tenants){
        this.records=records;this.history=history;this.tenants=tenants;
    }

    /** 客商查询类型。RECORDS 为默认，其余与原 API 字典、地理及数据权限视图对应。 */
    public enum View { RECORDS, OPTIONS, GEOGRAPHY, DATA_ACCESS }

    /**
     * @param view 查询视图，缺省 RECORDS
     * @param keyword 查询关键词，缺省空串
     * @param enabled 启停过滤，空值表示均返回
     * @param page 起始页为 1，缺省 1
     * @param size 每页条数，缺省 20，由原用例校验
     * @param organizationAction 仅 DATA_ACCESS 使用，缺省 read
     */
    public record ListInput(View view,String keyword,Boolean enabled,Integer page,Integer size,String organizationAction) {}

    /** @param page 起始页为 1，缺省 1 @param size 每页 1 至 100 条，缺省 20 */
    public record HistoryInput(Integer page,Integer size) {}

    @Override public Map<String,Class<?>> inputTypes(){return INPUTS;}

    @Override public Map<String,Object> execute(Context context,Request request,Object payload){
        if(!"BusinessPartner".equals(request.objectType())||!INPUTS.containsKey(request.operationCode()))
            throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
        var trusted=member(context);
        try{
            return switch(request.operationCode()){
                case "list" -> Map.of("result",list(trusted,request,(ListInput)payload));
                case "create" -> {
                    if(request.objectId()!=null||request.basisVersion()!=0)throw invalid();
                    yield Map.of("result",records.create(trusted,PARTNER,(MasterData.Input)payload));
                }
                case "update" -> {
                    var input=(MasterData.Input)payload;
                    verifyVersion(request,input.version());
                    records.update(trusted,PARTNER,id(request),input);
                    yield Map.of();
                }
                case "setStatus" -> {
                    var input=(MasterData.Status)payload;
                    verifyVersion(request,input.version());
                    records.setStatus(trusted,PARTNER,id(request),input);
                    yield Map.of();
                }
                case "readStatusHistory" -> {
                    var input=(HistoryInput)payload;
                    yield Map.of("result",history.list(trusted,StatusHistory.Subject.PARTNER,id(request),
                        page(input.page()),size(input.size())));
                }
                default -> throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
            };
        }catch(MasterData.Rejection error){throw new Rejection(error.status,error.getMessage());}
    }

    private Object list(VerifiedTenantContext trusted,Request request,ListInput input){
        var view=input.view()==null?View.RECORDS:input.view();
        if(view!=View.RECORDS&&request.objectId()!=null)throw invalid();
        return switch(view){
            case RECORDS -> records.list(trusted,PARTNER,input.keyword()==null?"":input.keyword(),input.enabled(),
                page(input.page()),size(input.size()),request.objectId()==null?null:id(request));
            case OPTIONS -> records.dictionaryOptions(trusted,PARTNER);
            case GEOGRAPHY -> records.geographyCatalog(trusted,PARTNER);
            case DATA_ACCESS -> records.partnerAccess(trusted,input.organizationAction()==null?"read":input.organizationAction());
        };
    }

    private VerifiedTenantContext member(Context context){
        try{
            UUID actor=UUID.fromString(context.actorId()),tenant=UUID.fromString(context.tenantId());
            var member=tenants.available(actor).stream().filter(value->value.tenantId().equals(tenant))
                .findFirst().orElseThrow();
            if(!tenants.isActiveMember(tenant,member.memberId()))throw new java.util.NoSuchElementException();
            return new VerifiedTenantContext(actor,tenant,member.memberId(),1,
                optionalId(context.personId()),optionalId(context.organizationId()));
        }catch(IllegalArgumentException|java.util.NoSuchElementException error){
            throw new Rejection(403,"BUSINESS_OBJECT_ACCESS_DENIED");
        }
    }

    private static UUID optionalId(String id){return id==null?null:UUID.fromString(id);}
    private static int page(Integer page){return page==null?1:page;}
    private static int size(Integer size){return size==null?20:size;}
    private static void verifyVersion(Request request,long version){
        if(!request.command().containsKey("version")||request.command().get("version")==null||request.basisVersion()!=version)
            throw invalid();
    }
    private static String id(Request request){
        try{
            String id=request.objectId();
            if(id!=null&&UUID.fromString(id).toString().equals(id))return id;
        }catch(IllegalArgumentException error){throw invalid();}
        throw invalid();
    }
    private static Rejection invalid(){return new Rejection(400,"BUSINESS_OBJECT_COMMAND_INVALID");}
}
