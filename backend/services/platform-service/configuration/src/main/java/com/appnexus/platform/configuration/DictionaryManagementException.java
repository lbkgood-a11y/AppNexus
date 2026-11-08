package com.appnexus.platform.configuration;

/** 携带稳定业务码的字典冲突；HTTP 适配器不得依赖中文消息判断错误类型。 */
public final class DictionaryManagementException extends RuntimeException {
    private final String code;
    public DictionaryManagementException(String code, String message){super(message);this.code=code;}
    /** @return 可供前端国际化映射的稳定业务码 */
    public String code(){return code;}
}
