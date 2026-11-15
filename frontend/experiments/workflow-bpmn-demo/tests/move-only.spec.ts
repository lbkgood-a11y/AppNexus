import {test,expect} from '@playwright/test';
declare const window:any;
/** 无关联命令的精确移动、撤销、重做和XML验证。 */
test('普通shape.move精确DI历史',async({page},info)=>{
 await page.goto('/move.html');await page.waitForFunction(()=>window.moveReady);
 const result=await page.evaluate(async()=>{
 const {modeler,trace}=window.moveProbe;
 const m=modeler.get('modeling'),r=modeler.get('elementRegistry'),stack=modeler.get('commandStack');
 const shape=r.getAll().find((e:any)=>e.type==='bpmn:StartEvent');
 const xml=(await modeler.saveXML()).xml;
 const d=new DOMParser().parseFromString(xml,'application/xml');const b=d.getElementsByTagNameNS('*','Bounds')[0];
 const before={x:530.25,y:140.5,width:100.75,height:80.125};for(const[k,v]of Object.entries(before))b.setAttribute(k,String(v));
 await modeler.importXML(new XMLSerializer().serializeToString(d));const e=r.get(shape.id);
 const read=async()=>{const xml=(await modeler.saveXML()).xml;const doc=new DOMParser().parseFromString(xml,'application/xml');const b=doc.getElementsByTagNameNS('*','Bounds')[0];return {di:Object.fromEntries(Object.keys(before).map(k=>[k,e.di.bounds[k]])),canvas:Object.fromEntries(Object.keys(before).map(k=>[k,e[k]])),xmlBounds:Object.fromEntries(Object.keys(before).map(k=>[k,Number(b.getAttribute(k))])),xml};};
 const initial=await read();m.moveShape(e,{x:20,y:10},undefined,undefined,{layout:false,recurse:false});const executed=await read();stack.undo();const undone=await read();stack.redo();const redone=await read();return {initial,executed,undone,redone,trace};
 });
 await info.attach('move-only.json',{body:JSON.stringify(result,null,2),contentType:'application/json'});
 const before={x:530.25,y:140.5,width:100.75,height:80.125},after={...before,x:550.25,y:150.5};
 expect(result.initial.di).toEqual(before);expect(result.initial.xmlBounds).toEqual(before);
 for(const [stage,expected]of [[result.executed,after],[result.undone,before],[result.redone,after]]as const){expect(stage.di).toEqual(expected);expect(stage.canvas).toEqual(expected);expect(stage.xmlBounds).toEqual(expected);}
});
