<script setup lang="ts">
/** 平台功能清单装配；领域组件通过公开入口访问，会话与错误转换由应用层负责。 */
import FeatureCatalog from "@appnexus/configuration/feature-catalog";
import { getFeatureCatalog, getOperationCatalog, getObjectCatalog } from "@/api/menuManagement";
import { requestErrorMessage } from "@/utils/http";
</script>
<template><FeatureCatalog :load="getFeatureCatalog" :load-operations="getOperationCatalog" :load-objects="getObjectCatalog" :error-text="requestErrorMessage" /></template>
