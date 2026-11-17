package com.appnexus.platform.configuration;

/** 字典新增入口的统一命名契约；历史不规范值只能由版本迁移处理，禁止在运行时猜测或改写业务含义。 */
public final class DictionaryValueStandard {
    private DictionaryValueStandard() {}

    /**
     * 校验字典或字典项编码；不自动 trim，避免客户端提交值与稳定标识不一致。
     * @param code 1–64 位小写英文、数字及下划线，以字母开头；禁止 UUID、哈希及旧生成编码
     * @return 是否满足新增编码约束
     */
    public static boolean validCode(String code) {
        return code != null && code.matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
            && code.length() <= 64 && !opaque(code);
    }

    /**
     * 校验稳定业务值；标准数字代码按字符串保留前导零，普通值使用大写英文语义码。
     * @param value 1–100 位大写英文、数字及下划线，或纯数字标准码；不允许空白、控制字符和技术主键
     * @return 是否可用于新建字典项；国家、行业标准的具体含义由字典定义约束
     */
    public static boolean validValue(String value) {
        return value != null && value.length() <= 100
            && (value.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*") || value.matches("[0-9]+"))
            && !opaque(value);
    }

    /** UUID 去连字符后仍是技术主键；预留历史 v_ 前缀的哈希形式，不能通过大小写变化绕过。 */
    private static boolean opaque(String value) {
        return value.matches("(?i)(?:v_)?[0-9a-f]{32,64}");
    }
}
