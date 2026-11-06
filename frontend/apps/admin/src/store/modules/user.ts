import { defineStore } from "pinia";
import {
  type userType,
  store,
  router,
  resetRouter,
  routerArrays
} from "../utils";
import { useMultiTagsStoreHook } from "./multiTags";
import { getSessionProfile, removeToken } from "@/utils/auth";

export const useUserStore = defineStore("pure-user", {
  state: (): userType => ({
    // 头像
    avatar: getSessionProfile()?.avatar ?? "",
    // 用户名
    username: getSessionProfile()?.username ?? "",
    // 昵称
    nickname: getSessionProfile()?.nickname ?? "",
    // 页面级别权限
    roles: getSessionProfile()?.roles ?? [],
    // 按钮级别权限
    permissions: getSessionProfile()?.permissions ?? []
  }),
  actions: {
    SET_AVATAR(value: string) { this.avatar = value; },
    SET_USERNAME(value: string) { this.username = value; },
    SET_NICKNAME(value: string) { this.nickname = value; },
    SET_ROLES(value: string[]) { this.roles = value; },
    SET_PERMS(value: string[]) { this.permissions = value; },
    async logOut() {
      await (await import("@/api/session")).endSession();
      this.$reset();
      removeToken();
      useMultiTagsStoreHook().handleTags("equal", [...routerArrays]);
      resetRouter();
      await router.push("/login");
    }
  }
});
export function useUserStoreHook() { return useUserStore(store); }
