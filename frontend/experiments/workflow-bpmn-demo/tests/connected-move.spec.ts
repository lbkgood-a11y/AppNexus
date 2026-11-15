import {test,expect} from '@playwright/test';
declare const window:any;
/** 第二道门槛仅增加一条关联SequenceFlow，原第一道探针不变。 */
test('单关联连线shape.move精度与一次撤销',async({page},info)=>{
 await page.goto('/connected-move.html');await page.waitForFunction(()=>window.moveReady);
 const result=await page.evaluate(async()=>{
 const {modeler,trace}=window.moveProbe,m=modeler.get('modeling'),r=modeler.get('elementRegistry'),stack=modeler.get('commandStack');
 const source=r.getAll().find((e:any)=>e.type==='bpmn:StartEvent');
 const target=m.createShape({type:'bpmn:UserTask'},{x:850,y:180},source.parent);
 const edge=m.connect(source,target,{type:'bpmn:SequenceFlow'});
 const doc=new DOMParser().parseFromString((await modeler.saveXML()).xml,'application/xml');
 const shape=[...doc.getElementsByTagNameNS('*','BPMNShape')].find(x=>x.getAttribute('bpmnElement')===source.id)!;
 const b=shape.getElementsByTagNameNS('*','Bounds')[0];
 for(const[k,v]of Object.entries({x:530.25,y:140.5,width:100.75,height:80.125}))b.setAttribute(k,String(v));
 const diEdge=doc.getElementsByTagNameNS('*','BPMNEdge')[0];
 const points=[{x:631,y:180.5625},{x:700.25,y:180.5625},{x:700.25,y:180},{x:800,y:180}];
 for(const w of [...diEdge.getElementsByTagNameNS('*','waypoint')])w.remove();
 for(const point of points){const w=doc.createElementNS('http://www.omg.org/spec/DD/20100524/DI','di:waypoint');w.setAttribute('x',String(point.x));w.setAttribute('y',String(point.y));diEdge.append(w);}
 await modeler.importXML(new XMLSerializer().serializeToString(doc));trace.length=0;
 const node=r.get(source.id),flow=r.get(edge.id);
 const read=async()=>{const xml=(await modeler.saveXML()).xml;const parsed=await modeler.get('moddle').fromXML(xml);const elements=parsed.rootElement.diagrams[0].plane.planeElement;const bounds=elements.find((e:any)=>e.bpmnElement?.id===node.id).bounds;const de=elements.find((e:any)=>e.bpmnElement?.id===flow.id);const xy=(p:any)=>({x:p.x,y:p.y});const bo=flow.businessObject;return {di:{x:node.di.bounds.x,y:node.di.bounds.y,width:node.di.bounds.width,height:node.di.bounds.height},canvas:{x:node.x,y:node.y,width:node.width,height:node.height},xmlBounds:{x:bounds.x,y:bounds.y,width:bounds.width,height:bounds.height},points:flow.di.waypoint.map(xy),canvasPoints:flow.waypoints.map(xy),xmlPoints:de.waypoint.map(xy),topology:[flow.source.id,flow.target.id,bo.sourceRef.id,bo.targetRef.id],xml,canUndo:stack.canUndo(),canRedo:stack.canRedo()};};
 const initial=await read();m.moveShape(node,{x:20,y:10},undefined,undefined,{recurse:false});const executed=await read();await new Promise(resolve=>setTimeout(resolve,100));const settled=await read();stack.undo();const undone=await read();stack.redo();const redone=await read();await new Promise(resolve=>setTimeout(resolve,100));const final=await read();return {initial,executed,settled,undone,redone,final,trace};
 });
 await info.attach('connected-move.json',{body:JSON.stringify(result,null,2),contentType:'application/json'});
 const before={x:530.25,y:140.5,width:100.75,height:80.125},after={...before,x:550.25,y:150.5};
 expect(result.initial.canUndo).toBe(false);
 expect(result.initial.points).toEqual([{x:631,y:180.5625},{x:700.25,y:180.5625},{x:700.25,y:180},{x:800,y:180}]);
 expect(result.executed.points).toEqual([{x:651,y:191},{x:726,y:191},{x:726,y:180},{x:800,y:180}]);
 for(const[stage,bounds]of [[result.executed,after],[result.undone,before],[result.redone,after]]as const){expect(stage.di).toEqual(bounds);expect(stage.canvas).toEqual(bounds);expect(stage.xmlBounds).toEqual(bounds);expect(stage.points).toEqual(stage.canvasPoints);expect(stage.xmlPoints).toEqual(stage.points);expect(stage.topology).toEqual(result.initial.topology);}
 expect(result.undone.points).toEqual(result.initial.points);expect(result.redone.points).toEqual(result.executed.points);
 expect(result.undone.canUndo).toBe(false);expect(result.undone.canRedo).toBe(true);
 expect(result.settled).toEqual(result.executed);expect(result.final).toEqual(result.redone);
 expect(result.trace.filter((e:any)=>e.phase==='restore:after')).toHaveLength(2);
 expect(result.trace.filter((e:any)=>e.phase==='restore:before')).toHaveLength(1);
});
