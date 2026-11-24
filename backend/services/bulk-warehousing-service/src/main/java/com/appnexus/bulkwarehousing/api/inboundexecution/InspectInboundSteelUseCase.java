package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.UUID;

/** 入库钢铁外观和钢厂质保书核验。 */
public interface InspectInboundSteelUseCase {
    String PERMISSION = RegisterInboundSampleUseCase.RESOURCE + ":inspect_steel";

    /**
     * @param taskId 来源作业 UUID
     * @param taskVersion 当前作业版本，非负
     * @param qualityNo 核验单编码，最长 64 字符
     * @param heatNo 核验对应的炉批号，最长 64 字符
     * @param certificateNo 钢厂质保书编号，最长 64 字符
     * @param certificateId 该作业的已通过上传校验的质保书附件 UUID
     * @param appearanceId 该作业的已通过上传校验的外观照片 UUID
     * @param certificateVerified 检验员对质保书和炉批号一致性给出的结论
     * @param appearanceVerified 检验员对实物外观给出的结论
     */
    record Command(UUID taskId, long taskVersion, String qualityNo, String heatNo, String certificateNo,
                   UUID certificateId, UUID appearanceId, boolean certificateVerified, boolean appearanceVerified) {}

    /** 已保存核验单身份、判定和作业新版本。 */
    record SteelInspection(UUID qualityId, String decision, long taskVersion) {}

    /** @param context 服务端可信租户及检验员 @param command 外观与质保书核验 @return 提交后的判定 */
    SteelInspection inspect(VerifiedTenantContext context, Command command);
}
