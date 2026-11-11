<script setup lang="ts">
import LeaveRequests from "@appnexus/workflow/leave";
import { leaveApi } from "@/api/leave";
import { workflowApi } from "@/api/workflow";
import { requestErrorMessage } from "@/utils/http";
</script>
<template>
  <LeaveRequests
    :api="leaveApi"
    :workflow="workflowApi"
    :error-text="requestErrorMessage"
  />
</template>
