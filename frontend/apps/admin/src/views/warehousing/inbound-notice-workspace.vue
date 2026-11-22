<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import { BusinessWorkspace, FloatingError } from "@appnexus/ui";
import type { PositionOption, ProductOptions } from "@appnexus/warehousing/flow-types";
import { coilFlowApi as api } from "@/api/coilFlow";
import { requestErrorMessage } from "@/utils/http";

interface Position {
  warehouseId: string; areaId: string; locationId: string;
  warehouseVersion: number; areaVersion: number; locationVersion: number;
}
interface ApprovedLine {
  id: string; productId: string; identityKind: string; identityId: string; productVersion: number; unitId: string;
}
interface NoticeAction {
  mode: "custody" | "receive"; noticeId: string; ownerId: string; warehouseId: string; line: ApprovedLine;
  custody?: { id: string; position: Position };
}
const props = defineProps<{ action?: NoticeAction }>();
const emit = defineEmits<{
  /** 业务接口确认成功后返回来源通知，供宿主重新读取。 */
  saved: [noticeId: string];
  cancel: [];
}>();
const { t } = useI18n();
const label = (key: string) => t("inboundNotice." + key);
const mode = props.action?.mode ?? "inbound";
const operation = { inbound: "BulkInboundReceipt.issue", custody: "BulkInboundReceipt.take_custody", receive: "BulkInboundReceipt.receive" }[mode];
const issue = mode === "inbound";
const pending = ref(false);
const error = ref("");
const owners = ref<Awaited<ReturnType<typeof api.owners>>>([]);
const products = ref<Awaited<ReturnType<typeof api.products>>>([]);
const warehouses = ref<PositionOption[]>([]);
const areas = ref<PositionOption[]>([]);
const locations = ref<PositionOption[]>([]);
const ownerId = ref("");
const warehouseId = ref("");
const areaId = ref("");
const locationId = ref("");
const noticeNo = ref("");
const expectedArrivalAt = ref<Date>();
const remark = ref("");
const batch = ref("");
const evidenceId = ref("");
const quantity = ref("");

interface LineDraft {
  key: string; productId: string; itemId: string; product?: ProductOptions; plannedQuantity: string; limit: string; remark: string;
}
const newLine = (): LineDraft => ({
  key: crypto.randomUUID(), productId: "", itemId: "", plannedQuantity: "", limit: "", remark: ""
});
const lines = ref([newLine()]);
let command: { fingerprint: string; id: string } | undefined;
let optionQuery = 0, ownerQuery = 0, locationQuery = 0;
const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
function positiveAmount(value: string) {
  return /^(?:0|[1-9][0-9]{0,17})(?:\.[0-9]{1,12})?$/.test(value) && /[1-9]/.test(value);
}
const position = computed<Position | undefined>(() => {
  const w = warehouses.value.find(row => row.id === props.action?.warehouseId);
  const a = areas.value.find(row => row.id === areaId.value);
  const l = locations.value.find(row => row.id === locationId.value);
  if (w && a && l) return {
    warehouseId: w.id, areaId: a.id, locationId: l.id,
    warehouseVersion: w.version, areaVersion: a.version, locationVersion: l.version
  };
});
const valid = computed(() => {
  if (issue) return Boolean(ownerId.value && warehouseId.value &&
    (!noticeNo.value || (/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(noticeNo.value) && !uuid.test(noticeNo.value.toLowerCase()))) &&
    lines.value.every(line =>
    line.product?.items.some(item => item.id === line.itemId) && positiveAmount(line.limit)
    && (!line.plannedQuantity || positiveAmount(line.plannedQuantity))));
  if (!uuid.test(evidenceId.value)) return false;
  return mode === "custody" ? Boolean(position.value) : Boolean(props.action?.custody && positiveAmount(quantity.value));
});

async function searchProducts(keyword = "") {
  const request = ++optionQuery;
  try {
    const options = await api.products(keyword);
    if (request === optionQuery) {
      const ids = new Set(lines.value.map(line => line.productId));
      products.value = [...options, ...products.value.filter(row => ids.has(row.id) && !options.some(item => item.id === row.id))];
    }
  } catch (cause) { if (request === optionQuery) error.value = requestErrorMessage(cause); }
}
async function searchOwners(keyword = "") {
  const request = ++ownerQuery;
  try {
    const options = await api.owners(keyword);
    if (request === ownerQuery) {
      owners.value = [...options, ...owners.value.filter(row => row.id === ownerId.value && !options.some(item => item.id === row.id))];
    }
  } catch (cause) { if (request === ownerQuery) error.value = requestErrorMessage(cause); }
}

/** 丢弃换商品过程中晚到的结果；每行保存自己的品规和库存单位。 */
async function changeProduct(line: LineDraft) {
  line.product = undefined;
  line.itemId = "";
  const id = line.productId;
  if (!id) return;
  try {
    const detail = await api.product(id);
    if (line.productId === id) line.product = detail;
  } catch (cause) { if (line.productId === id) error.value = requestErrorMessage(cause); }
}

async function changeArea() {
  const current = ++locationQuery;
  locationId.value = "";
  locations.value = [];
  const selected = areas.value.find(row => row.id === areaId.value);
  if (!selected) return;
  try {
    const options = await api.locations("storage-location", selected.code);
    if (current === locationQuery) locations.value = options;
  } catch (cause) { if (current === locationQuery) error.value = requestErrorMessage(cause); }
}

onMounted(async () => {
  try {
    if (issue) {
      await Promise.all([searchOwners(), searchProducts(), api.locations("warehouse").then(rows => { warehouses.value = rows; })]);
    } else if (mode === "custody") {
      warehouses.value = await api.locations("warehouse");
      const warehouse = warehouses.value.find(row => row.id === props.action?.warehouseId);
      if (warehouse) areas.value = await api.locations("storage-area", warehouse.code);
    }
  } catch (cause) { error.value = requestErrorMessage(cause); }
});

/** 请求未确认时沿用原 UUID，修改输入后使用新 UUID。权限与跨行核对始终由原业务端点执行。 */
async function submit() {
  if (!valid.value || pending.value) return;
  pending.value = true;
  error.value = "";
  try {
    const action = props.action;
    const payload = !action ? {
      warehouseId: warehouseId.value, ownerPartnerId: ownerId.value,
      noticeNo: noticeNo.value || undefined, expectedArrivalAt: expectedArrivalAt.value?.toISOString(),
      remark: remark.value || undefined,
      lines: lines.value.map(line => ({
        id: line.key, item: {
          productId: line.productId, identityId: line.itemId,
          kind: line.product!.items.find(item => item.id === line.itemId)!.kind,
          productVersion: line.product!.version
        }, baseUnitId: line.product!.unitId, plannedQuantity: line.plannedQuantity || undefined,
        limit: line.limit, remark: line.remark || undefined
      }))
    } : mode === "custody" ? {
      noticeId: action.noticeId, noticeLineId: action.line.id, position: position.value,
      evidenceId: evidenceId.value, arrivalBatchNo: batch.value || undefined
    } : {
      custodyId: action.custody?.id, measurementId: evidenceId.value, position: action.custody?.position,
      item: { productId: action.line.productId, kind: action.line.identityKind,
        identityId: action.line.identityId, productVersion: action.line.productVersion },
      ownerPartnerId: action.ownerId, sourceUnitId: action.line.unitId,
      quantity: quantity.value, receiptBatchNo: batch.value || undefined
    };
    const fingerprint = JSON.stringify(payload);
    if (command?.fingerprint !== fingerprint) command = { fingerprint, id: crypto.randomUUID() };
    await api.submit(mode, { ...payload, id: command.id });
    ElMessage.success(t("coilFlow.submitted"));
    emit("saved", action?.noticeId ?? command.id);
  } catch (cause) { error.value = requestErrorMessage(cause); }
  finally { pending.value = false; }
}
</script>

<template>
  <div class="management-page">
    <BusinessWorkspace :title="t('coilFlow.steps.' + mode)">
      <template #feedback><FloatingError :title="error" /></template>
      <template #search>
        <el-form label-position="top" :disabled="pending" @submit.prevent="submit">
          <template v-if="issue">
            <div class="management-filter">
              <el-form-item :label="t('coilFlow.owner')" required>
                <el-select v-model="ownerId" filterable remote :remote-method="searchOwners">
                  <el-option v-for="item in owners" :key="item.id" :label="item.label" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('coilFlow.warehouse')" required>
                <el-select v-model="warehouseId" filterable>
                  <el-option v-for="item in warehouses" :key="item.id" :label="item.label" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="label('noticeNo')"><el-input v-model.trim="noticeNo" maxlength="64" /></el-form-item>
              <el-form-item :label="label('expectedArrivalAt')">
                <el-date-picker v-model="expectedArrivalAt" type="datetime" />
              </el-form-item>
              <el-form-item :label="label('remark')"><el-input v-model.trim="remark" maxlength="500" /></el-form-item>
            </div>
            <div class="business-workspace-stack">
              <el-card v-for="(line,index) in lines" :key="line.key" shadow="never">
                <template #header>{{ label('line') }} {{ index + 1 }}</template>
                <div class="management-filter">
                  <el-form-item :label="t('coilFlow.product')" required>
                    <el-select v-model="line.productId" filterable remote :remote-method="searchProducts" @change="changeProduct(line)">
                      <el-option v-for="item in products" :key="item.id" :label="item.label" :value="item.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item :label="t('coilFlow.item')" required>
                    <el-select v-model="line.itemId" filterable>
                      <el-option v-for="item in line.product?.items ?? []" :key="item.id" :label="item.label" :value="item.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item :label="label('plannedQuantity')"><el-input v-model.trim="line.plannedQuantity" maxlength="31" /></el-form-item>
                  <el-form-item :label="label('approvedQuantity')" required><el-input v-model.trim="line.limit" maxlength="31" /></el-form-item>
                  <el-form-item :label="t('coilFlow.unit')"><el-input :model-value="line.product?.unitName" readonly /></el-form-item>
                  <el-form-item :label="label('remark')"><el-input v-model.trim="line.remark" maxlength="500" /></el-form-item>
                  <el-button :disabled="lines.length < 2" @click="lines.splice(index,1)">{{ label('removeLine') }}</el-button>
                </div>
              </el-card>
            </div>
            <div class="management-filter-actions">
              <el-button :disabled="lines.length === 100" @click="lines.push(newLine())">{{ label('addLine') }}</el-button>
            </div>
          </template>
          <div v-else class="management-filter">
            <el-form-item v-if="mode === 'custody'" :label="t('coilFlow.area')" required>
              <el-select v-model="areaId" @change="changeArea">
                <el-option v-for="item in areas" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="mode === 'custody'" :label="t('coilFlow.location')" required>
              <el-select v-model="locationId">
                <el-option v-for="item in locations" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t(mode === 'custody' ? 'coilFlow.fields.custodyEvidence' : 'coilFlow.fields.measurement')" required>
              <el-input v-model.trim="evidenceId" maxlength="36" />
            </el-form-item>
            <el-form-item :label="label(mode === 'custody' ? 'arrivalBatchNo' : 'receiptBatchNo')">
              <el-input v-model.trim="batch" maxlength="64" />
            </el-form-item>
            <el-form-item v-if="mode === 'receive'" :label="t('coilFlow.fields.quantity')" required>
              <el-input v-model.trim="quantity" maxlength="31" />
            </el-form-item>
          </div>
          <div class="management-filter-actions">
            <el-button v-operation="operation" type="primary" native-type="submit" :loading="pending" :disabled="!valid">
              {{ t('coilFlow.steps.' + mode) }}
            </el-button>
            <el-button :disabled="pending" @click="emit('cancel')">{{ t("masterData.cancel") }}</el-button>
          </div>
        </el-form>
      </template>
    </BusinessWorkspace>
  </div>
</template>
