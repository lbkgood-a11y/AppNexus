<script setup lang="ts">
import Management from "@appnexus/master-data/personnel";
import { personnelApi } from "@/api/personnel";
import { requestErrorMessage } from "@/utils/http";
/** Person 应用装配；复用统一工作区并注入真实 API。 */
</script>
<template><Management :api="personnelApi" :error-text="requestErrorMessage" /></template>
