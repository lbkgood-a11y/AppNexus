<script setup lang="ts">
import { useRouter } from "vue-router";
import type { AuthorizationRecoveryEntry } from "@appnexus/warehousing";
import CoilTitleWorkspace from "@appnexus/warehousing/coil-title";
import { warehouseTitleApi } from "@/api/warehouseTitle";
import { requestErrorMessage } from "@/utils/http";

const router=useRouter();
/** 用授权列表已验证的库存与返仓关联打开续办，工作区再次读取服务端当前状态。 */
function openRecovery(authorization:string,entry:AuthorizationRecoveryEntry) {
  void router.push({path:"/warehousing/coil-flow",
    query:{authorization,receipt:entry.receiptId,transit:entry.transitId,reissue:"1"}});
}

/** 仓储工作区装配，应用壳属性通过单根页面透传。 */
</script>

<template><CoilTitleWorkspace :api="warehouseTitleApi" :error-text="requestErrorMessage" :open-recovery="openRecovery" /></template>
