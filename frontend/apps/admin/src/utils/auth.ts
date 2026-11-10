import { useUserStoreHook } from "@/store/modules/user";
import { isString, isIncludeAllChildren } from "@pureadmin/utils";
export interface DataInfo<T = number> {
  avatar?: string; username?: string; nickname?: string;
  roles?: string[]; permissions?: string[];
}
let profile: DataInfo | undefined;
export function getSessionProfile() { return profile; }
export function setSessionProfile(value: DataInfo) {
  profile = value;
  const store = useUserStoreHook();
  store.SET_AVATAR(value.avatar ?? "");
  store.SET_USERNAME(value.username ?? "");
  store.SET_NICKNAME(value.nickname ?? "");
  store.SET_ROLES(value.roles ?? []);
  store.SET_PERMS(value.permissions ?? []);
}
/** 清理本人展示资料并使按钮能力快照失效，不保留上一账号的操作集合。 */
export function removeToken() { profile = undefined; window.dispatchEvent(new Event("appnexus:session-ended")); }
export function hasPerms(value: string | string[]): boolean {
  if (!value) return false;
  const { permissions } = useUserStoreHook();
  if (!permissions) return false;
  return isString(value) ? permissions.includes(value) : isIncludeAllChildren(value, permissions);
}
