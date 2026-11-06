<script setup lang="ts">
import Management from "@appnexus/organization/management";
import { organizationApi } from "@/api/organization";
import { requestErrorMessage } from "@/utils/http";
</script>
<template><Management :api="organizationApi" :error-text="requestErrorMessage" /></template>
