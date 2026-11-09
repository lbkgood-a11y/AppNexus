<script setup lang="ts">import {ref} from "vue";import {onBeforeRouteLeave} from "vue-router";import AuthorizationCenter from "@appnexus/authorization/authorization-center";import {tenantRoleApi} from "@/api/roleManagement";import {tenantAuthorizationApi,tenantSubjectAuthorizationApi} from "@/api/authorizationCenter";import {requestErrorMessage} from "@/utils/http";/** 路由离开统一询问领域草稿，守卫由应用壳注册，领域包不依赖路由内部实现。 */
const workspace=ref<{canLeave:()=>Promise<boolean>}>();
onBeforeRouteLeave(()=>workspace.value?.canLeave()??true);
</script>
<template><AuthorizationCenter ref="workspace" :role-api="tenantRoleApi" :api="tenantAuthorizationApi" :subject-api="tenantSubjectAuthorizationApi" :error-text="requestErrorMessage" /></template>
