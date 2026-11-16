<script setup lang="ts">
import Management from "@appnexus/master-data/management";
import { masterDataApi } from "@/api/masterData";
import { requestErrorMessage } from "@/utils/http";
/** Warehouse 应用装配；复用统一工作区并注入真实 API。 */
</script>
<template><Management kind="warehouse" :api="masterDataApi" :error-text="requestErrorMessage" /></template>
