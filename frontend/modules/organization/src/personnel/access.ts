/** 人员页面的入口模式；查看态始终只读，编辑态仍需平台逐操作准入。 */
export type PersonnelMode = "view" | "edit";

/** 编辑入口覆盖实际可维护的主档、明细及账号关联；启停仍为独立列表操作。 */
export const personnelEditOperations = [
  "Person.update", "Person.createDetail", "Person.updateDetail", "Person.deleteDetail",
  "Person.assign", "Person.updateAssignment", "Person.unassign"
] as const;

/**
 * 将平台返回的操作标识与页面模式组合，不生成权限或推导额外授权。
 * @param operations 当前租户 operation-decisions 的真实结果；缺失时传空集合，默认拒绝
 * @param mode 查看或编辑入口；查看态不能执行任何写操作，即使账号有全部权限
 * @returns 详情读取、编辑入口及逐项写入准入；加载、失败、版本等业务状态由页面额外限制
 */
export function personnelAccess(operations: readonly string[], mode: PersonnelMode) {
  const allowed = new Set(operations);
  const canRead = allowed.has("Person.read");
  const writes: readonly string[] = ["Person.create", ...personnelEditOperations];
  return {
    canRead,
    canOpenEdit: canRead && personnelEditOperations.some(operation => allowed.has(operation)),
    allows: (operation: string) => mode === "edit" && canRead && writes.includes(operation) && allowed.has(operation)
  };
}
