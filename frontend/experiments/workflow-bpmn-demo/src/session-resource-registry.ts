/** 资源作用域：按逆注册顺序执行一次，失败不跳过其余资源。 */
export class SessionResources{
 private entries:{name:string;dispose:()=>void}[]=[];
 private done=false;
 readonly outcomes:{name:string;status:'PASS'|'FAIL';error?:string}[]=[];
 constructor(readonly sessionId:string){}
 /** 注册资源归属；关闭后禁止再次绑定。 */
 add(name:string,dispose:()=>void){if(this.done)throw Error('RESOURCE_SCOPE_CLOSED');this.entries.push({name,dispose});}
 /** 清理失败不中断后续步骤；不重复运行已尝试的清理器。 */
 close(){if(this.done)return this.outcomes;this.done=true;for(const e of this.entries.reverse()){try{e.dispose();this.outcomes.push({name:e.name,status:'PASS'});}catch(error){this.outcomes.push({name:e.name,status:'FAIL',error:String(error)});}}this.entries=[];return this.outcomes;}
}
