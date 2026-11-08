<script setup lang="ts">
import { ref } from "vue";
import Administration from "@appnexus/tenant/management";
import { administrationApi } from "@/api/administration";
import { enterTenant } from "@/api/tenant";
import { requestErrorMessage } from "@/utils/http";

const error = ref("");

/**
 * 进入租户业务上下文。
 *
 * 平台身份不自动成为租户成员；服务端验证真实成员资格后，应用内定位组织管理页并重建内容区。
 *
 * @param id 平台目录中的目标租户 UUID，仅用于发起服务端成员资格验证。
 */
async function enter(id: string) {
  try {
    error.value = "";
    await enterTenant(id, "/organization/manage");
  } catch (cause) {
    error.value = requestErrorMessage(cause);
  }
}
</script>

<template>
  <div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="mb-4"
    />
    <Administration
      section="tenants"
      :api="administrationApi"
      :error-text="requestErrorMessage"
      @enter="enter"
    />
  </div>
</template>
