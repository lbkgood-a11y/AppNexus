package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 品类模板草稿、编辑及版本发布的租户契约。 */
public interface CommodityTemplates {
    String OBJECT = "BulkCommodityTemplate";
    String RESOURCE = "warehousing.commoditytemplate";
    String PATH = "/api/bulk-commodity-templates";
    String READ = RESOURCE + ":list";
    String WRITE = RESOURCE + ":save";
    String PUBLISH = RESOURCE + ":publish";

    /** 受支持的入库策略配置组合。 */
    enum Profile { QUALITY_DEDUCTION, SAMPLED_BULK, STEEL_LPN }
    /** 指标单位、有效区间和合格区间，均含端点。 */
    record TemplateMetric(String unit,BigDecimal minimum,BigDecimal maximum,BigDecimal acceptMinimum,BigDecimal acceptMaximum) {}
    /** 百分数基准和每超一个百分点对应的扣量系数。 */
    record TemplateDeductionRule(String metric,BigDecimal baseline,BigDecimal multiplier) {}
    /** 两类扣重分项及业务批准的规则版本。 */
    record TemplateDeductionFormula(String version,TemplateDeductionRule moisture,TemplateDeductionRule impurity) {}
    /** 业务批准的配置，不提供质量范围或扣量系数缺省值。 */
    record CommodityTemplateDefinition(List<UUID> noticeProductIds, Map<String, TemplateMetric> qualityMetrics,
                      TemplateDeductionFormula deductionFormula, BigDecimal maxVarianceRatio, Boolean requireBothWeights) {}
    /** 指定逻辑模板编码、正整数修订号和显式策略。 */
    record CommodityTemplateDraft(String code, int version, Profile profile, CommodityTemplateDefinition definition) {}
    /** 编辑仅限草稿，使用读取的乐观锁版本。 */
    record CommodityTemplateEdit(Long rowVersion, CommodityTemplateDraft draft) {}
    /** 发布使用读取的草稿版本。 */
    record CommodityTemplatePublication(Long rowVersion) {}
    /** 列表与表单共用快照。 */
    record CommodityTemplateVersion(UUID id, String status, long rowVersion, CommodityTemplateDraft draft) {}
    /** 同范围分页及总数。 */
    record CommodityTemplatePage(List<CommodityTemplateVersion> rows, long total) {}

    /** @return 授权租户内的模板及精确总数 */
    CommodityTemplatePage list(VerifiedTenantContext context, int page, int pageSize);
    /** @return 新建的草稿 */
    CommodityTemplateVersion create(VerifiedTenantContext context, CommodityTemplateDraft draft);
    /** @return 保存后的草稿 */
    CommodityTemplateVersion update(VerifiedTenantContext context, UUID id, CommodityTemplateEdit edit);
    /** @return 发布后的快照 */
    CommodityTemplateVersion publish(VerifiedTenantContext context, UUID id, CommodityTemplatePublication publication);
}
