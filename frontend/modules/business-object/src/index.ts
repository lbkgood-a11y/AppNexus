/** 业务对象管理公开类型；不暴露身份输入，租户上下文由应用 HTTP 层附加。 */
export interface Descriptor {
  objectType: string; nameZh: string; nameEn: string; module: string; version: number;
  /** 业务对象性质，与审批和打印能力独立。 */
  category: "MASTER_DATA"|"TRANSACTION"|"CONFIGURATION"|"LEDGER"|"SYSTEM";
  /** 同一业务声明中实际接通的编码字段；空数组时不展示配置入口。 */
  codingFields?: {field:string;operationCode:string;maxLength:number}[];
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
  /** 对象内编码器配置，只有已声明编码字段的对象才显示入口。 */
  codingRules:()=>Promise<CodingRuleRow[]>;
  saveCodingRule:(key:string,input:CodingRuleInput)=>Promise<unknown>;
  codingRuleOptions:(type:string)=>Promise<CodingRuleRow[]>;
  encoders:(type:string)=>Promise<EncoderRow[]>;
  saveEncoder:(type:string,key:string,input:EncoderInput)=>Promise<unknown>;
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

/** 有序编码段；固定文本最多32字符，日期使用受控格式，流水宽度1–12位。 */
export interface CodingSegment {kind:"TEXT"|"DATE"|"SEQUENCE";value:string;width:number}
/** 编码日期和重置周期共同使用配置时区，审计仍以 UTC 返回。 */
export interface CodingRule {segments:CodingSegment[];reset:"NEVER"|"YEAR"|"MONTH"|"DAY";zoneId:string}
/** 对象字段绑定：revision=-1首次创建；ruleKey必填且ruleVersion>0，固定版本不自动升级；不含身份或审计。 */
export interface EncoderInput {revision:number;field:string;name:string;enabled:boolean;ruleKey:string;ruleVersion:number}
/** 只读审计来自当前配置读取权限，不额外查询账号目录。 */
export interface EncoderRow extends EncoderInput {encoderKey:string;createdBy:string|null;createdAt:string|null;updatedBy:string|null;updatedAt:string|null}

/** 规则版本不可变；revision=-1创建规则，其余值为当前最新版本号。 */
export interface CodingRuleInput {revision:number;name:string;rule:CodingRule}
/** 当前租户独立规则版本及服务端只读审计。 */
export interface CodingRuleRow {ruleKey:string;ruleVersion:number;name:string;rule:CodingRule;createdBy:string|null;createdAt:string|null;updatedBy:string|null;updatedAt:string|null}
