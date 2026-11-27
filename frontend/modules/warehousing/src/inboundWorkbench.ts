/** 入库工作台只读队列与分岗命令的第一方契约。 */

export const workbenchStages = ["WAIT_QUALITY", "WAIT_GROSS", "UNLOADING", "WAIT_TARE", "WAIT_RECEIPT"] as const;
export type WorkbenchStage = (typeof workbenchStages)[number];

/** 创建或完成作业时由服务器推进行版本和阶段。 */
export interface InboundQueueItem {
  taskNo?: string;
  taskId: string;
  stage: WorkbenchStage;
  stageStartedAt: string | null;
}

export interface InboundQueuePage {
  rows: InboundQueueItem[];
  counts: Record<WorkbenchStage, number>;
  nextAfterId: string | null;
}

/** 只保留后端允许显示的字段；公斤和比率均用精确十进制字符串。 */
export interface InboundWorkbenchDetail {
  taskId: string;
  taskVersion: number;
  stage: WorkbenchStage | null;
  status: string;
  blind: boolean;
  identity: { vehiclePlate?: string; waybillNo?: string; driverName?: string; ownerId?: string };
  quality: { id?: string; qualityNo?: string; sampleBarcode?: string; status?: string; decision?: string; rowVersion?: number };
  metering: { id?: string; meteringNo?: string; status?: string; method?: string; rowVersion?: number;
    physicalNetKg?: string; theoreticalKg?: string; varianceRatio?: string; packages?: number };
  receipt: { id?: string; receiptNo?: string; status?: string; rowVersion?: number; qualityState?: string;
    lines?: { id: string; lpn?: string; heatNo?: string; batchNo?: string; specification?: string;
      material?: string; accountingKg: string }[] };
  totals: { physicalNetKg?: string; theoreticalKg?: string; varianceRatio?: string; deductionKg?: string;
    accountingKg?: string; qualityState?: string };
  risk: { unloadDisposition?: string };
}

/** 业务模块只消费类型化端口，应用层负责会话、CSRF 和 HTTP。 */
export interface InboundWorkbenchApi {
  /** 获取同车次最新的只读现场采集快照。 */
  site: (id: string) => Promise<InboundSiteReading>;
  siteCommand: (task: string, action: SiteCommandAction, id: string, retry: boolean) => Promise<SiteCommandStatus>;
  siteCommandStatus: (task: string, id: string) => Promise<SiteCommandStatus>;
  labMetrics: import("./inboundDocuments").InboundDocumentsApi["labMetrics"];
  attachmentOptions: import("./inboundDocuments").InboundDocumentsApi["attachmentOptions"];
  /** 复用质检和计量专用入口，以当前作业版本提交钢铁核验、双计量或化验。 */
  executeDocument(kind: "quality" | "metering", path: string, input: Record<string, unknown>): Promise<unknown>;
  queue(stage: WorkbenchStage | undefined, cursor?: string, unopened?: boolean): Promise<InboundQueuePage>;
  detail(id: string): Promise<InboundWorkbenchDetail>;
  operations(): Promise<string[]>;
  sample(payload: { taskId: string; taskVersion: number; qualityNo: string; barcode: string;
    priorQualityId?: string; priorRowVersion?: number }): Promise<unknown>;
  external(payload: { taskId: string; taskVersion: number; meteringNo: string;
    source: "RAIL_SCALE" | "DRAFT_SURVEY"; externalReference: string; netKg: string }): Promise<unknown>;
  gross(payload: { taskId: string; taskVersion: number; grossMeteringNo: string; ticket: WeighTicket }): Promise<unknown>;
  tare(payload: { taskId: string; taskVersion: number; meterVersion: number; ticket: WeighTicket }): Promise<unknown>;
  unload(id: string, payload: { taskVersion: number; meterVersion: number;
    outcome: UnloadingOutcome; extraDeductionKg: string }): Promise<unknown>;
}

/** 固定现场动作和当前服务端回执。 */
export type SiteCommandAction = "gate" | "call" | "print";
export interface SiteCommandStatus {
  commandId: string;
  taskId: string;
  state: "PENDING" | "SUCCEEDED" | "FAILED" | "TIMED_OUT";
  sentAt: string;
  acknowledgedAt?: string;
  simulated: boolean;
}

/** 稳定状态来自采集源，空态与过期数据不携带读数或图片。 */
export interface InboundSiteReading {
  state: "LIVE" | "STALE" | "UNAVAILABLE";
  simulated: boolean;
  station?: string;
  kg?: string;
  stable?: boolean;
  observedAt?: string;
  capture?: string;
}

/** 人工复核的磅票，仅供提交独立命令，重量为正，单位千克。 */
export interface WeighTicket {
  station: string;
  ticketNo: string;
  observedAt: string;
  kg: string;
}
export type UnloadingOutcome = "ACCEPTED" | "ACCEPTED_WITH_DEDUCTION" | "RETURN";
export type WorkbenchNumberOperation = "BulkInboundQualityOrder.register_sample"
  | "BulkInboundMeteringOrder.register_external" | "BulkInboundMeteringOrder.record_gross";

/** 空磅票用于独立录入，避免复用上一张单据的证据。 */
export function newWeighTicket(): WeighTicket {
  return { station: "", ticketNo: "", observedAt: "", kg: "" };
}

/** 物理重量以千克十进制文本传输，允许 DECIMAL(18,4) 正值。 */
export function normalizedWeighTicket(input: WeighTicket): WeighTicket | undefined {
  const station = input.station.trim();
  const ticketNo = input.ticketNo.trim();
  const kg = input.kg.trim();
  if (!station || !ticketNo || station.length > 64 || ticketNo.length > 96
    || !/^\d{1,14}(\.\d{1,4})?$/.test(kg) || !/[1-9]/.test(kg) || !Number.isFinite(Date.parse(input.observedAt))) return;
  return { station, ticketNo, kg, observedAt: new Date(input.observedAt).toISOString() };
}
