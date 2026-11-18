import type { RecordAudit } from "@appnexus/ui";
/** 商品配置页面只接收固定四类资源，不开放任意路径或表名。 */
export type CatalogKind="unit"|"category"|"attribute"|"template";
/** 单位/分类/属性/模板摘要；可选字段仅对应实际资源公开字段。 */
export interface CatalogRow {id:string;code:string;name?:string;dimensionCode?:string;precisionScale?:number;parentId?:string|null;currentConfigId?:string|null;depth?:number;enabled:boolean;version:number;purpose?:string;groupId?:string;audit?:RecordAudit}
/** 发布历史按固定版本标识，不能把版本号当CAS。 */
export interface CatalogVersion {id:string;name?:string;state:string;revision?:number;versionNo?:number;dataType?:string;unitId?:string|null;numericScale?:number;validationJson?:string;description?:string}
/** 显式直连换算；系数为精确字符串，四审计由统一列表展示。 */
export interface UnitConversion {id:string;sourceUnitId:string;targetUnitId:string;sourceCode:string;targetCode:string;factor:string;enabled:boolean;version:number;audit?:RecordAudit}
/** 壳层注入真实HTTP与CSRF适配，业务组件不深层导入admin。 */
export interface ProductCatalogApi {
 operations():Promise<string[]>;
 conversions():Promise<UnitConversion[]>;
 createConversion(data:{sourceUnitId:string;targetUnitId:string;factor:string}):Promise<unknown>;
 conversionStatus(id:string,data:{enabled:boolean;version:number}):Promise<unknown>;
 calculateConversion(id:string,quantity:string):Promise<{exact:string;display:string}>;
 list(kind:CatalogKind,query:{keyword:string;page:number;size:number;parentId?:string}):Promise<{rows:CatalogRow[];total:number}>;
 create(kind:CatalogKind,data:object):Promise<{id:string}>;
 update(kind:CatalogKind,id:string,data:object):Promise<unknown>;
 status(kind:CatalogKind,id:string,data:{enabled:boolean;version:number}):Promise<unknown>;
 versions(kind:"attribute"|"template",id:string):Promise<CatalogVersion[]>;
 publishVersion(kind:"attribute"|"template",id:string,version:number):Promise<unknown>;
 options(id:string):Promise<Array<{code:string;label:string}>>;
 items(id:string):Promise<Array<{attributeId:string;revisionId:string;groupCode:string;groupName:string;requiredFlag:boolean;searchable:boolean;allowCustom:boolean}>>;
 updateRevision(id:string,data:object):Promise<unknown>;
 updateVersion(id:string,data:object):Promise<unknown>;
 createRevision(id:string,data:object):Promise<{id:string}>;
 createVersion(id:string,data:object):Promise<{id:string}>;
 groups():Promise<CatalogRow[]>;
 createGroup(data:object):Promise<{id:string}>;
 updateGroup(id:string,data:object):Promise<unknown>;
 groupStatus(id:string,data:{enabled:boolean;version:number}):Promise<unknown>;
 retireVersion(kind:"attribute"|"template",id:string,version:number):Promise<unknown>;
 configuration(categoryId:string,configId:string):Promise<{templates:Record<string,string|null>;serviceUnitRequired:boolean}>;
 publishConfiguration(id:string,data:object):Promise<unknown>;
}
