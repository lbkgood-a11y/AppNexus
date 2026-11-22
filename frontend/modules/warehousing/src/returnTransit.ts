import type { CoilFlowApi, CoilTrace } from "./flow";
import type { WarehouseOption } from "./types";

/** 返仓每卷按原发运身份追踪。账面量和实测量沿用发运单位。 */
export interface TransitCoil {
  receiptId:string;
  serial:string;
  bookQuantity:number;
  measuredQuantity:number;
  receivedReturnId:string|null;
  lossId:string|null;
  state:"IN_TRANSIT"|"RECEIVED"|"LOSS_SETTLED";
  receivingWarehouseId:string;
  receivingWarehouseName?:string;
  rerouteId:string|null;
}
export interface TransitQuota {
  id:string; cap:number; reserved:number; issued:number; remaining:number;
}
export interface TransitEvidence {
  kind:string; id:string; receiptId:string; evidenceId:string; titleEvidenceId:string|null; qualityEvidenceId:string|null;
  previousWarehouseId:string|null; warehouseId:string|null; reason:string|null;
  previousWarehouseName?:string; warehouseName?:string;
}
export interface ReturnTransit {
  id:string; handoverId:string; sourceWarehouseId:string; receivingWarehouseId:string;
  unitId:string; evidenceId:string; coils:TransitCoil[]; history:TransitEvidence[];
  sourceWarehouseName?:string; receivingWarehouseName?:string; unitName?:string;
  noticeQuota:TransitQuota|null; authorizationQuota:TransitQuota|null; tripQuota:TransitQuota|null;
}
/** 从原交接读取的当前可发运卷；候选仅用于引导，提交时再次核验。 */
export interface ReturnPreparation {
  handoverId:string; sourceWarehouseId:string;
  coils:{receiptId:string; serial:string; bookQuantity:number; measuredQuantity:number}[];
}
/** 监管冻结及解除凭证，审计信息仅供有权操作员查看。 */
export interface StockRestriction {
  id:string;
  evidenceId:string;
  releaseEvidenceId:string|null;
  createdBy:string|null;
  createdAt:string;
  updatedBy:string|null;
  updatedAt:string;
}
export type TransitAction="receive"|"loss"|"reopen"|"reroute"|"quality"|"freeze"|"lift";
export interface DispatchInput {
  id:string; handoverId:string; receivingWarehouseId:string; evidenceId:string; receiptIds:string[];
}

/** 宿主沿用同一安全会话、主数据与既有实际业务命令。 */
export interface ReturnTransitApi {
  operations():Promise<string[]>;
  owners(keyword:string):Promise<WarehouseOption[]>;
  locations:CoilFlowApi["locations"];
  units():Promise<WarehouseOption[]>;
  /** 读取同一原卷的冻结和解除依据，按当前库存范围授权。 */
  restrictions(receiptId:string):Promise<StockRestriction[]>;
  load(id:string):Promise<ReturnTransit>;
  prepare(handoverId:string):Promise<ReturnPreparation>;
  dispatch(input:DispatchInput):Promise<ReturnTransit>;
  submit(action:TransitAction,input:Record<string,unknown>):Promise<unknown>;
  trace(receiptId:string):Promise<CoilTrace>;
}
