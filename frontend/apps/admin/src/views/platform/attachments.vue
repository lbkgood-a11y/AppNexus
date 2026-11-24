<script setup lang="ts">
import { AppPagination, BusinessWorkspace, FloatingError, ManagementTable, type ManagementColumn, ManagedDialog } from "@appnexus/ui";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { attachmentsApi, type AttachmentItem, type AttachmentFilter } from "@/api/attachments";
import AttachmentPanel from "@/components/Attachments/AttachmentPanel.vue";
import { requestErrorMessage } from "@/utils/http";

const { t, n, d } = useI18n();
const business = ref("");
const tenant = ref("");
const fileName = ref("");
const typeKey = ref("");
const createdBy = ref("");
const dates = ref<[Date, Date]>();
const types = ["jpeg", "png", "pdf", "docx", "xlsx", "pptx", "txt", "csv"];
const active = ref<AttachmentFilter>({});
const columns = computed<ManagementColumn[]>(() => [
  { key: "fileName", label: t("attachments.fileName"), prop: "fileName", minWidth: 180 },
  { key: "businessId", label: t("attachments.businessId"), prop: "businessId" },
  { key: "tenantId", label: t("attachments.tenant"), prop: "tenantId" },
  { key: "sizeBytes", label: t("attachments.size") },
  { key: "status", label: t("attachments.status") },
  { key: "createdAt", label: t("attachments.createdAt") },
  { key: "operations", label: t("attachments.detail"), width: 110 }
]);
const rows = ref<AttachmentItem[]>([]);
const businessForUpload = ref("");
const selected = ref<AttachmentItem>();
const details = ref(false);
const page = ref(1);
const total = ref(0);
const busy = ref(false);
const error = ref("");
let request: AbortController | undefined;
const pageSize = 20;

async function load() {
  request?.abort();
  const controller = new AbortController();
  request = controller;
  busy.value = true;
  error.value = "";
  try {
    const result = await attachmentsApi.ledger({
      ...active.value, offset: (page.value - 1) * pageSize, limit: pageSize
    }, controller.signal);
    if (!controller.signal.aborted) {
      rows.value = result.rows;
      total.value = result.total;
    }
  } catch (failure) {
    if (!controller.signal.aborted) {
      rows.value = [];
      total.value = 0;
      error.value = requestErrorMessage(failure);
    }
  } finally {
    if (!controller.signal.aborted) busy.value = false;
  }
}

function filter() {
  const businessId = business.value.trim();
  const interval = dates.value;
  const until = interval ? new Date(interval[1]) : undefined;
  if (until) until.setDate(until.getDate() + 1);
  active.value = {
    businessId: businessId || undefined,
    tenantId: tenant.value.trim() || undefined,
    fileName: fileName.value.trim() || undefined,
    typeKey: typeKey.value || undefined,
    createdBy: createdBy.value.trim() || undefined,
    from: interval?.[0].toISOString(),
    until: until?.toISOString()
  };
  businessForUpload.value = businessId;
  page.value = 1;
  void load();
}

function show(id: string) {
  const item = rows.value.find(row => row.id === id);
  if (!item) return;
  selected.value = item;
  details.value = true;
}

onMounted(load);
onBeforeUnmount(() => request?.abort());
</script>

<template>
  <BusinessWorkspace :title="t('attachments.ledger')">
    <template #feedback><FloatingError :title="error" /></template>
    <template #search>
      <el-form inline @submit.prevent="filter">
        <el-form-item :label="t('attachments.businessId')"><el-input v-model="business" clearable maxlength="128" /></el-form-item>
        <el-form-item :label="t('attachments.tenant')"><el-input v-model="tenant" clearable maxlength="36" /></el-form-item>
        <el-form-item :label="t('attachments.fileName')"><el-input v-model="fileName" clearable maxlength="255" /></el-form-item>
        <el-form-item :label="t('attachments.type')">
          <el-select v-model="typeKey" clearable :placeholder="t('attachments.allTypes')" style="min-width: 9rem">
            <el-option v-for="type in types" :key="type" :label="type.toUpperCase()" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('attachments.createdBy')"><el-input v-model="createdBy" clearable maxlength="36" /></el-form-item>
        <el-form-item :label="t('attachments.createdAt')">
          <el-date-picker v-model="dates" type="daterange" :start-placeholder="t('attachments.startDate')"
            :end-placeholder="t('attachments.endDate')" />
        </el-form-item>
        <el-form-item><el-button v-operation="'ManagedAttachment.ledger'" native-type="submit" type="primary">{{ t("attachments.search") }}</el-button></el-form-item>
      </el-form>
    </template>
    <template #data>
      <div class="business-workspace-stack management-table-stack">
        <ManagementTable :title="t('attachments.ledger')" :rows="rows" :columns="columns"
          :loading="busy" :total="total" row-key="id" embedded @refresh="load">
          <template #sizeBytes="scope"><template v-if="scope?.row">{{ n(scope.row.sizeBytes) }}</template></template>
          <template #status="scope"><template v-if="scope?.row"><el-tag effect="plain">{{ t("attachments.states." + scope.row.status) }}</el-tag></template></template>
          <template #createdAt="scope"><template v-if="scope?.row">{{ d(new Date(scope.row.createdAt)) }}</template></template>
          <template #operations="scope"><template v-if="scope?.row">
            <el-button v-operation="'ManagedAttachment.ledger'" link type="primary" @click="show(scope.row.id)">{{ t("attachments.detail") }}</el-button>
          </template></template>
          <template #pagination>
            <AppPagination v-model:current-page="page" :page-size="pageSize" :total="total" @current-change="load" />
          </template>
        </ManagementTable>
        <AttachmentPanel v-if="businessForUpload && !active.tenantId" :business-id="businessForUpload" @uploaded="load" />
      </div>
      <ManagedDialog v-model="details" :title="t('attachments.detail')" width="min(560px, 95vw)">
        <el-descriptions v-if="selected" :column="1" border>
          <el-descriptions-item :label="t('attachments.fileName')">{{ selected.fileName }}</el-descriptions-item>
          <el-descriptions-item :label="t('attachments.type')">{{ selected.mediaType }}</el-descriptions-item>
          <el-descriptions-item :label="t('attachments.storagePath')">{{ selected.storagePath }}</el-descriptions-item>
          <el-descriptions-item :label="t('attachments.createdBy')">{{ selected.createdBy }}</el-descriptions-item>
          <el-descriptions-item :label="t('attachments.updatedAt')">{{ d(new Date(selected.updatedAt)) }}</el-descriptions-item>
        </el-descriptions>
      </ManagedDialog>
    </template>
  </BusinessWorkspace>
</template>
