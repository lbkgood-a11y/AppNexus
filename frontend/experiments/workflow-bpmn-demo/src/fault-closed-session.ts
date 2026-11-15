import Modeler from 'bpmn-js/lib/Modeler';
import CommandInterceptor from 'diagram-js/lib/command/CommandInterceptor';
import {atomicPropertiesModule} from './atomicPropertiesModule';
import devhub from './moddle/devhub-workflow.json';
import flowable from './moddle/flowable-probe.json';
/** 实验专用状态；失败会话终结，不声称命令自动回滚。 */
type State='LOADING'|'ACTIVE'|'FAILED'|'DESTROYED';
type Op='edit'|'undo'|'redo'|'export'|'save'|'publish'|'preview';
const commands=['shape.create','shape.move','shape.delete','shape.resize','connection.create','connection.delete','connection.reconnect','connection.updateWaypoints','element.updateProperties','element.updateModdleProperties','devhub.atomicProperties'];
/** 注入只存在于实验构造参数，生产入口不可引用。 */
export class FaultClosedSession{
 readonly sessionId=crypto.randomUUID();readonly generation:number;
 private state:State='LOADING';private modeler:Modeler|null=null;private host:HTMLElement;private disposers:(()=>void)[]=[];private pending=new Set<Promise<unknown>>();private readonly identity=Symbol('session');private bus:any=null;
 readonly trace:{event:string;state:string}[]=[];
 constructor(generation:number,private readonly report:(error:unknown)=>void=()=>{}){this.generation=generation;this.host=document.createElement('div');this.host.dataset.faultSession=this.sessionId;this.host.style.height='500px';document.body.append(this.host);}
 async open(xml:string){if(this.state!=='LOADING')throw Error('SESSION_NOT_LOADING');const modeler=new Modeler({container:this.host,moddleExtensions:{devhub,flowable},additionalModules:[atomicPropertiesModule]});this.modeler=modeler;try{await modeler.importXML(xml);if(this.state!=='LOADING')throw Error('STALE_SESSION');const bus:any=modeler.get('eventBus');this.bus=bus;const gate:any=new CommandInterceptor(bus);gate.on(commands.map(command=>'commandStack.'+command+'.canExecute'),10000,(event:any)=>{if(this.state!=='ACTIVE')event.preventDefault();});this.state='ACTIVE';}catch(error){this.state='FAILED';this.dispose();throw error;}}
 /** 测试专用公开事件故障注入；返回取消注册函数。 */
 inject(event:string,after:number){let seen=0;const listener=()=>{if(++seen===after)throw Error('INJECTED:'+event+':'+after);};this.bus.on(event,100,listener);const off=()=>this.bus?.off(event,listener);this.disposers.push(off);return {off,count:()=>seen};}
 private service(name:string):any{return this.modeler!.get(name);}
 private guard(op:Op){if(this.state!=='ACTIVE')throw Error('SESSION_NOT_ACTIVE:'+this.state+':'+op);}
 private fail(error:unknown){if(this.state!=='ACTIVE')return;this.state='FAILED';this.trace.push({event:'FAILED:'+String(error),state:this.state});this.report(error);this.dispose();}
 private invoke<T>(op:Op,run:()=>T):T{this.guard(op);try{return run();}catch(error){this.fail(error);throw error;}}
 configure(id:string,updates:Record<string,unknown>){return this.invoke('edit',()=>{const e=this.service('elementRegistry').get(id),c=e?.businessObject.extensionElements?.values?.find((x:any)=>x.$type==='devhub:WorkflowNode');if(!c)throw Error('BUSINESS_EXTENSION_REQUIRED');this.service('commandStack').execute('devhub.atomicProperties',{element:e,updates:Object.entries(updates).map(([k,v])=>({moddleElement:c,properties:{[k]:v}}))});});}
 undo(){return this.invoke('undo',()=>this.service('commandStack').undo());}
 redo(){return this.invoke('redo',()=>this.service('commandStack').redo());}
 export(){return this.invoke('export',()=>this.modeler!.saveXML());}
 save<T>(writer:()=>Promise<T>){return this.invoke('save',()=>{const task=writer();this.pending.add(task);task.finally(()=>this.pending.delete(task)).catch(()=>{});return task;});}
 publish(){return this.invoke('publish',()=>{throw Error('EXPERIMENT_PUBLISH_DISABLED');});}
 preview(){return this.invoke('preview',()=>{throw Error('EXPERIMENT_PREVIEW_DISABLED');});}
 /** 异步回调只有会话仍活动时才应用；Promise自身不保证可取消。 */
 async runAsync<T>(work:()=>Promise<T>,apply:(value:T)=>void){const id=this.identity,g=this.generation,task=work();this.pending.add(task);try{const value=await task;if(this.state==='ACTIVE'&&id===this.identity&&g===this.generation)apply(value);return {applied:this.state==='ACTIVE'&&g===this.generation};}catch(error){if(this.state==='ACTIVE')this.fail(error);return {applied:false,error:String(error)};}finally{this.pending.delete(task);}}
 /** 清理注册资源并销毁Modeler；原始对象引用不会因此成为不可调用的代理。 */
 dispose(){if(this.state==='DESTROYED')return;for(const off of this.disposers.splice(0)){try{off();}catch(error){this.report(error);}}const m=this.modeler;this.modeler=null;try{m?.destroy();}catch(error){this.report(error);}this.host.remove();this.bus=null;this.state='DESTROYED';}
 view(){return {sessionId:this.sessionId,generation:this.generation,state:this.state,hostConnected:this.host.isConnected,modelerAvailable:this.modeler!==null,pending:this.pending.size};}
 /** 仅测试观察销毁前引用存活性，不得提供给正常产品入口。 */
 rawForAudit(){return this.modeler;}
}
