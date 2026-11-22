<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { useI18n } from "vue-i18n";
import { BusinessWorkspace, FloatingError } from "@appnexus/ui";
import type { PositionOption } from "@appnexus/warehousing/flow-types";
import { coilFlowApi } from "@/api/coilFlow";
import { inboundBatchesApi, type BatchLine, type InboundPosition } from "@/api/inboundBatches";
import { requestErrorMessage } from "@/utils/http";

const props = defineProps<{
  mode: "entry" | "receipt";
  noticeId: string;
  warehouseId: string;
  entryId?: string;
  lines: BatchLine[];
}>();
const emit = defineEmits<{
  /** 已实际提交的新单据 UUID，由宿主切换至真实查询页。 */
  saved: [id: string];
  cancel: [];
}>();
const { t } = useI18n();
const caption = (key: string) => t("inboundBatch." + key);
const isEntry = props.mode === "entry";
const operation = isEntry ? "BulkInboundReceipt.record_entry" : "BulkInboundReceipt.confirm_document";
const vehicleNumber = ref("");
const carrier = ref("");
const arrivedAt = ref<Date>();
const measurementRef = ref("");
const remark = ref("");
const error = ref("");
const pending = ref(false);
const warehouses = ref<PositionOption[]>([]);
const areas = ref<PositionOption[]>([]);

interface Draft {
  source: BatchLine;
  selected: boolean;
  id: string;
  areaId: string;
  locationId: string;
  locations: PositionOption[];
  positionQuery: number;
  quantity: string;
  rejectionQuantity: string;
  rejectionReason: string;
  evidence: string;
}
const drafts = ref<Draft[]>(props.lines.map(source => ({
  source, selected: true, id: crypto.randomUUID(), areaId: "", locationId: "", locations: [],
  positionQuery: 0, quantity: "", rejectionQuantity: "", rejectionReason: "", evidence: ""
})));
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const amount = /^(?:0|[1-9][0-9]{0,17})(?:\.[0-9]{1,12})?$/;
function positive(value: string) {
  return amount.test(value) && /[1-9]/.test(value);
}
const warehouse = computed(() => warehouses.value.find(item => item.id === props.warehouseId));
function position(row: Draft): InboundPosition | undefined {
  const w = warehouse.value;
  const a = areas.value.find(item => item.id === row.areaId);
  const l = row.locations.find(item => item.id === row.locationId);
  if (w && a && l) return {
    warehouseId: w.id, areaId: a.id, locationId: l.id, warehouseVersion: w.version,
    areaVersion: a.version, locationVersion: l.version
  };
}
const selected = computed(() => drafts.value.filter(row => row.selected));
const canSave = computed(() => selected.value.length > 0 && (isEntry
  ? Boolean(vehicleNumber.value && arrivedAt.value && selected.value.every(row =>
      position(row) && uuid.test(row.evidence) && (!row.quantity || positive(row.quantity))))
  : Boolean(props.entryId && selected.value.every(row => {
      const rejected = row.rejectionQuantity || "0";
      return amount.test(row.quantity) && amount.test(rejected)
        && (positive(row.quantity) || positive(rejected))
        && (!positive(row.quantity) || uuid.test(row.evidence))
        && (!positive(rejected) || row.rejectionReason.length > 0);
    }))));

/** 忽略切换库区后晚到的选项，避免提交错误库位。 */
async function changeArea(row: Draft) {
  row.locationId = "";
  row.locations = [];
  const token = ++row.positionQuery;
  const area = areas.value.find(item => item.id === row.areaId);
  if (!area) return;
  try {
    const found = await coilFlowApi.locations("storage-location", area.code);
    if (token === row.positionQuery) row.locations = found;
  } catch (cause) {
    if (token === row.positionQuery) error.value = requestErrorMessage(cause);
  }
}

onMounted(async () => {
  if (!isEntry) return;
  try {
    warehouses.value = await coilFlowApi.locations("warehouse");
    if (warehouse.value) areas.value = await coilFlowApi.locations("storage-area", warehouse.value.code);
  } catch (cause) { error.value = requestErrorMessage(cause); }
});

let latest: { fingerprint: string; id: string } | undefined;
/** 主从使用同一请求，失败保留草稿和重试 UUID。服务端复核来源及数量。 */
async function save() {
  if (!canSave.value || pending.value) return;
  pending.value = true;
  error.value = "";
  try {
    const payload = isEntry ? {
      noticeId: props.noticeId, vehicleNumber: vehicleNumber.value, carrier: carrier.value || undefined,
      arrivedAt: arrivedAt.value!.toISOString(), remark: remark.value || undefined,
      lines: selected.value.map(row => ({
        id: row.id, noticeLineId: row.source.noticeLineId, position: position(row),
        evidenceId: row.evidence, expectedQuantity: row.quantity || undefined
      }))
    } : {
      entryId: props.entryId, measurementRef: measurementRef.value || undefined, remark: remark.value || undefined,
      lines: selected.value.map(row => ({
        id: row.id, custodyId: row.source.custodyId, quantity: row.quantity,
        rejectedQuantity: row.rejectionQuantity || "0", rejectionReason: row.rejectionReason || undefined,
        measurementId: positive(row.quantity) ? row.evidence : undefined
      }))
    };
    const fingerprint = JSON.stringify(payload);
    if (latest?.fingerprint !== fingerprint) latest = { fingerprint, id: crypto.randomUUID() };
    const data = { id: latest.id, ...payload };
    if (isEntry) await inboundBatchesApi.recordEntry(data); else await inboundBatchesApi.confirmReceipt(data);
    ElMessage.success(t("coilFlow.submitted"));
    emit("saved", latest.id);
  } catch (cause) { error.value = requestErrorMessage(cause); }
  finally { pending.value = false; }
}
</script>

<template>
  <div class="management-page">
    <BusinessWorkspace :title="caption(isEntry ? 'newEntry' : 'newReceipt')">
      <template #feedback><FloatingError :title="error" /></template>
      <template #data>
        <el-form label-position="top" :disabled="pending" @submit.prevent="save">
          <div class="business-workspace-stack">
            <el-card shadow="never">
              <template #header>{{ caption("header") }}</template>
              <div class="management-filter">
                <el-form-item :label="caption('noticeId')"><el-input :model-value="noticeId" readonly /></el-form-item>
                <template v-if="isEntry">
                  <el-form-item :label="caption('vehicleNumber')" required><el-input v-model.trim="vehicleNumber" maxlength="64" /></el-form-item>
                  <el-form-item :label="caption('carrier')"><el-input v-model.trim="carrier" maxlength="200" /></el-form-item>
                  <el-form-item :label="caption('arrivedAt')" required><el-date-picker v-model="arrivedAt" type="datetime" /></el-form-item>
                </template>
                <template v-else>
                  <el-form-item :label="caption('entryId')"><el-input :model-value="entryId" readonly /></el-form-item>
                  <el-form-item :label="caption('measurementRef')"><el-input v-model.trim="measurementRef" maxlength="64" /></el-form-item>
                </template>
                <el-form-item :label="caption('remark')"><el-input v-model.trim="remark" maxlength="500" /></el-form-item>
              </div>
            </el-card>
            <el-card v-for="row in drafts" :key="row.id" shadow="never">
              <template #header>
                <el-checkbox v-model="row.selected">{{ caption("line") }} {{ row.source.lineNo }}</el-checkbox>
              </template>
              <div v-if="row.selected" class="management-filter">
                <el-form-item :label="caption('identityId')"><el-input :model-value="row.source.identityId" readonly /></el-form-item>
                <el-form-item :label="caption('unitId')"><el-input :model-value="row.source.unitName || t('referenceName.unavailable')" readonly /></el-form-item>
                <template v-if="isEntry">
                  <el-form-item :label="t('coilFlow.area')" required>
                    <el-select v-model="row.areaId" filterable @change="changeArea(row)">
                      <el-option v-for="item in areas" :key="item.id" :label="item.label" :value="item.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item :label="t('coilFlow.location')" required>
                    <el-select v-model="row.locationId" filterable>
                      <el-option v-for="item in row.locations" :key="item.id" :label="item.label" :value="item.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item :label="caption('expectedQuantity')"><el-input v-model.trim="row.quantity" maxlength="31" /></el-form-item>
                </template>
                <template v-else>
                  <el-form-item :label="caption('custodyId')"><el-input :model-value="row.source.custodyId" readonly /></el-form-item>
                  <el-form-item :label="caption('receivedQuantity')" required><el-input v-model.trim="row.quantity" maxlength="31" /></el-form-item>
                  <el-form-item :label="caption('rejectionQuantity')"><el-input v-model.trim="row.rejectionQuantity" maxlength="31" /></el-form-item>
                  <el-form-item :label="caption('rejectionReason')" :required="positive(row.rejectionQuantity)">
                    <el-input v-model.trim="row.rejectionReason" maxlength="500" />
                  </el-form-item>
                </template>
                <el-form-item :label="t(isEntry ? 'coilFlow.fields.custodyEvidence' : 'coilFlow.fields.measurement')"
                  :required="isEntry || positive(row.quantity)">
                  <el-input v-model.trim="row.evidence" maxlength="36" />
                </el-form-item>
              </div>
            </el-card>
            <div class="management-filter-actions">
              <el-button v-operation="operation" type="primary" native-type="submit" :disabled="!canSave" :loading="pending">
                {{ caption(isEntry ? "recordEntry" : "confirmReceipt") }}
              </el-button>
              <el-button :disabled="pending" @click="emit('cancel')">{{ t("masterData.cancel") }}</el-button>
            </div>
          </div>
        </el-form>
      </template>
    </BusinessWorkspace>
  </div>
</template>
