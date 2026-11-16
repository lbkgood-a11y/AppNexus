/** 五类固定主数据；页面不能声明任意实体或 API 路径。 */
export type MasterDataKind = "partner" | "warehouse" | "country-region" | "product";
/** 当前租户公共档案；人员档案不是登录账号或组织任职。 */
export interface MasterDataRow { id:string; code:string; name:string; attributes:Record<string,string>; enabled:boolean; version:number; audit:import("@appnexus/ui").RecordAudit }
/** 新增或编辑输入；version 为非负版本，编码仅在创建时确定，属性限于此类白名单。 */
export interface MasterDataInput { code:string; name:string; attributes:Record<string,string>; version:number }
/** 页面请求由应用壳注入，复用真实会话、CSRF 和统一操作准入；不存在模拟数据回退。 */
export interface MasterDataApi {
  operations():Promise<string[]>;
  list(kind:MasterDataKind,query:{keyword:string;enabled?:boolean;page:number;size:number}):Promise<{rows:MasterDataRow[];total:number}>;
  create(kind:MasterDataKind,input:MasterDataInput):Promise<{id:string}>;
  update(kind:MasterDataKind,id:string,input:MasterDataInput):Promise<void>;
  setStatus(kind:MasterDataKind,id:string,input:{enabled:boolean;version:number}):Promise<void>;
}
/** 固定领域字段，与后端 DTO 和数据库列逐项对应；无任意 JSON 属性编辑器。 */
export const masterDataFields:Record<MasterDataKind,string[]> = {
  "partner":["partnerType", "creditCode", "contactName", "phone", "address"],
  "warehouse":["address", "contactName", "phone"],
  "country-region":["englishName", "alpha3Code", "numericCode"],
  "product":["specification", "unit", "barcode"],
};
/** 唯一固定操作映射；按钮和保存共用此映射，不以权限码或页面名称推断授权。 */
export const masterDataOperations = {
  "partner":{list:"BusinessPartner.list",create:"BusinessPartner.create",update:"BusinessPartner.update",setStatus:"BusinessPartner.setStatus"},
  "warehouse":{list:"Warehouse.list",create:"Warehouse.create",update:"Warehouse.update",setStatus:"Warehouse.setStatus"},
  "country-region":{list:"CountryRegion.list",create:"CountryRegion.create",update:"CountryRegion.update",setStatus:"CountryRegion.setStatus"},
  "product":{list:"Product.list",create:"Product.create",update:"Product.update",setStatus:"Product.setStatus"},
} as const;

