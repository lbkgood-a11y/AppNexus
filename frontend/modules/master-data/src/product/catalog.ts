import type { RecordAudit } from "@appnexus/ui";
/** 商品配置页面只接收固定四类资源，不开放任意路径或表名。 */
export type CatalogKind="unit"|"category"|"attribute"|"template";
/** 单位/分类/属性/模板摘要；可选字段仅对应实际资源公开字段。 */
export interface CatalogRow {id:string;code:string;name?:string;dimensionCode?:string;precisionScale?:number;parentId?:string|null;currentConfigId?:string|null;depth?:number;enabled:boolean;version:number;purpose?:string;groupId?:string;audit?:RecordAudit}
/** 发布历史按固定版本标识，不能把版本号当CAS。 */
export interface CatalogVersion {id:string;name?:string;state:string;revision?:number;versionNo?:number;dataType?:string}
/** 壳层注入真实HTTP与CSRF适配，业务组件不深层导入admin。 */
export interface ProductCatalogApi {
 operations():Promise<string[]>;
 list(kind:CatalogKind,query:{keyword:string;page:number;size:number;parentId?:string}):Promise<{rows:CatalogRow[];total:number}>;
 create(kind:CatalogKind,data:object):Promise<{id:string}>;
 update(kind:"unit"|"category",id:string,data:object):Promise<unknown>;
 status(kind:"unit"|"category",id:string,data:{enabled:boolean;version:number}):Promise<unknown>;
 versions(kind:"attribute"|"template",id:string):Promise<CatalogVersion[]>;
 publishVersion(kind:"attribute"|"template",id:string,version:number):Promise<unknown>;
 createRevision(id:string,data:object):Promise<{id:string}>;
 createVersion(id:string,data:object):Promise<{id:string}>;
 publishConfiguration(id:string,data:object):Promise<unknown>;
}
