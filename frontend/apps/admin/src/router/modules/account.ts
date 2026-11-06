/** 个人中心是已登录账号的自助入口，不授予其他账号管理权限。 */
export default {
  path: "/account",
  component: () => import("@/layout/index.vue"),
  meta: { title: "account.title", showLink: false },
  children: [{
    path: "/tenant/select", name: "TenantSelect",
    component: () => import("@/views/tenant/index.vue"),
    meta: { title: "tenant.select", showLink: false }
  }, {
    path: "/account/profile", name: "AccountProfile",
    component: () => import("@/views/account/index.vue"),
    // 分区只改变 query，保留个人中心实例，避免外框、导航和资料请求整体重建。
    meta: { title: "account.title", showLink: false, reuseOnQueryChange: true }
  }]
} satisfies RouteConfigsTable;
