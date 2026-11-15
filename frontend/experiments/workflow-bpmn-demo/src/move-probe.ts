import Modeler from 'bpmn-js/lib/Modeler';
import CommandInterceptor from 'diagram-js/lib/command/CommandInterceptor';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
/** 当前实验仅接受无子节点、无连线的普通节点及显式整数delta。 */
type Bounds = Readonly<{x:number;y:number;width:number;height:number}>;
type Shape = {id:string;x:number;y:number;width:number;height:number;di:{bounds:Bounds};incoming:unknown[];outgoing:unknown[];children:unknown[]};
type Context = {shape:Shape;delta:{x:number;y:number};movePrecision?:{before:Bounds;after:Bounds}};
type Event = {context:Context};
/** 观测记录不可作为布局写入来源；唯一当前权威为shape.di.bounds。 */
const trace:unknown[]=[];
const copy=(b:Bounds):Bounds=>Object.freeze({x:b.x,y:b.y,width:b.width,height:b.height});
/** 仅普通move的命令扩展；不承担路由、组合、异常或其他命令。 */
class ExactMove extends CommandInterceptor {
 static $inject=['eventBus'];
 constructor(eventBus:ConstructorParameters<typeof CommandInterceptor>[0]){
  super(eventBus);
  const log=(phase:string,e:Event):void=>{trace.push({phase,delta:{...e.context.delta},di:copy(e.context.shape.di.bounds),canvas:copy(e.context.shape)});};
  for(const phase of ['preExecute','preExecuted','execute','executed','postExecute','postExecuted','revert','reverted']){
   this.on('shape.move',phase,2000,(e:Event)=>log(phase+':before-sync',e));
  }
  this.on('shape.move','execute',1500,(e:Event)=>{
   const c=e.context;
   if(c.shape.incoming.length||c.shape.outgoing.length||c.shape.children.length)throw Error('ONLY_ISOLATED_SHAPE');
   if(!Number.isInteger(c.delta.x)||!Number.isInteger(c.delta.y))throw Error('EXPLICIT_INTEGER_DELTA_REQUIRED');
   if(!c.movePrecision){const before=copy(c.shape.di.bounds);c.movePrecision=Object.freeze({before,after:copy({...before,x:before.x+c.delta.x,y:before.y+c.delta.y})});}
   log('intent-and-history',e);
  });
  const restore=(e:Event,direction:'before'|'after')=>{
   const b=e.context.movePrecision![direction];
   Object.assign(e.context.shape.di.bounds,b);
   // 画布只从已恢复的DI派生，不提供第二个可写坐标入口。
   Object.assign(e.context.shape,copy(e.context.shape.di.bounds));
   log('restore:'+direction,e);
  };
  this.on('shape.move','executed',750,(e:Event)=>log('executed:after-native-updater',e));
  this.on('shape.move','reverted',750,(e:Event)=>log('reverted:after-native-updater',e));
  this.on('shape.move','executed',500,(e:Event)=>restore(e,'after'));
  this.on('shape.move','reverted',500,(e:Event)=>restore(e,'before'));
 }
}
const modeler=new Modeler({container:'#canvas',additionalModules:[{__init__:['exactMove'],exactMove:['type',ExactMove]}]});
Object.assign(window,{moveProbe:{modeler,trace}});
await modeler.createDiagram();
Object.assign(window,{moveReady:true});
