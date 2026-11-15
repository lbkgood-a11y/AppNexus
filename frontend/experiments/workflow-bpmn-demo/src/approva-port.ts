import Modeler from 'bpmn-js/lib/Modeler';
import {atomicPropertiesModule} from './atomicPropertiesModule';
import devhub from './moddle/devhub-workflow.json';
import flowable from './moddle/flowable-probe.json';
import {nodeMapping} from './businessMapping';
/** 实验独立Modeler，不使用RuoYi API、Store或下载入口。 */
export async function createPort(host:HTMLElement,xml:string){
 const modeler=new Modeler({container:host,moddleExtensions:{devhub,flowable},additionalModules:[atomicPropertiesModule]});
 await modeler.importXML(xml);
 const get=(name:string):any=>modeler.get(name);
 const configure=(id:string,changes:Record<string,unknown>)=>{
  if(Object.values(changes).some(v=>v===undefined)||('retries'in changes&&(!Number.isInteger(changes.retries)||Number(changes.retries)<0)))throw Error('PROPERTY_INVALID');
  const element=get('elementRegistry').get(id);if(!element)throw Error('NODE_NOT_FOUND');
  const ext=element.businessObject.extensionElements?.values?.find((x:any)=>x.$type==='devhub:WorkflowNode');
  if(!ext)throw Error('BUSINESS_EXTENSION_REQUIRED');
  get('commandStack').execute('devhub.atomicProperties',{element,updates:Object.entries(changes).map(([key,value])=>({moddleElement:ext,properties:{[key]:value}}))});
 };
 const seed=()=>{const m=get('modeling'),f=get('moddle'),root=get('canvas').getRootElement();let i=0;for(const[kind,type]of Object.entries(nodeMapping)){let e=get('elementRegistry').get('Port_'+kind);if(!e){e=m.createShape({type:'bpmn:'+type},{x:200+(i%5)*180,y:350+Math.floor(i/5)*140},root);m.updateProperties(e,{id:'Port_'+kind,name:kind});}const config=f.create('devhub:WorkflowNode',{kind,peopleJson:JSON.stringify([{kind:'MEMBER',reference:'member_probe'}]),completion:'ANY',allowSelf:false,capability:'probe',requiredJson:'["姓名"]',inputJson:'{"name":"literal:中文"}',retries:2,timeoutSeconds:86400,join:kind==='FORK'?'Port_JOIN':null,separate:true,positionJson:'null'});m.updateProperties(e,{extensionElements:f.create('bpmn:ExtensionElements',{values:[config]})});i++;}};
 return {modeler,get,configure,seed,xml:async()=> (await modeler.saveXML({format:true})).xml!,destroy:()=>modeler.destroy()};
}
