<script setup lang="ts">
import { ElButton, ElProgress, ElUpload } from "element-plus";
import type { UploadFile, UploadInstance } from "element-plus";
import { AppIcon } from "@appnexus/icons";
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
  mediaType?: string;
  status?: string;
}

interface UploadRule {
  extensions: string[];
  /** 单个文件的最大字节数，由当前生效策略提供。 */
  maxBytes: number;
}

interface QueueItem {
  key: string;
  name: string;
  size: number;
  file?: File;
  progress: number;
  status: "queued" | "uploading" | "failed" | "invalid" | "uploaded";
}

const props = defineProps<{
  /** 当前租户内的业务关联键，由调用方提供，空值时不发起请求。 */
  businessId: string;
  /** 可选的有效上传策略。提供时先加载策略，再开放文件选择。 */
  rules?: () => Promise<UploadRule[]>;
  /** 只读时隐藏所有上传交互。 */
  readonly?: boolean;
  /** 调用方从统一操作准入得到的列表能力，明确为 false 时不查询。 */
  canList?: boolean;
  /** 调用方从统一操作准入得到的上传能力，明确为 false 时不上传。 */
  canUpload?: boolean;
  /** 在宽屏并排展示已授权的附件列表和上传区，默认上下排列。 */
  splitLayout?: boolean;
  /** 显示调用方提供的操作列，缺省隐藏。授权由业务接线层执行。 */
  showOperations?: boolean;
  /** 按关联键查询当前页，偏移量和上限均以记录数计。 */
  list: (businessId: string, signal: AbortSignal, offset: number, limit: number) => Promise<{ rows: AttachmentItem[]; total: number }>;
  /** 上传单个文件，进度为 0–100 的百分比，同一队列项重试沿用原幂等键。 */
  upload: (businessId: string, file: File, idempotencyKey: string, progress: (percent: number) => void) => Promise<AttachmentItem>;
}>();
const emit = defineEmits<{
  uploaded: [attachment: AttachmentItem];
  busy: [uploading: boolean];
}>();
defineSlots<{ context?: () => unknown; actions?: () => unknown; operations?: (scope: { row: AttachmentItem }) => unknown }>();
const chinese = {
  upload: "上传附件", attachments: "附件", name: "文件名称", size: "大小", time: "上传时间", operations: "操作",
  listFailed: "加载附件失败", rulesFailed: "加载上传规则失败", loadingRules: "正在加载上传规则",
  noRules: "当前没有启用的上传格式", retry: "重试", retryFailed: "重试失败项", empty: "暂无附件",
  choose: "点击选择文件，或拖拽到此处", target: "关联业务", queue: "上传队列",
  enterBusiness: "填写业务 ID 后即可选择或拖入文件", enterBusinessToList: "填写业务 ID 后显示关联附件",
  limit: "{extensions}（单个文件最多 {size}）", remove: "从队列移除 {file}",
  queued: "等待上传", uploading: "正在上传", failed: "上传失败",
  invalid: "文件类型或大小不符合上传规则", uploaded: "已上传"
};
const { t, n, d } = useI18n({
  messages: {
    "zh-CN": chinese,
    zh: chinese,
    en: {
      upload: "Upload attachments", attachments: "Attachments", name: "File name", size: "Size", time: "Uploaded at", operations: "Actions",
      listFailed: "Could not load attachments", rulesFailed: "Could not load upload rules", loadingRules: "Loading upload rules",
      noRules: "No upload formats are enabled", retry: "Retry", retryFailed: "Retry failed files", empty: "No attachments",
      choose: "Click to select files or drop them here", target: "Business record", queue: "Upload queue",
      enterBusiness: "Enter a business ID to select or drop files", enterBusinessToList: "Enter a business ID to see related attachments",
      limit: "{extensions} (up to {size} per file)", remove: "Remove {file} from queue",
      queued: "Waiting to upload", uploading: "Uploading", failed: "Upload failed",
      invalid: "File type or size does not meet the upload rules", uploaded: "Uploaded"
    }
  }
});

const columns = computed<ManagementColumn[]>(() => [
  { key: "fileName", label: t("name"), prop: "fileName", minWidth: 160 },
  { key: "sizeBytes", label: t("size"), minWidth: 80 },
  { key: "createdAt", label: t("time"), minWidth: 110 },
  ...(props.showOperations ? [{ key: "operations", label: t("operations"), width: 132 }] : [])
]);
const rows = ref<AttachmentItem[]>([]);
const page = ref(1);
const total = ref(0);
const pageSize = 20;
const rules = ref<UploadRule[]>([]);
const rulesState = ref<"loading" | "ready" | "failed">("loading");
const rulesRevision = ref(0);
const accept = computed(() => rules.value.flatMap(rule => rule.extensions.map(ext => "." + ext)).join(","));
const ruleGroups = computed(() => {
  const groups = new Map<number, string[]>();
  for (const rule of rules.value) {
    const formats = groups.get(rule.maxBytes) ?? [];
    groups.set(rule.maxBytes, [...formats, ...rule.extensions]);
  }
  return [...groups].map(([maxBytes, formats]) => ({
    maxBytes,
    formats: [...new Set(formats)].map(ext => ext.toUpperCase()).join(" / ")
  }));
});
const canUpload = computed(() => !props.readonly && props.canUpload !== false);
const ready = computed(() => canUpload.value && !!props.businessId.trim() &&
  (!props.rules || (rulesState.value === "ready" && !!accept.value)));
const queue = ref<QueueItem[]>([]);
const picker = ref<UploadInstance>();
const busy = computed(() => queue.value.some(item => item.status === "queued" || item.status === "uploading"));
const hasFailures = computed(() => queue.value.some(item => item.status === "failed"));
const loading = ref(false);
const error = ref("");
let request: AbortController | undefined;
let generation = 0;

function fileSize(bytes: number) {
  const unit = bytes >= 1024 ** 2 ? 2 : bytes >= 1024 ? 1 : 0;
  return n(bytes / 1024 ** unit, { maximumFractionDigits: 1 }) + " " + ["B", "KiB", "MiB"][unit];
}

/** 按扩展名选择展示图标，文件校验仍使用租户策略和服务端规则。 */
function fileIcon(name: string) {
  const extension = name.split(".").at(-1)?.toLowerCase();
  if (extension === "pdf") return "pdfFile";
  if (extension === "xlsx" || extension === "xls" || extension === "csv") return "spreadsheetFile";
  if (extension && ["jpg", "jpeg", "png", "webp", "gif"].includes(extension)) return "imageFile";
  return "document";
}

async function refresh() {
  request?.abort();
  const businessId = props.businessId;
  if (!businessId.trim() || props.canList === false) {
    loading.value = false;
    return;
  }
  const controller = new AbortController();
  request = controller;
  error.value = "";
  loading.value = true;
  try {
    const result = await props.list(businessId, controller.signal, (page.value - 1) * pageSize, pageSize);
    if (!controller.signal.aborted) {
      rows.value = result.rows;
      total.value = result.total;
    }
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
  error.value = "";
  void refresh();
}, { immediate: true });

watch([() => props.businessId, canUpload], () => {
  generation++;
  queue.value = [];
}, { flush: "sync" });

watch([canUpload, () => props.rules, rulesRevision], async ([allowed, load], _, onCleanup) => {
  rules.value = [];
  rulesState.value = "loading";
  if (!allowed || !load) return;
  let current = true;
  onCleanup(() => { current = false; });
  try {
    const result = await load();
    if (current) {
      rules.value = result;
      rulesState.value = "ready";
    }
  } catch {
    if (current) rulesState.value = "failed";
  }
}, { immediate: true });

watch(busy, value => emit("busy", value));
onBeforeUnmount(() => {
  generation++;
  request?.abort();
  emit("busy", false);
});

/** 逐项提交，避免批量选择触发并发写入。业务键或授权变化后丢弃旧队列的回调。 */
async function drainQueue() {
  if (queue.value.some(item => item.status === "uploading")) return;
  const current = generation;
  const businessId = props.businessId;
  while (current === generation && ready.value) {
    const item = queue.value.find(entry => entry.status === "queued");
    if (!item?.file) break;
    item.status = "uploading";
    try {
      const attachment = await props.upload(businessId, item.file, item.key, percent => {
        if (current === generation && item.status === "uploading") {
          item.progress = Math.max(item.progress, Math.min(99, Math.round(percent)));
        }
      });
      if (current !== generation) return;
      item.progress = 100;
      item.status = "uploaded";
      item.file = undefined;
      emit("uploaded", attachment);
      page.value = 1;
      void refresh();
    } catch {
      if (current !== generation) return;
      item.status = "failed";
    }
  }
}

/** 从 Element Plus 接收拖拽或选择的原始文件，队列自行管理状态和幂等键。 */
function selectFile(selected: UploadFile) {
  picker.value?.clearFiles(["ready"]);
  const file = selected.raw;
  if (!ready.value || !file) return;
  const extension = file.name.includes(".") ? file.name.split(".").at(-1)?.toLowerCase() : undefined;
  const valid = file.size > 0 && (!props.rules || rules.value.some(rule =>
    extension !== undefined && rule.extensions.includes(extension) && file.size <= rule.maxBytes));
  queue.value.push({
    key: crypto.randomUUID(),
    name: file.name,
    size: file.size,
    file: valid ? file : undefined,
    status: valid ? "queued" : "invalid",
    progress: 0
  });
  void drainQueue();
}

/** 只重试已失败的请求，重用原文件和幂等键，避免响应丢失时创建重复附件。 */
function retry(item?: QueueItem) {
  if (!ready.value || busy.value) return;
  for (const entry of item ? [item] : queue.value) {
    if (entry.status !== "failed") continue;
    entry.progress = 0;
    entry.status = "queued";
  }
  void drainQueue();
}
</script>

<template>
  <section class="attachment-stack"
    :class="{ 'attachment-presentation': splitLayout, 'attachment-split': splitLayout && canList !== false && canUpload }">
    <FloatingError :title="error" />
    <section v-if="canUpload" class="attachment-stack attachment-uploader" :aria-label="t('upload')">
      <header v-if="splitLayout" class="attachment-upload-header">
        <h2>{{ t("upload") }}</h2>
      </header>
      <slot v-if="canList === false" name="context" />
      <div v-if="businessId.trim()" class="attachment-target">
        <span>{{ t("target") }} · {{ businessId }}</span>
      </div>
        <ElUpload
          ref="picker"
          class="attachment-drop"
          drag
          multiple
          :disabled="!ready"
          :accept="accept"
          :auto-upload="false"
          :show-file-list="false"
          :aria-label="t('choose')"
          :on-change="selectFile"
        >
          <AppIcon name="fileUpload" :size="splitLayout ? 52 : 32" class="attachment-upload-icon" />
          <div class="attachment-drop-title">{{ t(businessId.trim() ? "choose" : "enterBusiness") }}</div>
          <ul v-if="ruleGroups.length" class="attachment-rules">
            <li v-for="rule in ruleGroups" :key="rule.maxBytes">
              {{ t("limit", { extensions: rule.formats, size: fileSize(rule.maxBytes) }) }}
            </li>
          </ul>
        </ElUpload>
        <div v-if="rules && (rulesState !== 'ready' || !accept)" role="status">
          <template v-if="rulesState === 'failed'">
            {{ t("rulesFailed") }}
            <ElButton link type="primary" @click="rulesRevision++">{{ t("retry") }}</ElButton>
          </template>
          <template v-else>{{ t(rulesState === "loading" ? "loadingRules" : "noRules") }}</template>
        </div>
      <ul v-if="queue.length" class="attachment-queue" :aria-label="t('queue')">
        <li v-for="item in queue" :key="item.key" class="attachment-queue-row">
          <div class="attachment-file">
            <AppIcon :name="fileIcon(item.name)" :size="20" class="attachment-file-icon" :data-file-kind="fileIcon(item.name)" />
            <div class="attachment-file-text">
              <span :title="item.name">{{ item.name }}</span>
              <small>{{ fileSize(item.size) }}</small>
            </div>
          </div>
          <div class="attachment-progress">
            <ElProgress v-if="item.status === 'uploading'" :percentage="item.progress" :stroke-width="8" :aria-label="t('uploading')" />
            <span v-else class="attachment-state" :class="'attachment-status-' + item.status" role="status">
              <AppIcon v-if="item.status === 'failed' || item.status === 'invalid'" name="information" :size="16" />
              {{ t(item.status) }}
            </span>
          </div>
          <div class="attachment-actions">
            <ElButton v-if="item.status === 'failed'" :disabled="busy || !ready" link type="primary" @click="retry(item)">
              {{ t("retry") }}
            </ElButton>
            <ElButton v-if="item.status !== 'uploading'" :aria-label="t('remove', { file: item.name })"
              text @click="queue = queue.filter(entry => entry.key !== item.key)">
              <AppIcon name="close" :size="16" />
            </ElButton>
          </div>
        </li>
      </ul>
      <div v-if="hasFailures || $slots.actions" class="attachment-actions attachment-footer">
        <slot name="actions" />
        <ElButton :disabled="!hasFailures || busy || !ready"
          type="primary" @click="retry()">{{ t("retryFailed") }}</ElButton>
      </div>
    </section>
    <section v-if="canList !== false" class="attachment-list" :aria-label="t('attachments')">
    <slot name="context" />
    <ElButton v-if="error" link type="primary" @click="refresh">{{ t("retry") }}</ElButton>
    <ManagementTable :title="t('attachments')" :total="total" :rows="rows" :columns="columns"
      :loading="loading" :empty-text="t(businessId.trim() ? 'empty' : 'enterBusinessToList')"
      :excel-export="splitLayout ? false : undefined" :refreshable="!splitLayout"
      :paginate-locally="false" heading-level="h2" row-key="id" embedded readonly-view>
      <template v-if="splitLayout" #fileName="scope">
        <div v-if="scope?.row" class="attachment-file">
          <AppIcon :name="fileIcon(scope.row.fileName)" :size="20" class="attachment-file-icon" :data-file-kind="fileIcon(scope.row.fileName)" />
          <span class="attachment-file-text" :title="scope.row.fileName">{{ scope.row.fileName }}</span>
        </div>
      </template>
      <template #sizeBytes="scope"><template v-if="scope?.row">{{ fileSize(scope.row.sizeBytes) }}</template></template>
      <template #createdAt="scope"><template v-if="scope?.row">{{ d(new Date(scope.row.createdAt)) }}</template></template>
      <template v-if="showOperations" #operations="scope">
        <slot v-if="scope?.row" name="operations" :row="scope.row" />
      </template>
      <template v-if="total > pageSize" #pagination>
        <AppPagination v-model:current-page="page" :page-size="pageSize"
          :total="total" @current-change="refresh" />
      </template>
    </ManagementTable>
    </section>
  </section>
</template>

<style scoped>
.attachment-stack {
  display: grid;
  min-width: 0;
  gap: var(--app-section-gap);
}

.attachment-list {
  min-width: 0;
}

.attachment-split {
  grid-template-columns: minmax(0, 1.17fr) minmax(0, 1fr);
  grid-template-areas: "list upload";
  align-items: stretch;
}

.attachment-split .attachment-list {
  grid-area: list;
}

.attachment-split .attachment-uploader {
  grid-area: upload;
}

.attachment-presentation .attachment-list,
.attachment-presentation .attachment-uploader {
  padding: var(--app-content-padding);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--app-card-radius);
  background: var(--el-bg-color);
}

.attachment-presentation .attachment-uploader {
  display: flex;
  flex-direction: column;
  min-height: clamp(330px, 49vh, 500px);
}

.attachment-upload-header {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.attachment-upload-header h2 {
  margin: 0;
  font-size: var(--el-font-size-extra-large);
  font-weight: 600;
}

.attachment-presentation .attachment-target {
  margin-block: 8px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.attachment-presentation .attachment-drop :deep(.el-upload) {
  width: 100%;
}

.attachment-presentation .attachment-drop :deep(.el-upload-dragger) {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 8px;
  min-height: clamp(174px, 22vh, 232px);
  padding: 16px;
  border-color: var(--el-color-primary-light-5);
  background: color-mix(in srgb, var(--el-color-primary) 4%, var(--el-bg-color));
}

.attachment-presentation .attachment-drop :deep(.el-upload-dragger:hover),
.attachment-presentation .attachment-drop :deep(.el-upload-dragger.is-dragover) {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.attachment-presentation .attachment-drop-title {
  font-size: var(--el-font-size-large);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.attachment-presentation .attachment-footer {
  margin-top: auto;
  padding-block: 12px 4px;
  flex-wrap: wrap;
  row-gap: 8px;
}

@media (max-width: 760px) {
  .attachment-split {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: "list" "upload";
  }
}

.attachment-target {
  padding: var(--app-content-padding);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
}

.attachment-upload-icon {
  margin-bottom: 8px;
  color: var(--el-color-primary);
}

.attachment-rules,
.attachment-queue {
  margin: 0;
  padding: 0;
  list-style: none;
}

.attachment-rules {
  margin-top: 4px;
  color: var(--el-text-color-regular);
  font-size: var(--el-font-size-small);
}

.attachment-queue {
  max-height: 240px;
  overflow: auto;
}

.attachment-presentation .attachment-queue {
  border-top: 1px solid var(--el-border-color-lighter);
}

.attachment-presentation .attachment-queue-row {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(84px, max-content);
  min-height: 60px;
}

.attachment-queue-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 4px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.attachment-file {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.attachment-file-text {
  display: grid;
  min-width: 0;
  overflow: hidden;
}

.attachment-file-text,
.attachment-file-text > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-file-icon {
  box-sizing: content-box;
  padding: 5px;
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}

.attachment-file-icon[data-file-kind="pdfFile"] {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger-dark-2);
}

.attachment-file-icon[data-file-kind="spreadsheetFile"] {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success-dark-2);
}

.attachment-file-icon[data-file-kind="imageFile"] {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.attachment-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.attachment-file small {
  color: var(--el-text-color-regular);
}

.attachment-progress {
  min-width: 0;
  overflow-wrap: anywhere;
}

.attachment-status-invalid,
.attachment-status-failed {
  color: var(--el-color-danger-dark-2);
}

.attachment-status-uploaded {
  color: var(--el-color-success-dark-2);
}

.attachment-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.attachment-footer {
  padding-top: var(--app-content-padding);
  border-top: 1px solid var(--el-border-color-lighter);
}

@media (max-width: 480px) {
  .attachment-queue-row,
  .attachment-presentation .attachment-queue-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .attachment-file {
    grid-column: 1 / -1;
  }
  .attachment-actions :deep(.el-button) {
    min-height: 44px;
    min-width: 44px;
  }
}
</style>
