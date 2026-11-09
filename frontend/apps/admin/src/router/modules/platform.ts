/** 平台目录不依赖租户上下文；菜单按服务端身份展示，端点仍独立复核平台权限。 */
export default {
 path:"/platform",redirect:"/platform/tenants",component:()=>import("@/layout/index.vue"),
 meta:{title:"administration.title",icon:"ri/settings-3-line",rank:5,roles:["PLATFORM_ADMIN"]},
 children:[
  {path:"/platform/tenants",name:"PlatformTenantManagement",component:()=>import("@/views/platform/tenants.vue"),meta:{title:"administration.tenantTitle",icon:"ri/building-line",roles:["PLATFORM_ADMIN"]}},
  {path:"/platform/users",name:"PlatformUserManagement",component:()=>import("@/views/platform/users.vue"),meta:{title:"administration.userTitle",icon:"ri/user-settings-line",roles:["PLATFORM_ADMIN"]}},
  // 旧入口仅保留收藏链接兼容，不再承载混合管理页。
  {path:"/platform/manage",redirect:"/platform/tenants",meta:{title:"administration.tenantTitle",roles:["PLATFORM_ADMIN"],showLink:false}},
  {path:"/platform/menus",name:"PlatformMenuManagement",component:()=>import("@/views/platform/menus.vue"),meta:{title:"menuManagement.title",icon:"ri/menu-line",roles:["PLATFORM_ADMIN"]}},
  // 平台角色使用管理员图标，与当前租户的成员角色入口区分管理域。
  {path:"/platform/roles",name:"PlatformRoleManagement",component:()=>import("@/views/platform/roles.vue"),meta:{title:"roleManagement.platformTitle",icon:"ri/admin-line",roles:["PLATFORM_ADMIN"]}},
  {path:"/platform/authorization",name:"PlatformAuthorizationCenter",component:()=>import("@/views/platform/authorization.vue"),meta:{title:"authorizationCenter.title",icon:"ri/shield-keyhole-line",roles:["PLATFORM_ADMIN"]}},
  // 隐藏验收路由仅供开发验证模板主题与响应式，不展示生产菜单或模拟业务入口。
  {path:"/platform/template-lab",name:"PlatformTemplateLab",component:()=>import("@/views/template-lab/index.vue"),meta:{title:"templateLab.title",roles:["PLATFORM_ADMIN"],showLink:false}}
 ]
} satisfies RouteConfigsTable;
