<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from "vue";
import { ElMessage, ElMessageBox, type FormInstance } from "element-plus";
import { useI18n } from "vue-i18n";
import { ManagementTable, type ManagementColumn } from "@appnexus/ui";
import type { AuthorizationSubject, EffectivePermission, RoleItem, RoleManagementApi, SubjectAuthorizationApi } from "./index";

/** 主体角色编辑复用管理表格与 Element Plus；完整快照只有读取成功后才允许提交。 */
const props = defineProps<{ api: SubjectAuthorizationApi; roleApi: RoleManagementApi; errorText: (error: unknown) => string }>();
const { t, te } = useI18n();
const rows = ref<AuthorizationSubject[]>([]), total = ref(0), page = ref(1), query = ref(""), submittedQuery = ref("");
const roles = ref<RoleItem[]>([]), subject = ref<AuthorizationSubject>(), selected = ref<string[]>([]), original = ref<string[]>([]);
const effective = ref<EffectivePermission[]>([]), version = ref(0), reason = ref("");
const loading = ref(false), detailLoading = ref(false), saving = ref(false), ready = ref(false), error = ref(""), detailError = ref("");
const form = ref<FormInstance>();
let listRequest = 0, detailRequest = 0;
const columns = computed<ManagementColumn[]>(() => [
  { key: "name", prop: "name", label: t("authorizationCenter.subject"), minWidth: 160 },
  { key: "detail", prop: "detail", label: t("authorizationCenter.account"), minWidth: 140 },
  { key: "operations", label: t("tenant.operation"), width: 120 }
]);
const changed = computed(() => [...selected.value].sort().join() !== [...original.value].sort().join());
const rules = computed(() => ({ reason: [{ required: true, whitespace: true, message: t("authorizationCenter.reasonRequired"), trigger: "blur" }] }));
const missing = computed(() => original.value.filter(id => !roles.value.some(role => role.id === id)));

/** 后到响应不得覆盖新选择；失败时关闭写入口，保留原展示供重试。 */
async function loadList() {
  const request = ++listRequest;
  loading.value = true; error.value = "";
  try {
    const result = await props.api.subjects(page.value, submittedQuery.value);
    if (request !== listRequest) return;
    rows.value = result.items; total.value = result.total;
  } catch (cause) { if (request === listRequest) error.value = props.errorText(cause); }
  finally { if (request === listRequest) loading.value = false; }
}
/** 读取全部角色页，防止仅取前 100 条导致覆盖写时静默丢失原角色。 */
async function loadRoles() {
  const items: RoleItem[] = [];
  for (let current = 1; ; current++) {
    const result = await props.roleApi.list({ query: "", page: current, pageSize: 100 });
    items.push(...result.items);
    if (items.length >= result.total) return items;
    if (!result.items.length) throw new Error(t("authorizationCenter.roleListChanged"));
  }
}
async function confirmDiscard() {
  if (!changed.value && !reason.value.trim()) return true;
  try {
    await ElMessageBox.confirm(t("authorizationCenter.discard"), t("authorizationCenter.subjectRoles"), { confirmButtonText: t("authorizationCenter.discardAction"), cancelButtonText: t("organization.cancel"), type: "warning" });
    return true;
  } catch { return false; }
}
async function open(row: AuthorizationSubject) {
  if (saving.value || !(await confirmDiscard())) return;
  subject.value = row;
  await loadDetail();
}
async function loadDetail() {
  if (!subject.value || saving.value) return;
  const id = subject.value.id, request = ++detailRequest;
  detailLoading.value = true; ready.value = false; detailError.value = "";
  selected.value = []; original.value = []; effective.value = []; reason.value = "";
  try {
    const [assignment, explanations, catalog] = await Promise.all([props.api.assignment(id), props.api.effective(id), loadRoles()]);
    if (request !== detailRequest) return;
    selected.value = [...assignment.roleIds]; original.value = [...assignment.roleIds];
    version.value = assignment.authorityVersion; roles.value = catalog; effective.value = explanations; ready.value = true;
    form.value?.clearValidate();
  } catch (cause) { if (request === detailRequest) detailError.value = props.errorText(cause); }
  finally { if (request === detailRequest) detailLoading.value = false; }
}
/** 角色覆盖写有撤权副作用；先确认目标与原因，冲突后禁止盲目重试。 */
async function save() {
  if (!subject.value || !ready.value || !changed.value || saving.value || !(await form.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await ElMessageBox.confirm(t("authorizationCenter.replaceWarning", { name: subject.value.name }), t("authorizationCenter.subjectRoles"), { confirmButtonText: t("organization.save"), cancelButtonText: t("organization.cancel"), type: "warning" });
  } catch { saving.value = false; return; }
  try {
    const result = await props.api.replace(subject.value.id, { ids: [...selected.value], version: version.value, reason: reason.value.trim() });
    selected.value = [...result.roleIds]; original.value = [...result.roleIds]; version.value = result.authorityVersion; reason.value = "";
    ElMessage.success(t("authorizationCenter.saved"));
    // 写入已成功，解释刷新失败必须单独展示，避免管理员误认为需要重复写入。
    effective.value = [];
    try { effective.value = await props.api.effective(subject.value.id); }
    catch (cause) { detailError.value = props.errorText(cause); ready.value = false; }
  } catch (cause) { detailError.value = props.errorText(cause); ready.value = false; }
  finally { saving.value = false; }
}
function search(reset = false) { if (reset) query.value = ""; submittedQuery.value = query.value.trim(); page.value = 1; void loadList(); }
function inactiveReason(value: string | null) { const key = "authorizationCenter.reasons." + value; return value && te(key) ? t(key) : t("authorizationCenter.unknownReason"); }
onMounted(loadList);
onBeforeUnmount(() => { ++listRequest; ++detailRequest; });
/** 由应用壳路由守卫调用；提交期间不能离开，未保存草稿须确认丢弃。 */
defineExpose({ canLeave: async () => !saving.value && await confirmDiscard() });
</script>

<template>
  <div class="subject-workspace">
    <section class="subject-directory">
      <el-form class="subject-search" inline @submit.prevent="search()">
        <el-form-item :label="t('authorizationCenter.subjectQuery')"><el-input v-model="query" clearable :maxlength="100" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :disabled="loading || saving">{{ t('authorizationCenter.search') }}</el-button><el-button :disabled="loading || saving" @click="search(true)">{{ t('authorizationCenter.reset') }}</el-button></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button link @click="loadList">{{ t('account.retry') }}</el-button></el-alert>
      <ManagementTable :title="t('authorizationCenter.subjects')" :rows="rows" :columns="columns" :loading="loading" :busy="saving" :total="total" embedded @refresh="loadList">
        <template #operations="{ row }"><el-button link type="primary" :disabled="saving || detailLoading" @click="open(row)">{{ t('authorizationCenter.configure') }}</el-button></template>
        <template #pagination><el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next" :disabled="loading || saving" @current-change="loadList" /></template>
      </ManagementTable>
    </section>
    <section v-loading="detailLoading" class="subject-editor">
      <el-empty v-if="!subject" :description="t('authorizationCenter.selectSubject')" />
      <template v-else>
        <h2>{{ subject.name }}</h2>
        <el-alert v-if="subject.enabled === false" type="warning" :title="t('authorizationCenter.subjectDisabled')" :closable="false" />
        <el-alert v-if="detailError" type="error" :title="detailError" :closable="false" show-icon><el-button link :disabled="saving" @click="loadDetail">{{ t('authorizationCenter.reload') }}</el-button></el-alert>
        <el-form ref="form" :model="{ reason }" :rules="rules" label-position="top" @submit.prevent="save">
          <el-form-item :label="t('authorizationCenter.subjectRoles')">
            <el-select v-model="selected" multiple filterable :disabled="!ready || saving || detailLoading" class="role-select">
              <el-option v-for="role in roles" :key="role.id" :value="role.id" :label="role.name + ' (' + role.code + ')'" :disabled="role.status !== 'ENABLED'" />
              <el-option v-for="id in missing" :key="id" :value="id" :label="t('authorizationCenter.unavailableRole')" disabled />
            </el-select>
          </el-form-item>
          <el-form-item prop="reason" :label="t('authorizationCenter.reason')"><el-input v-model="reason" type="textarea" :maxlength="500" show-word-limit :disabled="!ready || saving" /></el-form-item>
          <el-button v-operation="'@role:grant'" native-type="submit" type="primary" :loading="saving" :disabled="!ready || !changed || detailLoading">{{ t('organization.save') }}</el-button>
          <el-button :disabled="saving || detailLoading" @click="async () => { if (await confirmDiscard()) await loadDetail(); }">{{ t('authorizationCenter.reload') }}</el-button>
        </el-form>
        <h2 class="explanation-heading">{{ t('authorizationCenter.effectivePermissions') }}</h2>
        <el-alert :title="t('authorizationCenter.explanationNote')" type="info" :closable="false" />
        <el-empty v-if="ready && !effective.length" :description="t('authorizationCenter.noEffectivePermissions')" />
        <el-collapse v-if="ready">
          <el-collapse-item v-for="item in effective" :key="item.permissionCode" :name="item.permissionCode">
            <template #title><span class="permission-code">{{ item.permissionCode }}</span><el-tag :type="item.effective ? 'success' : 'info'">{{ t('authorizationCenter.' + (item.effective ? 'effective' : 'inactive')) }}</el-tag></template>
            <div v-for="source in item.sources" :key="source.roleId" class="permission-source">
              <strong>{{ source.roleName }}</strong>
              <span>{{ source.effective ? t('authorizationCenter.effective') : inactiveReason(source.inactiveReason) }}</span>
              <span v-if="source.scopeType">{{ t('authorizationCenter.scopes.' + source.scopeType) }}</span>
              <span v-if="source.organizationIds.length">{{ t('authorizationCenter.organizationCount', { count: source.organizationIds.length }) }}</span>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>
    </section>
  </div>
</template>

<style scoped>
.subject-workspace { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(320px, 1fr); align-items: start; }
.subject-directory { min-width: 0; }
.subject-search { display: flex; flex-wrap: wrap; gap: 12px; padding: 20px; background: var(--el-fill-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter); }
.subject-search .el-form-item { margin-bottom: 0; margin-right: 0; }
.subject-search .el-form-item:last-child { margin-left: auto; margin-right: 0; }
.subject-editor { min-width: 0; padding: 20px; border-left: 1px solid var(--el-border-color); }
.subject-editor h2 { font-size: 16px; margin: 0 0 20px; }
.subject-editor h2.explanation-heading { margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--el-border-color-lighter); }
.subject-editor .el-alert { margin-bottom: 16px; }
.role-select { width: 100%; }
.permission-code { overflow-wrap: anywhere; margin-right: 12px; text-align: left; }
.permission-source { display: flex; flex-wrap: wrap; gap: 8px 16px; padding: 8px 0; color: var(--el-text-color-regular); }
@media (max-width: 1100px) { .subject-workspace { grid-template-columns: 1fr; } .subject-editor { border-left: 0; border-top: 1px solid var(--el-border-color); } }
@media (max-width: 600px) { .subject-directory { min-width: 0; }
.subject-search { padding: 12px; } .subject-search :deep(.el-input) { width: 100%; } .subject-editor :deep(.el-button) { min-height: 44px; } }
</style>
