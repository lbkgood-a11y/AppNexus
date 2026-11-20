import {ref} from "vue";
import type {AuthorizationOption,DataRecordSearch} from "./index";

/** 权限模块内聚的候选分页状态；不缓存跨资源身份，不读取会话，也不改变授权草稿。 */
export function useDataRecordSearch(onError:(cause:unknown)=>Promise<void>){
 const items=ref<AuthorizationOption[]>([]),labels=ref<Record<string,string>>({}),loading=ref(false),query=ref(""),page=ref(1),total=ref(0);
 let request=0;
 /** 切换资源、进入只读或卸载时使旧请求失效；清除候选标签，不删除规则中的记录标识。 */
 function reset(){++request;items.value=[];labels.value={};loading.value=false;query.value="";page.value=1;total.value=0;}
 /**
  * 读取一页受控候选，旧响应不覆盖新结果，重复记录去重；失败清候选并上报，无自动重放。
  * @param source 应用固定资源映射，必须已由服务端业务读取权保护
  * @param resource 当前资源，不接受任意URL
  * @param keyword 名称或编码，最多200字符
  * @param nextPage 从1开始，每页20条；翻页调用方使用当前page+1
  */
 async function search(source:DataRecordSearch,resource:string,keyword:string,nextPage=1){
  if(!source.resources.includes(resource)||!Number.isInteger(nextPage)||nextPage<1)return;
  const current=++request;loading.value=true;query.value=keyword.slice(0,200);
  try{
   const result=await source.search(resource,query.value,nextPage);
   if(current!==request)return;
   items.value=[...new Map((nextPage===1?result.items:[...items.value,...result.items]).map(item=>[item.id,item])).values()];
   page.value=nextPage;total.value=result.total;
   for(const item of result.items)labels.value[item.id]=item.name;
  }catch(cause){if(current===request){items.value=[];total.value=0;await onError(cause);}}
  finally{if(current===request)loading.value=false;}
 }
 return {items,labels,loading,query,page,total,search,reset};
}
