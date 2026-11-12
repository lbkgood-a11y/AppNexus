<script setup lang="ts">
import WorkflowCenter from "@appnexus/workflow/center";
import { workflowApi } from "@/api/workflow";
import { requestErrorMessage } from "@/utils/http";
import {useRoute,useRouter} from 'vue-router';
const route=useRoute(),router=useRouter();
/** 独立流程设计器菜单，复用公开领域入口；编辑和发布仍由后端逐操作授权。 */
</script>
<template>
  <el-button v-if="route.query.returnTo==='/documents/workbench'" text @click="router.push('/documents/workbench')">{{ $t('document.returnWorkbench') }}</el-button>
  <WorkflowCenter
    workspace="designer"
    :api="workflowApi"
    :error-text="requestErrorMessage"
  />
</template>
