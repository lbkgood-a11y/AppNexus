package com.appnexus.masterdata.api.masterdata;

import com.appnexus.masterdata.api.masterdata.MasterData.Kind;
import java.time.Instant;
import java.util.List;

/** 固定主数据启停历史的只读契约；不返回业务快照。 */
public final class StatusHistory {
    private StatusHistory() {}

    /** 记录类型由真实 API 路由确定，客户端不提交任意表名或权限编码。 */
    public enum Subject {
        PARTNER(Kind.PARTNER), WAREHOUSE(Kind.WAREHOUSE),
        STORAGE_AREA(Kind.STORAGE_AREA), STORAGE_LOCATION(Kind.STORAGE_LOCATION),
        COUNTRY_REGION(Kind.COUNTRY_REGION), SUBDIVISION(Kind.SUBDIVISION),
        CATEGORY("ProductCategory", "masterdata.productcategory"),
        UNIT("ProductUnit", "masterdata.productunit"),
        ATTRIBUTE_GROUP("ProductAttribute.group", "masterdata.productattribute"),
        ATTRIBUTE("ProductAttribute", "masterdata.productattribute"),
        TEMPLATE("ProductTemplate", "masterdata.producttemplate"),
        CONVERSION("ProductUnit.conversion", "masterdata.productunit");

        public final String code;
        public final String resource;
        public final Kind kind;

        Subject(Kind kind) {
            this.code = kind.objectType;
            this.resource = kind.resource();
            this.kind = kind;
        }

        Subject(String code, String resource) {
            this.code = code;
            this.resource = resource;
            this.kind = null;
        }
    }

    /** @param version 变更后的对象版本
     * @param beforeEnabled 变更前启用状态
     * @param afterEnabled 变更后启用状态
     * @param createdBy 实际操作者 UUID，未知为 null
     * @param createdAt 数据库记录的 UTC 时间点
     */
    public record Entry(long version, boolean beforeEnabled, boolean afterEnabled, String createdBy, Instant createdAt) {}

    /** @param rows 当前页历史，版本倒序 @param total 当前对象的历史总数 */
    public record Page(List<Entry> rows, long total) {}
}
