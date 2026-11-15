import Modeler from 'bpmn-js/lib/Modeler';
import CommandInterceptor from 'diagram-js/lib/command/CommandInterceptor';
import type EventBus from 'diagram-js/lib/core/EventBus';
/** 测试专用注入定义，只由独立测试构造；不接入任何生产入口。 */
export type Fault={direction:'move'|'undo'|'redo';point:'native-executed'|'di-before'|'di-middle'|'di-after'|'canvas-middle'};
type Dynamic=any;
/** 当前会话只暴露受控操作；故障后销毁Modeler，不尝试修复历史。 */
export async function createFailureSession(xml:string,fault:Fault){
 let direction:Fault['direction']='move',poisoned=false,armed=false,hits=0;
 const events:Dynamic[]=[],snapshots:Dynamic[]=[];
 const host=document.createElement('div');host.dataset.failureSession='true';host.style.height='600px';document.body.append(host);
 const geometry=(e:Dynamic)=>({x:e.x,y:e.y,width:e.width,height:e.height});
 const state=(shape:Dynamic)=>({di:geometry(shape.di.bounds),canvas:geometry(shape)});
 const inject=(point:Fault['point'],shape:Dynamic)=>{if(armed&&direction===fault.direction&&point===fault.point){hits++;snapshots.push({at:point,...state(shape)});throw Error('INJECTED:'+direction+':'+point);}};
 class Precision extends CommandInterceptor{
 static $inject=['eventBus'];
 constructor(bus:EventBus){super(bus);
 for(const phase of ['preExecute','execute','executed','postExecute','postExecuted','revert','reverted'])this.on('shape.move',phase,2500,(e:Dynamic)=>{events.push({phase,direction,...state(e.context.shape)});});
 this.on('shape.move','execute',2000,(e:Dynamic)=>{const c=e.context;if(!c.precision){const before=geometry(c.shape.di.bounds);c.precision={before,after:{...before,x:before.x+c.delta.x,y:before.y+c.delta.y}};}});
 this.on('shape.move','executed',2000,(e:Dynamic)=>{inject('native-executed',e.context.shape);});
 const restore=(e:Dynamic,key:'before'|'after')=>{const s=e.context.shape,b=e.context.precision[key];inject('di-before',s);s.di.bounds.x=b.x;inject('di-middle',s);Object.assign(s.di.bounds,b);inject('di-after',s);s.x=b.x;inject('canvas-middle',s);Object.assign(s,geometry(s.di.bounds));};
 this.on('shape.move','executed',500,(e:Dynamic)=>{restore(e,'after');});this.on('shape.move','reverted',500,(e:Dynamic)=>{restore(e,'before');});
 }
 }
 const modeler=new Modeler({container:host,additionalModules:[{__init__:['precision'],precision:['type',Precision]}]});
 await modeler.importXML(xml);
 const service=(name:string):Dynamic=>modeler.get(name);
 const node=service('elementRegistry').getAll().find((e:Dynamic)=>e.type==='bpmn:StartEvent');
 const collect=async()=>{
 const all=service('elementRegistry').getAll(),flow=all.find((e:Dynamic)=>e.type==='bpmn:SequenceFlow');
 const output=(await modeler.saveXML()).xml!;const d=new DOMParser().parseFromString(output,'application/xml');
 const xmlShapes=[...d.getElementsByTagNameNS('*','BPMNShape')].map(s=>({ref:s.getAttribute('bpmnElement'),bounds:Object.fromEntries(['x','y','width','height'].map(k=>[k,Number(s.getElementsByTagNameNS('*','Bounds')[0].getAttribute(k))]))}));
 const xy=(p:Dynamic)=>({x:p.x,y:p.y});
 return {node:state(node),ids:all.map((e:Dynamic)=>e.id),count:all.length,topology:[flow.source.id,flow.target.id,flow.businessObject.sourceRef.id,flow.businessObject.targetRef.id],diPoints:flow.di.waypoint.map(xy),canvasPoints:flow.waypoints.map(xy),xmlShapes,xmlPoints:[...d.getElementsByTagNameNS('*','waypoint')].map(p=>({x:Number(p.getAttribute('x')),y:Number(p.getAttribute('y'))})),xmlFlow:[...d.getElementsByTagNameNS('*','sequenceFlow')].map(f=>({id:f.getAttribute('id'),source:f.getAttribute('sourceRef'),target:f.getAttribute('targetRef')})),xml:output,canUndo:service('commandStack').canUndo(),canRedo:service('commandStack').canRedo()};
 };
 const guard=()=>{if(poisoned)throw Error('SESSION_ISOLATED: reload last committed document');};
 /** 所有变更同步包裹；捕获故障立即封锁，再只读采证，最后销毁会话。 */
 const operate=async(op:Fault['direction'])=>{guard();direction=op;try{if(op==='move')service('modeling').moveShape(node,{x:20,y:10},undefined,undefined,{recurse:false});else service('commandStack')[op]();return {success:true};}catch(error){poisoned=true;let diagnostic;try{diagnostic=await collect();}finally{modeler.destroy();host.remove();}return {success:false,error:String(error),diagnostic};}};
 return {arm(){armed=true;},operate,inspect:async()=>{guard();return collect();},save:async()=>{guard();return (await modeler.saveXML()).xml;},export:async()=>{guard();return (await modeler.saveXML()).xml;},status:()=>({poisoned,hits,events,snapshots}),close(){if(!poisoned){modeler.destroy();host.remove();}}};
}
