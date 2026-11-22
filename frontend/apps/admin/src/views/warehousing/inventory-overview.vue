<script setup lang="ts">
import InventoryOverview from "@appnexus/warehousing/inventory-overview";
import type { InventoryOverviewApi } from "@appnexus/warehousing";
import { warehouseTitleApi } from "@/api/warehouseTitle";
import { http, requestErrorMessage } from "@/utils/http";

/** 库存查询沿用当前租户会话及现有主档选项，页面属性透传给工作区单根。 */
const api: InventoryOverviewApi = {
  list: (filters, afterId) => http.request("get", "/api/bulk-warehouse-inventory", { params: { ...filters, afterId } }),
  warehouses: warehouseTitleApi.warehouses,
  owners: warehouseTitleApi.owners,
  products: warehouseTitleApi.products
};
</script>

<template><InventoryOverview :api="api" :error-text="requestErrorMessage" /></template>
