import {shallowRef} from "vue";
import {storageLocal} from "@pureadmin/utils";
import {responsiveStorageNameSpace} from "@/config";
/** 内存展示上下文，不持久化身份；权威来源是服务端会话。 */
export interface TenantContext {tenantId:string;memberId:string;name:string;version:number}
export const currentTenant=shallowRef<TenantContext>();
export const availableTenants=shallowRef<{tenantId:string;memberId:string;name:string}[]>([]);
let channel:BroadcastChannel|undefined;
let switching=false;
let writes=0;
/** 阻止切换中的新业务写入，等待在途写入结束后才允许切换。 */
export function beginTenantWrite(){if(switching)throw new Error("Tenant switch in progress");writes++;return()=>{writes--;};}
export function beginTenantSwitch(){if(writes||switching)throw new Error("Wait for pending changes");switching=true;}
export function endTenantSwitch(){switching=false;}
/** 整页重建以丢弃旧租户组件与表单，不复用旧租户数据。 */
export function reloadTenant(path="/welcome"){
 storageLocal().removeItem(`${responsiveStorageNameSpace()}tags`);
 window.location.replace(window.location.pathname+window.location.search+"#"+path);window.location.reload();
}
/** 消息不携带账号、租户或凭据，其他标签只重读服务器上下文。 */
export function notifyTenantChange(){channel?.postMessage("changed");}
/** BroadcastChannel 不可用时，服务器版本门禁及焦点检查仍拒绝陈旧请求。 */
export function monitorTenantContext(){
 if(typeof BroadcastChannel!=="undefined"){
  channel=new BroadcastChannel("appnexus-tenant-context");channel.onmessage=()=>{if(currentTenant.value)reloadTenant();};
 }
 const focus=async()=>{
  if(!currentTenant.value||document.hidden||switching)return;
  try{const live=await(await import("@/api/tenant")).probeCurrentTenant();if(!live||live.version!==currentTenant.value?.version||live.tenantId!==currentTenant.value?.tenantId)reloadTenant();}
  catch(cause){const {isAxiosError}=await import("axios");if(isAxiosError(cause)&&cause.response?.status===409)reloadTenant();/* 网络故障不注销，认证错误交由统一入口处理。 */}
 };
 window.addEventListener("focus",focus);
 return()=>{channel?.close();channel=undefined;window.removeEventListener("focus",focus);};
}
