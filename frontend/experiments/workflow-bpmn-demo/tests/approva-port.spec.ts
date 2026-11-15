import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
const xml=readFileSync('evidence/connected-initial.bpmn','utf8');
/** 上游组合机制使用真实Modeler及现有非整数DI模型。 */
test('组合属性历史与十类moddle往返',async({page},info)=>{
 await page.goto('/');
 const result=await page.evaluate(async xml=>{
 const {createPort}=await import('/src/approva-port.ts');const host=document.createElement('div');host.style.height='700px';document.body.append(host);let p=await createPort(host,xml);p.seed();const initialXml=await p.xml();p.destroy();p=await createPort(host,initialXml);
 const config=(id:string)=>{const e=p.get('elementRegistry').get(id);const c=e.businessObject.extensionElements.values[0];return {kind:c.kind,peopleJson:c.peopleJson,completion:c.completion,allowSelf:c.allowSelf,capability:c.capability,requiredJson:c.requiredJson,inputJson:c.inputJson,retries:c.retries,timeoutSeconds:c.timeoutSeconds,join:c.join??null,separate:c.separate,positionJson:c.positionJson};};
 const before=config('Port_REVIEW'),other=config('Port_ACTION'),s=p.get('commandStack');
 p.configure('Port_REVIEW',{completion:'ALL',timeoutSeconds:3600});const first=config('Port_REVIEW');s.undo();const undone=config('Port_REVIEW'),empty=!s.canUndo();s.redo();const redone=config('Port_REVIEW');p.configure('Port_REVIEW',{completion:'SEQUENTIAL',retries:4});s.undo();s.undo();const allUndone=config('Port_REVIEW');s.redo();s.redo();const allRedone=config('Port_REVIEW');s.undo();p.configure('Port_REVIEW',{completion:'ANY',retries:1});const redoCleared=!s.canRedo();let failed=false;const preFail=config('Port_REVIEW');try{p.configure('Port_REVIEW',{completion:'ALL',retries:-1});}catch{failed=true;}const postFail=config('Port_REVIEW');const otherAfter=config('Port_ACTION');const configs=Object.fromEntries(Object.keys((await import('/src/businessMapping.ts')).nodeMapping).map(k=>[k,config('Port_'+k)]));const out=await p.xml();p.destroy();p=await createPort(host,out);const restored=Object.fromEntries(Object.keys(configs).map(k=>[k,config('Port_'+k)]));const finalXml=await p.xml();p.destroy();return {before,first,undone,empty,redone,allUndone,allRedone,redoCleared,failed,preFail,postFail,other,otherAfter,configs,restored,initialXml,out,finalXml};
 },xml);
 await info.attach('approva-port.json',{body:JSON.stringify(result,null,2),contentType:'application/json'});
 expect(result.first.completion).toBe('ALL');expect(result.first.timeoutSeconds).toBe(3600);expect(result.undone).toEqual(result.before);expect(result.empty).toBe(true);expect(result.redone).toEqual(result.first);expect(result.allUndone).toEqual(result.before);expect(result.allRedone.retries).toBe(4);expect(result.redoCleared).toBe(true);expect(result.failed).toBe(true);expect(result.postFail).toEqual(result.preFail);expect(result.otherAfter).toEqual(result.other);expect(result.restored).toEqual(result.configs);
 const di=(xml:string)=>xml.match(/<bpmndi:BPMNShape[\s\S]*?<\/bpmndi:BPMNShape>|<bpmndi:BPMNEdge[\s\S]*?<\/bpmndi:BPMNEdge>/g);
 expect(di(result.finalXml)).toEqual(di(result.out));
});
