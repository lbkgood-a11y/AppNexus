import Modeler from 'bpmn-js/lib/Modeler';
import {SessionResources} from './session-resource-registry';
import {MockDocumentServer,type Receipt} from './mock-document-server';
import type {Document,Envelope,Identity,Lifecycle,SaveState} from './session-types';
/** 模拟入口范围；不提供完整DesignerAdapter或真实发布能力。 */
export const operations=['add','delete','move','properties','connect','disconnect','undo','redo','save','export','publish','preview']as const;
type Operation=typeof operations[number];
const transitions:Record<Lifecycle,Lifecycle[]>={CREATED:['LOADING','DISPOSING'],LOADING:['ACTIVE','INVALID','DISPOSING'],ACTIVE:['INVALID','DISPOSING'],INVALID:['DISPOSING'],DISPOSING:['DISPOSED'],DISPOSED:[]};
/** 一个实例仅属于一个Modeler；所有可变内部状态不向组件开放。 */
export class EditingSession{
 private lifecycle:Lifecycle='CREATED';private revision:number;private saveState:SaveState='IDLE';private reason='';private modeler:Modeler|null=null;
 readonly createdAt=Date.now();readonly resources:SessionResources;
 readonly identity:Identity;
 private cache:unknown=null;private committedXml:string|null=null;
 constructor(identity:Identity,readonly definitionId:string,readonly tenantId:string,revision:number){this.identity=Object.freeze({...identity});this.revision=revision;this.resources=new SessionResources(identity.sessionId);}
 /** 校验显式状态边；非法跳转立即拒绝。 */
 transition(next:Lifecycle){if(!transitions[this.lifecycle].includes(next))throw Error('ILLEGAL_TRANSITION:'+this.lifecycle+'->'+next);this.lifecycle=next;}
 /** 加载只读服务端来源；失败由Manager销毁候选实例。 */
 async initialize(xml:string){this.transition('LOADING');const host=document.createElement('div');host.dataset.sessionId=this.identity.sessionId;document.body.append(host);this.resources.add('DOM',()=>host.remove());const modeler=new Modeler({container:host});this.modeler=modeler;this.resources.add('Modeler',()=>{modeler.destroy();this.modeler=null;});this.resources.add('Adapter cache',()=>{this.cache=null;this.committedXml=null;});await modeler.importXML(xml);if(this.lifecycle!=='LOADING')throw Error('STALE_INITIALIZATION');this.cache={xml};this.committedXml=xml;this.transition('ACTIVE');}
 /** 撤销编辑能力；旧实例不得恢复ACTIVE。 */
 invalidate(reason:string){if(this.lifecycle==='ACTIVE'||this.lifecycle==='LOADING'){this.reason=reason;this.transition('INVALID');}}
 /** 逆序清理资源；重复调用不重复副作用，失败保留诊断。 */
 dispose(){if(this.lifecycle==='DISPOSED')return this.resources.outcomes;this.transition('DISPOSING');const outcomes=this.resources.close();this.transition('DISPOSED');return outcomes;}
 /** 返回诊断投影，未暴露Modeler或可写状态字段。 */
 view(){return {identity:this.identity,definitionId:this.definitionId,tenantId:this.tenantId,loadedRevision:this.revision,lifecycleState:this.lifecycle,saveState:this.saveState,reason:this.reason,createdAt:this.createdAt,hasModeler:this.modeler!==null,committedXml:this.committedXml};}
 /** 只由Manager在身份与生命周期校验之后调用。 */
 acknowledge(receipt:Receipt){this.saveState=receipt.status==='PENDING'?'PENDING':receipt.status;if(receipt.status==='SUCCEEDED')this.revision=receipt.revision!;}
 /** 由请求协调器记录操作状态，与生命周期分离。 */
 markSave(state:SaveState){this.saveState=state;}
}
/** Manager持有跨会话请求结算；generation不能代替服务端栅栏。 */
export class SessionManager{
 private generation=0;private current:EditingSession|null=null;private token=0;private reloading=false;
 private requests=new Map<string,{envelope:Envelope;status:SaveState}>();
 readonly tasks:{identity:Identity;status:'PENDING'|'COMPLETED'|'IGNORED'|'FAILED'}[]=[];
 readonly audit:string[]=[];
 constructor(private readonly server:MockDocumentServer,readonly tenantId:string,readonly definitionId:string){}
 /** 返回诊断投影，未暴露Modeler或可写状态字段。 */
 view(){return {managerState:this.reloading?'RELOADING':'IDLE',session:this.current?.view(),requests:[...this.requests.values()].map(r=>structuredClone(r))};}
 /** 同时校验sessionId、generation和ACTIVE状态。 */
 isCurrent(identity:Identity){return !!this.current&&this.current.identity.sessionId===identity.sessionId&&this.current.identity.generation===identity.generation&&this.current.view().lifecycleState==='ACTIVE';}
 /** 所有模拟命令入口共用门禁；未结算保存冻结编辑。 */
 assertWritable(identity:Identity){if(!this.isCurrent(identity)||this.reloading)throw Error('SESSION_NOT_WRITABLE');if([...this.requests.values()].some(r=>r.status==='PENDING'||r.status==='UNKNOWN'))throw Error('SAVE_UNSETTLED');}
 /** 只模拟业务操作准入，不声称发布或几何命令已实现。 */
 invoke(identity:Identity,operation:Operation){this.assertWritable(identity);if(!operations.includes(operation))throw Error('UNKNOWN_OPERATION');this.audit.push(operation);return operation;}
 /** 首次创建由Mock服务器授予token，不能复用旧会话。 */
 async create(){if(this.current)throw Error('USE_RELOAD');this.token=this.server.grant(this.tenantId,this.definitionId);return this.open(this.server.read(this.tenantId,this.definitionId));}
 private async open(doc:Document){const session=new EditingSession({sessionId:crypto.randomUUID(),generation:++this.generation},this.definitionId,this.tenantId,doc.revision);this.current=session;try{await session.initialize(doc.xml);if(this.current!==session){session.invalidate('STALE_INITIALIZATION');session.dispose();throw Error('STALE_INITIALIZATION');}return session;}catch(error){session.invalidate(String(error));session.dispose();throw error;}}
 /** 撤销编辑能力；旧实例不得恢复ACTIVE。 */
 invalidate(reason:string){this.current?.invalidate(reason);}
 /** 逆序清理资源；重复调用不重复副作用，失败保留诊断。 */
 dispose(){return this.current?.dispose();}
 /** Promise不可强制终止；迟到结果记录IGNORED，不回写新会话。 */
 async task<T>(identity:Identity,work:()=>Promise<T>){this.assertWritable(identity);const record={identity,status:'PENDING' as 'PENDING'|'COMPLETED'|'IGNORED'|'FAILED'};this.tasks.push(record);try{const value=await work();if(!this.isCurrent(identity)){record.status='IGNORED';return {ignored:true};}record.status='COMPLETED';return {ignored:false,value};}catch(error){record.status=this.isCurrent(identity)?'FAILED':'IGNORED';return {ignored:record.status==='IGNORED',error:String(error)};}}
 /** 事件闭包绑定启动身份，旧事件无法取得新会话能力。 */
 event(identity:Identity){return ()=>{try{this.invoke(identity,'properties');return true;}catch{return false;}};}
 /** 候选XML由测试明确提供；不使用故障诊断或旧缓存重载。 */
 save(identity:Identity,xml:string,requestId=crypto.randomUUID()){
 this.assertWritable(identity);const session=this.current!;const envelope=Object.freeze({...identity,requestId,definitionId:this.definitionId,tenantId:this.tenantId,expectedRevision:session.view().loadedRevision,token:this.token,xml});
 const record={envelope,status:'PENDING' as SaveState};this.requests.set(requestId,record);session.markSave('PENDING');
 const completion=this.server.send(envelope).then(receipt=>{record.status=receipt.status==='PENDING'?'UNKNOWN':receipt.status;if(this.isCurrent(identity))session.acknowledge(receipt);return receipt;},error=>{if(record.status==='PENDING')record.status='UNKNOWN';if(this.isCurrent(identity))session.markSave(record.status);return {status:'UNKNOWN' as const,reason:String(error)};});return {requestId,completion,envelope};
 }
 /** 查询终态是模拟服务器契约；未知或查询失败不得开放新会话。 */
 async reload(){this.invalidate('RELOAD');this.reloading=true;try{for(const r of this.requests.values()){const receipt=this.server.query(this.tenantId,this.definitionId,r.envelope.requestId);if(receipt.status==='PENDING')throw Error('RELOAD_BLOCKED_PENDING');r.status=receipt.status;}const old=this.current;old?.dispose();if(old?.resources.outcomes.some(r=>r.status==='FAIL'))throw Error('CLEANUP_FAILED');this.token=this.server.grant(this.tenantId,this.definitionId);const doc=this.server.read(this.tenantId,this.definitionId);return await this.open(doc);}finally{this.reloading=false;}}
}
