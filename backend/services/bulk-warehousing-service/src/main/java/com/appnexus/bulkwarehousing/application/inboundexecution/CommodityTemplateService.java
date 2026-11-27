package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.inboundexecution.CommodityTemplates;
import com.appnexus.bulkwarehousing.application.InboundReferenceSource;
import com.appnexus.bulkwarehousing.domain.inboundexecution.LabQualityRules;
import com.appnexus.bulkwarehousing.domain.inboundexecution.ReceiptQualityDeduction;
import com.appnexus.contracts.businessobject.BusinessObjects.DataAccessExecutor;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlan;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 品类模板生命周期。公式参数由业务配置，不自动生成。 */
public final class CommodityTemplateService implements CommodityTemplates, DataAccessExecutor {
    private final AccessPlanning access;
    private final CommodityTemplateStore store;
    private final InboundReferenceSource references;

    /** @param access 平台授权 @param store 模板事务 @param references 授权商品读取 */
    public CommodityTemplateService(AccessPlanning access, CommodityTemplateStore store, InboundReferenceSource references) {
        this.access=access;
        this.store=store;
        this.references=references;
    }

    /** {@inheritDoc} */
    @Override public Set<String> enforcedResources() { return Set.of(RESOURCE); }

    /** {@inheritDoc} */
    @Override public CommodityTemplatePage list(VerifiedTenantContext context,int page,int pageSize) {
        if (page<1 || pageSize<1 || pageSize>100 || (long)(page-1)*pageSize>Integer.MAX_VALUE) throw invalid();
        return store.transaction(require(context,READ),()->store.list(context.tenantId(),(page-1)*pageSize,pageSize));
    }

    /** {@inheritDoc} */
    @Override public CommodityTemplateVersion create(VerifiedTenantContext context,CommodityTemplateDraft draft) {
        return store.transaction(require(context,WRITE),()->{
            validate(context,draft);
            var id=UUID.randomUUID();
            store.insert(context.tenantId(),context.accountId(),id,draft);
            return new CommodityTemplateVersion(id,"DRAFT",0,draft);
        });
    }

    /** {@inheritDoc} */
    @Override public CommodityTemplateVersion update(VerifiedTenantContext context,UUID id,CommodityTemplateEdit edit) {
        if(edit==null || edit.rowVersion()==null || edit.rowVersion()<0) throw invalid();
        return store.transaction(require(context,WRITE),()->{
            var current=draft(context,id,edit.rowVersion());
            var input=edit.draft();
            validate(context,input);
            if(!current.draft().code().equals(input.code()) || current.draft().version()!=input.version()) throw invalid();
            if(store.update(context.tenantId(),context.accountId(),id,edit.rowVersion(),input)!=1) throw conflict();
            return new CommodityTemplateVersion(id,"DRAFT",edit.rowVersion()+1,input);
        });
    }

    /** {@inheritDoc} */
    @Override public CommodityTemplateVersion publish(VerifiedTenantContext context,UUID id,CommodityTemplatePublication publication) {
        if(publication==null || publication.rowVersion()==null || publication.rowVersion()<0) throw invalid();
        return store.transaction(require(context,PUBLISH),()->{
            var located=store.find(context.tenantId(),id);
            if(located==null) throw new Rejection(404,"INBOUND_TEMPLATE_NOT_FOUND");
            var versions=store.lockCode(context.tenantId(),located.draft().code());
            var current=versions.stream().filter(row->row.id().equals(id)).findFirst().orElseThrow(CommodityTemplateService::conflict);
            if(!"DRAFT".equals(current.status()) || current.rowVersion()!=publication.rowVersion()) throw conflict();
            var definition=current.draft();
            validate(context,definition);
            if(versions.stream().anyMatch(v -> !"DRAFT".equals(v.status()) && v.draft().version()>=definition.version())) {
                throw conflict();
            }
            store.retire(context.tenantId(),context.accountId(),definition.code(),id);
            if(store.publish(context.tenantId(),context.accountId(),id,publication.rowVersion())!=1) throw conflict();
            return new CommodityTemplateVersion(id,"PUBLISHED",publication.rowVersion()+1,definition);
        });
    }

    private CommodityTemplateVersion draft(VerifiedTenantContext context,UUID id,long version) {
        var current=store.lock(context.tenantId(),id);
        if(current==null) throw new Rejection(404,"INBOUND_TEMPLATE_NOT_FOUND");
        if(!"DRAFT".equals(current.status()) || current.rowVersion()!=version) throw conflict();
        return current;
    }

    private AccessPlan require(VerifiedTenantContext context,String permission) {
        var subject=new Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        var plan=access.plan(subject,permission,RESOURCE);
        if(!plan.tenantWide()||!subject.equals(plan.subject())||!permission.equals(plan.permissionCode()))
            throw new Rejection(403,"INBOUND_TEMPLATE_ACCESS_DENIED");
        return plan;
    }

    private void validate(VerifiedTenantContext context,CommodityTemplateDraft draft) {
        if(draft==null || draft.code()==null || !draft.code().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || draft.version()<1 || draft.profile()==null || draft.definition()==null) throw invalid();
        var definition=draft.definition();
        if(definition.noticeProductIds()==null || definition.noticeProductIds().isEmpty()
                || definition.noticeProductIds().size()>100 || definition.noticeProductIds().stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(definition.noticeProductIds()).size()!=definition.noticeProductIds().size()) throw invalid();
        definition.noticeProductIds().forEach(id->references.requireProduct(context,id.toString()));
        if(draft.profile()==Profile.STEEL_LPN) {
            if(definition.maxVarianceRatio()==null || definition.maxVarianceRatio().signum()<0
                    || definition.maxVarianceRatio().compareTo(BigDecimal.ONE)>0 || definition.requireBothWeights()==null
                    || definition.deductionFormula()!=null || (definition.qualityMetrics()!=null && !definition.qualityMetrics().isEmpty()))
                throw invalid();
            return;
        }
        try {
            var values=new LinkedHashMap<String,BigDecimal>();
            var rules=new LinkedHashMap<String,LabQualityRules.Metric>();
            definition.qualityMetrics().forEach((key,rule)->{
                values.put(key,rule.minimum());
                rules.put(key,new LabQualityRules.Metric(rule.unit(),rule.minimum(),rule.maximum(),rule.acceptMinimum(),rule.acceptMaximum()));
            });
            LabQualityRules.decide(rules,values);
            if(draft.profile()==Profile.QUALITY_DEDUCTION) {
                var formula=definition.deductionFormula();
                for(var rule:java.util.List.of(formula.moisture(),formula.impurity())) {
                    var metric=definition.qualityMetrics().get(rule.metric());
                    if(!"pct".equals(metric.unit()) && !"%".equals(metric.unit())) throw invalid();
                    if(metric.minimum().signum()<0 || metric.maximum().compareTo(BigDecimal.valueOf(100))>0) throw invalid();
                }
                ReceiptQualityDeduction.calculate(BigDecimal.ONE,values,new ReceiptQualityDeduction.Formula(formula.version(),
                        deduction(formula.moisture()),deduction(formula.impurity())),BigDecimal.ZERO);
            } else if (definition.deductionFormula()!=null) throw invalid();
        } catch(IllegalArgumentException | IllegalStateException | NullPointerException ex) {
            throw invalid();
        }
    }

    private static Rejection invalid() { return new Rejection(400,"INBOUND_TEMPLATE_CONFIG_INVALID"); }
    private static ReceiptQualityDeduction.Rule deduction(TemplateDeductionRule rule) {
        return new ReceiptQualityDeduction.Rule(rule.metric(),rule.baseline(),rule.multiplier());
    }
    private static Rejection conflict() { return new Rejection(409,"INBOUND_TEMPLATE_VERSION_CONFLICT"); }
}
