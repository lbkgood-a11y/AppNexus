<script setup lang="ts">
import { AppPagination, BusinessWorkspace, FloatingError, ManagementTable, type ManagementColumn, ManagedDialog } from "@appnexus/ui";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { InputInstance } from "element-plus";
import { AppIcon } from "@appnexus/icons";
import { useI18n } from "vue-i18n";
import { attachmentsApi, type AttachmentItem, type AttachmentFilter } from "@/api/attachments";
import { operationGroups } from "@/api/operationGroups";
import AttachmentPanel from "@/components/Attachments/AttachmentPanel.vue";
import { requestErrorMessage } from "@/utils/http";
import { currentTenant } from "@/utils/tenantContext";

const { t, n, d } = useI18n();
const business = ref("");
const fileName = ref("");
const typeKey = ref("");
const createdBy = ref("");
const dates = ref<[Date, Date]>();
const types = ["jpeg", "png", "pdf", "docx", "xlsx", "pptx", "txt", "csv"];
const active = ref<AttachmentFilter>({});
const columns = computed<ManagementColumn[]>(() => [
  { key: "fileName", label: t("attachments.fileName"), prop: "fileName", minWidth: 180 },
  { key: "businessId", label: t("attachments.businessId"), prop: "businessId" },
  { key: "sizeBytes", label: t("attachments.size") },
  { key: "status", label: t("attachments.status") },
  { key: "createdAt", label: t("attachments.createdAt") },
  { key: "operations", label: t("attachments.detail"), width: 155 }
]);
const rows = ref<AttachmentItem[]>([]);
const uploadOpen = ref(false);
const uploadBusy = ref(false);
const uploadBusiness = ref("");
const uploadBusinessInput = ref<InputInstance>();
const suppliedBusinessId = ref("");
const canUpload = ref(false);
const canDownload = ref(false);
const selected = ref<AttachmentItem>();
const page = ref(1);
const total = ref(0);
const busy = ref(false);
const error = ref("");
const downloadError = ref("");
const downloads = ref(new Set<string>());
const previewOpen = ref(false);
const preview = ref<{ url: string; name: string; mediaType: string }>();
const previewLoading = ref<string>();
let previewVersion = 0;
let request: AbortController | undefined;
const pageSize = 20;

let authorityVersion = 0;
/** 台账上传入口按当前租户决策显示，旧租户的在途响应不得恢复入口。 */
async function refreshUploadAuthority() {
  const version = ++authorityVersion;
  const context = currentTenant.value;
  canUpload.value = false;
  canDownload.value = false;
  closePreview();
  try {
    const allowed = context && await operationGroups("tenant", {
      upload: "ManagedAttachment.upload", download: "ManagedAttachment.download"
    });
    if (version === authorityVersion && context === currentTenant.value) {
      canUpload.value = Boolean(allowed && allowed.includes("upload"));
      canDownload.value = Boolean(allowed && allowed.includes("download"));
      if (!canUpload.value && !selected.value) uploadOpen.value = false;
    }
  } catch {
    if (version === authorityVersion && !selected.value) uploadOpen.value = false;
  }
}

/** 冻结本次打开时传入的业务关联键。未提供时由用户在弹窗内填写。 */
function openUpload(businessId?: string, item?: AttachmentItem) {
  selected.value = item;
  suppliedBusinessId.value = businessId?.trim() ?? "";
  uploadBusiness.value = suppliedBusinessId.value;
  uploadOpen.value = true;
}

function closeUpload() {
  if (!uploadBusy.value) uploadOpen.value = false;
}

function uploaded() {
  page.value = 1;
  void load();
}

async function load() {
  request?.abort();
  const controller = new AbortController();
  request = controller;
  busy.value = true;
  error.value = "";
  try {
    const result = await attachmentsApi.ledger({
      ...active.value, offset: (page.value - 1) * pageSize, limit: pageSize
    }, controller.signal);
    if (!controller.signal.aborted) {
      rows.value = result.rows;
      total.value = result.total;
    }
  } catch (failure) {
    if (!controller.signal.aborted) {
      rows.value = [];
      total.value = 0;
      error.value = requestErrorMessage(failure);
    }
  } finally {
    if (!controller.signal.aborted) busy.value = false;
  }
}

function filter() {
  const businessId = business.value.trim();
  const interval = dates.value;
  const until = interval ? new Date(interval[1]) : undefined;
  if (until) until.setDate(until.getDate() + 1);
  active.value = {
    businessId: businessId || undefined,
    fileName: fileName.value.trim() || undefined,
    typeKey: typeKey.value || undefined,
    createdBy: createdBy.value.trim() || undefined,
    from: interval?.[0].toISOString(),
    until: until?.toISOString()
  };
  page.value = 1;
  void load();
}

function show(id: string) {
  const item = rows.value.find(row => row.id === id);
  if (!item) return;
  openUpload(item.businessId, item);
}

/** 按台账记录下载私有原件，同时避免同一行重复请求。 */
async function download(item: Pick<AttachmentItem, "id" | "fileName">) {
  if (downloads.value.has(item.id)) return;
  downloads.value.add(item.id);
  downloadError.value = "";
  try {
    const file = await attachmentsApi.download(item.id);
    const url = URL.createObjectURL(file);
    const link = document.createElement("a");
    link.href = url;
    link.download = item.fileName;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch {
    downloadError.value = t("attachments.downloadFailed");
  } finally {
    downloads.value.delete(item.id);
  }
}

const previewSignatures = new Map<string, number[]>([
  ["application/pdf", [0x25, 0x50, 0x44, 0x46, 0x2d]],
  ["image/jpeg", [0xff, 0xd8, 0xff]],
  ["image/png", [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]]
]);

function closePreview() {
  previewVersion++;
  previewLoading.value = undefined;
  previewOpen.value = false;
  if (preview.value) URL.revokeObjectURL(preview.value.url);
  preview.value = undefined;
}

/** 通过已授权的原件读取接口预览文件。按字节签名和 MIME 白名单限定浏览器可渲染的格式。 */
async function openPreview(item: Pick<AttachmentItem, "id" | "fileName"> & { mediaType?: string }) {
  const mediaType = item.mediaType ?? "";
  const signature = previewSignatures.get(mediaType);
  if (!canDownload.value || !signature || previewLoading.value) return;
  closePreview();
  const version = previewVersion;
  const tenant = currentTenant.value;
  previewLoading.value = item.id;
  downloadError.value = "";
  try {
    const file = await attachmentsApi.download(item.id);
    const header = new Uint8Array(await file.slice(0, signature.length).arrayBuffer());
    if (version !== previewVersion || tenant !== currentTenant.value) return;
    if (!signature.every((byte, index) => header[index] === byte)) throw new Error("Unexpected attachment signature");
    preview.value = { url: URL.createObjectURL(new Blob([file], { type: mediaType })), name: item.fileName, mediaType };
    previewOpen.value = true;
  } catch {
    if (version === previewVersion) downloadError.value = t("attachments.previewFailed");
  } finally {
    if (version === previewVersion) previewLoading.value = undefined;
  }
}

watch(uploadOpen, open => { if (!open) closePreview(); });
watch(currentTenant, (tenant, previous) => {
  uploadOpen.value = false;
  if (tenant?.tenantId !== previous?.tenantId || tenant?.memberId !== previous?.memberId) {
    // 公共导航异步重建期间撤下上一租户的数据和详情。
    request?.abort();
    rows.value = [];
    total.value = 0;
    selected.value = undefined;
  }
  void refreshUploadAuthority();
}, { immediate: true, flush: "sync" });
onMounted(() => {
  void load();
  window.addEventListener("appnexus:authority-changed", refreshUploadAuthority);
});
onBeforeUnmount(() => {
  request?.abort();
  closePreview();
  authorityVersion++;
  window.removeEventListener("appnexus:authority-changed", refreshUploadAuthority);
});
</script>

<template>
  <BusinessWorkspace :title="t('attachments.ledger')">
    <template #feedback><FloatingError :title="downloadError || error" /></template>
    <template #search>
      <el-form inline @submit.prevent="filter">
        <el-form-item :label="t('attachments.businessId')"><el-input v-model="business" clearable maxlength="128" /></el-form-item>
        <el-form-item :label="t('attachments.fileName')"><el-input v-model="fileName" clearable maxlength="255" /></el-form-item>
        <el-form-item :label="t('attachments.type')">
          <el-select v-model="typeKey" clearable :placeholder="t('attachments.allTypes')" style="min-width: 9rem">
            <el-option v-for="type in types" :key="type" :label="type.toUpperCase()" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('attachments.createdBy')"><el-input v-model="createdBy" clearable maxlength="36" /></el-form-item>
        <el-form-item :label="t('attachments.createdAt')">
          <el-date-picker v-model="dates" type="daterange" :start-placeholder="t('attachments.startDate')"
            :end-placeholder="t('attachments.endDate')" />
        </el-form-item>
        <el-form-item><el-button v-operation="'ManagedAttachment.ledger'" native-type="submit" type="primary">{{ t("attachments.search") }}</el-button></el-form-item>
      </el-form>
    </template>
    <template #data>
      <div class="business-workspace-stack management-table-stack">
        <ManagementTable :title="t('attachments.ledger')" :rows="rows" :columns="columns"
          :loading="busy" :total="total" row-key="id" embedded @refresh="load">
          <template #actions>
            <span v-if="canUpload">
              <el-button type="primary" aria-haspopup="dialog" @click="openUpload(business)">
                <AppIcon name="add" :size="16" />{{ t('attachments.upload') }}
              </el-button>
            </span>
          </template>
          <template #sizeBytes="scope"><template v-if="scope?.row">{{ n(scope.row.sizeBytes) }}</template></template>
          <template #status="scope"><template v-if="scope?.row"><el-tag effect="plain">{{ t("attachments.states." + scope.row.status) }}</el-tag></template></template>
          <template #createdAt="scope"><template v-if="scope?.row">{{ d(new Date(scope.row.createdAt)) }}</template></template>
          <template #operations="scope"><template v-if="scope?.row">
            <el-button v-operation="'ManagedAttachment.ledger'" link type="primary" @click="show(scope.row.id)">{{ t("attachments.detail") }}</el-button>
            <el-button v-operation="'ManagedAttachment.download'" v-if="scope.row.status === 'AVAILABLE'"
              link type="primary" :loading="downloads.has(scope.row.id)"
              @click="download(scope.row)">{{ t("attachments.download") }}</el-button>
          </template></template>
          <template #pagination>
            <AppPagination v-model:current-page="page" :page-size="pageSize" :total="total" @current-change="load" />
          </template>
        </ManagementTable>
      </div>
      <ManagedDialog v-model="uploadOpen" :title="t(selected ? 'attachments.detail' : 'attachments.ledger')" width="min(1240px, 95vw)"
        :busy="uploadBusy" :close-on-click-modal="false" @opened="uploadBusinessInput?.focus()">
        <AttachmentPanel v-if="uploadOpen" :business-id="uploadBusiness.trim()" :ledger-scope="Boolean(selected)"
          :show-operations="canDownload" split-layout
          @uploaded="uploaded" @busy="uploadBusy = $event">
          <template v-if="selected || !suppliedBusinessId" #context>
            <section v-if="selected" class="attachment-record-context">
              <div class="attachment-record-heading">
                <AppIcon name="document" :size="24" />
                <h2>{{ selected.fileName }}</h2>
                <el-tag effect="light" class="app-status-tag">{{ t("attachments.states." + selected.status) }}</el-tag>
              </div>
              <dl class="attachment-record-meta">
                <div><dt>{{ t("attachments.businessId") }}</dt><dd>{{ selected.businessId }}</dd></div>
                <div><dt>{{ t("attachments.type") }}</dt><dd>{{ selected.mediaType }}</dd></div>
                <div><dt>{{ t("attachments.updatedAt") }}</dt><dd>{{ d(new Date(selected.updatedAt)) }}</dd></div>
                <div><dt>{{ t("attachments.createdBy") }}</dt><dd>{{ selected.createdBy || "—" }}</dd></div>
              </dl>
            </section>
            <el-form v-else class="attachment-business-context" label-position="top" @submit.prevent>
              <el-form-item :label="t('attachments.businessId')" required>
                <el-input ref="uploadBusinessInput" v-model="uploadBusiness" maxlength="128" :readonly="uploadBusy" />
              </el-form-item>
            </el-form>
          </template>
          <template #operations="{ row }">
            <div v-if="row.status === 'AVAILABLE'" class="attachment-row-actions">
              <el-button v-operation="'ManagedAttachment.download'" v-if="previewSignatures.has(row.mediaType ?? '')"
                link type="primary" :loading="previewLoading === row.id" @click="openPreview(row)">{{ t("attachments.preview") }}</el-button>
              <el-tooltip v-else :content="t('attachments.previewUnsupported')">
                <span tabindex="0"><el-button link disabled>{{ t("attachments.preview") }}</el-button></span>
              </el-tooltip>
              <el-button v-operation="'ManagedAttachment.download'" link type="primary"
                :loading="downloads.has(row.id)" @click="download(row)">{{ t("attachments.download") }}</el-button>
            </div>
            <span v-else>—</span>
          </template>
          <template #actions>
            <el-button :disabled="uploadBusy" @click="closeUpload">{{ t('attachments.closeUpload') }}</el-button>
          </template>
        </AttachmentPanel>
      </ManagedDialog>
      <ManagedDialog v-model="previewOpen" :title="preview?.name ?? t('attachments.preview')" width="min(960px, 94vw)"
        @closed="closePreview">
        <iframe v-if="preview?.mediaType === 'application/pdf'" class="attachment-preview-frame"
          :src="preview.url" :title="preview.name" />
        <img v-else-if="preview" class="attachment-preview-image" :src="preview.url" :alt="preview.name" />
      </ManagedDialog>
    </template>
  </BusinessWorkspace>
</template>

<style scoped>
.attachment-business-context {
  max-width: 320px;
}

.attachment-record-context {
  margin-bottom: var(--app-section-gap);
  padding: var(--app-content-padding);
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow-wrap: anywhere;
}

.attachment-record-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.attachment-record-heading h2 {
  margin: 0;
  font-size: var(--el-font-size-extra-large);
  font-weight: 600;
  min-width: 0;
}

.attachment-record-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin: 12px 0 0;
}

.attachment-record-meta dt {
  font-size: var(--el-font-size-small);
  color: var(--el-text-color-secondary);
}

.attachment-record-meta dd {
  margin: 2px 0 0;
  color: var(--el-text-color-primary);
}

.attachment-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.attachment-row-actions :deep(.el-button + .el-button) {
  margin-inline-start: 0;
}

.attachment-preview-frame {
  width: 100%;
  height: min(70vh, 760px);
  border: 0;
}

.attachment-preview-image {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  margin-inline: auto;
  object-fit: contain;
}
</style>

