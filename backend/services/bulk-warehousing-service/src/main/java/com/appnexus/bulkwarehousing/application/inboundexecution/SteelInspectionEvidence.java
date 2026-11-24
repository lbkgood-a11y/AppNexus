package com.appnexus.bulkwarehousing.application.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;

/** 核验附件的授权出站端口，附件所有权及状态由平台提供。 */
public interface SteelInspectionEvidence {
    /**
     * @param context 当前检验员的可信租户及账号
     * @param task 来源作业 UUID，用来生成固定附件关联值
     * @param certificate 质保书附件 UUID，支持 PDF 和照片
     * @param appearance 外观照片附件 UUID，须为独立照片
     */
    void requireAvailable(VerifiedTenantContext context, UUID task, UUID certificate, UUID appearance);
}
