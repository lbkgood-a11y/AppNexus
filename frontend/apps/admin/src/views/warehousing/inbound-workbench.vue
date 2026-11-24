<script setup lang="ts">
import InboundWorkbench from "@appnexus/warehousing/inbound-workbench";
import type { InboundWorkbenchApi, WorkbenchNumberOperation } from "@appnexus/warehousing/inbound-workbench-types";
import CodingInput from "@appnexus/business-object/coding-input";
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { executeObjectCommand, issueObjectCode, listCodingOptions } from "@/api/objectExecution";
import { http, requestErrorMessage } from "@/utils/http";

const queue = "/api/bulk-inbound-workbench/queue";
const task = "/api/bulk-inbound-workbench/tasks";
const receipts = "/api/bulk-inbound-receipt-orders";
const props = defineProps<{ taskId?: string; stockGranularity?: string }>();
const emit = defineEmits<{ changed: [] }>();
const router = useRouter();
const { locale } = useI18n();
const opening = ref(false);
const openError = ref("");

/** 工作台按选中的作业开草稿，后端以作业锁保证重复打开返回同一入库单。 */
async function openReceipt(detail: Awaited<ReturnType<InboundWorkbenchApi["detail"]>>) {
  if (opening.value) return;
  opening.value = true;
  openError.value = "";
  try {
    const id = detail.receipt.id ?? (await executeObjectCommand<{ receiptId: string }>(
      receipts + ":open-draft", { taskId: detail.taskId, taskVersion: detail.taskVersion })).receiptId;
    await router.push({ path: "/warehousing/inbound-receipt-orders", query: { document: id } });
  } catch (cause) {
    openError.value = requestErrorMessage(cause);
  } finally {
    opening.value = false;
  }
}
const numberFields: Record<WorkbenchNumberOperation, { object: string; field: string }> = {
  "BulkInboundQualityOrder.register_sample": { object: "BulkInboundQualityOrder", field: "qualityNo" },
  "BulkInboundMeteringOrder.register_external": { object: "BulkInboundMeteringOrder", field: "meteringNo" },
  "BulkInboundMeteringOrder.record_gross": { object: "BulkInboundMeteringOrder", field: "grossMeteringNo" }
};

/** 应用宿主通过现有会话和 CSRF 客户端组装队列、详情与独立单据命令。 */
const api: InboundWorkbenchApi = {
  executeDocument: (kind, path, input) => executeObjectCommand(`/api/bulk-inbound-${kind}-orders${path}`,input),
  queue: (stage, afterId, unopened) => http.request("get", queue, { params: { stage, afterId, unopened } }),
  detail: id => http.request("get", `${task}/${encodeURIComponent(id)}`),
  operations: () => http.request("get", "/api/operation-decisions"),
  sample: data => executeObjectCommand("/api/bulk-inbound-quality-orders:register-sample",data),
  external: data => executeObjectCommand("/api/bulk-inbound-metering-orders:register-external",data),
  gross: data => executeObjectCommand("/api/bulk-inbound-metering-orders:record-gross",data),
  tare: data => executeObjectCommand("/api/bulk-inbound-metering-orders:record-tare",data),
  unload: (id, data) => executeObjectCommand(`/api/bulk-inbound-tasks/${encodeURIComponent(id)}:confirm-unloading`,data),
};
</script>

<template>
  <InboundWorkbench :api="api" :task-id="props.taskId" :stock-granularity="props.stockGranularity"
    :error-text="requestErrorMessage" @changed="emit('changed')">
    <template #open-receipt="{ detail, can }">
      <el-alert v-if="openError" :title="openError" type="error" :closable="false" show-icon />
      <el-button v-if="can('BulkWarehouseReceiptOrder.read_document') && (detail.receipt.id ||
        (can('BulkWarehouseReceiptOrder.open_draft') && ['ARRIVED','WORKING','READY_TO_RECEIVE'].includes(detail.status)))"
        type="primary" :loading="opening" @click="openReceipt(detail)">
        {{ locale.startsWith("zh") ? (detail.receipt.id ? "进入入库单" : "生成入库单草稿") :
          (detail.receipt.id ? "Open receipt" : "Create receipt draft") }}
      </el-button>
    </template>
    <template #document-number="{ operation, value, disabled, update }">
      <CodingInput :model-value="value" :operation="operation" :disabled="disabled" :max-length="64"
        :options="() => listCodingOptions(numberFields[operation].object, numberFields[operation].field)"
        :issue="(key, requestId) => issueObjectCode(numberFields[operation].object, key, requestId)"
        :error-text="requestErrorMessage" @update:model-value="update" />
    </template>
  </InboundWorkbench>
</template>
