/** 当前租户角色入口；页面展示不替代后端逐请求 GRANT 管理能力校验。 */
export default {
  path: "/access",
  redirect: "/access/roles",
  component: () => import("@/layout/index.vue"),
  meta: {
    title: "roleManagement.access",
    icon: "ri/shield-user-line",
    rank: 9
  },
  children: [
    {
      path: "/access/roles",
      name: "TenantRoleManagement",
      component: () => import("@/views/roles/index.vue"),
      // 租户角色使用成员组图标，明确其权限仅作用于当前租户成员。
      meta: {
        title: "roleManagement.tenantTitle",
        icon: "ri/group-line"
      }
    },
    {
      path: "/access/authorization",
      name: "TenantAuthorizationCenter",
      component: () => import("@/views/roles/authorization.vue"),
      meta: { title: "authorizationCenter.title", icon: "ri/shield-keyhole-line" }
    }
  ]
} satisfies RouteConfigsTable;
