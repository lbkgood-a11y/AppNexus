<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed } from "vue";
import { useI18n } from "vue-i18n";
import { BusinessWorkspace, ManagementTable, type ManagementColumn } from "@appnexus/ui";
import type { AdministrationApi, IntegrationClient, IntegrationSecret } from "./administration";

/** 租户上下文弹窗内组合统一工作区和表格；密钥只在组件内存中显示一次。 */
const props = defineProps<{ tenantId: string; api: AdministrationApi; errorText: (error: unknown) => string }>();
const { t } = useI18n();
const rows = ref<IntegrationClient[]>([]), name = ref(""), error = ref("");
const loading = ref(false), saving = ref(false), secret = ref<IntegrationSecret>();
const allowReferences = ref(false);
const form = ref<{ validate: () => Promise<boolean> }>();
const columns = computed<ManagementColumn[]>(() => [
  { key: "name", prop: "name", label: t("administration.clientName"), minWidth: 160 },
  { key: "id", prop: "id", label: "Client ID", minWidth: 300 },
  { key: "status", label: t("administration.status"), width: 100 },
  { key: "operations", label: t("tenant.operation"), minWidth: 220 }
]);
/** 读取失败保留旧目录并禁写，用户需先刷新，不能用陈旧版本覆盖。 */
async function load() {
  loading.value = true; error.value = "";
  try { rows.value = await props.api.clients(props.tenantId); }
  catch (cause) { error.value = props.errorText(cause); }
  finally { loading.value = false; }
}
/** 密钥只在内存显示，创建及轮换不能自动重试，错误后需重新读取版本。 */
async function write(action: () => Promise<unknown>) {
  if (saving.value || loading.value || error.value) return;
  saving.value = true; error.value = "";
  try { await action(); await load(); }
  catch (cause) { error.value = props.errorText(cause); }
  finally { saving.value = false; }
}
async function create() {
  if (!await form.value?.validate().catch(() => false)) return;
  await write(async () => { secret.value = await props.api.createClient(props.tenantId, name.value.trim(), allowReferences.value ? ["dictionaries:read", "dictionary-references:write"] : ["dictionaries:read"]); name.value = ""; });
}
/** 固定使用当前行版本和完整 scope，不能由按钮悄悄扩大外部权限。 */
async function rotate(row: IntegrationClient) { await write(async () => { secret.value = await props.api.rotateClient(row); }); }
onMounted(load);
onBeforeUnmount(() => { secret.value = undefined; name.value = ""; });
</script>
<template>
  <BusinessWorkspace :title="t('administration.clients')">
    <template #feedback>
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link :disabled="loading || saving" @click="load">{{ t("account.retry") }}</el-button></el-alert>
      <el-alert :title="t('administration.clientScopeHint')" type="info" :closable="false" />
      <div v-if="secret" class="client-secret">
        <el-alert :title="t('administration.clientSecretHint')" type="warning" :closable="false" />
        <div><strong>Client ID</strong><code>{{ secret.client.id }}</code></div>
        <div><strong>Client Secret</strong><code>{{ secret.secret }}</code></div>
        <el-button @click="secret = undefined">{{ t("account.mfaRecoverySaved") }}</el-button>
      </div>
    </template>
    <template #search>
      <el-form ref="form" :model="{ name }" inline @submit.prevent="create">
        <el-form-item prop="name" :label="t('administration.clientName')" :rules="[{ required: true, whitespace: true, max: 100, message: t('administration.invalidName'), trigger: 'blur' }]"><el-input v-model="name" :maxlength="100" :disabled="saving || loading" /></el-form-item>
        <el-form-item><el-checkbox v-model="allowReferences" :disabled="saving || loading">{{ t("administration.allowReferences") }}</el-checkbox></el-form-item>
        <el-form-item><el-button v-operation="'integration.client:create'" type="primary" native-type="submit" :loading="saving" :disabled="loading || !!error">{{ t("administration.createClient") }}</el-button></el-form-item>
      </el-form>
    </template>
    <template #data>
      <ManagementTable embedded :title="t('administration.clients')" :columns="columns" :rows="rows" :total="rows.length" :loading="loading" :busy="saving" :empty-text="t('administration.empty')" @refresh="load">
        <template #status="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ t(row.enabled ? "administration.enabled" : "administration.disabled") }}</el-tag></template>
        <template #operations="{ row }">
          <el-popconfirm :title="t('administration.clientStateConfirm')" :confirm-button-text="t(row.enabled ? 'administration.disable' : 'administration.enable')" :cancel-button-text="t('organization.cancel')" @confirm="write(() => props.api.clientState(row as IntegrationClient, !row.enabled))"><template #reference><el-button v-operation="'integration.client:update'" link :type="row.enabled ? 'danger' : 'primary'" :disabled="saving || loading || !!error">{{ t(row.enabled ? "administration.disable" : "administration.enable") }}</el-button></template></el-popconfirm>
          <el-popconfirm :title="t('administration.clientRotateConfirm')" :confirm-button-text="t('administration.rotateClient')" :cancel-button-text="t('organization.cancel')" @confirm="rotate(row as IntegrationClient)"><template #reference><el-button v-operation="'integration.client:rotate_secret'" link type="danger" :disabled="saving || loading || !!error">{{ t("administration.rotateClient") }}</el-button></template></el-popconfirm>
        </template>
      </ManagementTable>
    </template>
  </BusinessWorkspace>
</template>
<style scoped>
.client-secret { display: grid; gap: 16px; padding: 16px 0; }
.client-secret strong { display: block; font-weight: 500; }
.client-secret code { display: block; overflow-wrap: anywhere; margin-top: 8px; }
</style>
