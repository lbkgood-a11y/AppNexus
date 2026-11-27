<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { onBeforeRouteLeave } from "vue-router";
import AuthorizationCenter from "@appnexus/authorization/authorization-center";
import { tenantAuthorizationApi, tenantSubjectAuthorizationApi } from "@/api/authorizationCenter";
import { tenantRoleApi } from "@/api/roleManagement";
import { requestErrorMessage } from "@/utils/http";

const { t } = useI18n();
const workspace = ref<{ canLeave: () => Promise<boolean> }>();

/** 独立菜单与统一授权中心共用成员授权和未保存草稿保护。 */
onBeforeRouteLeave(() => workspace.value?.canLeave() ?? true);
</script>

<template>
  <AuthorizationCenter
    ref="workspace"
    :role-api="tenantRoleApi"
    :api="tenantAuthorizationApi"
    :subject-api="tenantSubjectAuthorizationApi"
    :error-text="requestErrorMessage"
    :page-title="t('loginWarehouse.authorizePersonnel')"
    member-data-only
  />
</template>
