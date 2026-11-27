package com.appnexus.contracts.businessobject;

import java.util.Map;

/** 公共命令的对象级出站边界；按已登记操作解码固定输入，并调用原受控业务用例。 */
public interface BusinessObjectCommandRuntime {
    /**
     * @return 已接入命令的操作编码和固定输入类型；调用方不能指定 Java 类型
     */
    Map<String, Class<?>> inputTypes();

    /** @param operation 固定业务操作编码 @return 是否只接受平台管理入口验证的身份 */
    default boolean platformOperation(String operation){return false;}

    /**
     * @param context 通过公共准入的可信身份
     * @param request 已核对对象及操作的命令请求；业务参数仍按固定类型校验
     * @param input 平台按本操作固定类型完成绑定的输入
     * @return 原受控业务用例的响应
     */
    Map<String, Object> execute(BusinessObjects.Context context, BusinessObjectExecution.Request request,Object input);

    /** 宿主提供的命令解码器，仅使用固定映射中的类型。 */
    @FunctionalInterface
    interface Decoder {
        /**
         * @param command 请求中的业务参数
         * @param type 登记的固定 Java 类型
         * @return 通过类型检查且未接受未知字段的参数实例
         */
        Object decode(Map<String,Object> command,Class<?> type);
    }
}
