/** 当前租户配置入口；菜单可见性不替代服务端逐请求管理授权校验。 */
export default {
  path: "/configuration",
  redirect: "/configuration/dictionaries",
  component: () => import("@/layout/index.vue"),
  meta: {
    title: "dictionary.configuration",
    icon: "ri/settings-4-line",
    rank: 10
  },
  children: [
    {
      path: "/configuration/dictionaries",
      name: "DictionaryManagement",
      component: () => import("@/views/configuration/dictionaries.vue"),
      meta: {
        title: "dictionary.title",
        icon: "ri/book-2-line"
      }
    }
  ]
} satisfies RouteConfigsTable;
