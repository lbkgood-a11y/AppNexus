<script setup lang="ts">
import { BusinessWorkspace, FloatingError, ManagementTable, type ManagementColumn, ManagedDialog, RecordAuditDetails, type RecordAudit } from "@appnexus/ui";
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { attachmentsApi, type AttachmentRule } from "@/api/attachments";
import { requestErrorMessage } from "@/utils/http";
import { currentTenant } from "@/utils/tenantContext";

/** 系统配置页面仅查询和更新当前租户的附件上传规则。 */
const { t } = useI18n();
const columns = computed<ManagementColumn[]>(() => [
  { key: "typeKey", label: t("attachments.type"), prop: "typeKey" },
  { key: "extensions", label: t("attachments.extensions") },
  { key: "enabled", label: t("attachments.enabled") },
  { key: "maxBytes", label: t("attachments.maximum"), minWidth: 230, showOverflow: false },
  { key: "hardLimit", label: t("attachments.hardLimit") },
  { key: "operations", label: t("attachments.detail"), width: 175 }
]);
const rows = ref<AttachmentRule[]>([]);
const busy = ref(false);
const error = ref("");
const audit = ref<RecordAudit>();
const showAudit = ref(false);

async function load() {
  busy.value = true;
  error.value = "";
  try { rows.value = await attachmentsApi.policies(); }
  catch (failure) { error.value = requestErrorMessage(failure); }
  finally { busy.value = false; }
}

async function save(typeKey: string) {
  const rule = rows.value.find(row => row.typeKey === typeKey);
  if (!rule) return;
  busy.value = true;
  error.value = "";
  try {
    await attachmentsApi.savePolicy(rule);
    await load();
  } catch (failure) {
    error.value = requestErrorMessage(failure);
  } finally {
    busy.value = false;
  }
}

function inspectAudit(typeKey: string) {
  const selected = rows.value.find(row => row.typeKey === typeKey)?.audit;
  if (!selected) return;
  audit.value = selected;
  showAudit.value = true;
}

// 公共导航完成重建前，撤下上一租户的规则及其审计信息。
watch(currentTenant, (tenant, previous) => {
  if (tenant?.tenantId !== previous?.tenantId || tenant?.memberId !== previous?.memberId) {
    rows.value = [];
    showAudit.value = false;
    audit.value = undefined;
  }
}, { flush: "sync" });
onMounted(load);
</script>

<template>
  <BusinessWorkspace :title="t('attachments.policies')">
    <template #feedback><FloatingError :title="error" /></template>
    <template #data>
      <ManagementTable :title="t('attachments.policies')" :rows="rows" :columns="columns"
        :loading="busy" row-key="typeKey" embedded @refresh="load">
        <template #extensions="scope"><template v-if="scope?.row">{{ scope.row.extensions.join(", ") }}</template></template>
        <template #enabled="scope"><template v-if="scope?.row">
          <el-switch v-operation="'AttachmentUploadPolicy.update'" v-model="scope.row.enabled"
                     :aria-label="t('attachments.enabled')" :disabled="busy" />
          <el-tag effect="plain">{{ scope.row.enabled ? t("attachments.allowed") : t("attachments.blocked") }}</el-tag>
        </template></template>
        <template #maxBytes="scope"><template v-if="scope?.row">
          <el-input-number v-operation="'AttachmentUploadPolicy.update'" v-model="scope.row.maxBytes"
              :min="1" :max="scope.row.hardLimitBytes" :precision="0" :disabled="busy"
              :aria-label="t('attachments.maximum')" controls-position="right" />
          <span>{{ scope.row.maxBytes?.toLocaleString() ?? "—" }}</span>
        </template></template>
        <template #hardLimit="scope"><template v-if="scope?.row">{{ scope.row.hardLimitBytes.toLocaleString() }}</template></template>
        <template #operations="scope"><template v-if="scope?.row">
          <el-button v-operation="'AttachmentUploadPolicy.update'" :disabled="busy || !scope.row.maxBytes"
                     link type="primary" @click="save(scope.row.typeKey)">{{ t("attachments.save") }}</el-button>
          <el-button v-operation="'AttachmentUploadPolicy.list'" v-if="scope.row.audit"
                     link @click="inspectAudit(scope.row.typeKey)">{{ t("attachments.detail") }}</el-button>
        </template></template>
      </ManagementTable>
      <ManagedDialog v-model="showAudit" :title="t('attachments.detail')" :width="520">
        <RecordAuditDetails v-if="audit" :audit="audit" dialog />
      </ManagedDialog>
    </template>
  </BusinessWorkspace>
</template>
