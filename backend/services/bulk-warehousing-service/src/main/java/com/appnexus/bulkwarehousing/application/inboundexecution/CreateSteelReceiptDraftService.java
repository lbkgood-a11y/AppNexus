package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.bulkwarehousing.api.InboundReceiptReferences;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateSteelReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.api.inboundexecution.CreateBulkReceiptDraftUseCase;
import com.appnexus.bulkwarehousing.application.InboundReferenceSource;
import com.appnexus.bulkwarehousing.domain.inboundexecution.LpnWeightAllocation;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.authorization.AccessAuthorization.Subject;
import com.appnexus.platform.authorization.AccessPlanning;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 按现行 MTC 炉批及计量比例生成钢铁 LPN 入仓主从数据。 */
public final class CreateSteelReceiptDraftService implements CreateSteelReceiptDraftUseCase {
    private static final Set<String> NOTICES=Set.of("ISSUED","RECEIVING");
    private final AccessPlanning access;
    private final InboundReceiptOrderRepository repository;
    private final InboundReceiptReferences references;
    private final InboundReferenceSource source;

    /** @param access 统一权限 @param repository 作业及原始证据仓储 @param references 授权引用和换算 @param source 千克验证 */
    public CreateSteelReceiptDraftService(AccessPlanning access,InboundReceiptOrderRepository repository,
                                          InboundReceiptReferences references,InboundReferenceSource source) {
        this.access=access;this.repository=repository;this.references=references;this.source=source;
    }

    /** {@inheritDoc} */
    @Override public SteelReceiptDraft createSteel(VerifiedTenantContext context,Command input) {
        if(input==null||input.taskId()==null||input.taskVersion()<0||input.meteringVersion()<0
                ||!label(input.steelReceiptNo())||input.lines()==null||input.lines().isEmpty()) throw invalid();
        var subject=new Subject(context.accountId(),context.tenantId(),context.memberId(),context.personId(),context.organizationId());
        var plan=access.plan(subject,PERMISSION,CreateBulkReceiptDraftUseCase.RESOURCE);
        if(!plan.tenantWide()||!subject.equals(plan.subject())) throw new Rejection(403,"INBOUND_RECEIPT_ACCESS_DENIED");
        var tenant=context.tenantId();var actor=context.accountId();
        return repository.transaction(plan,()->{
            var task=repository.lockTask(tenant,input.taskId());
            if(task==null)throw new Rejection(404,"INBOUND_TASK_NOT_FOUND");
            if(task.rowVersion()!=input.taskVersion())throw conflict();
            if(!"READY_TO_RECEIVE".equals(task.status())||task.noticeStatus()==null||!NOTICES.contains(task.noticeStatus()))
                throw new Rejection(409,"INBOUND_RECEIPT_DRAFT_STATE_CONFLICT");
            var meter=repository.lockSteelMeter(tenant,input.taskId());
            if(meter==null||meter.version()!=input.meteringVersion())throw conflict();
            if(meter.packages()!=input.lines().size())throw invalid();

            var seen=new HashSet<String>();
            var byId=new HashMap<UUID,Line>();
            var weights=new ArrayList<LpnWeightAllocation.Line>();
            BigDecimal theoretical=BigDecimal.ZERO;
            for(var line:input.lines()){
                if(line==null||line.noticeLineId()==null||line.position()==null||line.kilogramUnitId()==null
                        ||!label(line.lpn())||!seen.add(line.lpn().toUpperCase(Locale.ROOT))
                        ||!meter.heatNo().equals(line.heatNo())||!label(line.specification())||!label(line.material())
                        ||!label(line.batchNo())||line.theoreticalKg()==null||line.theoreticalKg().signum()<=0
                        ||line.theoreticalKg().stripTrailingZeros().scale()>4
                        ||(line.conversionRuleId()==null)!=(line.conversionRuleVersion()==null)
                        ||(line.conversionRuleVersion()!=null&&line.conversionRuleVersion()<0)
                        ||!task.warehouseId().toString().equals(line.position().warehouseId()))throw invalid();
                var id=UUID.randomUUID();
                weights.add(new LpnWeightAllocation.Line(id,line.lpn(),line.theoreticalKg()));
                byId.put(id,line);
                theoretical=theoretical.add(line.theoreticalKg());
            }
            if(theoretical.compareTo(meter.theoreticalKg())!=0)throw new Rejection(409,"INBOUND_STEEL_THEORETICAL_WEIGHT_CONFLICT");
            var chosen="SCALE_DIFFERENCE".equals(meter.method())?meter.physicalNetKg():meter.theoreticalKg();
            if(chosen==null)throw conflict();
            var assigned="SCALE_DIFFERENCE".equals(meter.method())?LpnWeightAllocation.allocate(chosen,weights)
                    :weights.stream().map(value->new LpnWeightAllocation.Allocation(value.id(),value.theoreticalKg())).toList();

            var limits=new HashMap<UUID,InboundReceiptOrderRepository.NoticeLine>();
            for(var id:input.lines().stream().map(Line::noticeLineId).distinct().sorted().toList()){
                var line=repository.lockNoticeLine(tenant,task.noticeId(),id);
                if(line==null)throw new Rejection(404,"INBOUND_NOTICE_LINE_NOT_FOUND");
                limits.put(id,line);
            }

            var totals=new HashMap<UUID,BigDecimal>();
            var lines=new ArrayList<InboundReceiptOrderRepository.SteelLine>();
            for(var assignment:assigned.stream().sorted(Comparator.comparing(value->value.id().toString())).toList()){
                var line=byId.get(assignment.id());
                var limit=limits.get(line.noticeLineId());
                var result=resolve(context,task,line,limit,assignment.netKg());
                var cumulative=totals.merge(line.noticeLineId(),result.quantity().baseQuantity(),BigDecimal::add);
                if(cumulative.compareTo(limit.remaining())>0)throw new Rejection(409,"INBOUND_RECEIPT_NOTICE_LIMIT_OR_UNIT_CONFLICT");
                lines.add(new InboundReceiptOrderRepository.SteelLine(assignment.id(),line.noticeLineId(),line.lpn(),line.heatNo(),
                        line.specification(),line.material(),line.batchNo(),line.theoreticalKg(),assignment.netKg(),result));
            }
            var opened=repository.lockActiveDraft(tenant,input.taskId());
            if(opened!=null && (!opened.collecting() || !opened.number().equals(input.steelReceiptNo())))throw conflict();
            var id=opened==null ? UUID.randomUUID() : opened.id();
            repository.insertSteelDraft(tenant,actor,new InboundReceiptOrderRepository.SteelDraft(id,input.taskId(),task.noticeId(),
                    meter.id(),meter.qualityId(),input.steelReceiptNo(),meter.method(),meter.physicalNetKg(),chosen,List.copyOf(lines)),opened!=null);
            if(repository.advanceTask(tenant,actor,input.taskId(),task.rowVersion())!=1)throw conflict();
            return new SteelReceiptDraft(id,opened==null?0:opened.version()+1,task.rowVersion()+1);
        });
    }

    private InboundReceiptReferences.Resolved resolve(VerifiedTenantContext context,InboundReceiptOrderRepository.Task task,
            Line line,InboundReceiptOrderRepository.NoticeLine limit,BigDecimal kg) {
        String unit=line.kilogramUnitId().toString();
        source.requireMassUnit(context,unit,"KILOGRAM");
        InboundReceiptReferences.Conversion conversion=line.conversionRuleId()==null
                ?new InboundReceiptReferences.SameUnit()
                :new InboundReceiptReferences.UnitRule(line.conversionRuleId().toString(),line.conversionRuleVersion());
        var result=references.resolve(context,new InboundReceiptReferences.Request(line.position(),limit.item(),
                task.ownerId().toString(),unit,kg.toPlainString(),conversion));
        var quantity=result.quantity();
        if(!limit.baseUnitId().toString().equals(quantity.baseUnitId()) || quantity.baseQuantity().signum()<=0
                ||quantity.baseQuantity().stripTrailingZeros().scale()>12)
            throw new Rejection(409,"INBOUND_RECEIPT_NOTICE_LIMIT_OR_UNIT_CONFLICT");
        return result;
    }

    private static boolean label(String value) {
        return value!=null&&!value.isBlank()&&value.length()<=64&&value.chars().noneMatch(Character::isISOControl);
    }
    private static Rejection invalid() {return new Rejection(400,"INBOUND_STEEL_RECEIPT_INPUT_INVALID");}
    private static Rejection conflict() {return new Rejection(409,"INBOUND_RECEIPT_DRAFT_VERSION_CONFLICT");}
}
