package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.SceneRequest;
import com.appnexus.printing.api.PrintSceneManagement;
import java.util.Map;

/** 人员打印场景命令；原场景用例拥有已发布模板、契约、版本及事务校验。 */
public final class PrintSceneCommandRuntime implements BusinessObjectCommandRuntime {
    private final PrintSceneManagement printing;

    /** @param printing 原打印场景目录及保存端口 */
    public PrintSceneCommandRuntime(PrintSceneManagement printing){this.printing=printing;}

    /** 场景查询固定读取可信租户。 */
    public record ListOptions() {}

    @Override public Map<String,Class<?>> inputTypes(){
        return Map.of("list",ListOptions.class,"create",SceneRequest.class,"update",SceneRequest.class);
    }

    @Override public Map<String,Object> execute(Context context,Request request,Object input){
        if(!"PrintScene".equals(request.objectType()))throw unavailable();
        var trusted=new PrintContext(context.tenantId(),context.actorId(),context.requestId());
        return switch(request.operationCode()){
            case "list" -> {
                if(!(input instanceof ListOptions))throw unavailable();
                requireNoRecord(request);
                yield PrintCommandSupport.result(()->printing.scenes(trusted));
            }
            case "create" -> {
                if(!(input instanceof SceneRequest scene))throw unavailable();
                requireNoRecord(request);
                yield PrintCommandSupport.result(()->printing.saveScene(trusted,null,scene));
            }
            case "update" -> {
                if(!(input instanceof SceneRequest scene))throw unavailable();
                if(request.objectId()==null)throw new Rejection(400,"PRINT_INPUT_INVALID");
                if(request.basisVersion()!=scene.revision())
                    throw new Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
                yield PrintCommandSupport.result(()->printing.saveScene(trusted,request.objectId(),scene));
            }
            default -> throw unavailable();
        };
    }

    private static void requireNoRecord(Request request){
        if(request.objectId()!=null)throw new Rejection(400,"PRINT_INPUT_INVALID");
        if(request.basisVersion()!=0)throw new Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
    }

    private static Rejection unavailable(){return new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");}
}
