/** 实验生命周期：旧会话DISPOSED为终态；RELOADING由Manager持有。 */
export type Lifecycle='CREATED'|'LOADING'|'ACTIVE'|'INVALID'|'DISPOSING'|'DISPOSED';
/** 保存状态与生命周期正交；UNKNOWN不允许继续写。 */
export type SaveState='IDLE'|'PENDING'|'SUCCEEDED'|'FAILED'|'UNKNOWN';
/** 本地异步任务身份，不作为服务端授权凭据。 */
export type Identity=Readonly<{sessionId:string;generation:number}>;
/** Mock已提交文档信封；revision由服务器管理。 */
export type Document=Readonly<{definitionId:string;tenantId:string;revision:number;xml:string}>;
/** 一次不可变逻辑写；requestId重试保持不变，token为服务器栅栏。 */
export type Envelope=Readonly<Identity & {requestId:string;definitionId:string;tenantId:string;expectedRevision:number;token:number;xml:string}>;
/** 确定性延迟控制；resolve只由测试传输调度，不改变服务器提交语义。 */
export function deferred<T>(){let resolve!:(value:T)=>void;let reject!:(error:Error)=>void;const promise=new Promise<T>((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
