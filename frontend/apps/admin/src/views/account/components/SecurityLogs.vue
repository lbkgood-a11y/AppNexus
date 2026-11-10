<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { getSecurityLogs, type SecurityLogEntry } from "@/api/account";
import { requestErrorMessage } from "@/utils/http";
import { displayInstant, refreshDisplayFormat } from "@/utils/displayFormat";
const { t, locale } = useI18n();
const rows = ref<SecurityLogEntry[]>([]);
const total = ref(0);
const page = ref(1);
/** 固定每页 10 条，与当前列表密度一致；上限仍由后端校验。 */
const pageSize = 10;
const loading = ref(false);
const error = ref("");
let generation = 0;
/** 只允许最新分页请求写入界面，切页失败保留重试入口。 */
async function load() {
  const current = ++generation;
  loading.value = true; error.value = "";
  try {
    const result = await getSecurityLogs(page.value, pageSize);
    if (current !== generation) return;
    rows.value = result.items; total.value = result.total;
  } catch (cause) {
    if (current === generation) error.value = requestErrorMessage(cause);
  } finally { if (current === generation) loading.value = false; }
}
/** 时间点按当前语言及租户展示时区格式化，原 UTC 证据保留，禁止推断地理位置或设备。 */
function time(value: string) {
  return displayInstant(value,locale.value);
}
onMounted(()=>{void refreshDisplayFormat();void load();});
</script>
<template>
  <section>
    <div class="log-heading"><div><h1>{{ t("account.securityLogs") }}</h1>
      <p>{{ t("account.logsIntro") }}</p></div>
      <el-button :loading="loading" @click="load">{{ t("account.refresh") }}</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-4" />
    <el-table v-loading="loading" :data="error ? [] : rows" :empty-text="error ? t('account.logsRetry') : t('account.noLogs')" class="security-table">
      <el-table-column :label="t('account.event')" min-width="200">
        <template #default="{ row }">{{ t("account.events." + row.action) }}</template>
      </el-table-column>
      <el-table-column :label="t('account.result')" width="120">
        <template #default="{ row }"><el-tag :type="['LOGIN_FAILED','MFA_FAILED','REFRESH_TOKEN_REPLAY'].includes(row.action) ? 'danger' : 'success'" effect="plain">{{ t(['LOGIN_FAILED','MFA_FAILED','REFRESH_TOKEN_REPLAY'].includes(row.action) ? 'account.failed' : 'account.success') }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="t('account.eventTime')" min-width="220">
        <template #default="{ row }"><time :datetime="row.occurredAt" :title="row.occurredAt">{{ time(row.occurredAt) }}</time></template>
      </el-table-column>
    </el-table>
    <div class="log-pagination"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total"
      :disabled="loading || Boolean(error)" layout="total, prev, pager, next" @current-change="load" /></div>
    <p class="log-note">{{ t("account.logsScope") }}</p>
  </section>
</template>
<style scoped>
.log-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 24px; }
h1 { margin: 0 0 10px; font-size: 23px; font-weight: 600; }
p { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.8; }
.security-table { width: 100%; }
.log-pagination { display: flex; justify-content: flex-end; margin-top: 20px; overflow-x: auto; }
.log-note { margin-top: 24px; }
</style>
