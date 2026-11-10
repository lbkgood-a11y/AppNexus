<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { http, requestErrorMessage } from "@/utils/http";
import { currentTenant } from "@/utils/tenantContext";
import { displayInstant, refreshDisplayFormat } from "@/utils/displayFormat";

/** 个人中心承载受权租户或平台审计摘要，复用 Element Plus 表格和分页，不请求敏感原值。 */
interface Entry { source: string; eventId: string; actor: string; action: string; target: string | null; traceId: string | null; status: number | null; occurredAt: string }
const { t, locale } = useI18n();
const rows = ref<Entry[]>([]), total = ref(0), page = ref(1), loading = ref(false), error = ref("");
let generation = 0;
/** 有租户只查当前可信租户，无租户走平台受权入口；403 显示明确反馈，不当作空结果。 */
async function load() {
  const current = ++generation; loading.value = true; error.value = "";
  try {
    const result = await http.request<{ items: Entry[]; total: number }>("get", currentTenant.value ? "/api/audits" : "/bff/platform/audits", { params: { page: page.value, pageSize: 20 } });
    if (current !== generation) return; rows.value = result.items; total.value = result.total;
  } catch (cause) { if (current === generation) error.value = requestErrorMessage(cause); }
  finally { if (current === generation) loading.value = false; }
}
onMounted(() => { void refreshDisplayFormat(); void load(); });
</script>
<template>
  <section>
    <div class="audit-heading"><h1>{{ t("account.auditQuery") }}</h1><el-button :loading="loading" @click="load">{{ t("account.refresh") }}</el-button></div>
    <p class="audit-note">{{ t("account.auditHint") }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
    <el-table :data="rows" v-loading="loading" :empty-text="t(error ? 'account.logsRetry' : 'account.noLogs')">
      <el-table-column prop="source" :label="t('account.auditSource')" min-width="120" />
      <el-table-column prop="action" :label="t('account.event')" min-width="200" />
      <el-table-column prop="actor" :label="t('account.auditActor')" min-width="260" />
      <el-table-column prop="target" :label="t('account.auditTarget')" min-width="220" />
      <el-table-column :label="t('account.auditTrace')" min-width="260"><template #default="{ row }">{{ row.traceId || '—' }}</template></el-table-column>
      <el-table-column :label="t('account.eventTime')" min-width="220"><template #default="{ row }">{{ displayInstant(row.occurredAt, locale) }}</template></el-table-column>
    </el-table>
    <div class="audit-pagination"><el-pagination :current-page="page" :page-size="20" :total="total" :disabled="loading || !!error" layout="total, prev, pager, next" @current-change="value => { page = value; load(); }" /></div>
  </section>
</template>
<style scoped>
.audit-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.audit-heading h1 { font-size: 23px; font-weight: 600; }
.audit-note { color: var(--el-text-color-secondary); line-height: 1.8; margin: 16px 0; }
.audit-pagination { display: flex; justify-content: flex-end; margin-top: 20px; overflow-x: auto; }
</style>
