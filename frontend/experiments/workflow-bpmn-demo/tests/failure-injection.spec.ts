import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
const fixture=readFileSync('evidence/connected-initial.bpmn','utf8');
/** 全部故障用例独立会话；注入在公开生命周期或自有恢复边界。 */
const cases=[['A1','move','native-executed'],['B1','move','di-before'],['B2','move','di-middle'],['B3','move','di-after'],['C1','move','canvas-middle'],['D1','undo','di-middle'],['D2','redo','di-middle'],['D3-undo','undo','canvas-middle'],['D3-redo','redo','canvas-middle']]as const;
for(const[id,direction,point]of cases)test(id+' 会话隔离',async({page},info)=>{
 await page.goto('/');
 const result=await page.evaluate(async({fixture,direction,point})=>{
 // 测试动态模块仅存在Demo，不暴露原始Modeler或命令栈。
 const {createFailureSession}=await import('/src/failure-session.ts');
 const s=await createFailureSession(fixture,{direction,point});
 if(direction==='undo')await s.operate('move');
 if(direction==='redo'){await s.operate('move');await s.operate('undo');}
 const before=await s.inspect();s.arm();const failed=await s.operate(direction);const status=s.status();
 const blocked:Record<string,string>={};for(const action of ['move','undo','redo','save','export']as const){try{if(action==='save'||action==='export')await s[action]();else await s.operate(action);blocked[action]='ALLOWED';}catch(e){blocked[action]=String(e);}}
 const after=s.status();await new Promise(r=>setTimeout(r,100));const settled=s.status();s.close();return {before,failed,status,blocked,after,settled};
 },{fixture,direction,point});
 await info.attach(id+'.json',{body:JSON.stringify(result,null,2),contentType:'application/json'});
 expect(result.failed.success).toBe(false);expect(result.failed.error).toContain('INJECTED:');expect(result.status.hits).toBe(1);expect(result.status.poisoned).toBe(true);
 for(const value of Object.values(result.blocked))expect(value).toContain('SESSION_ISOLATED');
 expect(result.after).toEqual(result.status);expect(result.settled).toEqual(result.after);
 const d=result.failed.diagnostic!;expect(new Set(d.ids).size).toBe(d.ids.length);expect(d.ids).toEqual(result.before.ids);expect(d.count).toBe(result.before.count);expect(d.topology).toEqual(result.before.topology);expect(d.xmlFlow).toEqual(result.before.xmlFlow);expect(d.xmlPoints).toEqual(d.diPoints);
 expect(await page.locator('[data-failure-session]').count()).toBe(0);
 const exactBefore={x:530.25,y:140.5,width:100.75,height:80.125},exactAfter={...exactBefore,x:550.25,y:150.5};
 expect(result.before.node.di).toEqual(direction==='undo'?exactAfter:exactBefore);
 const expectedDI=id==='A1'?exactBefore:id==='B1'?{x:550,y:151,width:101,height:80}:id==='B2'?{x:550.25,y:151,width:101,height:80}:direction==='undo'?exactBefore:exactAfter;
 expect(d.node.di).toEqual(expectedDI);
 expect(d.xmlShapes.find((s:any)=>s.ref===d.topology[0])!.bounds).toEqual(d.node.di);
});
