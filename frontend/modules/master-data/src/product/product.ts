import type {RecordAudit} from "@appnexus/ui";
/** V1无规格商品，数值属性始终使用字符串，避免浏览器浮点/大整数精度损失。 */
export interface ProductValue {templateItemId:string;value:string|null;optionIds:string[]}
export interface ProductField {templateItemId:string;attributeId:string;revisionId:string;purpose:"BASIC"|"QUALITY"|"EXTENSION";name:string;dataType:string;numericScale:number|null;groupCode:string;groupName:string;requiredFlag:boolean;unitId:string|null;unitCode:string|null;unitName:string|null;options:Array<{id:string;code:string;label:string;enabled:boolean}>}
export interface ProductConfiguration {id:string;categoryId:string;revision:number;path:string;serviceUnitRequired:boolean;fields:ProductField[];templates:Record<string,string|null>}
export interface ProductOption {id:string;code:string;name:string;configId?:string|null}
export interface ProductRow {id:string;code:string;name:string;productForm:string|null;status:"DRAFT"|"ENABLED"|"DISABLED";structureState:string;archivedAt:string|null;version:number}
export interface ProductRecord extends ProductRow {
 shortName:string|null;description:string|null;brand:string|null;origin:string|null;categoryId:string|null;configId:string|null;unitId:string|null;structureRevision:number;
 completionIssues:string[];audit:RecordAudit;
 values:Array<{templateItemId:string;revisionId:string;attributeName:string;dataType:string;textValue:string|null;integerValue:string|null;decimalValue:string|null;booleanValue:boolean|null;dateValue:string|null}>;
 choices:Array<{templateItemId:string;optionId:string;optionCode:string;label:string}>;
 baseUnit?:{code:string;name:string};categoryConfiguration?:{revision:number;path:string};
 legacyEnabled:boolean|null;legacySpecificationOriginal:string|null;legacyDisposition:string|null;
}
export interface ProductDraft {code:string;name:string;shortName:string|null;categoryId:string|null;configId:string|null;productForm:string|null;unitId:string|null;description:string|null;brand:string|null;origin:string|null;values:ProductValue[]}
export interface ProductUpdate {name:string;shortName:string|null;description:string|null;brand:string|null;origin:string|null;values:ProductValue[];version:number}
/** 迁移源版本与完整目标固定绑定；退出项由预览显示并经用户明确确认。 */
export interface ProductMigration {sourceVersion:number;sourceStructureRevision:number;sourceCategoryConfigId:string|null;categoryId:string|null;configId:string|null;productForm:string|null;unitId:string|null;values:ProductValue[];droppedItemIds:string[];reason:string;legacyDisposition:string|null}
export interface ProductPreview {sourceVersion:number;sourceStructureRevision:number;target:ProductMigration;completionIssues:string[];droppedItemIds:string[];previewToken:string;expiresAt:string;executable:boolean}
export interface ProductMigrationResult {productId:string;resultVersion:number;resultStructureRevision:number;changeId:string;replayUntil:string;replayed:boolean}
export interface ProductChange {id:string;version:number;eventType:string;beforeSnapshot:string|null;afterSnapshot:string;schemaVersion:number;createdAt:string;createdBy:string|null}
export interface ProductQuery {name:string;code:string;form?:string;status?:string;categoryId?:string;archived:boolean;page:number;size:number}
/** 所有API由应用壳注入真实Cookie/CSRF；组件不持久化token，不自动重放写入。 */
export interface ProductApi {
 operations():Promise<string[]>;
 list(query:ProductQuery):Promise<{rows:ProductRow[];total:number}>;
 read(id:string):Promise<ProductRecord>;
 create(data:ProductDraft):Promise<ProductRecord>;
 update(id:string,data:ProductUpdate):Promise<ProductRecord>;
 status(id:string,status:"ENABLED"|"DISABLED",version:number):Promise<ProductRecord>;
 archive(id:string,version:number,restore:boolean):Promise<ProductRecord>;
 changes(id:string,page:number,size:number):Promise<{rows:ProductChange[];total:number}>;
 options(kind:"category"|"unit",keyword:string,page:number):Promise<{rows:ProductOption[];total:number}>;
 configuration(id:string):Promise<ProductConfiguration>;
 configurations(categoryId:string,page:number):Promise<{rows:Array<{id:string;revision:number;path:string}>;total:number}>;
 preview(id:string,change:ProductMigration):Promise<ProductPreview>;
 migrate(id:string,change:ProductMigration,previewToken:string,key:string):Promise<ProductMigrationResult>;
}
