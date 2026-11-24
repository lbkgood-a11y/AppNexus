<script setup lang="ts">
import { AttachmentUpload } from "@appnexus/ui";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { attachmentsApi, type AttachmentItem } from "@/api/attachments";
import { operationGroups } from "@/api/operationGroups";
import { currentTenant } from "@/utils/tenantContext";

/**
 * 应用接线层。业务调用方传入本租户的关联 ID，当前会话、操作能力和上传 API 由应用提供。
 */
defineProps<{
  /** 租户内稳定业务关联 ID，最多 128 字符。 */
  businessId: string;
  /** 只读时仅允许查询已经获准查看的附件。 */
  readonly?: boolean;
}>();
const emit = defineEmits<{ uploaded: [item: Pick<AttachmentItem, "id" | "fileName" | "sizeBytes" | "createdAt">] }>();
const canList = ref(false);
const canUpload = ref(false);
let sequence = 0;

async function refreshAuthority() {
  const token = ++sequence;
  const tenant = currentTenant.value;
  canList.value = false;
  canUpload.value = false;
  if (!tenant) return;
  try {
    const allowed = await operationGroups("tenant", { list: "ManagedAttachment.list", upload: "ManagedAttachment.upload" });
    if (sequence === token && currentTenant.value === tenant) {
      canList.value = allowed.includes("list");
      canUpload.value = allowed.includes("upload");
    }
  } catch {
    // 无法确认权限时收起入口，后端对每个真实操作仍执行统一准入。
  }
}

watch(currentTenant, refreshAuthority, { immediate: true });
onMounted(() => window.addEventListener("appnexus:authority-changed", refreshAuthority));
onBeforeUnmount(() => {
  sequence++;
  window.removeEventListener("appnexus:authority-changed", refreshAuthority);
});
</script>

<template>
  <AttachmentUpload
    v-if="canList || (canUpload && !readonly)"
    :key="currentTenant?.tenantId + ':' + currentTenant?.version"
    :business-id="businessId"
    :readonly="readonly"
    :list="attachmentsApi.list"
    :upload="attachmentsApi.upload"
    :rules="attachmentsApi.rules"
    :can-list="canList"
    :can-upload="canUpload"
    @uploaded="emit('uploaded', $event)"
  />
</template>
