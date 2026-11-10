/** 工作流公开模型；所有业务调用经注入API，领域组件不依赖应用壳内部实现。 */
export type Kind =
  | "START"
  | "END"
  | "REVIEW"
  | "HANDLE"
  | "ACTION"
  | "WAIT"
  | "CHOICE"
  | "MERGE"
  | "FORK"
  | "JOIN";
/** 节点配置；timeoutSeconds为秒，60—2592000，超时不会自动通过；retries为0—5次。 */
export interface WorkflowNode {
  id: string;
  name: string;
  kind: Kind;
  people: { kind: string; reference: string }[];
  completion: "ALL" | "ANY" | "SEQUENTIAL";
  allowSelf: boolean;
  capability: string | null;
  required: string[];
  input: Record<string, string>;
  retries: number;
  timeoutSeconds: number;
  join: string | null;
  separate: boolean;
}
/** 受控连接，只有CHOICE允许条件和默认分支。 */
export interface Edge {
  from: string;
  to: string;
  field: string | null;
  value: string | null;
  defaultEdge: boolean;
}
/** 审批型与端到端型共享图模型；实例绑定发布快照。 */
export interface WorkflowModel {
  name: string;
  objectType: string;
  purpose: string;
  mode: "APPROVAL" | "BUSINESS";
  entryCapability: string;
  matches: { field: string; value: string }[];
  nodes: WorkflowNode[];
  edges: Edge[];
  terminalActions: Record<string, string>;
  copies?: { kind: string; reference: string }[];
  withdrawalCapability?: string | null;
}
/** 已注册业务能力，只展示契约元数据，不携带凭据或任意URL。 */
export interface Capability {
  key: string;
  name: string;
  permission: string;
  route: string;
  inputs: string[];
  outputs: string[];
}
/** 列表数据，JSON只在定义编辑器解析，不作为任意脚本执行。 */
export interface Definition {
  id: string;
  revision: number;
  model: string;
  enabled: boolean;
  published: number;
}
/** 服务端返回的受限事实记录；字段来自明确工作流API。 */
export type Row = Record<string, any>;
/** 写请求幂等标识由应用适配器生成；客户端不得传入租户或操作者。 */
export interface WorkflowApi {
  capabilities: () => Promise<string[]>;
  businesses: () => Promise<Capability[]>;
  definitions: () => Promise<Definition[]>;
  save: (
    id: string,
    revision: number,
    model: WorkflowModel,
  ) => Promise<{ revision: number }>;
  validate: (model: WorkflowModel) => Promise<string[]>;
  preview: (
    model: WorkflowModel,
    sample: Record<string, string>,
  ) => Promise<string[]>;
  withdraw: (id: string, reason: string) => Promise<unknown>;
  publish: (
    id: string,
    revision: number,
  ) => Promise<{ version: number; revision: number }>;
  disable: (id: string, revision: number, reason: string) => Promise<unknown>;
  versions: (id: string) => Promise<Row[]>;
  tasks: (view: string) => Promise<Row[]>;
  task: (id: string) => Promise<Row>;
  detail: (id: string) => Promise<Row>;
  instances: () => Promise<Row[]>;
  complete: (
    id: string,
    action: string,
    values: Record<string, string>,
    comment?: string,
  ) => Promise<unknown>;
  reassign: (id: string, actor: string, reason: string) => Promise<unknown>;
  retry: (id: string, reason: string) => Promise<unknown>;
}
/** 初始草稿只有开始与结束，未配置业务能力不可发布，禁止伪造可用默认流程。 */
export function emptyModel(): WorkflowModel {
  return {
    name: "",
    objectType: "",
    purpose: "",
    mode: "APPROVAL",
    entryCapability: "",
    matches: [],
    nodes: [newNode("start", "START"), newNode("end", "END")],
    edges: [
      {
        from: "start",
        to: "end",
        field: null,
        value: null,
        defaultEdge: false,
      },
    ],
    terminalActions: {},
    copies: [],
    withdrawalCapability: null,
  };
}
/** 创建节点使用统一安全默认值：不自审，重试2次，期限86400秒。名称需由使用者填写。 */
export function newNode(id: string, kind: Kind): WorkflowNode {
  return {
    id,
    name: id,
    kind,
    people: [],
    completion: "ALL",
    allowSelf: false,
    capability: null,
    required: [],
    input: {},
    retries: 2,
    timeoutSeconds: 86400,
    join: null,
    separate: true,
  };
}
