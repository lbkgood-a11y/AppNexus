<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import ReturnTransitWorkspace from "@appnexus/warehousing/return-transit";
import { returnTransitApi } from "@/api/returnTransit";
import { requestErrorMessage } from "@/utils/http";

const route=useRoute();
const router=useRouter();
function openOriginal(receipt:string) {
  void router.push({path:"/warehousing/coil-flow",query:{receipt}});
}
function startOutbound(receipt:string,transit:string) {
  void router.push({path:"/warehousing/coil-flow",query:{receipt,transit,reissue:"1"}});
}
function showTransit(transit:string) {
  void router.replace({path:"/warehousing/return-transit",query:{...route.query,transit}});
}

/** 跨仓返仓工作区通过现有安全宿主装配，单根承接应用壳布局属性。 */
</script>
<template><ReturnTransitWorkspace :api="returnTransitApi" :error-text="requestErrorMessage"
  :handover-id="typeof route.query.handover==='string' ? route.query.handover : undefined"
  :transit-id="typeof route.query.transit==='string' ? route.query.transit : undefined"
  :restriction-receipt="typeof route.query.restriction==='string' ? route.query.restriction : undefined"
  :open-original="openOriginal" :start-outbound="startOutbound" :show-transit="showTransit" /></template>
