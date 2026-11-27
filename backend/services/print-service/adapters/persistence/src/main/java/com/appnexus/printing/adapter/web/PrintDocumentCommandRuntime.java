package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintData.DocumentRequest;
import com.appnexus.printing.api.PrintData.DocumentPrintRequest;
import com.appnexus.printing.api.PrintSceneManagement;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 人员单据查询、创建和打印命令。以原用例处理场景、契约及可信本人范围。 */
@Component
public final class PrintDocumentCommandRuntime implements BusinessObjectCommandRuntime {
    private final PrintSceneManagement printing;

    /** @param printing 原本人单据打印公开端口，拥有打印授权和执行事务 */
    public PrintDocumentCommandRuntime(PrintSceneManagement printing){this.printing=printing;}

    /** 本人单据列表不允许自定义主体或范围。 */
    public record ListOptions() {}

    @Override public Map<String,Class<?>> inputTypes(){
        return Map.of("list",ListOptions.class,"create",DocumentRequest.class,"print",DocumentPrintRequest.class);
    }

    @Override public Map<String,Object> execute(Context context,Request request,Object input){
        if(!"PrintDocument".equals(request.objectType()))throw unavailable();
        if(request.basisVersion()!=0)throw new Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
        var trusted=new PrintContext(context.tenantId(),context.actorId(),context.requestId());
        return switch(request.operationCode()){
            case "list" -> {
                if(!(input instanceof ListOptions))throw unavailable();
                requireNoId(request);
                yield PrintCommandSupport.result(()->printing.documents(trusted));
            }
            case "create" -> {
                if(!(input instanceof DocumentRequest options))throw unavailable();
                requireNoId(request);
                yield PrintCommandSupport.result(()->printing.saveDocument(trusted,options));
            }
            case "print" -> {
                if(!(input instanceof DocumentPrintRequest options))throw unavailable();
                yield PrintCommandSupport.result(()->printing.print(trusted,request.objectId(),options));
            }
            default -> throw unavailable();
        };
    }

    // 查询与新建由当前会话和原场景确定范围，禁止用通用记录键表达额外筛选或自定义单据 ID。
    private static void requireNoId(Request request){
        if(request.objectId()!=null)throw new Rejection(400,"PRINT_INPUT_INVALID");
    }

    private static Rejection unavailable(){return new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");}
}
