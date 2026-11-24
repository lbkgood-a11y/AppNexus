<script setup lang="ts">
import { AppPagination, BusinessWorkspace, FloatingError, ManagedDialog, ManagementTable, type ManagementColumn } from "@appnexus/ui";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { eventWorkbenchApi, type EventDetail, type PlatformEvent } from "@/api/eventWorkbench";
import { requestErrorMessage } from "@/utils/http";

const { t } = useI18n();
const auditFields = ["createdAt", "createdBy", "updatedAt", "updatedBy"] as const;
const states = ["PENDING", "PROCESSING", "DONE", "MANUAL"];
const tenant = ref("");
const type = ref("");
const status = ref("");
const dates = ref<[Date, Date]>();
const page = ref(1);
const total = ref(0);
const rows = ref<PlatformEvent[]>([]);
const loading = ref(false);
const detailLoading = ref(false);
const retrying = ref(false);
const error = ref("");
const detailError = ref("");
const visible = ref(false);
const detail = ref<EventDetail>();
let filters: Omit<Parameters<typeof eventWorkbenchApi.list>[0], "page"> = {};
let listRequest: AbortController | undefined;
let detailRequest: AbortController | undefined;

const columns = computed<ManagementColumn[]>(() => [
  {key: "type", label: t("eventWorkbench.type"), prop: "type", minWidth: 240},
  {key: "tenant", label: t("eventWorkbench.tenant"), prop: "tenant", minWidth: 240},
  {key: "createdAt", label: t("eventWorkbench.createdAt"), prop: "createdAt", minWidth: 190},
  {key: "progress", label: t("eventWorkbench.progress"), minWidth: 230},
  {key: "operations", label: t("eventWorkbench.detail"), width: 110}
]);
const deliveryColumns = computed<ManagementColumn[]>(() => [
  {key: "consumer", prop: "consumer", label: t("eventWorkbench.consumer"), minWidth: 180},
  {key: "status", label: t("eventWorkbench.status"), width: 110},
  {key: "attempts", prop: "attempts", label: t("eventWorkbench.attempts"), width: 100},
  {key: "totalAttempts", prop: "totalAttempts", label: t("eventWorkbench.totalAttempts"), width: 100},
  {key: "availableAt", label: t("eventWorkbench.nextAt"), minWidth: 180},
  {key: "failureCode", prop: "failureCode", label: t("eventWorkbench.failure"), minWidth: 175},
  {key: "operations", label: t("eventWorkbench.retry"), width: 110}
]);
const attemptColumns = computed<ManagementColumn[]>(() => [
  {key: "consumer", label: t("eventWorkbench.consumer"), minWidth: 180},
  {key: "attemptNo", prop: "attemptNo", label: t("eventWorkbench.attemptNo"), width: 100},
  {key: "outcome", prop: "outcome", label: t("eventWorkbench.status"), minWidth: 130},
  {key: "failureCode", prop: "failureCode", label: t("eventWorkbench.failure"), minWidth: 150},
  {key: "createdAt", prop: "createdAt", label: t("eventWorkbench.createdAt"), minWidth: 180}
]);
const consumers = computed(() => new Map(detail.value?.deliveries.map(row => [row.id, row.consumer])));

/** 以已提交的条件查询，页码变更不会应用尚未提交的表单内容。 */
async function load() {
  listRequest?.abort();
  const request = listRequest = new AbortController();
  loading.value = true;
  error.value = "";
  try {
    const result = await eventWorkbenchApi.list({...filters, page: page.value}, request.signal);
    if (!request.signal.aborted) {
      rows.value = result.rows;
      total.value = result.total;
    }
  } catch (failure) {
    if (!request.signal.aborted) error.value = requestErrorMessage(failure);
  } finally {
    if (!request.signal.aborted) loading.value = false;
  }
}

function search() {
  const interval = dates.value;
  const until = interval ? new Date(interval[1]) : undefined;
  if (until) until.setDate(until.getDate() + 1);
  filters = {tenant: tenant.value.trim() || undefined, type: type.value.trim() || undefined,
    status: status.value || undefined, from: interval?.[0].toISOString(), until: until?.toISOString()};
  page.value = 1;
  void load();
}

/** 查询选中的事件，关闭详情后忽略迟到响应。 */
async function show(id: string) {
  detailRequest?.abort();
  const request = detailRequest = new AbortController();
  visible.value = true;
  detail.value = undefined;
  detailError.value = "";
  detailLoading.value = true;
  try {
    const result = await eventWorkbenchApi.detail(id, request.signal);
    if (!request.signal.aborted) detail.value = result;
  } catch (failure) {
    if (!request.signal.aborted) detailError.value = requestErrorMessage(failure);
  } finally {
    if (!request.signal.aborted) detailLoading.value = false;
  }
}

async function retry(deliveryId: string) {
  if (!detail.value || retrying.value) return;
  const eventId = detail.value.event.id;
  retrying.value = true;
  try {
    const reason = await ElMessageBox.prompt(t("eventWorkbench.reason"), t("eventWorkbench.retry"), {
      inputValidator: value => {
        const length = (value ?? "").trim().length;
        return length > 0 && length <= 200 || t("eventWorkbench.reasonLength");
      }
    }).then(result => result.value, () => undefined);
    if (reason === undefined) return;
    await eventWorkbenchApi.retry(deliveryId, reason.trim());
    ElMessage.success(t("eventWorkbench.retried"));
    if (visible.value && detail.value?.event.id === eventId) void show(eventId);
    void load();
  } catch (failure) {
    detailError.value = requestErrorMessage(failure);
  } finally {
    retrying.value = false;
  }
}

function close() {
  detailRequest?.abort();
}

onMounted(load);
onBeforeUnmount(() => { listRequest?.abort(); close(); });
</script>

<template>
  <BusinessWorkspace :title="t('eventWorkbench.title')">
    <template #feedback><FloatingError :title="error" /></template>
    <template #search>
      <el-form inline @submit.prevent="search">
        <el-form-item :label="t('eventWorkbench.tenant')"><el-input v-model="tenant" clearable maxlength="36" /></el-form-item>
        <el-form-item :label="t('eventWorkbench.type')"><el-input v-model="type" clearable maxlength="100" /></el-form-item>
        <el-form-item :label="t('eventWorkbench.status')">
          <el-select v-model="status" clearable style="min-width: 9rem">
            <el-option v-for="state in states" :key="state" :value="state" :label="t('eventWorkbench.' + state)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('eventWorkbench.createdAt')"><el-date-picker v-model="dates" type="daterange" /></el-form-item>
        <el-form-item><el-button v-operation="'PlatformTask.listEvents'" native-type="submit" type="primary">{{ t("eventWorkbench.search") }}</el-button></el-form-item>
      </el-form>
    </template>
    <template #data>
      <ManagementTable :title="t('eventWorkbench.title')" :rows="rows" :columns="columns"
        :loading="loading" :total="total" row-key="id" embedded @refresh="load">
        <template #progress="scope"><template v-if="scope?.row">{{ t("eventWorkbench.counts", scope.row) }}</template></template>
        <template #operations="scope"><template v-if="scope?.row">
          <el-button v-operation="'PlatformTask.readEvent'" link type="primary" @click="show(scope.row.id)">{{ t("eventWorkbench.detail") }}</el-button>
        </template></template>
        <template #pagination>
          <AppPagination v-model:current-page="page" :page-size="20" :total="total" @current-change="load" />
        </template>
      </ManagementTable>
    <ManagedDialog v-model="visible" :title="t('eventWorkbench.detail')" width="min(76rem, 95vw)" @closed="close">
      <FloatingError :title="detailError" />
      <div v-loading="detailLoading" class="business-workspace-stack">
        <template v-if="detail">
          <el-descriptions :column="1">
            <el-descriptions-item :label="t('eventWorkbench.eventId')">{{ detail.event.id }}</el-descriptions-item>
            <el-descriptions-item :label="t('eventWorkbench.type')">{{ detail.event.type }} · v{{ detail.event.revision }}</el-descriptions-item>
            <el-descriptions-item :label="t('eventWorkbench.subject')">{{ detail.event.subjectReference }}</el-descriptions-item>
            <el-descriptions-item v-for="field in auditFields" :key="field" :label="t('eventWorkbench.' + field)">
              {{ detail.event[field] ?? "—" }}
            </el-descriptions-item>
          </el-descriptions>
          <ManagementTable :title="t('eventWorkbench.deliveries')" :rows="detail.deliveries" :columns="deliveryColumns"
            row-key="id" heading-level="h2" embedded readonly-view>
            <template #expandContent="scope">
              <el-descriptions v-if="scope?.row" :column="1">
                <el-descriptions-item v-for="field in auditFields" :key="field" :label="t('eventWorkbench.' + field)">
                  {{ scope.row[field] ?? "—" }}
                </el-descriptions-item>
              </el-descriptions>
            </template>
            <template #status="scope">
              <el-tag v-if="scope?.row" :type="scope.row.status === 'MANUAL' ? 'danger' : scope.row.status === 'DONE' ? 'success' : 'info'">
                {{ t("eventWorkbench." + scope.row.status) }}
              </el-tag>
            </template>
            <template #availableAt="scope">
              {{ scope?.row?.status === "PENDING" ? scope.row.availableAt : "—" }}
            </template>
            <template #operations="scope">
              <el-button v-if="scope?.row?.status === 'MANUAL' && !scope.row.failureCode?.startsWith('LEGACY_')"
                v-operation="'PlatformTask.retryDelivery'" link type="primary" :disabled="retrying" @click="retry(scope.row.id)">
                {{ t("eventWorkbench.retry") }}
              </el-button>
            </template>
          </ManagementTable>
          <ManagementTable :title="t('eventWorkbench.history')" :rows="detail.attempts" :columns="attemptColumns"
            heading-level="h2" embedded readonly-view>
            <template #consumer="scope">{{ scope?.row ? consumers.get(scope.row.deliveryId) : "" }}</template>
          </ManagementTable>
        </template>
      </div>
    </ManagedDialog>
    </template>
  </BusinessWorkspace>
</template>
