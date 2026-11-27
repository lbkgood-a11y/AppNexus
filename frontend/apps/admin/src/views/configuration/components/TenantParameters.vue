<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { AppIcon } from "@appnexus/icons";
import { FloatingError, ManagedDialog, RecordAuditDetails, TreeListTemplate, setErrorFeedback, type RecordAudit } from "@appnexus/ui";
import { http, requestErrorMessage } from "@/utils/http";
import { operationGroups } from "@/api/operationGroups";
import { beginTenantWrite, currentTenant } from "@/utils/tenantContext";
import { refreshDisplayFormat } from "@/utils/displayFormat";

/** 服务端受控参数；版本 -1 表示尚未保存，审计仅展示、不参与提交。 */
interface Parameter { key: string; value: string; defaultValue: string; description: string; version: number; audit?: RecordAudit }
/** 主题仅组织已有参数，不允许管理员新增任意系统配置或修改租户边界。 */
const themes = [
  { key: "regional", icon: "globe", keys: ["display.currency", "display.time-zone"] },
  { key: "lists", icon: "density", keys: ["display.page-size"] },
  { key: "maps", icon: "organization", keys: ["map.amap-key", "map.amap-service-host"] }
] as const;
type ThemeKey = typeof themes[number]["key"];
const { t } = useI18n();
const selected = ref<ThemeKey>("regional"), collapsed = ref(false);
const rows = ref<Parameter[]>([]), baseline = ref<Record<string, string>>({});
const loading = ref(false), saving = ref(false), editable = ref(false), error = ref("");
const formRef = ref<FormInstance>(), auditRow = ref<Parameter>();
let generation = 0;
/** 菜单事件提供任意字符串，只接受已登记主题，避免未知分区破坏参数视图。 */
function selectTheme(value:string){if(themes.some(theme=>theme.key===value))selected.value=value as ThemeKey;}
const activeTheme = computed(() => themes.find(theme => theme.key === selected.value)!);
const visibleRows = computed(() => activeTheme.value.keys.flatMap(key => rows.value.filter(row => row.key === key)));
const changedRows = computed(() => visibleRows.value.filter(row => row.key !== "map.amap-service-host" && row.value !== baseline.value[row.key]));
const busy = computed(() => loading.value || saving.value);
/** 币种为 ISO 4217，时区为 IANA 标识；选项由浏览器标准目录提供，服务端仍作最终校验。 */
const currencies = Intl.supportedValuesOf("currency");
const timeZones = [...new Set(["Asia/Shanghai", "UTC", ...Intl.supportedValuesOf("timeZone")])];
/** 稳定参数键对应中英文文案，禁止直接展示服务端中文定义替代国际化。 */
function parameterKey(key: string) { return key.startsWith("display.") ? key.slice(8) : key.replaceAll(".", "_"); }
/** 前端校验与后端数量、币种、时区及公开 Web Key 约束一致，字段错误就地展示。 */
const rules = computed<FormRules>(() => ({
  "display.currency": [{ validator: (_rule, value, callback) => { try { if (!/^[A-Z]{3}$/.test(value)) throw new Error(); new Intl.NumberFormat("en", { style: "currency", currency: value }); callback(); } catch { callback(new Error(t("tenantParameterEditor.invalidCurrency"))); } }, trigger: "change" }],
  "display.time-zone": [{ validator: (_rule, value, callback) => { try { new Intl.DateTimeFormat("en", { timeZone: value }); if (!value) throw new Error(); callback(); } catch { callback(new Error(t("tenantParameterEditor.invalidTimeZone"))); } }, trigger: "change" }],
  "display.page-size": [{ validator: (_rule, value, callback) => { const size = Number(value); callback(Number.isInteger(size) && size >= 10 && size <= 100 && String(size) === value ? undefined : new Error(t("tenantParameterEditor.invalidPageSize"))); }, trigger: "change" }],
  "map.amap-key": [{ pattern: /^([a-fA-F0-9]{32})?$/, message: t("tenantParameterEditor.invalidMapKey"), trigger: "blur" }]
}));
/** el-form 通过稳定参数编码定位值；每个主题仅校验当前呈现字段。 */
const formModel = computed(() => Object.fromEntries(rows.value.map(row => [row.key, row.value])));
/** 先检查维护准入再读取编辑数据；普通成员仍可通过原只读接口使用租户展示设置。 */
async function load() {
  const epoch = ++generation;
  rows.value = []; baseline.value = {}; editable.value = false; auditRow.value = undefined; error.value = "";
  if (!currentTenant.value) { loading.value = false; return; }
  loading.value = true;
  try {
    const capability = await operationGroups("tenant", { SAVE: "TenantParameter.save" });
    if (epoch !== generation) return;
    editable.value = capability.includes("SAVE");
    if (!editable.value) return;
    const values = await http.request<Parameter[]>("get", "/api/configuration/parameters");
    if (epoch !== generation) return;
    rows.value = values;
    baseline.value = Object.fromEntries(values.map(row => [row.key, row.value]));
  } catch (cause) { if (epoch === generation) await setErrorFeedback(error, requestErrorMessage(cause)); }
  finally { if (epoch === generation) loading.value = false; }
}
/** 放弃当前主题草稿，不发送业务写入，也不改变其他主题尚未保存的值。 */
function resetTheme() {
  visibleRows.value.forEach(row => { row.value = baseline.value[row.key]; });
  formRef.value?.clearValidate();
}
/**
 * 按原接口逐项保存当前主题已改动值，保留版本与 CSRF 校验，不伪装成原子批量事务。
 * 已成功项更新基线；失败项保留输入并停止后续写入，提示已保存数量，409 必须重读。
 * 在途写入占用租户写锁，防止切换上下文造成跨租户提交。
 */
async function saveTheme() {
  if (!editable.value || busy.value || !changedRows.value.length) return;
  if (!await formRef.value?.validate().catch(() => false)) return;
  const epoch = generation;
  const changes = [...changedRows.value];
  let completed = 0;
  const release = beginTenantWrite();
  saving.value = true; error.value = "";
  try {
    const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
    for (const row of changes) {
      const result = await http.request<Parameter>("put", "/api/configuration/parameters/" + encodeURIComponent(row.key), { data: { value: row.value, version: row.version }, headers: { [csrf.headerName]: csrf.token } });
      if (epoch !== generation) return;
      Object.assign(row, result); baseline.value[row.key] = result.value; completed++;
    }
    ElMessage.success(t("account.saved"));
  } catch (cause) {
    if (epoch === generation) {
      editable.value = false;
      await setErrorFeedback(error, (completed ? t("tenantParameterEditor.partialSave", { count: completed }) + " " : "") + requestErrorMessage(cause));
    }
  } finally {
    if (completed) await refreshDisplayFormat();
    release(); saving.value = false;
  }
}
onMounted(load);
watch(() => currentTenant.value?.tenantId, load);
</script>

<template>
  <div class="management-page tenant-parameters" v-loading="loading">
    <TreeListTemplate v-model:collapsed="collapsed" :title="t('account.tenantParameters')" :tree-label="t('tenantParameterEditor.themes')">
      <template #treeHeader><strong>{{ t('tenantParameterEditor.themes') }}</strong></template>
      <template #tree>
        <el-menu :default-active="selected" @select="selectTheme">
          <el-menu-item v-for="theme in themes" :key="theme.key" :index="theme.key" :disabled="busy">
            <AppIcon :name="theme.icon" :size="18" /><span>{{ t('tenantParameterEditor.' + theme.key + '.title') }}</span>
          </el-menu-item>
        </el-menu>
      </template>
      <template #header>
        <div class="theme-heading"><div><h2>{{ t('tenantParameterEditor.' + selected + '.title') }}</h2><p>{{ t('tenantParameterEditor.' + selected + '.description') }}</p></div>
          <el-button :disabled="busy" @click="load"><AppIcon name="refresh" :size="16" />{{ t('account.retry') }}</el-button>
        </div>
        <div v-if="currentTenant" class="tenant-context"><el-tag type="info" effect="plain">{{ t('tenantParameterEditor.tenant') }} · {{ currentTenant.name }}</el-tag><span>{{ t('tenantParameterEditor.scope') }}</span></div>
      </template>
      <template v-if="error" #feedback><FloatingError :title="error" scope="error" type="error" :closable="false" /></template>
      <template #table>
        <el-empty v-if="!currentTenant" :description="t('tenantParameterEditor.selectTenant')" />
        <el-result v-else-if="!loading && !editable && !rows.length && !error" icon="warning" :title="t('tenantParameterEditor.denied')" :sub-title="t('tenantParameterEditor.adminOnly')" />
        <el-empty v-else-if="!loading && !rows.length" :description="t('tenantParameterEditor.unavailable')" />
        <el-form v-else :key="selected" ref="formRef" :model="formModel" :rules="rules" label-position="top" class="theme-form" @submit.prevent="saveTheme">
          <el-form-item v-for="row in visibleRows" :key="row.key" :prop="[row.key]" :label="t('account.parameters.' + parameterKey(row.key))">
            <div class="parameter-field">
              <el-select v-if="row.key === 'display.currency' || row.key === 'display.time-zone'" v-model="row.value" filterable :disabled="!editable || busy">
                <el-option v-for="value in row.key === 'display.currency' ? currencies : timeZones" :key="value" :label="value" :value="value" />
              </el-select>
              <el-input-number v-else-if="row.key === 'display.page-size'" :model-value="Number(row.value)" :min="10" :max="100" :step="10" :step-strictly="false" :precision="0" :disabled="!editable || busy" @update:model-value="row.value = String($event ?? 20)" />
              <el-input v-else-if="row.key === 'map.amap-key'" v-model="row.value" :maxlength="32" clearable :disabled="!editable || busy" />
              <el-input v-else :model-value="row.value" readonly :disabled="true" />
              <p class="parameter-hint">{{ t('account.parameterHints.' + parameterKey(row.key)) }}</p>
              <el-button v-if="row.audit" link type="primary" class="audit-link" @click="auditRow = row">{{ t('tenantParameterEditor.audit') }}</el-button>
            </div>
          </el-form-item>
          <div class="theme-actions">
            <el-button v-operation="'TenantParameter.save'" v-if="editable" type="primary" native-type="submit" :loading="saving" :disabled="busy || !changedRows.length">{{ t('tenantParameterEditor.save') }}</el-button>
            <el-button :disabled="busy || !changedRows.length" @click="resetTheme">{{ t('tenantParameterEditor.reset') }}</el-button>
            <span v-if="changedRows.length" class="pending-hint">{{ t('tenantParameterEditor.pending', { count: changedRows.length }) }}</span>
          </div>
        </el-form>
      </template>
    </TreeListTemplate>
    <ManagedDialog :model-value="!!auditRow" :title="t('tenantParameterEditor.audit')" :width="520" @update:model-value="value => { if (!value) auditRow = undefined; }">
      <RecordAuditDetails v-if="auditRow?.audit" :audit="auditRow.audit" dialog />
    </ManagedDialog>
  </div>
</template>

<style scoped>
.tenant-parameters { min-width: 0; }
.tenant-parameters :deep(.el-menu) { border-right: 0; }
.tenant-parameters :deep(.el-menu-item) { gap: 10px; }
.theme-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.theme-heading h2 { margin: 0; font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); }
.theme-heading p { margin: 8px 0 0; color: var(--el-text-color-secondary); line-height: 1.6; }
.tenant-context { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 12px; font-size: 12px; color: var(--el-text-color-secondary); }
.theme-form { max-width: 760px; padding: 16px 8px; }
.theme-form :deep(.el-form-item) { margin-bottom: 28px; }
.parameter-field { width: 100%; min-width: 0; }
.parameter-field .el-select { width: 100%; }
.parameter-hint { margin: 8px 0 0; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; overflow-wrap: anywhere; }
.audit-link { font-size: 12px; }
.theme-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding-top: 20px; border-top: 1px solid var(--el-border-color-lighter); }
.theme-actions .el-button { margin: 0; }
.pending-hint { font-size: 12px; color: var(--el-text-color-secondary); }
@media (max-width: 600px) { .theme-heading { flex-wrap: wrap; } .theme-form { padding: 12px 0; } }
</style>
