import {expect,it} from 'vitest';
import {organizationSaveOperation} from './operationBinding';
/** 动态表单必须指向所属对象，禁止模板拼接旧控制器操作名。 */
it('binds every form kind and fails closed for unknown kinds',()=>{
 const cases={type:['OrganizationType.create','OrganizationType.update'],unit:['OrganizationUnit.create','OrganizationUnit.update'],position:['Position.create','Position.update'],assignment:['PositionAssignment.create','PositionAssignment.update'],move:['OrganizationUnit.move','OrganizationUnit.move'],leader:['OrganizationUnit.setLeader','OrganizationUnit.setLeader'],relation:['OrganizationRelation.create','OrganizationRelation.create']};
 for(const [kind,ids]of Object.entries(cases)){expect(organizationSaveOperation(kind,false)).toBe(ids[0]);expect(organizationSaveOperation(kind,true)).toBe(ids[1]);}
 expect(organizationSaveOperation('unknown',false)).toBe('');
});
