<script setup lang="ts">
import StorageManagement from "@appnexus/master-data/storage";
import { masterDataApi } from "@/api/masterData";
import { requestErrorMessage } from "@/utils/http";
/** 库区与库位管理装配；会话、CSRF、租户和操作准入复用应用壳真实契约。 */
</script>
<template><StorageManagement :api="masterDataApi" :error-text="requestErrorMessage" /></template>
