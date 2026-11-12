<script setup lang="ts">
import PrintManagement from "@appnexus/print/management";
import { printingApi } from "@/api/printing";
import { requestErrorMessage } from "@/utils/http";
import { ref } from "vue";
import { onBeforeRouteLeave, useRoute } from "vue-router";

/** 应用路由守卫复用领域页实际快照确认，不让标签或菜单切换直接丢弃原生设计。 */
const route=useRoute();
const management=ref<InstanceType<typeof PrintManagement>>();
onBeforeRouteLeave(()=>management.value?.confirmLeave()??true);
</script>
<template><PrintManagement ref="management" :initial-output-id="typeof route.query.output==='string'?route.query.output:undefined" :api="printingApi" :error-text="requestErrorMessage" /></template>
