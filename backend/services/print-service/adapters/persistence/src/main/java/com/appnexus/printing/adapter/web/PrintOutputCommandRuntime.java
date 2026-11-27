package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.DocumentOutputRequest;
import com.appnexus.printing.api.PrintOutputManagement;
import java.util.Map;

/** 人员打印输出命令，预览确认、权限及事务由原输出用例拥有。 */
public final class PrintOutputCommandRuntime implements BusinessObjectCommandRuntime {
    private static final Map<String,Class<?>> INPUTS=Map.of(
        "inspect",InspectOptions.class,"readStatus",StatusOptions.class,
        "create",DocumentOutputRequest.class,"confirm",ConfirmOptions.class);
    private final PrintOutputManagement outputs;

    /** @param outputs 原打印输出应用端口 */
    public PrintOutputCommandRuntime(PrintOutputManagement outputs){this.outputs=outputs;}

    /** @param deviceId 本租户启用设备 UUID，缺省沿用输出请求保存的设备 */
    public record ConfirmOptions(String deviceId) {}

    /** @param mode PREVIEW 或 SILENT @param deviceId SILENT 时提供的启用设备 UUID */
    public record InspectOptions(String mode,String deviceId) {}

    /** 人员状态查询只使用输出请求 UUID，不接收主体或过滤条件。 */
    public record StatusOptions() {}

    @Override public Map<String,Class<?>> inputTypes(){return INPUTS;}

    @Override public Map<String,Object> execute(Context context,Request request,Object input){
        if(!"PrintOutput".equals(request.objectType())||!INPUTS.containsKey(request.operationCode()))
            throw unavailable();
        if(request.basisVersion()!=0)throw new Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
        var trusted=new PrintContext(context.tenantId(),context.actorId(),context.requestId());
        return switch(request.operationCode()){
            case "inspect" -> {
                if(!(input instanceof InspectOptions options))throw unavailable();
                yield PrintCommandSupport.result(()->inspect(trusted,request.objectId(),options));
            }
            case "readStatus" -> {
                if(!PrintOutputDeclaration.PERSON_STATUS_ENTRY.equals(request.operationId())
                    ||!(input instanceof StatusOptions))throw unavailable();
                yield PrintCommandSupport.result(()->outputs.status(trusted,request.objectId()));
            }
            case "create" -> {
                if(!(input instanceof DocumentOutputRequest options))throw unavailable();
                yield PrintCommandSupport.result(()->outputs.document(trusted,request.objectId(),options));
            }
            case "confirm" -> {
                // 原主入口属于外部调用。人员公共命令只能使用原人员 API 的稳定入口别名。
                if(!PrintOutputDeclaration.PERSON_CONFIRM_ENTRY.equals(request.operationId())
                    ||!(input instanceof ConfirmOptions options))throw unavailable();
                yield PrintCommandSupport.result(()->outputs.confirm(trusted,request.objectId(),options.deviceId()));
            }
            default -> throw unavailable();
        };
    }

    // 复用原分层预检。公共命令对缺失配置明确失败，暂时离线仍是只读诊断。
    private Object inspect(PrintContext context,String document,InspectOptions options){
        var checks=outputs.preflight(context,document,options.mode(),options.deviceId());
        for(var check:checks){
            switch(check.reason()){
                case "PRINT_TARGET_INVALID","PRINT_DEVICE_UNAVAILABLE" -> throw new Rejection(409,check.reason());
                case "PRINT_DEVICE_REQUIRED" -> throw new Rejection(400,check.reason());
                default -> { }
            }
        }
        return checks;
    }

    private static Rejection unavailable(){return new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");}
}
