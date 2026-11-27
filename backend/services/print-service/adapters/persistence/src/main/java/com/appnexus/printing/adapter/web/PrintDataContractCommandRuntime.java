package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.printing.PrintContext;
import com.appnexus.printing.api.PrintSceneManagement;
import java.util.List;
import java.util.Map;

/** 人员打印数据契约命令；字段和明细白名单均由原契约用例校验。 */
public final class PrintDataContractCommandRuntime implements BusinessObjectCommandRuntime {
    private final PrintSceneManagement printing;

    /** @param printing 原打印数据契约目录与新增端口 */
    public PrintDataContractCommandRuntime(PrintSceneManagement printing){this.printing=printing;}

    /** 契约目录从当前可信租户读取，不接受过滤或身份参数。 */
    public record ListOptions() {}

    /**
     * @param code 原租户契约编码，须符合既有命名及唯一约束
     * @param nameZh 中文名称，非空且最多 128 字符
     * @param nameEn 英文名称，非空且最多 128 字符
     * @param fields 既有字段结构，最多 50 项，原用例校验键、类型和一层明细
     */
    public record ContractDefinition(String code,String nameZh,String nameEn,List<Map<String,Object>> fields) {}

    @Override public Map<String,Class<?>> inputTypes(){
        return Map.of("list",ListOptions.class,"create",ContractDefinition.class);
    }

    @Override public Map<String,Object> execute(Context context,Request request,Object input){
        if(!"PrintDataContract".equals(request.objectType()))throw unavailable();
        if(request.objectId()!=null)throw new Rejection(400,"PRINT_INPUT_INVALID");
        if(request.basisVersion()!=0)throw new Rejection(409,"BUSINESS_OBJECT_VERSION_CONFLICT");
        var trusted=new PrintContext(context.tenantId(),context.actorId(),context.requestId());
        return switch(request.operationCode()){
            case "list" -> {
                if(!(input instanceof ListOptions))throw unavailable();
                yield PrintCommandSupport.result(()->printing.contracts(trusted));
            }
            case "create" -> {
                if(!(input instanceof ContractDefinition definition))throw unavailable();
                yield PrintCommandSupport.result(()->printing.createContract(trusted,definition));
            }
            default -> throw unavailable();
        };
    }

    private static Rejection unavailable(){return new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");}
}
