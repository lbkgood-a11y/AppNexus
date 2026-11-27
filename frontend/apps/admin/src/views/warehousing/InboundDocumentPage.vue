<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import InboundDocumentWorkspace from "@appnexus/warehousing/inbound-documents";
import CodingInput from "@appnexus/business-object/coding-input";
import InboundExecution from "./inbound-workbench.vue";
import type { InboundDocumentKind, InboundDocumentsApi } from "@appnexus/warehousing/inbound-documents-types";
import { executeObjectCommand, issueObjectCode, listCodingOptions } from "@/api/objectExecution";
import { http, requestErrorMessage } from "@/utils/http";
import { masterDataApi } from "@/api/masterData";
import { inboundEvidenceApi } from "@/api/inboundEvidence";
import AttachmentPanel from "@/components/Attachments/AttachmentPanel.vue";

/** 应用宿主负责已验证会话、导航参数和统一对象执行客户端。 */
const props = defineProps<{ kind: InboundDocumentKind }>();
const endpoint = () => `/api/bulk-inbound-${props.kind}-orders`;
const objectType = (operation: string) => operation.split(".")[0];
const route = useRoute();
const router = useRouter();
const detailId = ref<string>();

/** 工作台仍可携带单据入口。列表的查看与返回在同一工作区切换，保留列表查询和分页。 */
watch(() => route.query.document, id => {
  detailId.value = typeof id === "string" ? id : undefined;
}, { immediate: true });

/** 从工作台直达的单据返回列表时清理定位参数，避免刷新再次打开该单据。 */
function select(id?: string) {
  detailId.value = id;
  if (!id && typeof route.query.document === "string") {
    void router.replace({ query: { ...route.query, document: undefined }, hash: route.hash });
  }
}
const api: InboundDocumentsApi = {
  ...inboundEvidenceApi,
  receiptReferences: async noticeId => {
    const notice = await http.request<{
      header: { warehouseId: string };
      lines: { id: string; productName?: string; identityName?: string; unitId: string; unitName?: string }[];
    }>("get", "/api/bulk-warehouse-documents/INBOUND_NOTICE/" + encodeURIComponent(noticeId));
    const [warehouse, units] = await Promise.all([
      masterDataApi.readWarehouse(notice.header.warehouseId),
      http.request<{ rows: { id: string; code: string; name: string; enabled: boolean }[] }>(
        "get", "/api/master-data/product-unit", { params: { keyword: "KILOGRAM", size: 100 } })
    ]);
    const kilogram = units.rows.find(row => row.code === "KILOGRAM" && row.enabled);
    if (!kilogram || !warehouse.enabled) throw new Error("INBOUND_RECEIPT_REFERENCES_UNAVAILABLE");
    return {
      warehouse: { id: warehouse.id, code: warehouse.code, label: warehouse.name, version: warehouse.version },
      kilogram: { id: kilogram.id, label: kilogram.name },
      lines: notice.lines.map((line, index) => ({
        id: line.id, label: [index + 1, line.productName, line.identityName].filter(Boolean).join(" · "),
        unitId: line.unitId, unitName: line.unitName ?? "—"
      }))
    };
  },
  storageOptions: async (kind, parent, keyword) => {
    const result = await masterDataApi.list(kind === "area" ? "storage-area" : "storage-location",
      { scope: parent, keyword, page: 1, size: 100, enabled: true });
    return result.rows.map(row => ({ id: row.id, code: row.code, label: `${row.code} · ${row.name}`, version: row.version }));
  },
  conversionOptions: async (sourceUnitId, targetUnitId) => {
    const rows = await http.request<{
      id: string; version: number; enabled: boolean; sourceUnitId: string; targetUnitId: string; factor: string;
    }[]>("get", "/api/master-data/product-unit/conversions");
    return rows.filter(row => row.enabled && row.sourceUnitId === sourceUnitId && row.targetUnitId === targetUnitId)
      .map(row => ({ id: row.id, label: `× ${row.factor}`, version: row.version }));
  },
  taskOptions: (path, taskNo) => http.request("get", endpoint() + path, { params: { taskNo } }),
  list: (page, number) => http.request("get", endpoint(), { params: { page, number } }),
  detail: id => http.request("get", `${endpoint()}/${encodeURIComponent(id)}`),
  operations: () => http.request("get", "/api/operation-decisions"),
  execute: (path, payload, key) => executeObjectCommand(path.startsWith("/api/") ? path : endpoint() + path, payload, key)
};

</script>

<template>
  <InboundDocumentWorkspace :api="api" :kind="kind" :detail-id="detailId" :error-text="requestErrorMessage" @select="select">
    <template #inspection-upload="{ taskId, refresh }">
      <AttachmentPanel :business-id="`inbound-task:${taskId}`" @uploaded="refresh" />
    </template>
    <template #receipt-execution="{ record, reload }">
      <InboundExecution :key="record.taskId" :task-id="record.taskId"
        :stock-granularity="String(record.stockGranularity ?? '')"
        :metering-strategy="String(record.meteringStrategy ?? '')"
        :quality-strategy="String(record.qualityStrategy ?? '')" @changed="reload" />
    </template>
    <template #document-number="{ operation, field, value, disabled, update }">
      <CodingInput :operation="operation" :model-value="value" :disabled="disabled" :max-length="64"
        :options="() => listCodingOptions(objectType(operation), field)"
        :issue="(rule, requestId) => issueObjectCode(objectType(operation), rule, requestId)"
        :error-text="requestErrorMessage" @update:model-value="update" />
    </template>
  </InboundDocumentWorkspace>
</template>
