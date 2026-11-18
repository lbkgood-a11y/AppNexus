/** 固定主数据对象；页面不能声明任意实体或 API 路径。 */
export type MasterDataKind = "partner" | "warehouse" | "storage-area" | "storage-location" | "country-region" | "subdivision";
/** 当前租户公共档案；人员档案不是登录账号或组织任职。 */
export interface MasterDataRow { id:string; code:string; name:string; attributes:Record<string,string>; enabled:boolean; version:number; audit:import("@appnexus/ui").RecordAudit; contacts?:PartnerDetail[]; bankAccounts?:PartnerDetail[] }
/** 新增或编辑输入；version 为非负版本，编码仅在创建时确定，属性限于此类白名单。 */
export interface MasterDataInput { code:string; name:string; attributes:Record<string,string>; version:number; contacts?:PartnerDetailInput[]; bankAccounts?:PartnerDetailInput[] }
/** 页面请求由应用壳注入，复用真实会话、CSRF 和统一操作准入；不存在模拟数据回退。 */
export interface MasterDataApi {
  /** 宿主客商/仓库读取权限下的完整租户地理摘要；无浏览器长期缓存。 */
  geographyDirectory(kind:"partner"|"warehouse"):Promise<import("@appnexus/ui").GeographyNode[]>;
  /** 按客商 UUID 读取已授权详情；列表不带关联明细。 */
  readPartner(id:string):Promise<MasterDataRow>;
  /** 读取当前租户客商分类；包含历史停用名称，禁止作为新值选择。 */
  partnerOptions():Promise<Record<string,DictionaryOption[]>>;
  /** 商品计量单位来自业务字典，不在客户端推导换算关系。 */
  /** 按原读取权限查询仓库详情，版本及审计只读。 */
  readWarehouse(id:string):Promise<MasterDataRow>;
  /** 本租户仓库分类，含历史停用项。 */
  /** 读取当前可信租户的非敏感高德配置，不包含服务端安全密钥。 */
  warehouseMapConfig():Promise<{key:string;serviceHost:string}>;
  warehouseOptions():Promise<Record<string,DictionaryOption[]>>;
  /** 分页读取标准目录；query.keyword 为空不筛选，page 从 1 开始，size 为每页条数（1–100），默认 20。 */
  geographyOptions(kind:"country-region"|"subdivision",query:{keyword:string;page:number;size:number}):Promise<{rows:GeographyOption[];total:number}>;
  /** 懒加载导航直接子节点；parent 为空获取洲，page 从 1 开始，size 为每页 1–100 条。 */
  geographyTree(kind:"country-region"|"subdivision",query:{parent:string;page:number;size:number}):Promise<{parentCode:string;rows:GeographyNavigationNode[];total:number}>;
  operations():Promise<string[]>;
  list(kind:MasterDataKind,query:{keyword:string;enabled?:boolean;page:number;size:number;scope?:string;id?:string}):Promise<{rows:MasterDataRow[];total:number}>;
  create(kind:MasterDataKind,input:MasterDataInput):Promise<{id:string}>;
  update(kind:MasterDataKind,id:string,input:MasterDataInput):Promise<void>;
  setStatus(kind:MasterDataKind,id:string,input:{enabled:boolean;version:number}):Promise<void>;
}
/** 固定领域字段，与后端 DTO 和数据库列逐项对应；无任意 JSON 属性编辑器。 */
export const masterDataFields:Record<MasterDataKind,string[]> = {
  "partner":["partnerType", "creditCode", "contactName", "phone", "address"],
  "storage-area":["parentCode", "areaType", "capacity", "remarks"],
  "storage-location":["parentCode", "capacity", "remarks"],
  "warehouse":["shortName", "warehouseType", "capacity", "operatingMode", "address", "contactName", "phone"],
  "country-region":["englishName", "alpha3Code", "numericCode", "parentCode"],
  "subdivision":["countryCode", "subdivisionType", "parentCode"],
};
/** 唯一固定操作映射；按钮和保存共用此映射，不以权限码或页面名称推断授权。 */
export const masterDataOperations = {
  "partner":{list:"BusinessPartner.list",create:"BusinessPartner.create",update:"BusinessPartner.update",setStatus:"BusinessPartner.setStatus"},
  "storage-area":{list:"StorageArea.list",create:"StorageArea.create",update:"StorageArea.update",setStatus:"StorageArea.setStatus"},
  "storage-location":{list:"StorageLocation.list",create:"StorageLocation.create",update:"StorageLocation.update",setStatus:"StorageLocation.setStatus"},
  "warehouse":{list:"Warehouse.list",create:"Warehouse.create",update:"Warehouse.update",setStatus:"Warehouse.setStatus"},
  "country-region":{list:"CountryRegion.list",create:"CountryRegion.create",update:"CountryRegion.update",setStatus:"CountryRegion.setStatus"},
  "subdivision":{list:"Subdivision.list",create:"Subdivision.create",update:"Subdivision.update",setStatus:"Subdivision.setStatus"},
} as const;


/** 客商聚合明细输入；标识仅用于原客商下修改，新增时省略，审计禁止进入写入契约。 */
export interface PartnerDetailInput {id?:string;values:Record<string,string>}
/** 明细审计由服务端写入；页面仅展示，不从目录推测账号姓名。 */
export interface PartnerDetail extends PartnerDetailInput {audit?:import("@appnexus/ui").RecordAudit}

/** 租户业务字典选项，名称由字典维护，启用状态由后端复核。 */
export interface DictionaryOption {value:string;name:string;enabled:boolean}

/** 标准编码与派生属性；显示名称不作为关联键。 */
export interface GeographyOption {code:string;name:string;attributes:Record<string,string>}

/** 地理维护导航；洲键使用 continent: 前缀，与 AS 等 ISO 国家代码隔离。 */
export interface GeographyNavigationNode {code:string;name:string;originalName:string;hierarchyCode:string;parentCode:string;parentHierarchyCode:string;leaf:boolean}
