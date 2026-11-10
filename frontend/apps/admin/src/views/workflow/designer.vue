<script setup lang="ts">
import WorkflowCenter from "@appnexus/workflow/center";
import { workflowApi } from "@/api/workflow";
import { requestErrorMessage } from "@/utils/http";
/** 独立流程设计器菜单，复用公开领域入口；编辑和发布仍由后端逐操作授权。 */
</script>
<template>
  <WorkflowCenter
    workspace="designer"
    :api="workflowApi"
    :error-text="requestErrorMessage"
  />
</template>
