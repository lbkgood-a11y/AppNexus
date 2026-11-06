/** 平台目录不依赖租户上下文；菜单按服务端身份展示，端点仍独立复核平台权限。 */
export default {
 path:"/platform",redirect:"/platform/manage",component:()=>import("@/layout/index.vue"),
 meta:{title:"administration.title",icon:"ri/settings-3-line",rank:5,roles:["PLATFORM_ADMIN"]},
 children:[{path:"/platform/manage",name:"PlatformAdministration",component:()=>import("@/views/platform/index.vue"),meta:{title:"administration.title",roles:["PLATFORM_ADMIN"]}}]
} satisfies RouteConfigsTable;
