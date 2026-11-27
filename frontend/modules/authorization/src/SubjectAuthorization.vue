<script setup lang="ts">
import { setErrorFeedback } from "@appnexus/ui";
import { AppPagination } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import { InfoHint } from "@appnexus/ui";
import { AppIcon } from "@appnexus/icons";
import { computed, onMounted, onBeforeUnmount, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ManagementTable, type ManagementColumn } from "@appnexus/ui";
import type { OperationDiagnosis, AuthorizationSubject, EffectivePermission, RoleItem, RoleManagementApi, SubjectAuthorizationApi } from "./index";

/** 只读展示当前用户的角色列表及权限解释，关系以服务端授权快照为准。 */
const props = defineProps<{ api: SubjectAuthorizationApi; roleApi: RoleManagementApi; errorText: (error: unknown) => string }>();
const { t, te, locale } = useI18n();
const rows = ref<AuthorizationSubject[]>([]), total = ref(0), page = ref(1), query = ref(""), submittedQuery = ref("");
const roles = ref<RoleItem[]>([]), subject = ref<AuthorizationSubject>(), assignedRoleIds = ref<string[]>([]);
/** 仅折叠角色列表，保留当前用户选择。 */
const roleExpanded=ref(true);
const detailTab=ref("diagnostics");
const diagnostics=ref<OperationDiagnosis[]>([]),diagnosticQuery=ref("");
const diagnosticRows=computed(()=>diagnostics.value.filter(item=>[item.operation.id,item.operation.nameZh,item.operation.nameEn,...item.operation.permissions].some(value=>value.toLowerCase().includes(diagnosticQuery.value.trim().toLowerCase()))));
const effective = ref<EffectivePermission[]>([]);
const loading = ref(false), detailLoading = ref(false), ready = ref(false), error = ref(""), detailError = ref("");
let listRequest = 0, detailRequest = 0;
const columns = computed<ManagementColumn[]>(() => [
  { key: "name", prop: "name", label: t("authorizationCenter.subject"), minWidth: 160 },
  { key: "detail", prop: "detail", label: t("authorizationCenter.account"), minWidth: 140 },
  { key: "operations", label: t("tenant.operation"), width: 120 }
]);
/** 仅显示快照已分配角色；目录缺失时保留原标识，避免静默遗漏历史关系。 */
const assignedRoles = computed(() => assignedRoleIds.value.map(id => roles.value.find(role => role.id === id) ?? { id, name: t("authorizationCenter.unavailableRole"), code: id, status: "" }));
const roleColumns = computed<ManagementColumn[]>(() => [
  { key: "status", label: t("roleManagement.status"), width: 100 },
  { key: "code", prop: "code", label: t("roleManagement.code"), minWidth: 180 },
  { key: "name", prop: "name", label: t("roleManagement.name"), minWidth: 150 }
]);

/** 后到目录响应不得覆盖新查询；首次成功读取非空目录时默认选择首位用户。 */
async function loadList() {
  const request = ++listRequest;
  loading.value = true; error.value = "";
  try {
    const result = await props.api.subjects(page.value, submittedQuery.value);
    if (request !== listRequest) return;
    rows.value = result.items; total.value = result.total;
    if (!subject.value && result.items.length) await open(result.items[0]);
  } catch (cause) { if (request === listRequest) await setErrorFeedback(error, props.errorText(cause)); }
  finally { if (request === listRequest) loading.value = false; }
}
/** 读取全部角色页，避免超过 100 条后已分配角色无法匹配名称。 */
async function loadRoles() {
  const items: RoleItem[] = [];
  for (let current = 1; ; current++) {
    const result = await props.roleApi.list({ query: "", page: current, pageSize: 100 });
    items.push(...result.items);
    if (items.length >= result.total) return items;
    if (!result.items.length) throw new Error(t("authorizationCenter.roleListChanged"));
  }
}
/** 切换用户后重新读取角色和解释；旧请求由详情序号隔离。 */
async function open(row: AuthorizationSubject) {
  subject.value = row;
  await loadDetail();
}
/** 读取同一用户的快照及解释，失败时不展示上一用户角色。 */
async function loadDetail() {
  if (!subject.value) return;
  const id = subject.value.id, request = ++detailRequest;
  detailLoading.value = true; ready.value = false; detailError.value = "";
  assignedRoleIds.value = []; roles.value = []; effective.value = []; diagnostics.value=[];
  try {
    const [assignment, explanations, catalog, operationDetails] = await Promise.all([props.api.assignment(id), props.api.effective(id), loadRoles(), props.api.diagnostics(id)]);
    if (request !== detailRequest) return;
    assignedRoleIds.value = [...assignment.roleIds];
    roles.value = catalog; effective.value = explanations; diagnostics.value=operationDetails; ready.value = true;
  } catch (cause) { if (request === detailRequest) await setErrorFeedback(detailError, props.errorText(cause)); }
  finally { if (request === detailRequest) detailLoading.value = false; }
}
function search(reset = false) { if (reset) query.value = ""; submittedQuery.value = query.value.trim(); page.value = 1; void loadList(); }
function inactiveReason(value: string | null) { const key = "authorizationCenter.reasons." + value; return value && te(key) ? t(key) : t("authorizationCenter.unknownReason"); }
onMounted(loadList);
onBeforeUnmount(() => { ++listRequest; ++detailRequest; });
/** 只读列表无待保存草稿，供外层弹框统一检查关闭。 */
defineExpose({ canLeave: async () => true });
</script>

<template>
  <div class="subject-workspace">
    <section class="subject-directory">
      <el-form class="subject-search query-border-label-form" inline @submit.prevent="search()">
        <el-form-item :label="t('authorizationCenter.subjectQuery')"><el-input v-model="query" clearable :maxlength="100" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :disabled="loading">{{ t('authorizationCenter.search') }}</el-button><el-button :disabled="loading" @click="search(true)">{{ t('authorizationCenter.reset') }}</el-button></el-form-item>
      </el-form>
      <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" show-icon><el-button link @click="loadList">{{ t('account.retry') }}</el-button></FloatingError>
      <ManagementTable :title="t('authorizationCenter.subjects')" fit-height :rows="rows" :columns="columns" :loading="loading" :total="total" row-key="id" :current-row-key="subject?.id" embedded @refresh="loadList">
        <template #operations="{ row }"><el-button link type="primary" :disabled="detailLoading" @click="open(row)">{{ t('authorizationCenter.viewRoles') }}</el-button></template>
        <template #pagination><AppPagination v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next" :disabled="loading" @current-change="loadList" /></template>
      </ManagementTable>
    </section>
    <section v-loading="detailLoading" class="subject-editor">
      <el-empty v-if="!subject" :description="t('authorizationCenter.selectSubject')" />
      <template v-else>
        <section class="subject-role-panel">
        <div class="subject-role-heading"><h2>{{ subject.name }}</h2><el-button text :aria-label="t(roleExpanded?'authorizationCenter.collapseRoles':'authorizationCenter.expandRoles')" :aria-expanded="roleExpanded" aria-controls="subject-role-form" @click="roleExpanded=!roleExpanded"><AppIcon :name="roleExpanded?'arrowUp':'arrowDown'" :size="18" /></el-button></div>
        <div id="subject-role-form" v-show="roleExpanded">
        <el-alert v-if="subject.enabled === false" type="warning" :title="t('authorizationCenter.subjectDisabled')" :closable="false" />
        <FloatingError v-if="detailError" type="error" :title="detailError" scope="detailError" :closable="false" show-icon><el-button link @click="loadDetail">{{ t('authorizationCenter.reload') }}</el-button></FloatingError>
        <ManagementTable :title="t('authorizationCenter.subjectRoles')" heading-level="h2" :rows="assignedRoles" :columns="roleColumns" :loading="detailLoading" :total="assignedRoles.length" embedded @refresh="loadDetail">
          <template #status="{ row }"><el-tag class="app-status-tag" v-if="row.status" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ t('roleManagement.statuses.' + row.status) }}</el-tag><span v-else>—</span></template>
        </ManagementTable>
        </div>
        </section>
        <section class="subject-analysis">
        <el-tabs v-model="detailTab"><el-tab-pane :label="t('operationDiagnostics.title')" name="diagnostics" /><el-tab-pane :label="t('authorizationCenter.effectivePermissions')" name="effective" /></el-tabs>
        <div v-show="detailTab==='diagnostics'" class="subject-analysis-scroll">
        <h2>{{ t('operationDiagnostics.title') }}<InfoHint :content="t('operationDiagnostics.limits')" /></h2>
        <el-input v-model="diagnosticQuery" clearable :placeholder="t('operationDiagnostics.search')" />
        
        <el-collapse><el-collapse-item v-for="item in diagnosticRows" :key="item.operation.id" :name="item.operation.id">
          <template #title>{{ locale.startsWith('zh') ? item.operation.nameZh : item.operation.nameEn }} · {{ item.operation.id }} — {{ t('operationDiagnostics.'+item.status) }}</template>
          <p>{{ item.operation.policy }}</p>
          <p v-for="page in item.pages" :key="page.page">{{ page.path }}：{{ t(page.accessible ? 'operationDiagnostics.pageAllowed' : 'operationDiagnostics.pageDenied') }} / {{ t(page.visible ? 'operationDiagnostics.menuVisible' : 'operationDiagnostics.menuHidden') }}<span v-for="reason in page.reasons" :key="reason"> · {{ t('operationDiagnostics.reasons.'+reason) }}</span></p>
          <p v-for="data in item.data" :key="data.permission">{{data.permission}} · {{t('operationDiagnostics.dataScope')}}：{{data.scope==='NOT_APPLICABLE'?t('operationDiagnostics.notApplicable'):t('authorizationCenter.scopes.'+data.scope)}}<span v-if="data.organizations.length"> · {{t('authorizationCenter.organizationCount',{count:data.organizations.length})}}</span><span v-for="(mode,field) in data.fields" :key="field"> · {{field}}: {{mode}}</span></p>
          <p>{{t('operationDiagnostics.objectHint')}}</p>
          <p v-for="permission in item.permissions" :key="permission.permissionCode">{{ permission.permissionCode }}：{{ permission.effective ? t('authorizationCenter.effective') : t('operationDiagnostics.missing') }}<span v-for="source in permission.sources" :key="source.roleId"> · {{ source.roleName }} / {{ source.effective ? t('authorizationCenter.effective') : inactiveReason(source.inactiveReason) }}{{ source.scopeSource ? ' / '+t('authorizationCenter.scopeSources.'+source.scopeSource) : '' }}</span></p>
        </el-collapse-item></el-collapse>
        </div>
        <div v-show="detailTab==='effective'" class="subject-analysis-scroll">
        <h2 class="explanation-heading">{{ t('authorizationCenter.effectivePermissions') }}<InfoHint :content="t('authorizationCenter.explanationNote')" /></h2>
        
        <el-empty v-if="ready && !effective.length" :description="t('authorizationCenter.noEffectivePermissions')" />
        <el-collapse v-if="ready">
          <el-collapse-item v-for="item in effective" :key="item.permissionCode" :name="item.permissionCode">
            <template #title><span class="permission-code">{{ item.permissionCode }}</span><el-tag class="app-status-tag" :type="item.effective ? 'success' : 'info'">{{ t('authorizationCenter.' + (item.effective ? 'effective' : 'inactive')) }}</el-tag></template>
            <div v-for="source in item.sources" :key="source.roleId" class="permission-source">
              <strong>{{ source.roleName }}</strong>
              <span>{{ source.effective ? t('authorizationCenter.effective') : inactiveReason(source.inactiveReason) }}</span>
              <span v-if="source.scopeType">{{ t('authorizationCenter.scopes.' + source.scopeType) }}</span>
              <span v-if="source.scopeSource">{{ t('authorizationCenter.scopeSources.'+source.scopeSource) }}</span>
              <span v-if="source.organizationIds.length">{{ t('authorizationCenter.organizationCount', { count: source.organizationIds.length }) }}</span>
            </div>
          </el-collapse-item>
        </el-collapse>
        </div>
        </section>
      </template>
    </section>
  </div>
</template>

<style scoped>
/* 页面外围留白引用全局内容与区块 Token，局部字段间距和触控高度按原交互保留。 */
.subject-workspace { min-height:0; height:100%; display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(320px, 1fr); align-items: stretch; overflow:hidden; }
.subject-directory { min-width: 0; min-height:0; display:flex; flex-direction:column; overflow:hidden; }
.subject-search { display: flex; flex-wrap: wrap; gap: 12px; padding:var(--app-content-padding,12px); background: var(--el-fill-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter); }
.subject-search .el-form-item { margin-bottom: 0; margin-right: 0; }
.subject-search .el-form-item:last-child { margin-left: auto; margin-right: 0; }
.subject-editor { display:flex; flex-direction:column; gap:var(--app-section-gap,8px); min-width: 0; min-height:0; overflow:hidden; padding:var(--app-content-padding,12px); border-left: 1px solid var(--el-border-color); }
.subject-editor h2 { font-size: 16px; margin: 0 0 20px; }
/* 弹框内通过深层选择器覆盖公共表格实际根节点，并由 fitHeight 测量剩余表体高度；分页不随表体滚动。 */
.subject-directory>:deep(.management-table){flex:1;min-height:0;display:flex;flex-direction:column;overflow:hidden}
.subject-directory :deep(.management-table-surface),.subject-directory :deep(.management-table-surface>.el-card__body){display:flex;flex-direction:column;flex:1;min-height:0;overflow:hidden}
.subject-directory :deep(.management-table-region){flex:1;min-height:0;overflow:auto}
.subject-search{flex:none}
.subject-role-panel{flex:none;max-height:55%;overflow:auto;border-bottom:1px solid var(--el-border-color-lighter)}
.subject-role-heading{display:flex;align-items:center;justify-content:space-between;gap:8px}
.subject-role-heading h2{margin:0}
.subject-analysis{display:flex;flex-direction:column;flex:1;min-height:0;overflow:hidden}
.subject-analysis>.el-tabs{flex:none}
.subject-analysis-scroll{flex:1;min-height:0;overflow:auto}
.subject-editor h2.explanation-heading{margin:0 0 20px}
.subject-editor .el-alert { margin-bottom:var(--app-section-gap,8px); }
.permission-code { overflow-wrap: anywhere; margin-right: 12px; text-align: left; }
.permission-source { display: flex; flex-wrap: wrap; gap: 8px 16px; padding: 8px 0; color: var(--el-text-color-regular); }
@media (max-width: 1100px) { .subject-workspace { grid-template-columns: 1fr; grid-template-rows:minmax(280px,1fr) minmax(360px,1fr); overflow:auto; } .subject-editor { border-left: 0; border-top: 1px solid var(--el-border-color); } }
@media (max-width: 600px) { .subject-directory { min-width: 0; }
.subject-search { padding: 12px; } .subject-search :deep(.el-input) { width: 100%; } .subject-editor :deep(.el-button) { min-height: 44px; } }
</style>
