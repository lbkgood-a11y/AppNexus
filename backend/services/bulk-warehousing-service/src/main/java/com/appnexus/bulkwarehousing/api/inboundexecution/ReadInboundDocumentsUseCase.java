package com.appnexus.bulkwarehousing.api.inboundexecution;

import com.appnexus.platform.tenant.VerifiedTenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 分岗独立单据读入口，详情以单据 UUID 标识。 */
public interface ReadInboundDocumentsUseCase {
    /** 三类单据都有独立读取权限。 */
    enum Kind {
        QUALITY("warehousing.inboundquality"),
        METERING("warehousing.inboundmetering"),
        RECEIPT("warehousing.receipt");
        private final String resource;
        Kind(String resource) { this.resource = resource; }
        /** @return 数据资源键 */
        public String resource() { return resource; }
        /** @return 独立列表权限 */
        public String listPermission() { return resource + ":list_documents"; }
        /** @return 独立详情权限 */
        public String detailPermission() { return resource + ":read_document"; }
    }

    /** 同一授权筛选范围内的分页记录。 */
    record InboundDocumentPage(List<Map<String,Object>> rows, long total) {}

    /** 对应已有新建命令及其权限，由接口固定。 */
    enum Creation {
        SAMPLE(Kind.QUALITY, RegisterInboundSampleUseCase.PERMISSION),
        INSPECT(Kind.QUALITY, InspectInboundSteelUseCase.PERMISSION),
        GROSS(Kind.METERING, GroundWeighingUseCase.GROSS),
        EXTERNAL(Kind.METERING, RegisterExternalMeasurementUseCase.PERMISSION),
        DUAL(Kind.METERING, ConfirmSteelMeasurementUseCase.PERMISSION),
        BULK(Kind.RECEIPT, CreateBulkReceiptDraftUseCase.PERMISSION),
        LPN(Kind.RECEIPT, CreateSteelReceiptDraftUseCase.PERMISSION);

        final Kind kind;
        final String permission;
        Creation(Kind kind, String permission) { this.kind = kind; this.permission = permission; }
        /** @return 命令所属资源类型 */
        public Kind kind() { return kind; }
        /** @return 命令对应的权限码 */
        public String permission() { return permission; }
    }

    /** 作业编号和并发版本，计量版本仅用于接续入库草稿，不包含运输身份。 */
    record TaskOption(UUID taskId, String taskNo, long taskVersion, Long meteringVersion) {}

    /**
     * @param context 可信租户主体
     * @param creation 具体新建命令
     * @param taskNo 可选作业号前缀，最多 64 字符
     * @return 同租户、同命令范围内最多 20 个候选
     */
    List<TaskOption> taskOptions(VerifiedTenantContext context, Creation creation, String taskNo);

    /**
     * @param context 可信租户主体
     * @param kind 固定单据类型
     * @param number 单号精确筛选，缺省不筛选
     * @param page 页码，从 1 开始
     * @param pageSize 页大小，1 至 100
     * @return 权限范围内的列表和总数
     */
    InboundDocumentPage list(VerifiedTenantContext context, Kind kind, String number, int page, int pageSize);

    /** @param context 可信租户主体 @param kind 单据类型 @param id 单据 UUID @return 受控详情 */
    Map<String,Object> detail(VerifiedTenantContext context, Kind kind, UUID id);
}
