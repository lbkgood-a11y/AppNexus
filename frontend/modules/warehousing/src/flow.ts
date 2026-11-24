import type { WarehouseOption } from "./types";

export interface PositionOption extends WarehouseOption {
  code: string;
  version: number;
}
export interface ItemOption extends WarehouseOption {
  kind: "PRODUCT" | "SPECIFICATION" | "SKU";
  /** 有效 SKU 对应的父规格及库存单位；普通商品和规格继承商品库存单位。 */
  specificationId?: string;
  inventoryUnit?: WarehouseOption;
}
export interface ProductOptions {
  unitId: string;
  unitCode: string;
  unitName: string;
  version: number;
  items: ItemOption[];
}
