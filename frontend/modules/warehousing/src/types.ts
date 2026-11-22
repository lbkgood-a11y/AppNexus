/** 两类余额与整卷明细共用筛选，品规为空时查询整个商品。 */
export interface WarehouseScope {
  ownerId: string;
  warehouseId: string;
  productId: string;
  identityId?: string;
}

export interface WarehouseOption {
  id: string;
  label: string;
}

export interface CursorPage<T> {
  rows: T[];
  /** 服务端无下一页时为 null，页面不推算总数。 */
  nextAfterId: string | null;
}

/** 数量均以对应行的库存基础计量单位显示。 */
export interface PhysicalBalance {
  identityId: string;
  unitId: string;
  onHandTitle: number;
  pickable: number;
}

export interface AuthorizationBalance {
  authorizationId: string;
  noticeId: string;
  identityId: string;
  unitId: string;
  cap: number;
  reserved: number;
  issued: number;
  remaining: number;
  status: string;
}

export interface WarehouseCoil {
  receiptId: string;
  serial: string;
  identityId: string;
  unitId: string;
  quantity: number;
  /** 提交时传服务端最近返回的货权代次，防止覆盖另一次过户。 */
  titleRevision: number;
  status: string;
  transferable: boolean;
}

export interface TitleTransferInput {
  id: string;
  receiptId: string;
  warehouseId: string;
  fromOwnerId: string;
  toOwnerId: string;
  expectedTitleRevision: number;
  evidenceId: string;
}

/** 原授权恢复入口，路由使用现有原卷与返仓发运标识。 */
export interface AuthorizationRecoveryEntry {
  receiptId:string;
  serial:string;
  transitId:string;
  executable:boolean;
  tripId:string;
  vehicleNumber:string;
  /** 所示车次的占用和已出，使用该授权的库存基础单位。 */
  tripReserved:number;
  tripIssued:number;
  /** 已撤配且当前没有在办车次时，入口恢复原授权以建立新车次。 */
  replacementRequired:boolean;
}

/** 页面只经宿主注入的有权查询、统一 CSRF 和会话入口使用真实业务。 */
export interface WarehouseTitleApi {
  operations(): Promise<string[]>;
  owners(keyword: string): Promise<WarehouseOption[]>;
  warehouses(keyword: string): Promise<WarehouseOption[]>;
  products(keyword: string): Promise<WarehouseOption[]>;
  identities(productId: string): Promise<WarehouseOption[]>;
  units(): Promise<WarehouseOption[]>;
  physical(scope: WarehouseScope): Promise<PhysicalBalance[]>;
  coils(scope: WarehouseScope, afterId?: string): Promise<CursorPage<WarehouseCoil>>;
  authorizations(scope: WarehouseScope, afterId?: string): Promise<CursorPage<AuthorizationBalance>>;
  /** 按原授权找回当前可续办的原卷及已完成的只读历史。 */
  recoveryEntries(authorizationId:string,afterId?:string):Promise<CursorPage<AuthorizationRecoveryEntry>>;
  transfer(input: TitleTransferInput): Promise<unknown>;
}
