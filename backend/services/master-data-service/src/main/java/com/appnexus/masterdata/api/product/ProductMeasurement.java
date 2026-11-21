package com.appnexus.masterdata.api.product;

import java.util.List;

/** 商品计量配置契约；单位引用均为当前租户已存在的单位 UUID。 */
public final class ProductMeasurement {
    private ProductMeasurement() {}

    /**
     * 业务单位及使用方式；一个场景可有多个单位和一个默认值。
     * @param unitId 具体单位 UUID
     * @param scene 到货、入仓或出仓，分别使用 ARRIVAL、INBOUND、OUTBOUND
     * @param method 数量确定方式 DIRECT、CONVERSION、PACKAGING 或 MEASURED
     * @param defaultUnit 是否为同场景默认单位
     */
    public record Unit(String unitId, String scene, String method, boolean defaultUnit) {}

    /**
     * 商品固定包装规格；一个包装单位可对应多个稳定规格编码。
     * @param code 同商品唯一编码，最多64字符
     * @param name 规格显示名称，最多200字符
     * @param unitId 包装单位 UUID
     * @param quantity 每包装包含的基础单位数量，正十进制文本
     */
    public record Packaging(String code, String name, String unitId, String quantity) {}

    /**
     * 商品计量整体变更，沿用父商品版本和审批冻结。
     * @param version 当前父商品版本，必填
     * @param units 当前业务单位列表，空集合恢复基础单位默认行为
     * @param packaging 当前包装列表，空集合表示无包装规则
     */
    public record Change(Long version, List<Unit> units, List<Packaging> packaging) {}
}
