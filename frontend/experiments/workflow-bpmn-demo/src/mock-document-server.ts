import {deferred,type Document,type Envelope} from './session-types';
/** 请求终态在Mock中不可逆，PENDING不能作为重载屏障。 */
export type Receipt={status:'PENDING'|'SUCCEEDED'|'FAILED';revision?:number;reason?:string};
/** 内存Mock，不是正式后端；所有提交/栅栏切换在单个同步步骤完成。 */
export class MockDocumentServer{
 private document:Document;
 private epoch=0;
 private requests=new Map<string,{envelope:Envelope;receipt:Receipt;response:ReturnType<typeof deferred<Receipt>>}>();
 queryFails=false;
 constructor(document:Document,private readonly authenticatedTenant:string){this.document=structuredClone(document);}
 private authorize(tenant:string,id:string){if(tenant!==this.authenticatedTenant||tenant!==this.document.tenantId||id!==this.document.definitionId)throw Error('ACCESS_DENIED');}
 /** 返回原子快照副本，调用方不能直接修改服务器revision。 */
 read(tenant:string,id:string){this.authorize(tenant,id);return structuredClone(this.document);}
 /** 可信服务器产生栅栏；同时终结旧token已接收请求，拒绝未来旧token提交。 */
 grant(tenant:string,id:string){this.authorize(tenant,id);this.epoch++;for(const r of this.requests.values())if(r.receipt.status==='PENDING'){r.receipt={status:'FAILED',reason:'FENCED'};}return this.epoch;}
 /** 接收逻辑写；相同键不同信封拒绝，同信封复用响应。 */
 send(envelope:Envelope){this.authorize(envelope.tenantId,envelope.definitionId);const old=this.requests.get(envelope.requestId);if(old){if(JSON.stringify(old.envelope)!==JSON.stringify(envelope))throw Error('IDEMPOTENCY_CONFLICT');return old.response.promise;}this.requests.set(envelope.requestId,{envelope:structuredClone(envelope),receipt:{status:'PENDING'},response:deferred<Receipt>()});return this.requests.get(envelope.requestId)!.response.promise;}
 /** 只推进服务端处理，不自动交付响应，便于模拟提交成功但响应丢失。 */
 commit(requestId:string,fail=false){const r=this.requests.get(requestId);if(!r)throw Error('REQUEST_NOT_ACCEPTED');if(r.receipt.status!=='PENDING')return structuredClone(r.receipt);const e=r.envelope;if(e.token!==this.epoch)r.receipt={status:'FAILED',reason:'FENCED'};else if(fail)r.receipt={status:'FAILED',reason:'REJECTED'};else if(e.expectedRevision!==this.document.revision)r.receipt={status:'FAILED',reason:'REVISION_CONFLICT'};else{this.document={...this.document,revision:this.document.revision+1,xml:e.xml};r.receipt={status:'SUCCEEDED',revision:this.document.revision};}return structuredClone(r.receipt);}
 /** 测试专用传输调度：终态响应到达不代表此时才提交。 */
 deliver(requestId:string){const r=this.requests.get(requestId)!;if(r.receipt.status==='PENDING')throw Error('NOT_TERMINAL');r.response.resolve(structuredClone(r.receipt));}
 /** 测试专用模拟响应丢失，不撤销服务器提交。 */
 lose(requestId:string){this.requests.get(requestId)!.response.reject(Error('RESPONSE_LOST'));}
 /** 授权查询终态；不存在请求不返回可安全重载结论。 */
 query(tenant:string,id:string,requestId:string){this.authorize(tenant,id);if(this.queryFails)throw Error('QUERY_UNAVAILABLE');const r=this.requests.get(requestId);if(!r)throw Error('UNKNOWN_REQUEST_NOT_TERMINAL');return structuredClone(r.receipt);}
}
