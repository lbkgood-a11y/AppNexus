<script setup lang="ts">
import InboundTaskWorkspace, { type InboundTaskWorkspaceApi } from "@appnexus/warehousing/inbound-tasks";
import { http, requestErrorMessage } from "@/utils/http";

/** 将受控台账和原聚合详情装配到租户会话客户端。 */
const api: InboundTaskWorkspaceApi = {
  list: (number, status, afterId) => http.request("get", "/api/bulk-inbound-tasks", { params: { number, status, afterId } }),
  detail: id => http.request("get", `/api/bulk-inbound-workbench/tasks/${encodeURIComponent(id)}`),
  operations: () => http.request("get", "/api/operation-decisions")
};
</script>

<template>
  <InboundTaskWorkspace :api="api" :error-text="requestErrorMessage" />
</template>
