<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import InboundDocumentWorkspace from "@appnexus/warehousing/inbound-documents";
import CodingInput from "@appnexus/business-object/coding-input";
import InboundExecution from "./inbound-workbench.vue";
import type { InboundDocumentKind, InboundDocumentsApi } from "@appnexus/warehousing/inbound-documents-types";
import { executeObjectCommand, issueObjectCode, listCodingOptions } from "@/api/objectExecution";
import { http, requestErrorMessage } from "@/utils/http";

/** 应用宿主负责已验证会话、导航参数和统一对象执行客户端。 */
const props = defineProps<{ kind: InboundDocumentKind }>();
const endpoint = () => `/api/bulk-inbound-${props.kind}-orders`;
const objectType = (operation: string) => operation.split(".")[0];
const route = useRoute();
const router = useRouter();
const detailId = computed(() => typeof route.query.document === "string" ? route.query.document : undefined);
const api: InboundDocumentsApi = {
  taskOptions: (path, taskNo) => http.request("get", endpoint() + path, { params: { taskNo } }),
  list: (page, number) => http.request("get", endpoint(), { params: { page, number } }),
  detail: id => http.request("get", `${endpoint()}/${encodeURIComponent(id)}`),
  operations: () => http.request("get", "/api/operation-decisions"),
  execute: (path, payload, key) => executeObjectCommand(path.startsWith("/api/") ? path : endpoint() + path, payload, key)
};

function select(id?: string) {
  void router.push({ query: { ...route.query, document: id } });
}
</script>

<template>
  <InboundDocumentWorkspace :api="api" :kind="kind" :detail-id="detailId" :error-text="requestErrorMessage" @select="select">
    <template #receipt-execution="{ record, reload }">
      <InboundExecution :key="record.taskId" :task-id="record.taskId"
        :stock-granularity="String(record.stockGranularity ?? '')" @changed="reload" />
    </template>
    <template #document-number="{ operation, field, value, disabled, update }">
      <CodingInput :operation="operation" :model-value="value" :disabled="disabled" :max-length="64"
        :options="() => listCodingOptions(objectType(operation), field)"
        :issue="(rule, requestId) => issueObjectCode(objectType(operation), rule, requestId)"
        :error-text="requestErrorMessage" @update:model-value="update" />
    </template>
  </InboundDocumentWorkspace>
</template>
