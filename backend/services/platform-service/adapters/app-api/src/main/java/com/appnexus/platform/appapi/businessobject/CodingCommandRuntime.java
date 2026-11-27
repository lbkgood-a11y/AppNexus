package com.appnexus.platform.appapi.businessobject;

import com.appnexus.contracts.businessobject.BusinessObjectCommandRuntime;
import com.appnexus.contracts.businessobject.BusinessObjectExecution.Request;
import com.appnexus.contracts.businessobject.BusinessObjects.Context;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.businessobject.CodingManagement;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** 编码配置命令适配，使用原读取、写入及版本控制用例。 */
final class CodingCommandRuntime implements BusinessObjectCommandRuntime {
    private static final Map<String,Class<?>> ENCODER_INPUTS=Map.of(
        "list",EncoderQuery.class,"ruleOptions",EncoderQuery.class,"save",EncoderSave.class);
    private static final Map<String,Class<?>> RULE_INPUTS=Map.of("list",Empty.class,"save",CodingManagement.RuleInput.class);

    private final Supplier<CodingManagement> service;
    private final String objectType;
    private final Map<String,Class<?>> types;

    /** @param service 延迟取得编码用例 @param rules true 接入规则，false 接入编码器 */
    CodingCommandRuntime(Supplier<CodingManagement> service,boolean rules){
        this.service=service;
        this.objectType=rules?"CodingRule":"CodingEncoder";
        this.types=rules?RULE_INPUTS:ENCODER_INPUTS;
    }

    /** @param targetObjectType 已声明并向租户开放的业务对象编码 */
    public record EncoderQuery(String targetObjectType) {}

    /**
     * @param targetObjectType 已向当前租户开放的业务对象编码
     * @param input 原编码器配置与版本契约，必填
     */
    public record EncoderSave(String targetObjectType,CodingManagement.Input input) {}

    /** 规则目录命令不接收业务参数。 */
    public record Empty() {}

    @Override public Map<String,Class<?>> inputTypes(){return types;}

    @Override public Map<String,Object> execute(Context context,Request request,Object payload){
        if(!objectType.equals(request.objectType())||!types.containsKey(request.operationCode()))
            throw new Rejection(403,"BUSINESS_OBJECT_COMMAND_UNAVAILABLE");
        boolean write="save".equals(request.operationCode());
        // 配置使用原 revision 语义，包括首次保存的 -1；公共记录版本不参与配置写入。
        if(request.basisVersion()!=0||write!=(request.objectId()!=null))throw invalid();
        var coding=service.get();
        try{
            if("CodingRule".equals(objectType)){
                if(!write)return Map.of("result",coding.rules(context));
                coding.saveRule(context,request.objectId(),(CodingManagement.RuleInput)payload);
                return Map.of();
            }
            if(write){
                var save=(EncoderSave)payload;
                coding.save(context,Objects.requireNonNull(save.targetObjectType()),request.objectId(),
                    Objects.requireNonNull(save.input()));
                return Map.of();
            }
            String target=Objects.requireNonNull(((EncoderQuery)payload).targetObjectType());
            return Map.of("result","list".equals(request.operationCode())
                ? coding.configurations(context,target) : coding.ruleOptions(context,target));
        }catch(IllegalArgumentException|NullPointerException|java.time.DateTimeException error){
            throw invalid();
        }
    }

    private static Rejection invalid(){return new Rejection(400,"CODING_INPUT_INVALID");}
}
