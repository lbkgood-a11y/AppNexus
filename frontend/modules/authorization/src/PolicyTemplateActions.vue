<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import type { AuthorizationCenterApi, PolicyTemplate } from "./index";

/** 组合既有授权中心工具栏及 Element Plus 控件，不建立独立授权草稿。 */
const props = defineProps<{ api: AuthorizationCenterApi; roleId: string; version: number; busy: boolean; dirty: boolean; reason: string; errorText: (error: unknown) => string }>();
const emit = defineEmits<{ applied: [] }>();
const { t } = useI18n();
const templates = ref<PolicyTemplate[]>([]), selected = ref(""), name = ref(""), error = ref("");
const loading = ref(false), saving = ref(false);
let generation = 0;
/** 切换角色只显示当前域模板，旧响应不覆盖新上下文，失败禁写。 */
async function load() {
  const current = ++generation; loading.value = true; error.value = ""; templates.value = []; selected.value = "";
  if (!props.roleId || !props.api.templates) { loading.value = false; return; }
  try { const values = await props.api.templates.list(props.roleId); if (current === generation) templates.value = values; }
  catch (cause) { if (current === generation) error.value = props.errorText(cause); }
  finally { if (current === generation) loading.value = false; }
}
/** 保存当前已持久化角色快照，未保存的编辑必须先完成，不能制造模板已包含草稿的误解。 */
async function save() {
  if (!props.api.templates || !name.value.trim() || props.dirty) return;
  saving.value = true; error.value = "";
  try { await props.api.templates.save(props.roleId, name.value.trim()); name.value = ""; await load(); ElMessage.success(t("authorizationCenter.templateSaved")); }
  catch (cause) { error.value = props.errorText(cause); }
  finally { saving.value = false; }
}
/** 模板应用是完整覆盖，显式确认并使用当前角色版本，失败后由用户重读。 */
async function apply() {
  if (!props.api.templates || !selected.value || props.dirty || !props.reason.trim()) return;
  try { await ElMessageBox.confirm(t("authorizationCenter.templateWarning"), t("authorizationCenter.templates"), { type: "warning", confirmButtonText: t("authorizationCenter.applyTemplate"), cancelButtonText: t("organization.cancel") }); }
  catch { return; }
  saving.value = true; error.value = "";
  try { await props.api.templates.apply(props.roleId, selected.value, props.version, props.reason.trim()); emit("applied"); ElMessage.success(t("authorizationCenter.saved")); }
  catch (cause) { error.value = props.errorText(cause); }
  finally { saving.value = false; }
}
watch(() => props.roleId, load, { immediate: true });
</script>
<template>
  <section v-if="api.templates" class="template-actions">
    <h3>{{ t("authorizationCenter.templates") }}</h3>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">{{ t("account.retry") }}</el-button></el-alert>
    <p>{{ t("authorizationCenter.templateHint") }}</p>
    <el-form inline @submit.prevent="save">
      <el-form-item :label="t('authorizationCenter.templateName')"><el-input v-model="name" :maxlength="100" :disabled="busy || loading || saving || dirty" /></el-form-item>
      <el-form-item><el-button v-operation="'@role:grant'" :loading="saving" :disabled="busy || loading || dirty || !name.trim() || !!error" native-type="submit">{{ t("authorizationCenter.saveTemplate") }}</el-button></el-form-item>
    </el-form>
    <el-form inline @submit.prevent="apply">
      <el-form-item :label="t('authorizationCenter.templates')"><el-select v-model="selected" filterable :disabled="busy || loading || saving || dirty" class="template-select"><el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item><el-button v-operation="'@role:grant'" type="warning" :loading="saving" :disabled="busy || loading || dirty || !selected || !reason.trim() || !!error" native-type="submit">{{ t("authorizationCenter.applyTemplate") }}</el-button></el-form-item>
    </el-form>
  </section>
</template>
<style scoped>
.template-actions { border-top: 1px solid var(--el-border-color-lighter); margin-top: 20px; padding-top: 16px; }
.template-actions h3 { font-size: 14px; font-weight: 500; margin-bottom: 12px; }
.template-actions p { color: var(--el-text-color-secondary); line-height: 1.8; margin-bottom: 12px; }
.template-select { min-width: 180px; }
</style>
