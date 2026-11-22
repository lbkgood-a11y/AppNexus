<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import CoilFlowWorkspace from "@appnexus/warehousing/coil-flow";
import { returnTransitApi } from "@/api/returnTransit";
import { coilFlowApi } from "@/api/coilFlow";
import { requestErrorMessage } from "@/utils/http";

const route=useRoute();
const router=useRouter();
function viewTransit(transit:string) {
  void router.push({path:"/warehousing/return-transit",query:{transit}});
}
/** 为监管处理打开独立页签，履约页保留车次、复磅和待重试的交接命令。 */
function restrictionLink(transit:string,receipt:string) {
  return router.resolve({path:"/warehousing/return-transit",query:{transit,restriction:receipt}}).href;
}
/** 将原授权作为恢复入口保存到当前路由，刷新时仍重新校验授权范围。 */
function saveResume(authorization:string) {
  void router.replace({query:{...route.query,authorization}});
}
function startReturn(handover:string) {
  void router.push({path:"/warehousing/return-transit",query:{handover}});
}

/** 整卷履约页在宿主安全会话下执行真实命令，页面单根承接应用壳。 */
</script>

<template><CoilFlowWorkspace :key="route.fullPath" :api="coilFlowApi" :error-text="requestErrorMessage"
  :initial-receipt="typeof route.query.receipt==='string' ? route.query.receipt : undefined"
  :reissue-transit="route.query.reissue==='1' && typeof route.query.transit==='string' ? route.query.transit : undefined"
  :resume-authorization="typeof route.query.authorization==='string' ? route.query.authorization : undefined"
  :save-resume="saveResume" :load-restrictions="returnTransitApi.restrictions"
  :restriction-link="restrictionLink" :load-transit="returnTransitApi.load" :view-transit="viewTransit" :start-return="startReturn" /></template>
