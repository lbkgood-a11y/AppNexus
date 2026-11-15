/**
 * 组织编辑表单绑定真实对象操作，未知表单类型默认不显示业务提交。
 * @param kind 表单对象：type/unit/position/assignment/move/leader/relation
 * @param editing 已选对象时为 true；新建为 false
 * @returns 后端登记操作 ID，未知类型为空字符串，不回退任意权限
 */
export function organizationSaveOperation(kind:string,editing:boolean):string {
 const special:Record<string,string>={move:'OrganizationUnit.move',leader:'OrganizationLeader.set',relation:'OrganizationRelation.create'};
 if(special[kind])return special[kind];
 const operations:Record<string,[string,string]>={type:['OrganizationType.create','OrganizationType.update'],unit:['OrganizationUnit.create','OrganizationUnit.update'],position:['Position.create','Position.update'],assignment:['PositionAssignment.create','PositionAssignment.update']};
 return operations[kind]?.[editing?1:0]??'';
}
