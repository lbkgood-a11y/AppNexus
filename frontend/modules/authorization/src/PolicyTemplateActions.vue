<script setup lang="ts">
import { setErrorFeedback } from "@appnexus/ui";
import { InfoHint } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import { computed, ref, watch } from "vue";
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
  catch (cause) { if (current === generation) await setErrorFeedback(error, props.errorText(cause)); }
  finally { if (current === generation) loading.value = false; }
}
/** 保存当前已持久化角色快照，未保存的编辑必须先完成，不能制造模板已包含草稿的误解。 */
async function save() {
  if (!props.api.templates || !name.value.trim() || props.dirty) return;
  saving.value = true; error.value = "";
  try { await props.api.templates.save(props.roleId, name.value.trim()); name.value = ""; await load(); ElMessage.success(t("authorizationCenter.templateSaved")); }
  catch (cause) { await setErrorFeedback(error, props.errorText(cause)); }
  finally { saving.value = false; }
}
/** 模板应用是完整覆盖，显式确认并使用当前角色版本，失败后由用户重读。 */
async function apply() {
  if (!props.api.templates || !selected.value || props.dirty || !props.reason.trim()) return;
  try { await ElMessageBox.confirm(t("authorizationCenter.templateWarning"), t("authorizationCenter.templates"), { type: "warning", confirmButtonText: t("authorizationCenter.applyTemplate"), cancelButtonText: t("organization.cancel") }); }
  catch { return; }
  saving.value = true; error.value = "";
  try { await props.api.templates.apply(props.roleId, selected.value, props.version, props.reason.trim()); emit("applied"); ElMessage.success(t("authorizationCenter.saved")); }
  catch (cause) { await setErrorFeedback(error, props.errorText(cause)); }
  finally { saving.value = false; }
}
watch(() => props.roleId, load, { immediate: true });
/** 同一组件按当前授权域选择真实模板接口操作，身份不作为服务端授权输入。 */
const templateSaveOperation = computed(() => location.hash.includes('/platform/') ? 'AuthorizationTemplate.savePlatform' : 'AuthorizationTemplate.saveTenant');
const templateApplyOperation = computed(() => location.hash.includes('/platform/') ? 'AuthorizationTemplate.applyPlatform' : 'AuthorizationTemplate.applyTenant');
</script>
<template>
  <section v-if="api.templates" class="template-actions">
    <h3>{{ t("authorizationCenter.templates") }}<InfoHint :content="t('authorizationCenter.templateHint')" /></h3>
    <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false"><el-button link @click="load">{{ t("account.retry") }}</el-button></FloatingError>
    <div class="template-forms">
    <el-form inline @submit.prevent="save">
      <el-form-item :label="t('authorizationCenter.templateName')"><el-input v-model="name" :maxlength="100" :disabled="busy || loading || saving || dirty" /></el-form-item>
      <el-form-item><el-button v-operation="templateSaveOperation" :loading="saving" :disabled="busy || loading || dirty || !name.trim() || !!error" native-type="submit">{{ t("authorizationCenter.saveTemplate") }}</el-button></el-form-item>
    </el-form>
    <el-form inline @submit.prevent="apply">
      <el-form-item :label="t('authorizationCenter.templates')"><el-select v-model="selected" filterable :disabled="busy || loading || saving || dirty" class="template-select"><el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item><el-button v-operation="templateApplyOperation" type="warning" :loading="saving" :disabled="busy || loading || dirty || !selected || !reason.trim() || !!error" native-type="submit">{{ t("authorizationCenter.applyTemplate") }}</el-button></el-form-item>
    </el-form>
    </div>
  </section>
</template>
<style scoped>
/* 复用原表单与提示组件；桌面并排、窄屏换行，保留全部操作及校验。 */
.template-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; min-width: 0; }
.template-actions h3 { display: flex; align-items: center; gap: 4px; font-size: var(--el-font-size-base); font-weight: 500; margin: 0; }
.template-forms { display: flex; flex-wrap: wrap; gap: 8px 16px; min-width: 0; }
.template-forms .el-form { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; min-width: 0; }
.template-forms .el-form-item { margin: 0; min-width: 0; }
.template-forms .el-input, .template-select { width: 180px; max-width: 100%; }
@media (max-width: 700px) {
  .template-actions, .template-forms { align-items: stretch; flex-direction: column; }
  .template-forms .el-form-item:first-child { flex: 1; }
  .template-forms .el-input, .template-select { width: 100%; }
  .template-forms :deep(.el-button) { min-height: 44px; }
}
</style>
