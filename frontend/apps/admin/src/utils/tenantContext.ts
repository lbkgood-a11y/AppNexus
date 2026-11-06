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
/**
 * 清理旧租户的标签与页面实例，并通过 Vue Router 重建目标内容区。
 * @param path 目标业务路由；缺省保持当前路由。跨业务穿透应传入明确目标，同路由切换通过 redirect 中转强制重建组件。
 */
export async function refreshTenantWorkspace(path?:string){
 storageLocal().removeItem(`${responsiveStorageNameSpace()}tags`);
 const [{router},{useMultiTagsStoreHook},{routerArrays},{usePermissionStoreHook}]=await Promise.all([
  import("@/router"),import("@/store/modules/multiTags"),import("@/layout/types"),import("@/store/modules/permission")
 ]);
 useMultiTagsStoreHook().handleTags("equal",[...routerArrays]);
 usePermissionStoreHook().cachePageList=[];
 const target=path||router.currentRoute.value.path||"/welcome";
 if(router.currentRoute.value.path===target){
  const normalized=target.startsWith("/")?target.slice(1):target;
  await router.replace({path:`/redirect/${normalized}`,query:router.currentRoute.value.query});
 }else await router.replace(target);
}
/** 上下文冲突时先从服务端重读权威状态，再刷新当前内容；网络失败交由原请求错误反馈。 */
export async function reloadTenant(path?:string){
 currentTenant.value=(await(await import("@/api/tenant")).probeCurrentTenant())||undefined;
 await refreshTenantWorkspace(path);
}
/** 消息不携带账号、租户或凭据，其他标签只重读服务器上下文。 */
export function notifyTenantChange(){channel?.postMessage("changed");}
/** BroadcastChannel 不可用时，服务器版本门禁及焦点检查仍拒绝陈旧请求。 */
export function monitorTenantContext(){
 if(typeof BroadcastChannel!=="undefined"){
  channel=new BroadcastChannel("appnexus-tenant-context");channel.onmessage=()=>{if(currentTenant.value)void reloadTenant();};
 }
 const focus=async()=>{
  if(!currentTenant.value||document.hidden||switching)return;
  try{const live=await(await import("@/api/tenant")).probeCurrentTenant();if(!live||live.version!==currentTenant.value?.version||live.tenantId!==currentTenant.value?.tenantId){currentTenant.value=live||undefined;await refreshTenantWorkspace();}}
  catch(cause){const {isAxiosError}=await import("axios");if(isAxiosError(cause)&&cause.response?.status===409)await reloadTenant();/* 网络故障不注销，认证错误交由统一入口处理。 */}
 };
 window.addEventListener("focus",focus);
 return()=>{channel?.close();channel=undefined;window.removeEventListener("focus",focus);};
}
