/** 业务对象管理公开类型；不暴露身份输入，租户上下文由应用 HTTP 层附加。 */
export interface Descriptor {
  objectType: string; nameZh: string; nameEn: string; module: string; version: number;
  /** 业务对象性质，与审批和打印能力独立。 */
  category: "MASTER_DATA"|"TRANSACTION"|"CONFIGURATION"|"LEDGER"|"SYSTEM";
  fields: { key: string; nameZh: string; nameEn: string; type: string; unit: string }[];
  operations: {operationCode:string;nameZh:string;nameEn:string;permissionCode:string;operationId:string;interaction:{code:string;version:number;objectType:string;operationCode:string;trigger:string;capabilities:{service:string;completion:string;reference:string;purpose:string;fields:unknown[];outcomes:string[]}[]};pageOperation?:PageOperation;aliases?:{operationId:string;pageOperation:PageOperation}[]}[];
}
/** 构建登记的真实页面入口，管理端不能录入任意地址。 */
export interface PageOperation {pageKey:string;pagePath:string;nameZh:string;nameEn:string;method:string;apiPath:string}
/** 代码登记的入口及平台启停投影；不能通过客户端新增页面或接口。 */
export interface PageRelationship {operationCode:string;operationId:string}
export interface PageRelationshipEditor {revision:number;relationships:PageRelationship[];candidates:{operationId:string;compatibleOperationCode:string;permissionCode:string;pageOperation:PageOperation}[]}
/** 每条绑定全部条件满足，绑定间要求唯一匹配，version 为具体流程发布版本。 */
export interface Binding {
  id: string; name: string; definitionId: string|null; definitionVersion: number;operationCode:string;service:'BUSINESS'|'WORKFLOW'|'PRINT';operationId:string;sceneCode?:string|null;
  conditions: { field: string; operator: string; value: string }[];
}
/** 管理接口注入约定；写操作只执行一次，冲突由管理人员重载后处理。 */
export interface BusinessObjectApi {
  readiness:(type:string)=>Promise<{operationCode:string;category:string;code:string;ready:boolean;target:string}[]>;
  pageRelationships:(type:string)=>Promise<PageRelationshipEditor>;
  controlPage:(type:string,revision:number,operationId:string,enabled:boolean,reason:string)=>Promise<PageRelationshipEditor>;
  catalog: () => Promise<{ descriptor: Descriptor; enabled: boolean; revision: number }[]>;
  activate: (type: string, revision: number, enabled: boolean) => Promise<unknown>;
  tenants: () => Promise<{ id: string; name: string }[]>;
  grants: (type: string) => Promise<{ tenant_id: string; enabled: boolean }[]>;
  grant: (type: string, tenant: string, enabled: boolean) => Promise<unknown>;
  configuration: (type: string) => Promise<{ revision: number; publishedVersion: number; bindings: Binding[] }>;
  workflows: (type: string,operationCode:string) => Promise<{ id: string; version: number; name: string }[]>;
  scenes:(type:string,operationCode:string)=>Promise<{id:string;code:string;name:string;dataContract:string}[]>;
  save: (type: string, revision: number, bindings: Binding[]) => Promise<unknown>;
  publish: (type: string, revision: number) => Promise<unknown>;
}
