import type { RecordAudit } from "@appnexus/ui";
/** 商品配置页面只接收固定四类资源，不开放任意路径或表名。 */
export type CatalogKind="unit"|"category"|"attribute"|"template";
/** 模板用途由服务端契约固定；页面筛选仅作用于授权后返回的模板摘要。 */
export const templatePurposes = ["BASIC", "SPEC", "QUALITY", "EXTENSION"] as const;
export type TemplatePurpose = (typeof templatePurposes)[number];
/** 单位/分类/属性/模板摘要；可选字段仅对应实际资源公开字段。 */
export interface CatalogRow {id:string;code:string;name?:string;dataType?:string;dimensionCode?:string;precisionScale?:number;parentId?:string|null;currentConfigId?:string|null;depth?:number;enabled:boolean;version:number;purpose?:string;groupId?:string;audit?:RecordAudit}
/** 模板项使用绑定修订的名称、类型和标识。 */
export interface CatalogTemplateItem {attributeId:string;revisionId:string;name:string;dataType:string;groupCode:string;groupName:string;requiredFlag:boolean;searchable:boolean;allowCustom:boolean}
/** 发布历史按固定版本标识，不能把版本号当CAS。 */
export interface CatalogVersion {id:string;name?:string;state:string;revision?:number;versionNo?:number;dataType?:string;unitId?:string|null;numericScale?:number;validationJson?:string;description?:string}
/** 显式直连换算；系数为精确字符串，四审计由统一列表展示。 */
export interface UnitConversion {id:string;sourceUnitId:string;targetUnitId:string;sourceCode:string;targetCode:string;factor:string;enabled:boolean;version:number;audit?:RecordAudit}
/** 壳层注入真实HTTP与CSRF适配，业务组件不深层导入admin。 */
export interface ProductCatalogApi {
 operations():Promise<string[]>;
 /** 读取当前租户的单位分组及编辑版本。 */
 unitGroups():Promise<Array<{code:string;name:string;version:number}>>;
 /** 创建稳定编码的单位分组。 */
 createUnitGroup(data:{code:string;name:string}):Promise<void>;
 /** 使用当前版本修改分组名称。 */
 renameUnitGroup(code:string,data:{name:string;version:number}):Promise<void>;
 conversions():Promise<UnitConversion[]>;
 createConversion(data:{sourceUnitId:string;targetUnitId:string;factor:string}):Promise<unknown>;
 conversionStatus(id:string,data:{enabled:boolean;version:number}):Promise<unknown>;
 calculateConversion(id:string,quantity:string):Promise<{exact:string;display:string}>;
 list(kind:CatalogKind,query:{keyword:string;page:number;size:number;parentId?:string;purpose?:TemplatePurpose;groupCode?:string}):Promise<{rows:CatalogRow[];total:number}>;
 /** 获取当前租户授权后的完整属性目录，供按模板关联后分页。 */
 attributes():Promise<CatalogRow[]>;
 create(kind:CatalogKind,data:object):Promise<{id:string}>;
 update(kind:CatalogKind,id:string,data:object):Promise<unknown>;
 status(kind:CatalogKind,id:string,data:{enabled:boolean;version:number}):Promise<unknown>;
 versions(kind:"attribute"|"template",id:string):Promise<CatalogVersion[]>;
 publishVersion(kind:"attribute"|"template",id:string,version:number):Promise<unknown>;
 options(id:string):Promise<Array<{code:string;label:string}>>;
 items(id:string):Promise<CatalogTemplateItem[]>;
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
