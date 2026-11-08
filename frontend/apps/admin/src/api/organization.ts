import { http } from "@/utils/http";
import type { OrganizationApi, OrganizationCapability, OrganizationType, OrganizationTreeUnit, OrganizationImpact, OrganizationRelation, OrganizationPosition, OrganizationAssignment, OrganizationLeader, OrganizationDirectoryMember, BusinessOption } from "@appnexus/organization";

/** 所有写操作取当前会话 CSRF，不缓存凭据；租户和操作者不进入客户端参数。 */
async function mutate(method: "post" | "put" | "delete", path: string, data?: object) {
  const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
  return http.request(method, "/api/organization" + path, { data, headers: { [csrf.headerName]: csrf.token } });
}
/** 组织页面的真实后端适配；ID 路径编码，服务端仍复核权限和租户边界。 */
export const organizationApi: OrganizationApi = {
  capabilities: () => http.request<OrganizationCapability[]>("get", "/api/organization/capabilities"),
  types: () => http.request<OrganizationType[]>("get", "/api/organization/domain/types")
  ,treeUnits: () => http.request<OrganizationTreeUnit[]>("get", "/api/organization/domain/units")
  ,createType: (code,name) => mutate("post", "/domain/types", { code, name }) as Promise<OrganizationType>
  ,updateType: (id,input) => mutate("put", "/domain/types/"+encodeURIComponent(id), input) as Promise<OrganizationType>
  ,createTreeUnit: input => mutate("post", "/domain/units", input) as Promise<OrganizationTreeUnit>
  ,updateTreeUnit: (id,input) => mutate("put", "/domain/units/"+encodeURIComponent(id), input) as Promise<OrganizationTreeUnit>
  ,moveTreeUnit: (id,parentId,version) => mutate("post", "/domain/units/" + encodeURIComponent(id) + "/move", { parentId, version }) as Promise<OrganizationTreeUnit>
  ,impact: id => http.request<OrganizationImpact>("get", "/api/organization/domain/units/" + encodeURIComponent(id) + "/impact")
  ,relations: () => http.request<OrganizationRelation[]>("get", "/api/organization/domain/relations")
  ,createRelation: input => mutate("post", "/domain/relations", input) as Promise<OrganizationRelation>
  ,removeRelation: (id,version) => mutate("delete", "/domain/relations/"+encodeURIComponent(id)+"?version="+version) as Promise<void>
  ,positions: () => http.request<OrganizationPosition[]>("get", "/api/organization/domain/positions")
  ,createPosition: input => mutate("post", "/domain/positions", input) as Promise<OrganizationPosition>
  ,updatePosition: (id,input) => mutate("put", "/domain/positions/"+encodeURIComponent(id), input) as Promise<OrganizationPosition>
  ,assignments: () => http.request<OrganizationAssignment[]>("get", "/api/organization/domain/assignments")
  ,createAssignment: input => mutate("post", "/domain/assignments", input) as Promise<OrganizationAssignment>
  ,updateAssignment: (id,input) => mutate("put", "/domain/assignments/"+encodeURIComponent(id), input) as Promise<OrganizationAssignment>
  ,leaders: () => http.request<OrganizationLeader[]>("get", "/api/organization/domain/leaders")
  ,setLeader: input => mutate("put", "/domain/leaders", input) as Promise<void>
  ,directoryMembers: () => http.request<OrganizationDirectoryMember[]>("get", "/api/organization/domain/members")
  ,dictionary: code => http.request<BusinessOption[]>("get","/api/configuration/dictionaries/lookup/"+encodeURIComponent(code))
};
