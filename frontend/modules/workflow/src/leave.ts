import { newNode, type WorkflowModel } from "./index";

/**
 * 生成待人工校验及发布的请假审批草稿；不绕过设计器和发布权限。
 * @param hrActor 当前租户有效人事账号 UUID；不允许使用模拟成员。
 * @param names 本地化节点名称；主管来自单据组织的 LEADER 规则。
 */
export function leaveModel(
  hrActor: string,
  names: Record<string, string>,
): WorkflowModel {
  const nodes = [
    newNode("start", "START"),
    newNode("supervisor", "REVIEW"),
    newNode("duration", "CHOICE"),
    newNode("hr", "REVIEW"),
    newNode("merge", "MERGE"),
    newNode("end", "END"),
  ];
  for (const node of nodes) node.name = names[node.id] || node.id;
  nodes[1].people = [{ kind: "LEADER", reference: "" }];
  nodes[3].people = [{ kind: "MEMBER", reference: hrActor }];
  nodes[1].capability = nodes[3].capability = "leavePoc";
  const edge = (from: string, to: string) => ({
    from,
    to,
    field: null,
    value: null,
    defaultEdge: false,
  });
  return {
    name: names.title,
    objectType: "LeaveRequest",
    purpose: "approval",
    mode: "APPROVAL",
    entryCapability: "leavePoc",
    matches: [],
    nodes,
    edges: [
      edge("start", "supervisor"),
      edge("supervisor", "duration"),
      {
        from: "duration",
        to: "hr",
        field: "requiresHr",
        value: "true",
        defaultEdge: false,
      },
      { ...edge("duration", "merge"), defaultEdge: true },
      edge("hr", "merge"),
      edge("merge", "end"),
    ],
    terminalActions: { APPROVED: "leavePoc", REJECTED: "leavePoc" },
    copies: [],
    withdrawalCapability: null,
  };
}

/** 请假 POC 页面依赖注入接口；日期为 YYYY-MM-DD，标识均为字符串，不传租户或操作者。 */
export interface LeaveApi {
  /** 页面操作与后端、工作台共享 operationKey，不在浏览器决定提交策略。 */
  actions:()=>Promise<{actionCode:string;nameZh:string;nameEn:string;permissionCode:string;operationKey:string;allowed:boolean}[]>;
  organizations: () => Promise<{ id: string; name: string }[]>;
  list: () => Promise<Record<string, any>[]>;
  create: (input: {
    organizationId: string;
    leaveType: string;
    startDate: string;
    endDate: string;
    reason: string;
  }) => Promise<Record<string, any>>;
  submit: (id: string) => Promise<unknown>;
  /** 本人编辑采用读取的依据版本，成功后形成可重提草稿。 */
  update: (id: string, basisVersion: number, input: { organizationId:string;leaveType:string;startDate:string;endDate:string;reason:string }) => Promise<unknown>;
  /** 本人各次审批关联，不返回历史原因原文。 */
  cycles: (id: string) => Promise<Record<string, any>[]>;
  read: (id: string, taskId?: string) => Promise<Record<string, any>>;
}
