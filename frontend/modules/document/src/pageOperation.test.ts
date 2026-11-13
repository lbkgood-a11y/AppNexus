import {describe,it,expect} from 'vitest';
import {pageOperations,selectPageOperation,hasPageOperation} from './pageOperation';
import type {Binding,Descriptor} from './index';

const interaction=(services: Array<'BUSINESS'|'WORKFLOW'|'PRINT'>): Descriptor['actions'][number]['interaction']=>({code:'test',version:1,objectType:'LeaveRequest',actionCode:'submit',trigger:'USER_ACTION',capabilities:services.map(service=>({service,completion:'SYNCHRONOUS',reference:'test',purpose:'test',fields:[],outcomes:[]}))});

/** 测试页面选择会决定保存的行为，并阻止跨单据或仅有权限无接入实现的操作。 */
describe('page operation binding',()=>{
  const descriptor:Descriptor={typeCode:'LeaveRequest',nameZh:'请假单',nameEn:'Leave',module:'leave',version:1,fields:[],actions:[
    {actionCode:'submit',nameZh:'提交',nameEn:'Submit',operationKey:'LeaveRequest.submit',permissionCode:'workflow:start',interaction:interaction(['BUSINESS','WORKFLOW']),pageOperation:{pageKey:'workflow/leave',pagePath:'/workflow/leave',nameZh:'请假单 POC',nameEn:'Leave request POC',method:'POST',apiPath:'/api/leave-poc/{id}/submit'}},
    {actionCode:'system',nameZh:'系统处理',nameEn:'System',operationKey:'LeaveRequest.system',permissionCode:'workflow:start',interaction:interaction(['BUSINESS'])}
  ]};
  const binding=():Binding=>({id:'binding',name:'test',definitionId:'stale',definitionVersion:9,conditions:[],actionCode:'',service:'BUSINESS',operationKey:''});
  it('only exposes integrated submit operations',()=>{expect(pageOperations(descriptor).map(a=>a.operationKey)).toEqual(['LeaveRequest.submit']);});
  it('selects the action and clears an old target before saving',()=>{const b=binding();selectPageOperation(b,descriptor,'LeaveRequest.submit');expect(hasPageOperation(b,descriptor)).toBe(true);expect(b.actionCode).toBe('submit');expect(b.service).toBe('WORKFLOW');expect(b.definitionVersion).toBe(0);expect(b.definitionId).toBe('');});
  it('rejects a different document or an unimplemented page operation',()=>{const b=binding();selectPageOperation(b,descriptor,'Order.submit');expect(hasPageOperation(b,descriptor)).toBe(false);expect(b.operationKey).toBe('');});
  it('does not confuse a shared permission with a matching action',()=>{const b=binding();b.operationKey='LeaveRequest.submit';b.actionCode='system';expect(hasPageOperation(b,descriptor)).toBe(false);});
  it('supports documents with confirm and cancel and no submit action',()=>{
    const order:Descriptor={...descriptor,typeCode:'Order',actions:['confirm','cancel'].map(code=>({...descriptor.actions[0],actionCode:code,operationKey:'Order.'+code,interaction:interaction(['BUSINESS'])}))};
    expect(pageOperations(order).map(a=>a.actionCode)).toEqual(['confirm','cancel']);
    const b=binding();selectPageOperation(b,order,'Order.cancel');
    expect(b.actionCode).toBe('cancel');expect(b.service).toBe('BUSINESS');expect(b.definitionId).toBeNull();expect(hasPageOperation(b,order)).toBe(true);
  });
  it('maps multiple pages to one action and supports print targets',()=>{
    const d:Descriptor={...descriptor,actions:[{...descriptor.actions[0],actionCode:'print',operationKey:'LeaveRequest.print',interaction:interaction(['PRINT']),aliases:[{operationKey:'LeaveRequest.detail.print',pageOperation:descriptor.actions[0].pageOperation!}]}]};
    expect(pageOperations(d)).toHaveLength(1);const b=binding();selectPageOperation(b,d,'LeaveRequest.print');expect(b.actionCode).toBe('print');expect(b.service).toBe('PRINT');expect(hasPageOperation(b,d)).toBe(true);expect(b.sceneCode).toBeNull();
  });
});
