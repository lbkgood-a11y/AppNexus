<script setup lang="ts">
import LeaveRequests from "@appnexus/workflow/leave";
import { leaveApi } from "@/api/leave";
import { workflowApi } from "@/api/workflow";
import { requestErrorMessage } from "@/utils/http";
import {ref} from 'vue';
import PrintPreview from '@appnexus/print/preview';
import {printingApi} from '@/api/printing';
/** 业务原位预览保留业务路由，打印 API 自身验证输出访问及设备权限。 */
const outputId=ref('');
</script>
<template>
  <div>
  <LeaveRequests
    :api="leaveApi"
    :workflow="workflowApi"
    :error-text="requestErrorMessage"
    @print-output="outputId=$event"
  />
  <PrintPreview v-if="outputId" :output-id="outputId" :api="printingApi" :error-text="requestErrorMessage" @close="outputId=''" />
  </div>
</template>
