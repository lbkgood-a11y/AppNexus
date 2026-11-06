import { http } from "@/utils/http";
import type { OrganizationApi, OrganizationCapability, OrganizationGrant, OrganizationMember, OrganizationUnit } from "@appnexus/organization";

/** 所有写操作取当前会话 CSRF，不缓存凭据；租户和操作者不进入客户端参数。 */
async function mutate(method: "post" | "put" | "delete", path: string, data?: object) {
  const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
  return http.request(method, "/api/organization" + path, { data, headers: { [csrf.headerName]: csrf.token } });
}
/** 组织页面的真实后端适配；ID 路径编码，服务端仍复核权限和租户边界。 */
export const organizationApi: OrganizationApi = {
  capabilities: () => http.request<OrganizationCapability[]>("get", "/api/organization/capabilities"),
  units: () => http.request<OrganizationUnit[]>("get", "/api/organization/units"),
  create: name => mutate("post", "/units", { name }),
  rename: (id, name) => mutate("put", "/units/" + encodeURIComponent(id), { name }),
  remove: id => mutate("delete", "/units/" + encodeURIComponent(id)),
  members: () => http.request<OrganizationMember[]>("get", "/api/organization/grants/members"),
  assignOrganization:(member,organizationId)=>mutate("put","/grants/members/"+encodeURIComponent(member)+"/organization",{organizationId}),
  grants: member => http.request<OrganizationGrant[]>("get", "/api/organization/grants/" + encodeURIComponent(member)),
  grant: (memberId, capability, expiresAt) => mutate("post", "/grants", { memberId, capability, expiresAt }),
  revoke: (member, capability) => mutate("delete", "/grants/" + encodeURIComponent(member) + "/" + capability)
};
