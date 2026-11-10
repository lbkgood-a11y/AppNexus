<script setup lang="ts">
import WorkflowCenter from "@appnexus/workflow/center";
import { workflowApi } from "@/api/workflow";
import { requestErrorMessage } from "@/utils/http";
</script>
<template>
  <WorkflowCenter :api="workflowApi" :error-text="requestErrorMessage" />
</template>
