package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.application.inboundexecution.SteelInspectionEvidence;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.platform.configuration.AttachmentRecords;
import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.Set;
import java.util.UUID;

/** 只识别当前检验员有权读取且已处于可用状态的原作业附件。 */
public final class AttachmentSteelInspectionEvidenceAdapter implements SteelInspectionEvidence {
    private static final int PAGE_SIZE=50;
    private static final Set<String> PHOTO=Set.of("image/jpeg","image/png");
    private final AttachmentRecords attachments;

    /** @param attachments 平台公开附件读取契约 */
    public AttachmentSteelInspectionEvidenceAdapter(AttachmentRecords attachments) { this.attachments=attachments; }

    /** {@inheritDoc} */
    @Override public void requireAvailable(VerifiedTenantContext context, UUID task, UUID certificate, UUID appearance) {
        var owner=new AttachmentRecords.Owner(context.tenantId(),context.accountId(),context.memberId());
        String businessId="inbound-task:"+task;
        boolean certificateFound=false, appearanceFound=false;
        for(int offset=0;;offset+=PAGE_SIZE) {
            var page=attachments.list(owner,businessId,offset,PAGE_SIZE);
            for(var item:page.rows()) {
                if (!context.tenantId().toString().equals(item.tenantId()) || !businessId.equals(item.businessId())
                        || !"AVAILABLE".equals(item.status())) continue;
                if(certificate.toString().equals(item.id())) {
                    certificateFound="application/pdf".equals(item.mediaType()) || PHOTO.contains(item.mediaType());
                }
                if(appearance.toString().equals(item.id())) {
                    appearanceFound=PHOTO.contains(item.mediaType());
                }
            }
            if(certificateFound && appearanceFound) return;
            if(page.rows().size()<PAGE_SIZE || (long)offset+PAGE_SIZE>=page.total()) break;
        }
        throw new Rejection(400,"INBOUND_STEEL_ATTACHMENTS_INVALID");
    }
}
