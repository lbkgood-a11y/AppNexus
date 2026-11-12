/** 单据管理公开类型；不暴露身份输入，租户上下文由应用 HTTP 层附加。 */
export interface Descriptor {
  typeCode: string; nameZh: string; nameEn: string; module: string; version: number;
  entry: string; purpose: string; fields: { key: string; nameZh: string; nameEn: string; type: string; unit: string }[];
  actions: {actionCode:string;nameZh:string;nameEn:string;permissionCode:string;operationKey:string;supportedModes:string[]}[];
}
/** 每条绑定全部条件满足，绑定间要求唯一匹配，version 为具体流程发布版本。 */
export interface Binding {
  id: string; name: string; definitionId: string|null; definitionVersion: number;actionCode:string;mode:'DIRECT'|'APPROVAL';
  conditions: { field: string; operator: string; value: string }[];
}
/** 管理接口注入约定；写操作只执行一次，冲突由管理人员重载后处理。 */
export interface DocumentApi {
  catalog: () => Promise<{ descriptor: Descriptor; enabled: boolean; revision: number }[]>;
  activate: (type: string, revision: number, enabled: boolean) => Promise<unknown>;
  tenants: () => Promise<{ id: string; name: string }[]>;
  grants: (type: string) => Promise<{ tenant_id: string; enabled: boolean }[]>;
  grant: (type: string, tenant: string, enabled: boolean) => Promise<unknown>;
  configuration: (type: string) => Promise<{ revision: number; publishedVersion: number; bindings: Binding[] }>;
  workflows: (type: string) => Promise<{ id: string; version: number; name: string }[]>;
  save: (type: string, revision: number, bindings: Binding[]) => Promise<unknown>;
  publish: (type: string, revision: number) => Promise<unknown>;
}
