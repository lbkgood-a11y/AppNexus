<script setup lang="ts">
import { setErrorFeedback } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { http, requestErrorMessage } from "@/utils/http";
import { operationGroups } from "@/api/operationGroups";
import { currentTenant } from "@/utils/tenantContext";
import { ElMessage } from "element-plus";
import { refreshDisplayFormat } from "@/utils/displayFormat";

/** 非敏感租户展示参数复用设置表单；服务端返回定义与版本，前端不创建任意配置项。 */
interface Parameter { key: string; value: string; defaultValue: string; description: string; version: number }
const { t } = useI18n();
const rows = ref<Parameter[]>([]), loading = ref(false), saving = ref(""), error = ref("");
const editable = ref(false);
/** 未选择租户时不发业务请求；能力检查仅控制表单，后端仍独立复核权限。 */
async function load() {
  if (!currentTenant.value) return;
  loading.value = true; error.value = ""; editable.value = false;
  try {
    const values = await http.request<Parameter[]>("get", "/api/configuration/parameters");
    rows.value = values;
    // 参数编辑使用自身操作准入，不能借用组织授权权限；服务端仍逐请求校验写入。
    const capability = await operationGroups("tenant", { SAVE: "TenantParameter.save" });
    editable.value = capability.includes("SAVE");
  } catch (cause) { await setErrorFeedback(error, requestErrorMessage(cause)); }
  finally { loading.value = false; }
}
/** 保存按当前版本执行，失败保留输入并要求重读，不能自动覆盖他人更改。 */
async function save(row: Parameter) {
  saving.value = row.key; error.value = "";
  try {
    const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
    const result = await http.request<Parameter>("put", "/api/configuration/parameters/" + encodeURIComponent(row.key), { data: { value: row.value, version: row.version }, headers: { [csrf.headerName]: csrf.token } });
    Object.assign(row, result); await refreshDisplayFormat(); ElMessage.success(t("account.saved"));
  } catch (cause) { await setErrorFeedback(error, requestErrorMessage(cause)); editable.value = false; }
  finally { saving.value = ""; }
}
onMounted(load);
</script>
<template>
  <section v-if="currentTenant" class="tenant-parameters" v-loading="loading">
    <h2>{{ t("account.tenantParameters") }}</h2>
    <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false"><el-button link @click="load">{{ t("account.retry") }}</el-button></FloatingError>
    <el-form label-position="top">
      <el-form-item v-for="row in rows" :key="row.key" :label="t('account.parameters.' + row.key.replace('display.', ''))">
        <div class="parameter-row"><el-input v-model="row.value" :maxlength="500" :disabled="!editable || loading || !!saving" /><el-button v-operation="'TenantParameter.save'" v-if="editable" :loading="saving === row.key" :disabled="loading || !!saving" @click="save(row)">{{ t("account.save") }}</el-button></div>
        <p>{{ t('account.parameterHints.' + row.key.replace('display.', '')) }}</p>
      </el-form-item>
    </el-form>
  </section>
</template>
<style scoped>
.tenant-parameters { margin-top: 24px; }
.tenant-parameters h2 { font-size: 16px; font-weight: 500; margin-bottom: 16px; }
.parameter-row { display: flex; gap: 12px; width: 100%; }
.tenant-parameters p { color: var(--el-text-color-secondary); line-height: 1.8; }
</style>
