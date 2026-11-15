import {test,expect} from '@playwright/test';
declare const window:any;
/** DI专项使用真实浏览器，旧断言保持原样。 */
test('DI分层：折点往返及Bounds编辑撤销',async({page},info)=>{
 await page.goto('/');await page.waitForFunction(()=>Boolean(window.probe));
 const result=await page.evaluate(async()=>{
 const p=window.probe,m=p.service('modeling'),r=p.service('elementRegistry');
 const e=m.connect(r.get('Node_CHOICE'),r.get('Node_REVIEW'),{type:'bpmn:SequenceFlow'});m.updateProperties(e,{id:'DI_edge'});
 const expected=[{x:10.25,y:20.5},{x:100.75,y:20.5},{x:100.75,y:200.125}];m.updateWaypoints(e,expected);
 const read=async()=>{const parsed=await p.service('moddle').fromXML(await p.xml());const elements=parsed.rootElement.diagrams[0].plane.planeElement;const edge=elements.find((x:any)=>x.bpmnElement?.id==='DI_edge');const shape=elements.find((x:any)=>x.bpmnElement?.id==='Node_REVIEW');return {points:edge.waypoint.map((x:any)=>({x:x.x,y:x.y})),bounds:Object.fromEntries(['x','y','width','height'].map(k=>[k,shape.bounds[k]]))};};
 const cycles=[];for(let i=0;i<5;i++){await p.import(await p.xml());cycles.push({...await read(),canvas:p.service('elementRegistry').get('DI_edge').waypoints});}
 const d=new DOMParser().parseFromString(await p.xml(),'application/xml');const s=[...d.getElementsByTagNameNS('*','BPMNShape')].find(x=>x.getAttribute('bpmnElement')==='Node_REVIEW')!;const b=s.getElementsByTagNameNS('*','Bounds')[0];const expectedBounds={x:530.25,y:140.5,width:100.75,height:80.125};for(const[k,v]of Object.entries(expectedBounds))b.setAttribute(k,String(v));await p.import(new XMLSerializer().serializeToString(d));
 const shape=p.service('elementRegistry').get('Node_REVIEW');const canvasBounds=Object.fromEntries(Object.keys(expectedBounds).map(k=>[k,shape[k]]));const untouched=await read();m.moveShape(shape,{x:10.25,y:20.5});const moved=await read();p.service('commandStack').undo();const undone=await read();p.service('commandStack').redo();const redone=await read();return {expected,cycles,expectedBounds,canvasBounds,untouched,moved,undone,redone};
 });
 await info.attach('di-stages.json',{body:JSON.stringify(result,null,2),contentType:'application/json'});
 for(const c of result.cycles){expect(c.points).toEqual(result.expected);expect(c.canvas.map((p:any)=>({x:p.x,y:p.y}))).toEqual(result.expected);expect(c.canvas[0].original).toEqual({x:380,y:21});expect(c.canvas[2].original).toEqual({x:101,y:180});}
 expect(result.untouched.bounds).toEqual(result.expectedBounds);
 expect(result.undone.bounds).toEqual(result.expectedBounds);
 expect(result.redone).toEqual(result.moved);
});
