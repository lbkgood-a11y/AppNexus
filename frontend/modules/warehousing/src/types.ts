import type { RecordAudit } from "@appnexus/ui";

export interface WarehouseOption {
  id: string;
  label: string;
}

export interface CursorPage<T> {
  rows: T[];
  /** 服务端无下一页时为 null，页面不推算总数。 */
  nextAfterId: string | null;
}

/** 库存总览的三个筛选项均可省略。 */
export interface InventoryFilter {
  warehouseId?: string;
  ownerId?: string;
  productId?: string;
}

/** 按实收身份显示当前在库量和待检量，保留原实收单位。 */
export interface InventoryRow {
  receiptId: string;
  serial?: string;
  warehouse: string;
  owner: string;
  product: string;
  specification?: string | null;
  skuCode?: string | null;
  receiptBatchNo?: string | null;
  unit: string | null;
  onHand: number;
  pending: number;
  audit: RecordAudit;
}

/** 使用宿主认证请求和受权限限制的主数据选项。 */
export interface InventoryOverviewApi {
  list(filters: InventoryFilter, afterId?: string): Promise<CursorPage<InventoryRow>>;
  warehouses(keyword: string): Promise<WarehouseOption[]>;
  owners(keyword: string): Promise<WarehouseOption[]>;
  products(keyword: string): Promise<WarehouseOption[]>;
}
