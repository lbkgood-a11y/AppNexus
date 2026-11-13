<script setup lang="ts">
import WorkflowCenter from "@appnexus/workflow/center";
import { workflowApi } from "@/api/workflow";
import { requestErrorMessage } from "@/utils/http";
import {useRoute,useRouter} from 'vue-router';
const route=useRoute(),router=useRouter();
/**
 * 独立流程设计器菜单；单一根容器承接应用壳的 main-content 类和标准外边距。
 * 返回按钮与工作区同属该容器，禁止改为多根节点以免 Vue 丢失路由透传样式。
 * 编辑和发布仍由后端逐操作授权。
 */
</script>
<template>
  <div class="workflow-designer-page">
  <el-button v-if="route.query.returnTo==='/documents/workbench'" text @click="router.push('/documents/workbench')">{{ $t('document.returnWorkbench') }}</el-button>
  <WorkflowCenter
    workspace="designer"
    :api="workflowApi"
    :error-text="requestErrorMessage"
  />
  </div>
</template>

<style scoped>
/* 仅定义内部区块间距，页面外边距由应用壳 main-content 统一提供。 */
.workflow-designer-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.workflow-designer-page > .el-button {
  align-self: flex-start;
}
</style>
