/** WorkflowModel中的业务类型、标准BPMN元素及对应业务字段集合。 */
export const nodeMapping:Record<string,string>={START:'StartEvent',END:'EndEvent',REVIEW:'UserTask',HANDLE:'UserTask',ACTION:'ReceiveTask',WAIT:'ReceiveTask',CHOICE:'ExclusiveGateway',MERGE:'ExclusiveGateway',FORK:'ParallelGateway',JOIN:'ParallelGateway'};
export const businessFields=['kind','people','completion','allowSelf','capability','required','input','retries','timeoutSeconds','join','separate','position'] as const;
export const standardBpmnFields=['id','name','sourceRef','targetRef','conditionExpression','default','bounds','waypoint'] as const;
export const flowableExtensionFields=['assignee','candidateUsers','candidateGroups','delegateExpression','type','skipExpression'] as const;
/** 扩展为WorkflowModel投影，不把业务Kind或执行策略改写成Flowable元素语义。 */
export function toBusinessConfig(node:any){return Object.fromEntries(businessFields.map(k=>[k,node[k]??(k==='position'?null:undefined)]));}
export function toFlowablePreview(node:any){return node.kind==='REVIEW'||node.kind==='HANDLE'?{assignee:'${wf_actor}'}:node.kind==='ACTION'||node.kind==='WAIT'?{receiveTask:true}:{};}
