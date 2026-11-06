import { http } from "@/utils/http";
import { currentTenant, availableTenants, beginTenantSwitch, endTenantSwitch, notifyTenantChange, refreshTenantWorkspace, type TenantContext } from "@/utils/tenantContext";
/** 有效成员身份来自服务端，前端不能自行构建租户身份。 */
export interface TenantMembership { tenantId: string; memberId: string; name: string }
/** 返回本人有效租户，空列表表示尚无有效成员资格。 */
export const getMyTenants = () => http.request<TenantMembership[]>("get","/bff/tenants");
/** 选择或切换租户，expectedVersion 仅用于拒绝过期页面，不是权限凭据。 */
export async function selectTenant(tenantId: string, expectedVersion?:number) {
 const csrf=await http.request<{token:string;headerName:string}>("get","/bff/csrf");
 return http.request<TenantContext>("post","/bff/tenants/current",{data:{tenantId,expectedVersion},headers:{[csrf.headerName]:csrf.token}});
}
/** 每次读取均由服务端重验租户和成员；不在 localStorage 保存身份边界。 */
export const getCurrentTenant=()=>http.request<TenantMembership & {version:number}>("get","/bff/tenants/current");
/** 登录探测允许无当前租户；204 是正常状态，业务接口仍要求有效租户。 */
export const probeCurrentTenant=()=>http.request<TenantContext|undefined>("get","/bff/tenants/current",{params:{optional:true}});

/** 工作台请求必须通过服务端统一租户执行边界。 */
export const getWorkspaceContext=()=>http.request("get","/api/workspace/context");

/** 本人只有一个有效租户时自动选择；零租户不隐式建立资格。 */
export async function ensureTenantContext(){
 availableTenants.value=await getMyTenants();
 currentTenant.value=(await probeCurrentTenant())||undefined;
 if(!currentTenant.value&&availableTenants.value.length===1){currentTenant.value=await selectTenant(availableTenants.value[0].tenantId);notifyTenantChange();}
 return currentTenant.value;
}
/** 切换时等待本标签写入，成功后通知其他标签并只重建目标内容区。 */
export async function enterTenant(id:string,path="/welcome"){
 beginTenantSwitch();
 try{
  let previous:TenantContext|undefined;
  previous=(await probeCurrentTenant())||undefined;
  currentTenant.value=await selectTenant(id,previous?.version);
  notifyTenantChange();
  await refreshTenantWorkspace(path);
 }finally{endTenantSwitch();}
}
