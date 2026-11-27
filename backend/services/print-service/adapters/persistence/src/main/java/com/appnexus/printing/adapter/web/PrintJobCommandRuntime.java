package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintRuntimeManagement;
import java.util.Map;

/** 人员任务查询和补打命令。查询使用可信租户，补打复用原任务 ID 与快照。 */
public final class PrintJobCommandRuntime implements BusinessObjectCommandRuntime {
    private final PrintRuntimeManagement printing;

    /** @param printing 原打印任务端口，保持原补打权限、事务和幂等 */
    public PrintJobCommandRuntime(PrintRuntimeManagement printing){this.printing=printing;}

    /** @param reason 必填补打原因，去空白后 1 至 500 字符 */
    public record ReprintOptions(String reason) {}

    /** 任务列表固定使用可信租户，不接受客户端过滤边界。 */
    public record ListOptions() {}

    @Override public Map<String,Class<?>> inputTypes(){
        return Map.of("list",ListOptions.class,"reprint",ReprintOptions.class);
    }

    @Override public Map<String,Object> execute(Context context,Request request,Object input){
        if(!"PrintJob".equals(request.objectType()))throw unavailable();
        if(request.basisVersion()!=0)throw new Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
        var trusted=new PrintContext(context.tenantId(),context.actorId(),context.requestId());
        return switch(request.operationCode()){
            case "list" -> {
                if(!(input instanceof ListOptions))throw unavailable();
                if(request.objectId()!=null)throw new Rejection(400,"PRINT_INPUT_INVALID");
                yield PrintCommandSupport.result(()->printing.jobs(trusted));
            }
            case "reprint" -> {
                if(!(input instanceof ReprintOptions options))throw unavailable();
                yield PrintCommandSupport.result(()->printing.reprint(trusted,request.objectId(),options.reason()));
            }
            default -> throw unavailable();
        };
    }

    private static Rejection unavailable(){return new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");}
}
