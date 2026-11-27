/** 三单分别声明页面和命令，命令只通过原单据 API 提交。 */
export const inboundDocuments = {
  quality: { title: ["质检单", "Quality orders"], object: "BulkInboundQualityOrder", path: "quality" },
  metering: { title: ["计量单（磅单）", "Metering orders"], object: "BulkInboundMeteringOrder", path: "metering" },
  receipt: { title: ["入库单", "Receipt orders"], object: "BulkWarehouseReceiptOrder", path: "receipt" }
} as const;

/** 页面使用的单据归属及精确版本来自已授权读接口。 */
export type InboundDocumentKind = keyof typeof inboundDocuments;
export interface InboundDocument {
  id: string;
  number: string;
  status: string;
  taskId: string;
  taskVersion: number;
  rowVersion: number;
  lines?: Record<string, unknown>[];
  [key: string]: unknown;
}

/** 应用宿主提供租户会话、平台授权决策和带 CSRF 的独立命令。 */
export interface InboundDocumentsApi {
  labMetrics: (id:string)=>Promise<LabMetricOption[]>;
  attachmentOptions: (taskId:string)=>Promise<InspectionAttachmentOption[]>;
  /** 复用来源通知和主数据的授权读取。 */
  receiptReferences: (noticeId: string) => Promise<ReceiptReferences>;
  storageOptions: (kind: "area" | "location", parent: string, keyword: string) => Promise<ReceiptStorageOption[]>;
  conversionOptions: (sourceUnitId: string, targetUnitId: string) => Promise<ReceiptConversionOption[]>;
  /** 按新建命令读取授权范围内的作业号及并发版本。 */
  taskOptions: (path: string, taskNo: string) => Promise<InboundTaskOption[]>;
  list: (page: number, number?: string) => Promise<{ rows: InboundDocument[]; total: number }>;
  detail: (id: string) => Promise<InboundDocument>;
  operations: () => Promise<string[]>;
  execute: (path: string, input: Record<string, unknown>, key: string) => Promise<unknown>;
}

/** 不包含判定区间的录入项目。 */
export interface LabMetricOption {code:string;unit:string}
/** 已扫描可用的附件选项。 */
export interface InspectionAttachmentOption {id:string;fileName:string;mediaType:string}

type FieldType = "text" | "integer" | "boolean" | "ticket" | readonly string[];
export type DocumentField = readonly [key: string, zh: string, en: string, type?: FieldType, optional?: boolean];

/** 命令字段与既有 HTTP 契约对应，金额与重量保留原始十进制字符串。 */
export interface DocumentAction {
  operation: string;
  path: string;
  title: readonly [string, string];
  fields: readonly DocumentField[];
  onRow?: boolean;
  /** 在已打开草稿的详情中补齐要素时继承草稿单号和计量版本。 */
  completeReceipt?: boolean;
  statuses?: readonly string[];
}

/** 选择器使用的受控投影，载荷携带版本但不提供手工输入。 */
export interface InboundTaskOption {
  taskId: string;
  taskNo: string;
  taskVersion: number;
  meteringVersion?: number | null;
  noticeId?: string | null;
}

/** 已授权仓位引用，版本仅作为原命令的并发输入。 */
export interface ReceiptStorageOption {
  id: string;
  code: string;
  label: string;
  version: number;
}
/** 正式启用且方向匹配的单位换算。 */
export interface ReceiptConversionOption { id: string; label: string; version: number }
/** 来源通知行、仓库、有效千克单位均由宿主读取。 */
export interface ReceiptReferences {
  lines: { id: string; label: string; unitId: string; unitName: string }[];
  warehouse: ReceiptStorageOption;
  kilogram: { id: string; label: string };
}
const ticket = ["ticket", "磅票", "Weigh ticket", "ticket"] as const;
const source = ["source", "计量来源", "Measurement source", ["RAIL_SCALE", "DRAFT_SURVEY"]] as const;

/** 每个页面复用原操作编号，按平台决策显示，作业状态仍由服务端检查。 */
export const documentActions: Record<InboundDocumentKind, DocumentAction[]> = {
  quality: [
    { operation: "register_sample", path: ":register-sample", title: ["采样登记", "Register sample"],
      fields: [["qualityNo", "质检单号", "Quality order number"], ["barcode", "样品条码", "Sample barcode"]] },
    { operation: "register_sample", path: ":register-sample", title: ["复检登记", "Register retest"], onRow: true, statuses: ["DECIDED"],
      fields: [["qualityNo", "新质检单号", "New quality order number"], ["barcode", "新样品条码", "New sample barcode"]] },
    { operation: "decide_quality", path: "/{id}:decide", title: ["录入化验结果", "Record lab results"], onRow: true, statuses: ["REGISTERED"],
      fields: [] },
    { operation: "inspect_steel", path: ":inspect-steel", title: ["钢铁 MTC 与外观核验", "Steel MTC and appearance"],
      fields: [["steelQualityNo", "质检单号", "Quality order number"], ["heatNo", "炉批号", "Heat number"],
        ["certificateNo", "质保书编号", "Certificate number"],
        ["certificateVerified", "质保书通过", "Certificate accepted", "boolean"], ["appearanceVerified", "外观通过", "Appearance accepted", "boolean"]] }
  ],
  metering: [
    { operation: "record_gross", path: ":record-gross", title: ["登记毛重", "Record gross"],
      fields: [["grossMeteringNo", "磅单号", "Weighing order number"], ticket] },
    { operation: "record_tare", path: ":record-tare", title: ["登记皮重", "Record tare"], onRow: true,
      statuses: ["GROSS_RECORDED"], fields: [ticket] },
    { operation: "BulkInboundTask.confirm_unloading", path: "/api/bulk-inbound-tasks/{taskId}:confirm-unloading",
      title: ["监卸确认", "Confirm unloading"], onRow: true, statuses: ["GROSS_RECORDED"],
      fields: [["outcome", "监卸结果", "Unloading outcome", ["ACCEPTED", "ACCEPTED_WITH_DEDUCTION", "RETURN"]],
        ["extraDeductionKg", "现场扣重（kg）", "Site deduction (kg)", "text", true]] },
    { operation: "register_external", path: ":register-external", title: ["外部计量", "External measurement"],
      fields: [["meteringNo", "计量单号", "Metering order number"], source,
        ["externalReference", "外部凭证号", "External reference"], ["netKg", "净重（kg）", "Net (kg)"]] },
    { operation: "confirm_steel", path: ":confirm-steel", title: ["钢铁双计量", "Steel measurement"],
      fields: [["steelMeteringNo", "计量单号", "Metering order number"],
        ["basis", "计量口径", "Measurement basis", ["SCALE", "THEORETICAL"]], ["packages", "件数", "Packages", "integer"],
        ["theoreticalKg", "理重（kg）", "Theoretical (kg)"], ["gross", "毛重磅票", "Gross ticket", "ticket", true],
        ["tare", "皮重磅票", "Tare ticket", "ticket", true]] }
  ],
  receipt: [
    { operation: "create_bulk_draft", path: ":create-bulk-draft", title: ["散货入库草稿", "Bulk receipt draft"],
      fields: [["receiptNo", "入库单号", "Receipt number"],
        ["replacesReceiptId", "冲红原单 ID", "Reversed original ID", "text", true]] },
    { operation: "create_steel_draft", path: ":create-steel-draft", title: ["钢铁 LPN 入库草稿", "Steel LPN receipt draft"],
      fields: [["steelReceiptNo", "入库单号", "Receipt number"]] },
    { operation: "post", path: "/{id}:post", title: ["提交入账", "Submit and post"], onRow: true, statuses: ["DRAFT"], fields: [] },
    { operation: "reverse", path: "/{id}:reverse", title: ["冲红", "Reverse"], onRow: true, statuses: ["POSTED"], fields: [] }
  ]
};

/** 嵌套字段显示契约示例，便于对照设备票据及主数据版本。 */
