import {http} from "@/utils/http";
import type {NavigationSnapshot} from "@/router/navigationModel";
/** 当前会话的真实导航；身份、租户与权限由后端复核，不写入浏览器持久缓存。 */
export const getNavigation=()=>http.request<NavigationSnapshot>("get","/bff/navigation");
