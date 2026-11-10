<script setup lang="ts">
import PrintManagement from "@appnexus/print/management";
import { printingApi } from "@/api/printing";
import { requestErrorMessage } from "@/utils/http";
</script>
<template><PrintManagement :api="printingApi" :error-text="requestErrorMessage" /></template>
