<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { AppPagination, ManagedDialog } from "@appnexus/ui";
import type { StatusHistoryPage } from "./index";

/** 主数据列表的启停历史入口，加载和翻页均由宿主的固定授权 API 执行。 */
const props = defineProps<{
  operation: string;
  load: (page: number, size: number) => Promise<StatusHistoryPage>;
  errorText: (error: unknown) => string;
}>();
const { t, locale } = useI18n();
const open = ref(false);
const busy = ref(false);
const denied = ref(false);
const error = ref("");
const page = ref(1);
const size = 20;
const data = ref<StatusHistoryPage>({ rows: [], total: 0 });
let request = 0;

async function read() {
  const token = ++request;
  busy.value = true;
  denied.value = false;
  error.value = "";
  try {
    const result = await props.load(page.value, size);
    if (token === request) data.value = result;
  } catch (cause) {
    if (token === request) {
      denied.value = (cause as { response?: { status?: number } })?.response?.status === 403;
      error.value = props.errorText(cause);
      data.value = { rows: [], total: 0 };
    }
  } finally {
    if (token === request) busy.value = false;
  }
}

function show() {
  page.value = 1;
  open.value = true;
  void read();
}

function close() {
  ++request;
  data.value = { rows: [], total: 0 };
  busy.value = false;
}

function time(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? t("recordAudit.unknown") :
    new Intl.DateTimeFormat(locale.value, { dateStyle: "medium", timeStyle: "medium" }).format(parsed);
}
</script>

<template>
  <el-button v-operation="operation" link type="primary" @click="show">{{ t("productV1.history") }}</el-button>
  <ManagedDialog v-if="open" v-model="open" :title="t('productV1.history')" @update:model-value="value => { if (!value) close(); }">
    <el-result v-if="error" :icon="denied ? 'warning' : 'error'" :title="error">
      <template v-if="!denied" #extra>
        <el-button v-operation="operation" @click="read">{{ t("masterData.retry") }}</el-button>
      </template>
    </el-result>
    <template v-else>
      <el-table v-loading="busy" :data="data.rows">
        <el-table-column prop="version" :label="t('productV1.version')" width="90" />
        <el-table-column :label="t('productV1.before')" min-width="110">
          <template #default="{ row }">{{ t(row.beforeEnabled ? "masterData.enabled" : "masterData.disabled") }}</template>
        </el-table-column>
        <el-table-column :label="t('productV1.after')" min-width="110">
          <template #default="{ row }">{{ t(row.afterEnabled ? "masterData.enabled" : "masterData.disabled") }}</template>
        </el-table-column>
        <el-table-column :label="t('recordAudit.createdBy')" min-width="160">
          <template #default="{ row }">{{ row.createdByName || row.createdBy || t("recordAudit.unknownActor") }}</template>
        </el-table-column>
        <el-table-column :label="t('recordAudit.createdAt')" min-width="180">
          <template #default="{ row }">{{ time(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current-page="page" :page-size="size" :total="data.total" :disabled="busy" @current-change="read" />
    </template>
  </ManagedDialog>
</template>
