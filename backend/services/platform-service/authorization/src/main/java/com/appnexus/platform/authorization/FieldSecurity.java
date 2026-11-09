package com.appnexus.platform.authorization;

import java.util.List;

/** 字段安全目录与最小披露规则；只登记已有后端执行点的字段，不接受任意属性路径。 */
public final class FieldSecurity {
    /** 强制展示上限；多个角色同时命中时 HIDDEN 优先于 MASKED，不能用普通角色原文权限绕过。 */
    public enum Mode { HIDDEN, MASKED }
    /** 脱敏算法按语义类型统一复用，业务字段不编写专用算法。 */
    public enum Kind { EMAIL, PHONE, PERSON_NAME, IDENTIFIER, BANK_ACCOUNT, TEXT }
    /** @param resource 稳定资源名 @param field 稳定字段名 @param mode 强制展示上限，不包含修改权或查询权 */
    public record Policy(String resource,String field,Mode mode) {}
    /** @param resource 稳定资源名 @param field 字段名 @param nameZh 中文名称 @param nameEn 英文名称 @param kind 敏感类型及算法 @param modes 已接通的展示限制 */
    public record Descriptor(String resource,String field,String nameZh,String nameEn,Kind kind,List<Mode> modes) {}
    private FieldSecurity() {}
    /**
     * 对联系方式应用字段限制；隐藏返回 NULL，空值保持 NULL，短值整段遮盖避免暴露全部内容。
     * @param kind 目录登记的敏感类型，不能按字段名称猜测算法
     * @param value 数据库联系方式，仅在服务端处理，不进入日志
     * @param mode 强制展示模式，NULL 表示无额外限制
     * @return 原文、遮盖值或 NULL，不能用于写回数据库
     */
    public static String display(Kind kind,String value,Mode mode) {
        if(value==null||mode==Mode.HIDDEN)return null;
        if(mode==null)return value;
        return switch(kind) {
            case EMAIL -> {int at=value.indexOf('@');yield at>0?value.substring(0,1)+"***@"+value.substring(at+1):"***";}
            case PHONE -> value.length()>7?value.substring(0,3)+"****"+value.substring(value.length()-4):"***";
            case PERSON_NAME -> value.isEmpty()?"***":value.substring(0,value.offsetByCodePoints(0,1))+"***";
            case IDENTIFIER,BANK_ACCOUNT -> value.length()>4?"****"+value.substring(value.length()-4):"***";
            case TEXT -> "***";
        };
    }
}
