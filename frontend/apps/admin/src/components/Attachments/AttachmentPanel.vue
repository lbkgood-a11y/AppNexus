<script setup lang="ts">
import { AttachmentUpload } from "@appnexus/ui";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { attachmentsApi, type AttachmentItem } from "@/api/attachments";
import { operationGroups } from "@/api/operationGroups";
import { currentTenant } from "@/utils/tenantContext";

/**
 * 应用接线层。业务调用方传入本租户的关联 ID，当前会话、操作能力和上传 API 由应用提供。
 */
const props = defineProps<{
  /** 租户内稳定业务关联 ID，最多 128 字符。 */
  businessId: string;
  /** 只读时仅允许查询已经获准查看的附件。 */
  readonly?: boolean;
  /** 弹窗内并排展示关联附件和上传区，默认沿用业务页面的上下布局。 */
  splitLayout?: boolean;
  /** 台账详情使用台账读取权限，普通业务入口仍使用本人关联附件权限。 */
  ledgerScope?: boolean;
  /** 显示由台账提供并完成准入检查的行操作。 */
  showOperations?: boolean;
}>();
defineSlots<{
  context?: () => unknown;
  actions?: () => unknown;
  operations?: (scope: { row: Pick<AttachmentItem, "id" | "fileName" | "sizeBytes" | "createdAt"> &
    Partial<Pick<AttachmentItem, "mediaType" | "status">> }) => unknown;
}>();
const emit = defineEmits<{
  uploaded: [item: Pick<AttachmentItem, "id" | "fileName" | "sizeBytes" | "createdAt">];
  busy: [uploading: boolean];
}>();
const canList = ref(false);
const canUpload = ref(false);
let sequence = 0;
const listAttachments = computed(() => props.ledgerScope
  ? (businessId: string, signal: AbortSignal, offset: number, limit: number) =>
    attachmentsApi.ledger({ businessId, offset, limit }, signal)
  : attachmentsApi.list);

async function refreshAuthority() {
  const token = ++sequence;
  const tenant = currentTenant.value;
  canList.value = false;
  canUpload.value = false;
  if (!tenant) return;
  try {
    const allowed = await operationGroups("tenant", {
      list: props.ledgerScope ? "ManagedAttachment.ledger" : "ManagedAttachment.list",
      upload: "ManagedAttachment.upload"
    });
    if (sequence === token && currentTenant.value === tenant) {
      canList.value = allowed.includes("list");
      canUpload.value = allowed.includes("upload");
    }
  } catch {
    // 无法确认权限时收起入口，后端对每个真实操作仍执行统一准入。
  }
}

watch([currentTenant, () => props.ledgerScope], refreshAuthority, { immediate: true });
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
    :list="listAttachments"
    :upload="attachmentsApi.upload"
    :rules="attachmentsApi.rules"
    :can-list="canList"
    :split-layout="splitLayout"
    :show-operations="showOperations && canList"
    :can-upload="canUpload"
    @uploaded="emit('uploaded', $event)"
    @busy="emit('busy', $event)"
  >
    <template v-if="$slots.context && (!ledgerScope || canList)" #context><slot name="context" /></template>
    <template v-if="$slots.actions" #actions><slot name="actions" /></template>
    <template v-if="$slots.operations" #operations="scope"><slot name="operations" v-bind="scope" /></template>
  </AttachmentUpload>
</template>
