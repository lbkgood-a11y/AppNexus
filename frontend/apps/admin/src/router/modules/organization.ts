/** 组织管理入口复用应用壳；页面实时读取能力，所有数据和操作仍由后端授权。 */
export default {
  path: "/organization",
  redirect: "/organization/manage",
  component: () => import("@/layout/index.vue"),
  meta: { title: "organization.title", icon: "ri/organization-chart", rank: 10 },
  children: [{
    path: "/organization/manage", name: "OrganizationManagement",
    component: () => import("@/views/organization/index.vue"),
    meta: { title: "organization.title" }
  }]
} satisfies RouteConfigsTable;
