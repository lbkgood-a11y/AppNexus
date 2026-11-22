import type { AuthorizationBalance, WarehouseOption, WarehouseScope } from "./types";

/** 仓储流程实际命令顺序。每项均对应一条既有业务操作。 */
export const steps = [
  "inbound", "custody", "receive", "title", "quality", "identify", "transfer", "outbound",
  "authorize", "trip", "allocate", "vehicle", "weigh", "handover", "return"
] as const;
export type FlowStep = typeof steps[number];

export interface PositionOption extends WarehouseOption {
  code: string;
  version: number;
}
export interface ItemOption extends WarehouseOption {
  kind: "PRODUCT" | "SPECIFICATION" | "SKU";
}
export interface ProductOptions {
  unitId: string;
  unitCode: string;
  unitName: string;
  version: number;
  items: ItemOption[];
}
export interface CoilTrace {
  stock: {
    receiptId: string;
    serial: string|null;
    noticeId: string;
    custodyId: string;
    measurementId: string;
    receiptBatchNo?: string | null;
    ownerId: string;
    warehouseId: string;
    productId: string;
    identityId: string;
    unitId: string;
    onHand: number;
    pending: number;
    title: number;
    titleEvidenceId: string|null;
    qualityEvidenceId: string|null;
  };
  documents: {kind:string; id:string; parentId:string; warehouseId:string; ownerId:string; otherOwnerId:string|null; quantity:number}[];
}

/** 授权下与原卷相关的车次事件。单据额度为读取时的快照。 */
export interface RecoveryEvent {
  tripId:string;
  vehicleNumber:string;
  cap:number;
  reserved:number;
  issued:number;
  kind:string;
  id:string;
  evidenceId:string|null;
  occurredAt:string;
}

/** 同一原卷的已建续办车次；下一步由后端核对占用与当前复磅后返回。 */
export interface RecoveryTrip {
  tripId:string;
  vehicleNumber:string;
  allocationId:string|null;
  reviewId:string|null;
  nextStep:"ALLOCATE"|"VEHICLE"|"WEIGH"|"HANDOVER";
}
export interface AuthorizationRecovery {
  noticeId:string;
  authorizationId:string;
  pickupPartnerId:string;
  canPlan:boolean;
  events:RecoveryEvent[];
  activeTrip:RecoveryTrip|null;
}

/** 宿主注入权威业务端点及版本化主数据。 */
export interface CoilFlowApi {
  operations(): Promise<string[]>;
  owners(keyword:string): Promise<WarehouseOption[]>;
  products(keyword:string): Promise<WarehouseOption[]>;
  locations(kind:"warehouse"|"storage-area"|"storage-location",parent?:string): Promise<PositionOption[]>;
  product(id:string): Promise<ProductOptions>;
  submit(step:FlowStep,payload:Record<string,unknown>): Promise<{state?:string;reasons?:string[]}|undefined>;
  /**
   * 读取已有授权的续办上下文，调用不占用额度。
   * @param authorizationId 原提货授权 UUID
   * @param receiptId 已接管原卷的库存 UUID
   */
  recovery(authorizationId:string,receiptId:string):Promise<AuthorizationRecovery>;
  /** 使用原配货、车次和库存标识撤销，服务端按原配货识别重试。 */
  cancelAllocation(input:{id:string;tripId:string;receiptId:string}):Promise<void>;
  /** 当前可发结论，读取不产生占用。 */
  eligibility(receiptId:string):Promise<string>;
  trace(receiptId:string): Promise<CoilTrace>;
  authorization(scope:WarehouseScope,id:string): Promise<AuthorizationBalance|undefined>;
}
