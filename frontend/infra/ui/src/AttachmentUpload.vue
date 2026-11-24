<script setup lang="ts">
import { ElButton, ElUpload } from "element-plus";
import type { UploadRequestOptions } from "element-plus";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import ManagementTable from "./ManagementTable.vue";
import type { ManagementColumn } from "./types";
import AppPagination from "./AppPagination.vue";
import FloatingError from "./FloatingError.vue";

interface AttachmentItem {
  id: string;
  fileName: string;
  sizeBytes: number;
  createdAt: string;
}

const props = defineProps<{
  /** 当前租户内的业务关联键，由调用方提供，空值时不发起请求。 */
  businessId: string;
  /** 可选的格式提示，由调用方通过有效策略获得。服务端执行最终校验。 */
  rules?: () => Promise<{ extensions: string[]; maxBytes: number }[]>;
  readonly?: boolean;
  canList?: boolean;
  canUpload?: boolean;
  list: (businessId: string, signal: AbortSignal, offset: number, limit: number) => Promise<{ rows: AttachmentItem[]; total: number }>;
  upload: (businessId: string, file: File, idempotencyKey: string, progress: (percent: number) => void) => Promise<AttachmentItem>;
}>();
const emit = defineEmits<{ uploaded: [attachment: AttachmentItem] }>();
const chinese = { upload: "上传附件", name: "附件名称", size: "大小（字节）", time: "上传时间", listFailed: "加载附件失败", uploadFailed: "上传附件失败", retry: "重试", empty: "暂无附件", invalidFile: "文件类型或大小不符合上传规则" };
const { t, n, d } = useI18n({
  messages: {
    "zh-CN": chinese,
    zh: chinese,
    en: { upload: "Upload attachment", name: "File name", size: "Size (bytes)", time: "Uploaded at", listFailed: "Could not load attachments", uploadFailed: "Could not upload the attachment", retry: "Retry", empty: "No attachments", invalidFile: "File type or size does not meet the upload rules" }
  }
});

const columns = computed<ManagementColumn[]>(() => [
  { key: "fileName", label: t("name"), prop: "fileName" },
  { key: "sizeBytes", label: t("size") },
  { key: "createdAt", label: t("time") }
]);
const rows = ref<AttachmentItem[]>([]);
const page = ref(1);
const total = ref(0);
const pageSize = 20;
const rules = ref<{ extensions: string[]; maxBytes: number }[]>([]);
const accept = computed(() => rules.value.flatMap(rule => rule.extensions.map(ext => "." + ext)).join(","));
const canUpload = computed(() => !props.readonly && props.canUpload !== false);
const loading = ref(false);
const pending = ref(false);
const error = ref("");
const failedUpload = ref<{ businessId: string; file: File; key: string }>();
let request: AbortController | undefined;

async function refresh() {
  request?.abort();
  const businessId = props.businessId;
  if (!businessId.trim() || props.canList === false) {
    loading.value = false;
    return;
  }
  const controller = new AbortController();
  request = controller;
  loading.value = true;
  try {
    const result = await props.list(businessId, controller.signal, (page.value - 1) * pageSize, pageSize);
    if (!controller.signal.aborted) { rows.value = result.rows; total.value = result.total; }
  } catch {
    if (!controller.signal.aborted) error.value = t("listFailed");
  } finally {
    if (!controller.signal.aborted) loading.value = false;
  }
}

watch([() => props.businessId, () => props.canList], () => {
  rows.value = [];
  page.value = 1;
  total.value = 0;
  failedUpload.value = undefined;
  error.value = "";
  void refresh();
}, { immediate: true });
watch(canUpload, async (allowed, _, onCleanup) => {
  rules.value = [];
  if (!allowed || !props.rules) return;
  let current = true;
  onCleanup(() => { current = false; });
  try {
    const result = await props.rules();
    if (current) rules.value = result;
  } catch {
    if (current) error.value = t("listFailed");
  }
}, { immediate: true });
onBeforeUnmount(() => request?.abort());

async function send(businessId: string, file: File, key: string, progress: (percent: number) => void) {
  pending.value = true;
  error.value = "";
  failedUpload.value = undefined;
  try {
    const attachment = await props.upload(businessId, file, key, progress);
    if (props.businessId === businessId) {
      emit("uploaded", attachment);
      page.value = 1;
      await refresh();
    }
    return attachment;
  } catch (failure) {
    if (props.businessId === businessId) {
      failedUpload.value = { businessId, file, key };
      error.value = t("uploadFailed");
    }
    throw failure;
  } finally {
    pending.value = false;
  }
}

function uploadFile(options: UploadRequestOptions) {
  const extension = options.file.name.split(".").at(-1)?.toLowerCase() ?? "";
  const rule = rules.value.find(candidate => candidate.extensions.includes(extension));
  if (!canUpload.value || pending.value || (props.rules && (!rule || options.file.size < 1 || options.file.size > rule.maxBytes))) {
    error.value = t("invalidFile");
    return Promise.reject(new Error(error.value));
  }
  return send(props.businessId, options.file, crypto.randomUUID(),
    percent => options.onProgress(Object.assign(new ProgressEvent("progress"), { percent })));
}

function retry() {
  const failed = failedUpload.value;
  if (failed && !pending.value && canUpload.value) {
    void send(failed.businessId, failed.file, failed.key, () => {}).catch(() => {});
  }
}
</script>

<template>
  <section>
    <FloatingError :title="error" />
    <ElUpload
      v-if="canUpload"
      action="#"
      :accept="accept"
      :disabled="pending || !businessId.trim()"
      :http-request="uploadFile"
    >
      <ElButton type="primary" :disabled="pending || !businessId.trim()">{{ t("upload") }}</ElButton>
    </ElUpload>
    <ElButton v-if="failedUpload && canUpload" :disabled="pending" link @click="retry">{{ t("retry") }}</ElButton>
    <ManagementTable v-if="canList !== false" :title="t('name')" :rows="rows" :columns="columns"
      :loading="loading" :empty-text="t('empty')" row-key="id" embedded readonly-view>
      <template #sizeBytes="scope"><template v-if="scope?.row">{{ n(scope.row.sizeBytes) }}</template></template>
      <template #createdAt="scope"><template v-if="scope?.row">{{ d(new Date(scope.row.createdAt)) }}</template></template>
      <template #pagination>
        <AppPagination v-if="total > pageSize" v-model:current-page="page" :page-size="pageSize"
          :total="total" @current-change="refresh" />
      </template>
    </ManagementTable>
  </section>
</template>
