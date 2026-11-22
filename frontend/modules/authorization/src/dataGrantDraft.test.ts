import {describe,expect,it} from "vitest";
import {incompleteDataPolicy,ruleDraftErrors,persistedScope,scopeChoices,shownScope} from "./dataGrantDraft";
import {conditionDimensions,type DataAccessDeclaration,type DataGrantRule,type DataPolicy,type ScopeSupport} from "./index";

const id="00000000-0000-0000-0000-000000000001";
const rule=():DataGrantRule=>({subjectType:'ROLE_MEMBERS',subjectIds:[],recordIds:[id],conditions:[],allRecords:false});
describe('统一授权配置草稿反馈',()=>{
 it('空选择必须显式全部，已有记录不能同时声明全部',()=>{
  expect(ruleDraftErrors({...rule(),recordIds:[]})).toContain('selection');
  expect(ruleDraftErrors({...rule(),recordIds:[],allRecords:true})).toEqual([]);
  expect(ruleDraftErrors({...rule(),allRecords:true})).toContain('selection');
 });
 it('主体及记录格式错误可以定位，不能把无效UUID提交后才发现',()=>{
  expect(ruleDraftErrors({...rule(),subjectType:'MEMBER'})).toContain('subject');
  expect(ruleDraftErrors({...rule(),subjectIds:[id]})).toContain('subject');
  expect(ruleDraftErrors({...rule(),recordIds:['bad']})).toContain('record');
 });
 it('条件遵守目录类型及运算并保留有效的记录AND条件草稿',()=>{
  const dimensions=[{field:'organizationId',nameZh:'组织',nameEn:'Organization',valueType:'UUID' as const,operators:['EQ' as const]}];
  const valid={...rule(),conditions:[{field:'organizationId',operator:'EQ' as const,values:[id]}]};
  const before=JSON.stringify(valid);expect(ruleDraftErrors(valid,dimensions)).toEqual([]);expect(JSON.stringify(valid)).toBe(before);
  expect(ruleDraftErrors({...valid,conditions:[{field:'organizationId',operator:'IN',values:['bad']}]},dimensions)).toEqual(expect.arrayContaining(['operator','uuid']));
  expect(ruleDraftErrors({...valid,conditions:[{field:'phone',operator:'EQ',values:['123']}]},dimensions)).toContain('field');
 });
 it('按声明与操作区分查询条件和写入的指定记录范围',()=>{
  const declarations:DataAccessDeclaration[]=[{
   objectType:'Warehouse',version:6,
   resources:[{resource:'masterdata.warehouse',parentResource:'',nameZh:'仓库',nameEn:'Warehouse',fields:[]}],
   operations:[{operationId:'Warehouse.list',permissionCode:'masterdata.warehouse:read'},
    {operationId:'Warehouse.update',permissionCode:'masterdata.warehouse:update'}],
   dimensions:[{field:'warehouseType',nameZh:'仓库类型',nameEn:'Warehouse type',valueType:'TEXT',
    operators:['EQ','IN'],source:'DICTIONARY',dictionary:'warehouse_type',operationCodes:['list']}]
  }];
  expect(conditionDimensions(declarations,'masterdata.warehouse','masterdata.warehouse:read')).toEqual(declarations[0].dimensions);
  expect(conditionDimensions(declarations,'masterdata.warehouse','masterdata.warehouse:update')).toEqual([]);
 });
 it('指定记录是保持既有 RULES 协议的选择器捷径',()=>{
  const support:ScopeSupport[]=["ALL","RULES"].map(scopeType=>({permissionId:id,permissionCode:"masterdata.warehouse:read",resourceType:"masterdata.warehouse",scopeType:scopeType as ScopeSupport["scopeType"]}));
  expect(scopeChoices(support,"masterdata.warehouse:read",true)).toEqual(["ALL","RECORDS","RULES"]);
  expect(scopeChoices(support,"masterdata.warehouse:read",false)).toEqual(["ALL","RECORDS"]);
  expect(scopeChoices(support,"masterdata.warehouse:create",true)).toEqual(["ALL","RULES"]);
  expect(scopeChoices(support.slice(0,1),"masterdata.warehouse:update",true)).toEqual(["ALL"]);
  const policy:DataPolicy={permissionId:id,resourceType:"masterdata.warehouse",scopeType:"RULES",organizationIds:[],rules:[rule()]};
  expect(persistedScope("RECORDS")).toBe("RULES");
  expect(shownScope(policy)).toBe("RECORDS");
  expect(shownScope({...policy,rules:[rule(),rule()]})).toBe("RULES");
  expect(shownScope({...policy,rules:[{...rule(),conditions:[{field:"warehouseType",operator:"EQ",values:["PHYSICAL"]}]}]})).toBe("RULES");
  expect(shownScope(policy,"RULES")).toBe("RULES");
 });
 it('保存禁用与规则错误共用入口并限制策略规则数',()=>{
  const policy:DataPolicy={permissionId:id,resourceType:'masterdata.partner',scopeType:'RULES',organizationIds:[],rules:[rule()]};
  expect(incompleteDataPolicy(policy)).toBe(false);
  expect(incompleteDataPolicy({...policy,rules:[{...rule(),recordIds:['bad']}]})).toBe(true);
  expect(incompleteDataPolicy({...policy,rules:Array.from({length:21},rule)})).toBe(true);
  const conditionPolicy={...policy,rules:[{...rule(),conditions:[{field:'organizationId',operator:'EQ' as const,values:['bad']}]}]};
  expect(incompleteDataPolicy(conditionPolicy,[{field:'organizationId',nameZh:'组织',nameEn:'Organization',valueType:'UUID',operators:['EQ']}])).toBe(true);
 });
});
