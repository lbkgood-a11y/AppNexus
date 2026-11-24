package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;

/** 入库质检独立采样与复检登记。 */
public interface RegisterInboundSampleUseCase {
    String RESOURCE = "warehousing.inboundquality";
    String PERMISSION = RESOURCE + ":register_sample";

    /**
     * 采样输入。首次采样省略现行质检标识及版本，复检必须同时提供两个字段。
     * @param taskId 同一入库作业 UUID
     * @param taskVersion 客户端读取的作业版本，非负
     * @param priorQualityId 复检时预期的现行质检单 UUID
     * @param priorRowVersion 复检时预期的现行质检行版本，非负
     * @param qualityNo 本次质检单编码，1 至 64 个标识字符
     * @param barcode 新样品条码，1 至 64 个标识字符
     */
    record Command(UUID taskId, long taskVersion, UUID priorQualityId, Long priorRowVersion,
                   String qualityNo, String barcode) {}

    /** 新质检单、业务检验版本及成功推进后的作业版本。 */
    record Registered(UUID qualityId, int qualityVersion, long taskVersion) {}

    /**
     * @param context 服务端验证的租户主体
     * @param command 采样或复检登记
     * @return 已提交的现行样品身份
     */
    Registered register(VerifiedTenantContext context, Command command);
}
