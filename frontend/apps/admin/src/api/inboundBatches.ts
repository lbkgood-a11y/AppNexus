import { http } from "@/utils/http";

/** 已授权仓储位置及签认时引用版本，供实收沿用。 */
export interface InboundPosition {
  warehouseId: string;
  areaId: string;
  locationId: string;
  locationName?: string;
  warehouseVersion: number;
  areaVersion: number;
  locationVersion: number;
}

/** 通知、接管和实收三页共享的品规及收货额度。 */
export interface BatchLine {
  id: string;
  noticeLineId: string;
  lineNo: number;
  productId: string;
  productName?: string;
  identityKind: string;
  identityId: string;
  identityName?: string;
  unitId: string;
  unitName?: string;
  approvedQuantity: number;
  receivedQuantity: number;
  custodyId?: string;
  position?: InboundPosition;
  expectedQuantity?: number;
  rejectionQuantity?: number;
  rejectionReason?: string;
  evidenceId?: string;
  measurementId?: string;
  receiptId?: string;
  processed?: boolean;
  stockStatus?: string;
}

/** 入场接管／实收确认的主单与同一事务提交的明细。 */
export interface InboundBatchDocument {
  id: string;
  noticeId: string;
  warehouseId: string;
  ownerId: string;
  ownerName?: string;
  warehouseName?: string;
  entryId?: string;
  vehicleNumber?: string;
  carrier?: string;
  arrivedAt?: string;
  measurementRef?: string;
  remark?: string;
  audit: Record<string, unknown>;
  lines: BatchLine[];
}

const entries = "/api/bulk-inbound-entries";
const receipts = "/api/bulk-inbound-receipt-documents";

async function post<T>(path: string, data: object) {
  const csrf = await http.request<{ headerName: string; token: string }>("get", "/bff/csrf");
  return http.request<T>("post", path, { data, headers: { [csrf.headerName]: csrf.token } });
}

export const inboundBatchesApi = {
  entry: (id: string) => http.request<InboundBatchDocument>("get", entries + "/" + encodeURIComponent(id)),
  receipt: (id: string) => http.request<InboundBatchDocument>("get", receipts + "/" + encodeURIComponent(id)),
  recordEntry: (data: object) => post<void>(entries, data),
  confirmReceipt: (data: object) => post<void>(receipts, data)
};
