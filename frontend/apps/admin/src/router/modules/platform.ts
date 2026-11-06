/** 平台目录不依赖租户上下文；菜单按服务端身份展示，端点仍独立复核平台权限。 */
export default {
 path:"/platform",redirect:"/platform/manage",component:()=>import("@/layout/index.vue"),
 meta:{title:"administration.title",icon:"ri/settings-3-line",rank:5,roles:["PLATFORM_ADMIN"]},
 children:[
  {path:"/platform/manage",name:"PlatformAdministration",component:()=>import("@/views/platform/index.vue"),meta:{title:"administration.title",roles:["PLATFORM_ADMIN"]}},
  {path:"/platform/menus",name:"PlatformMenuManagement",component:()=>import("@/views/platform/menus.vue"),meta:{title:"menuManagement.title",icon:"ri/menu-line",roles:["PLATFORM_ADMIN"]}},
  // 隐藏验收路由仅供开发验证模板主题与响应式，不展示生产菜单或模拟业务入口。
  {path:"/platform/template-lab",name:"PlatformTemplateLab",component:()=>import("@/views/template-lab/index.vue"),meta:{title:"templateLab.title",roles:["PLATFORM_ADMIN"],showLink:false}}
 ]
} satisfies RouteConfigsTable;
