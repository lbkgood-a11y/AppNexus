import Modeler from 'bpmn-js/lib/Modeler';
/** 探针中的动态建模服务类型；仅测试边界使用，正式适配器不得照搬。 */
type Dynamic = any;
const descriptor={name:'DevHub',uri:'urn:devhub:workflow:probe',prefix:'devhub',xml:{tagAlias:'lowerCase'},types:[{name:'Config',superClass:['Element'],properties:[{name:'kind',type:'String',isAttr:true},{name:'payload',type:'String'}]}]};
/** 验证完整业务配置，不代表真实能力注册。 */
export const sample={people:[{kind:'MEMBER',reference:'member_probe'}],completion:'ANY',allowSelf:false,capability:'probe',required:['姓名'],input:{name:'literal:中文'},retries:2,timeoutSeconds:86400,join:'JOIN',separate:true,nullable:null};
export const mapping:Record<string,string>={START:'StartEvent',END:'EndEvent',REVIEW:'UserTask',HANDLE:'UserTask',ACTION:'ReceiveTask',WAIT:'ReceiveTask',CHOICE:'ExclusiveGateway',MERGE:'ExclusiveGateway',FORK:'ParallelGateway',JOIN:'ParallelGateway'};
/** 单一命令处理器；execute/revert同时更新扩展，投影仅按需读取。 */
class ConfigHandler {
 execute(c:Dynamic){c.old=c.element.businessObject.extensionElements;c.element.businessObject.extensionElements=c.value;}
 revert(c:Dynamic){c.element.businessObject.extensionElements=c.old;}
}
/** 独立建模探针；不连接后端，所有业务属性修改进入唯一命令栈。 */
export class Probe {
 modeler:Modeler;
 constructor(host:HTMLElement){this.modeler=new Modeler({container:host,moddleExtensions:{devhub:descriptor}});this.service('commandStack').registerHandler('devhub.config',ConfigHandler);}
 service(name:string):Dynamic{return this.modeler.get(name);}
 async init(){await this.modeler.createDiagram();let i=0;for(const [kind,type]of Object.entries(mapping)){const e=this.service('modeling').createShape({type:'bpmn:'+type},{x:180+(i%5)*200,y:180+Math.floor(i/5)*200},this.service('canvas').getRootElement());this.service('modeling').updateProperties(e,{id:'Node_'+kind});this.configure(e,kind);i++;}this.service('canvas').zoom('fit-viewport');}
 configure(e:Dynamic,kind:string,payload:Dynamic=sample){const f=this.service('moddle');const value=f.create('bpmn:ExtensionElements',{values:[f.create('devhub:Config',{kind,payload:JSON.stringify(payload)})]});this.service('commandStack').execute('devhub.config',{element:e,value});}
 /** 读取投影，不持有第二份可写Vue模型。 */
 snapshot(){return this.service('elementRegistry').getAll().filter((e:Dynamic)=>e.businessObject.extensionElements).map((e:Dynamic)=>({id:e.id,type:e.type,x:e.x,y:e.y,width:e.width,height:e.height,waypoints:e.waypoints,config:e.businessObject.extensionElements})).map((e:Dynamic)=>({...e,config:JSON.parse(JSON.stringify(e.config))}));}
 async xml(){return (await this.modeler.saveXML({format:true})).xml!;}
 /** 解析先在隔离Modeler进行，失败不触碰当前画布；生产安全仍需后端。 */
 async import(xml:string){if(xml.length>1000000||/<!DOCTYPE|<!ENTITY/i.test(xml))throw Error('XML_UNSAFE');const doc=new DOMParser().parseFromString(xml,'application/xml');if(doc.querySelector('parsererror'))throw Error('XML_INVALID');const ids=new Set<string>();for(const e of doc.querySelectorAll('[id]')){const id=e.getAttribute('id')!;if(ids.has(id))throw Error('DUPLICATE_ID');ids.add(id);}const stage=new Modeler({moddleExtensions:{devhub:descriptor}});try{const result=await stage.importXML(xml);if(result.warnings.length)throw Error('IMPORT_WARNINGS:'+result.warnings.map(w=>String(w)).join(','));}finally{stage.destroy();}return this.modeler.importXML(xml);}
 theme(dark:boolean){document.documentElement.classList.toggle('dark',dark);}
 destroy(){this.modeler.destroy();}
}
