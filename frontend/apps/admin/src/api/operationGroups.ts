import {http} from "@/utils/http";
/**
 * 将统一操作准入投影为领域页面分组；不执行对象授权，也不持久化主体能力。
 * @param domain 当前页面固定管理域，不能作为后端身份凭据
 * @param groups 分组名到真实操作标识的显式映射，未知操作默认不展示
 * @returns 当前主体满足入口准入的分组；请求失败向调用页传播，禁止回退全部可用
 */
export async function operationGroups<T extends string>(domain:"platform"|"tenant",groups:Record<T,string>):Promise<T[]> {
 const allowed=new Set(await http.request<string[]>("get",domain==="platform"?"/bff/operation-decisions":"/api/operation-decisions"));
 return (Object.keys(groups) as T[]).filter(name=>allowed.has(groups[name]));
}
