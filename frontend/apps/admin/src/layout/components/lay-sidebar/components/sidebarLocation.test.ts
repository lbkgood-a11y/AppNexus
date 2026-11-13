import {describe,it,expect} from 'vitest';
import {createRouter,createMemoryHistory} from 'vue-router';
import {sidebarLocation} from './sidebarLocation';

/** 验证租户重建路由后旧菜单不会因命名路由已移除抛错，导航仍由守卫执行访问检查。 */
describe('sidebar links during tenant switch',()=>{
  it('resolves old menu by path after route name removal',()=>{
    const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>null}}]});
    const remove=router.addRoute({path:'/platform/users',name:'PlatformUserManagement',component:{render:()=>null}});
    const menu={path:'/platform/users',name:'PlatformUserManagement'};
    remove();expect(()=>router.resolve(menu)).toThrow();
    expect(()=>router.resolve(sidebarLocation(menu))).not.toThrow();
    expect(router.resolve(sidebarLocation(menu)).path).toBe('/platform/users');
  });
});
