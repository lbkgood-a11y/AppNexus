

import { getConfig } from "@/config";
import NProgress from "@/utils/progress";
import { transformI18n } from "@/plugins/i18n";
import { buildHierarchyTree } from "@/utils/tree";
import remainingRouter from "./modules/remaining";
import { useMultiTagsStoreHook } from "@/store/modules/multiTags";
import { usePermissionStoreHook } from "@/store/modules/permission";
import {
  isUrl,
  openLink,
  cloneDeep,
} from "@pureadmin/utils";
import {
  ascending,
  getHistoryMode,
  handleAliveRoute,
  formatTwoStageRoutes,
  formatFlatteningRoutes
} from "./utils";
import {
  type Router,
  type RouteRecordRaw,
  type RouteComponent,
  createRouter
} from "vue-router";
import { loadNavigation, resetNavigation, isBusinessPath, canVisitBusinessPath } from "./navigation";
import { getSessionProfile, removeToken } from "@/utils/auth";

/** 自动导入全部静态路由，无需再手动引入！匹配 src/router/modules 目录（任何嵌套级别）中具有 .ts 扩展名的所有文件，除了 remaining.ts 文件
 * 如何匹配所有文件请看：https://github.com/mrmlnc/fast-glob#basic-syntax
 * 如何排除文件请看：https://cn.vitejs.dev/guide/features.html#negative-patterns
 */
const modules: Record<string, any> = import.meta.glob(
  ["./modules/home.ts", "./modules/error.ts", "./modules/account.ts"],
  {
    eager: true
  }
);

/** 原始静态路由（未做任何处理） */
const routes = [];

Object.keys(modules).forEach(key => {
  routes.push(modules[key].default);
});

/** 导出处理后的静态路由（三级及以上的路由全部拍成二级） */
export const constantRoutes: Array<RouteRecordRaw> = formatTwoStageRoutes(
  formatFlatteningRoutes(buildHierarchyTree(ascending(routes.flat(Infinity))))
);

/** 初始的静态路由，用于退出登录时重置路由 */
const initConstantRoutes: Array<RouteRecordRaw> = cloneDeep(constantRoutes);

/** 用于渲染菜单，保持原始层级 */
export const constantMenus: Array<RouteComponent> = ascending(
  routes.flat(Infinity)
).concat(...remainingRouter);

/** 不参与菜单的路由 */
export const remainingPaths = Object.keys(remainingRouter).map(v => {
  return remainingRouter[v].path;
});

/** 创建路由实例 */
export const router: Router = createRouter({
  history: getHistoryMode(import.meta.env.VITE_ROUTER_HISTORY),
  routes: constantRoutes.concat(...(remainingRouter as any)),
  strict: true,
  scrollBehavior(to, from, savedPosition) {
    return new Promise(resolve => {
      if (savedPosition) {
        return savedPosition;
      } else {
        if (from.meta.saveSrollTop) {
          const top: number =
            document.documentElement.scrollTop || document.body.scrollTop;
          resolve({ left: 0, top });
        }
      }
    });
  }
});

/** 记录已经加载的页面路径 */
const loadedPaths = new Set<string>();

/** 重置已加载页面记录 */
export function resetLoadedPaths() {
  loadedPaths.clear();
}

/** 重置路由 */
export function resetRouter() {
  resetNavigation();
  router.clearRoutes();
  for (const route of initConstantRoutes.concat(...(remainingRouter as any))) {
    router.addRoute(route);
  }
  router.options.routes = formatTwoStageRoutes(
    formatFlatteningRoutes(buildHierarchyTree(ascending(routes.flat(Infinity))))
  );
  usePermissionStoreHook().clearAllCachePage();
  resetLoadedPaths();
}

/** 路由白名单 */
const whiteList = ["/login"];

const { VITE_HIDE_HOME } = import.meta.env;

router.beforeEach(async (to: ToRouteType, _from) => {
  to.meta.loaded = loadedPaths.has(to.path);

  if (!to.meta.loaded) {
    NProgress.start();
  }

  if (to.meta?.keepAlive) {
    handleAliveRoute(to, "add");
    // 页面整体刷新和点击标签页刷新
    if (_from.name === undefined || _from.name === "Redirect") {
      handleAliveRoute(to);
    }
  }
  let userInfo = getSessionProfile();
  if (!userInfo && to.path !== "/login") {
    try {
      await (await import("@/api/session")).restoreSession(); userInfo = getSessionProfile();
    } catch (error) {
      const { isAxiosError } = await import("axios");
      if (isAxiosError(error) && error.response?.status === 401)
        return { path: "/login", query: { reason: "expired" } };
      // 服务故障保留错误页和重试能力，不能把网络故障误报为会话已失效。
      if (to.path !== "/server-error") return { path: "/server-error" };
      return true;
    }
  }
  if (userInfo && !["/login","/server-error","/access-denied"].includes(to.path)) {
    try {
      const context=await(await import("@/api/tenant")).ensureTenantContext();
      if(!context){

        if(to.path!=="/tenant/select"&&!to.path.startsWith("/platform")&&!to.path.startsWith("/account")&&to.path!=="/access-denied"&&to.path!=="/server-error")return {path:"/tenant/select"};
      } else if(to.path==="/tenant/select")return {path:"/welcome"};
    }
    catch (error) {
      const { isAxiosError } = await import("axios");
      if (isAxiosError(error) && error.response?.status === 409) return { path: "/tenant/select" };
      if (isAxiosError(error) && error.response?.status === 401) return { path: "/login" };
      return { path: "/server-error" };
    }
  }
  if(userInfo&&!["/login","/server-error","/access-denied"].includes(to.path)) {
    const wasReady=usePermissionStoreHook().navigationReady;
    try { await loadNavigation(isBusinessPath(to.path)); }
    catch { return {path:"/server-error"}; }
    if(isBusinessPath(to.path)&&!canVisitBusinessPath(to.path))return {path:"/access-denied"};
    if(!wasReady&&isBusinessPath(to.path))return {path:to.path,query:to.query,hash:to.hash,replace:true};
  }
  const externalLink = isUrl(to?.name as string);
  if (!externalLink) {
    to.matched.some(item => {
      if (!item.meta.title) return "";
      const Title = getConfig().Title;
      if (Title)
        document.title = `${transformI18n(item.meta.title)} | ${Title}`;
      else document.title = transformI18n(item.meta.title);
    });
  }
  /** 如果已经登录并存在登录信息后不能跳转到路由白名单，而是继续保持在当前页面 */
  function toCorrectRoute() {
    return whiteList.includes(to.fullPath) ? "/welcome" : undefined;
  }
  if (userInfo) {
    // 开启隐藏首页后在浏览器地址栏手动输入首页welcome路由则跳转到404页面
    if (VITE_HIDE_HOME === "true" && to.fullPath === "/welcome") {
      return { path: "/error/404" };
    }
    if (_from?.name) {
      // name为超链接
      if (externalLink) {
        openLink(to?.name as string);
        NProgress.done();
        return false;
      } else {
        return toCorrectRoute();
      }
    } else {
      // 刷新
      return toCorrectRoute();
    }
  } else {
    if (to.path !== "/login") {
      if (whiteList.indexOf(to.path) !== -1) {
        return true;
      } else {
        removeToken();
        return { path: "/login" };
      }
    } else {
      return true;
    }
  }
});

router.afterEach(to => {
  // 直接刷新业务地址也建立标签，不能仅依赖侧栏点击事件。
  if(to.meta.navigation)useMultiTagsStoreHook().handleTags("push",{path:to.path,name:to.name,meta:to.meta,query:to.query,params:to.params});
  loadedPaths.add(to.path);
  NProgress.done();
});

export default router;
