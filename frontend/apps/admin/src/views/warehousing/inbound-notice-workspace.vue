<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import CodingInput from "@appnexus/business-object/coding-input";
import { executeObjectCommand, issueObjectCode, listCodingOptions } from "@/api/objectExecution";
import { FloatingError, InfoHint, ManagedDialog, ManagementTable, PageHeader, RecordAuditDetails, TabbedDetailTemplate, type ManagementColumn, type RecordAudit } from "@appnexus/ui";
import type { PositionOption, ProductOptions } from "@appnexus/warehousing/flow-types";
import { coilFlowApi as api } from "@/api/coilFlow";
import { http, requestErrorMessage } from "@/utils/http";

const props = defineProps<{ id?: string; readonly?: boolean }>();
const header = ref<Record<string, unknown>>({});
const version = ref(0);
const audit = ref<RecordAudit>({createdBy:null,createdAt:null,updatedBy:null,updatedAt:null});
const loaded = ref(!props.id);
const operation = computed(() => props.id ? "BulkInboundReceipt.update" : "BulkInboundReceipt.issue");
const emit = defineEmits<{
  /** 业务接口确认成功后返回来源通知，供宿主重新读取。 */
  saved: [noticeId: string];
  cancel: [];
}>();
const { t, locale } = useI18n();
const label = (key: string) => t("inboundNotice." + key);
const pending = ref(false);
const error = ref("");
const owners = ref<Awaited<ReturnType<typeof api.owners>>>([]);
const products = ref<Awaited<ReturnType<typeof api.products>>>([]);
const warehouses = ref<PositionOption[]>([]);
const ownerId = ref("");
const warehouseId = ref("");
const noticeNo = ref("");
const expectedArrivalAt = ref<Date>();
type TaskTemplate = { id: string; code: string; version: number };
type VehicleIdentity = "PLATED" | "UNPLATED" | "OTHER";
type TaskContext = {
  creatable: boolean; template?: TaskTemplate | null; templates: TaskTemplate[];
  tasks: { id: string; number: string; status: string; stage?: string }[];
  identities: VehicleIdentity[];
};
const taskOperation = "BulkInboundTask.create_from_notice";
const taskDialog = ref(false);
const taskContext = ref<TaskContext>();
const taskTemplateId = ref("");
const taskIdentity = ref<VehicleIdentity>("PLATED");
const taskIdentifier = ref("");
const taskPending = ref(false);
const taskError = ref("");
const createdTask = ref("");
let taskAttempt: { fingerprint: string; id: string } | undefined;
const text = (zh: string, en: string) => locale.value.startsWith("en") ? en : zh;
const taskPath = () => "/api/bulk-inbound-notices/" + encodeURIComponent(props.id ?? "") + "/tasks";
const templateLabel = (item: TaskTemplate) => `${item.code} · v${item.version}`;
const canSaveTask = computed(() => Boolean(taskContext.value?.creatable &&
  (taskContext.value.template || taskTemplateId.value) &&
  (taskIdentity.value === "OTHER" || taskIdentifier.value.trim())));

/** 从当前详情读取授权登记上下文，模板选项和版本由服务端控制。 */
async function loadTasks() {
  taskContext.value = await http.request<TaskContext>("get", taskPath());
  if (!taskContext.value.identities.includes(taskIdentity.value)) taskIdentity.value = taskContext.value.identities[0] ?? "OTHER";
}

async function openTaskDialog() {
  taskDialog.value = true;
  taskContext.value = undefined;
  createdTask.value = "";
  taskTemplateId.value = "";
  taskIdentifier.value = "";
  taskIdentity.value = "PLATED";
  taskAttempt = undefined;
  taskError.value = "";
  taskPending.value = true;
  try { await loadTasks(); }
  catch (cause) { taskError.value = requestErrorMessage(cause); }
  finally { taskPending.value = false; }
}

/** 同一请求重试使用同一作业主键，成功后更新通知内的车次及模板。 */
async function saveTask() {
  if (!canSaveTask.value || taskPending.value) return;
  const input = {
    identity: taskIdentity.value,
    identifier: taskIdentity.value === "OTHER" ? null : taskIdentifier.value.trim(),
    templateId: taskContext.value?.template ? undefined : taskTemplateId.value
  };
  const fingerprint = JSON.stringify(input);
  if (taskAttempt?.fingerprint !== fingerprint) taskAttempt = { fingerprint, id: crypto.randomUUID() };
  taskError.value = "";
  taskPending.value = true;
  try {
    const result = await executeObjectCommand<{ number: string }>(taskPath(),
      { ...input, requestId: taskAttempt.id }, taskAttempt.id);
    createdTask.value = result.number;
    taskAttempt = undefined;
    taskIdentifier.value = "";
    taskIdentity.value = "PLATED";
    await loadTasks();
  } catch (cause) {
    const code = (cause as { response?: { data?: { code?: string } } }).response?.data?.code;
    taskError.value = code === "INBOUND_TASK_TEMPLATE_CHANGED"
      ? text("通知模板已变化，请刷新后重试", "The notice template changed. Refresh and try again.")
      : requestErrorMessage(cause);
  } finally { taskPending.value = false; }
}
const appointmentNo = ref("");
const plannedConveyance = ref("");
const remark = ref("");
const driverName = ref("");
const driverPhone = ref("");
const driverIdNumber = ref("");
const driverRestrictions = ref<Record<string,string>>({});

interface LineDraft {
  key: string; productId: string; specificationId: string; itemId: string; unitId: string; product?: ProductOptions; productName?: string; itemName?: string; unitName?: string; receivedQuantity?: unknown; audit?: RecordAudit; plannedQuantity: string; limit: string; remark: string; contractNo: string;
}
const newLine = (): LineDraft => ({
  key: crypto.randomUUID(), productId: "", specificationId: "", itemId: "", unitId: "", plannedQuantity: "", limit: "", remark: "", contractNo: ""
});
const lines = ref<LineDraft[]>([]);
const lineDialog = ref(false);
const lineDraft = ref<LineDraft>();
const lineError = ref("");
const lineColumns = computed<ManagementColumn[]>(() => [
  { key: "product", prop: "product", label: t("coilFlow.product") },
  { key: "item", prop: "item", label: t("coilFlow.item") },
  { key: "unit", prop: "unit", label: t("coilFlow.unit"), minWidth: 90 },
  { key: "plannedQuantity", prop: "plannedQuantity", label: label("plannedQuantity"), minWidth: 100 },
  { key: "limit", prop: "limit", label: label("approvedQuantity"), minWidth: 100 },
  { key: "contractNo", prop: "contractNo", label: label("contractNo"), minWidth: 110 },
  { key: "remark", prop: "remark", label: label("remark"), minWidth: 110 },
  { key: "operations", prop: "operations", label: label("operations"), minWidth: 150 }
].filter(column => !props.readonly || column.key !== "operations"));
const lineRows = computed(() => lines.value.map(line => ({
  key: line.key, audit: line.audit, product: line.productName ?? products.value.find(product => product.id === line.productId)?.label,
  item: line.itemName ?? line.product?.items.find(item => item.id === line.itemId)?.label,
  unit: line.unitName ?? availableUnit(line)?.label, plannedQuantity: line.plannedQuantity,
  limit: line.limit, contractNo: line.contractNo, remark: line.remark
})));
let command: { fingerprint: string; id: string } | undefined;
let optionQuery = 0, ownerQuery = 0;
const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
function positiveAmount(value: string) {
  return /^(?:0|[1-9][0-9]{0,17})(?:\.[0-9]{1,12})?$/.test(value) && /[1-9]/.test(value);
}
/** 签发数量只能使用当前商品身份的受控库存单位。 */
function availableUnit(line: LineDraft) {
  const product = line.product;
  const item = product?.items.find(option => option.id === line.itemId);
  if (!product || !item) return;
  return item.kind === "SKU" ? item.inventoryUnit : { id: product.unitId, label: product.unitName };
}

const lineSpecifications = computed(() => lineDraft.value?.product?.items.filter(item => item.kind === "SPECIFICATION") ?? []);
const lineSkus = computed(() => (lineDraft.value?.product?.items ?? []).filter(item =>
  item.kind === "SKU" && item.specificationId === lineDraft.value?.specificationId));

/** 规格变化时清除旧货号和单位；选择货号后按该货号重新带出单位。 */
function selectSpecification(line: LineDraft) {
  line.itemId = line.specificationId;
  line.unitId = availableUnit(line)?.id ?? "";
}

function selectSku(line: LineDraft, id?: string) {
  line.itemId = id || line.specificationId;
  line.unitId = availableUnit(line)?.id ?? "";
}

/** 弹窗和签发共用同一数量及品规校验，空白计划量允许缺省。 */
function validLine(line: LineDraft) {
  return Boolean(line.unitId && availableUnit(line)?.id === line.unitId &&
    positiveAmount(line.limit) && (!line.plannedQuantity || positiveAmount(line.plannedQuantity)));
}

/** 通过明细 UUID 复制草稿，关闭弹窗时丢弃尚未应用的修改。 */
function editLine(key?: string) {
  const existing = key && lines.value.find(line => line.key === key);
  if (pending.value || (key && !existing) || (!key && lines.value.length >= 100)) return;
  lineError.value = "";
  lineDraft.value = existing ? { ...existing } : newLine();
  lineDialog.value = true;
}

function applyLine() {
  const draft = lineDraft.value;
  if (!lineDialog.value || !draft || !validLine(draft)) return;
  const index = lines.value.findIndex(line => line.key === draft.key);
  if (index >= 0) lines.value.splice(index, 1, { ...draft });
  else if (lines.value.length < 100) lines.value.push({ ...draft });
  lineDialog.value = false;
}

async function removeLine(key: string) {
  try {
    await ElMessageBox.confirm(label("removeLineConfirm"), label("lines"), {
      type: "warning", confirmButtonText: label("removeLine"), cancelButtonText: t("masterData.cancel")
    });
  } catch { return; }
  if (!pending.value) lines.value = lines.value.filter(line => line.key !== key);
}

const valid = computed(() => loaded.value && Boolean(ownerId.value && warehouseId.value &&
    (!noticeNo.value || (/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(noticeNo.value) && !uuid.test(noticeNo.value.toLowerCase()))) &&
    !lineDialog.value && lines.value.length > 0 && lines.value.every(validLine)));

async function searchProducts(keyword = "") {
  const request = ++optionQuery;
  try {
    const options = await api.products(keyword);
    if (request === optionQuery) {
      const ids = new Set([...lines.value.map(line => line.productId), lineDraft.value?.productId]);
      products.value = [...options, ...products.value.filter(row => ids.has(row.id) && !options.some(item => item.id === row.id))];
    }
  } catch (cause) { if (request === optionQuery) (lineDialog.value ? lineError : error).value = requestErrorMessage(cause); }
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
  line.specificationId = "";
  line.itemId = "";
  line.unitId = "";
  lineError.value = "";
  const id = line.productId;
  if (!id) return;
  try {
    const detail = await api.product(id);
    if (lineDraft.value === line && line.productId === id) {
      line.product = detail;
      line.itemId = detail.items.find(item => item.kind === "PRODUCT")?.id ?? "";
      line.unitId = availableUnit(line)?.id ?? "";
    }
  } catch (cause) { if (lineDraft.value === line && line.productId === id) lineError.value = requestErrorMessage(cause); }
}
/** 查看与编辑共用字段顺序及分组，查看态仅投影授权响应中的可读值。 */
const readonlyFields=computed(()=>[
  {key:"status",label:t("masterData.status"),value:header.value.status},
  {key:"noticeNo",label:label("noticeNo"),value:noticeNo.value},
  {key:"appointmentNo",label:label("appointmentNo"),value:appointmentNo.value||"—"},
  {key:"ownerId",label:t("coilFlow.owner"),value:header.value.ownerName||"—"},
  {key:"warehouseId",label:t("coilFlow.warehouse"),value:header.value.warehouseName||"—"},
  {key:"plannedConveyance",label:label("plannedConveyance"),value:plannedConveyance.value||"—"},
  {key:"expectedArrivalAt",label:label("expectedArrivalAt"),value:expectedArrivalAt.value?new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"medium"}).format(expectedArrivalAt.value):"—"},
  {key:"driverName",label:label("driverName"),value:driverName.value||"—"},
  {key:"driverPhone",label:label("driverPhone"),value:driverPhone.value||"—"},
  {key:"driverIdNumber",label:label("driverIdNumber"),value:driverIdNumber.value||"—"},
  {key:"issuedAt",label:label("issuedAt"),value:header.value.issuedAt?new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"medium"}).format(new Date(String(header.value.issuedAt))):"—"},
  {key:"flowVersion",label:label("flowVersion"),value:header.value.flowVersion??"—"},
  {key:"remark",label:label("remark"),value:remark.value||"—"}
].filter(field=>driverRestrictions.value[field.key]!=="HIDDEN"));

onMounted(async () => {
  try {
    if(!props.readonly) await Promise.all([searchOwners(), searchProducts(), api.locations("warehouse").then(rows => { warehouses.value = rows; })]);
    if (props.id) {
      const data = await http.request<{header: Record<string, unknown>; lines: Record<string, unknown>[]; driverRestrictions?: Record<string,string>}>(
        "get", "/api/bulk-warehouse-documents/INBOUND_NOTICE/" + encodeURIComponent(props.id));
      const head = data.header;
      header.value = head;
      driverRestrictions.value = data.driverRestrictions ?? {};
      const driver = head.driver as Record<string,string|null> | undefined;
      driverName.value = driver?.driverName ?? "";
      driverPhone.value = driver?.driverPhone ?? "";
      driverIdNumber.value = driver?.driverIdNumber ?? "";
      version.value = Number(head.version);
      audit.value = head.audit as RecordAudit;
      noticeNo.value = String(head.documentNo);
      ownerId.value = String(head.ownerId);
      warehouseId.value = String(head.warehouseId);
      appointmentNo.value = String(head.appointmentNo ?? "");
      plannedConveyance.value = String(head.plannedConveyance ?? "");
      remark.value = String(head.remark ?? "");
      expectedArrivalAt.value = head.expectedArrivalAt ? new Date(String(head.expectedArrivalAt)) : undefined;
      if (!owners.value.some(row => row.id === ownerId.value)) owners.value.push({id:ownerId.value,label:String(head.ownerName ?? ownerId.value)});
      if(props.readonly) {
        lines.value=data.lines.map(row=>({
          key:String(row.id),productId:String(row.productId),specificationId:"",itemId:String(row.identityId),
          unitId:String(row.unitId),productName:String(row.productName ?? "—"),itemName:String(row.identityName ?? "—"),
          unitName:String(row.unitName ?? "—"),plannedQuantity:row.plannedQuantity==null?"":String(row.plannedQuantity),
          limit:String(row.approvedQuantity),remark:String(row.remark ?? ""),contractNo:String(row.contractNo ?? ""),audit:row.audit as RecordAudit
        }));
        loaded.value=true;
        return;
      }
      lines.value = await Promise.all(data.lines.map(async row => {
        const product = await api.product(String(row.productId));
        const item = product.items.find(item => item.id === row.identityId);
        if (!products.value.some(item => item.id === row.productId)) products.value.push({id:String(row.productId),label:String(row.productName ?? row.productId)});
        return {audit:row.audit as RecordAudit,productName:String(row.productName ?? ""),itemName:String(row.identityName ?? ""),unitName:String(row.unitName ?? ""),key:String(row.id),productId:String(row.productId),specificationId:item?.specificationId ?? (item?.kind === "SPECIFICATION" ? item.id : ""),
          itemId:String(row.identityId),unitId:String(row.unitId),product,
          plannedQuantity:row.plannedQuantity == null ? "" : String(row.plannedQuantity),
          limit:String(row.approvedQuantity),remark:String(row.remark ?? ""),contractNo:String(row.contractNo ?? "")};
      }));
      loaded.value = true;
    }
  } catch (cause) { error.value = requestErrorMessage(cause); }
});

/** 请求未确认时沿用原 UUID，修改输入后使用新 UUID。权限与跨行核对始终由原业务端点执行。 */
async function submit() {
  if (props.readonly || !valid.value || pending.value) return;
  pending.value = true;
  error.value = "";
  try {
    const payload = {
      warehouseId: warehouseId.value, ownerPartnerId: ownerId.value,
      noticeNo: noticeNo.value || undefined, appointmentNo: appointmentNo.value || undefined,
      plannedConveyance: plannedConveyance.value || undefined, expectedArrivalAt: expectedArrivalAt.value?.toISOString(),
      remark: remark.value || undefined,
      driver: {
        driverName: driverRestrictions.value.driverName ? undefined : driverName.value || null,
        driverPhone: driverRestrictions.value.driverPhone ? undefined : driverPhone.value || null,
        driverIdNumber: driverRestrictions.value.driverIdNumber ? undefined : driverIdNumber.value || null
      },
      lines: lines.value.map(line => ({
        id: line.key, item: {
          productId: line.productId, identityId: line.itemId,
          kind: line.product!.items.find(item => item.id === line.itemId)!.kind,
          productVersion: line.product!.version
        }, baseUnitId: line.unitId, plannedQuantity: line.plannedQuantity || undefined,
        limit: line.limit, contractNo: line.contractNo || undefined, remark: line.remark || undefined
      }))
    };
    const fingerprint = JSON.stringify(payload);
    if (command?.fingerprint !== fingerprint) command = { fingerprint, id: crypto.randomUUID() };
    if (props.id) {
      const csrf = await http.request<{headerName:string;token:string}>("get","/bff/csrf");
      await http.request("put","/api/bulk-inbound-notices/" + encodeURIComponent(props.id),{
        data:{notice:{...payload,id:props.id},version:version.value},headers:{[csrf.headerName]:csrf.token}
      });
    } else await api.issue({ ...payload, id: command.id });
    ElMessage.success(t("coilFlow.submitted"));
    emit("saved", props.id ?? command.id);
  } catch (cause) { error.value = requestErrorMessage(cause); }
  finally { pending.value = false; }
}
</script>

<template>
  <el-form class="inbound-notice-editor" label-position="top" :disabled="pending || !loaded" @submit.prevent="submit">
    <TabbedDetailTemplate master-data system-last>
      <template #header>
        <PageHeader :title="(props.id ? t('masterData.edit') : t('coilFlow.steps.inbound'))">
          <template #actions>
            <el-button :disabled="pending" @click="emit('cancel')">{{ t("partner.back") }}</el-button>
            <el-button v-if="props.readonly && loaded && props.id" v-operation="taskOperation"
              type="primary" @click="openTaskDialog">{{ text("入库车次", "Inbound arrivals") }}</el-button>
            <el-button v-if="!props.readonly" v-operation="operation" type="primary" :loading="pending" :disabled="!valid" native-type="submit">
              {{ t('masterData.save') }}
            </el-button>
          </template>
        </PageHeader>
      </template>
      <template #content>
        <FloatingError :title="error" />
        <div class="detail-profile-form detail-border-label-form">
          <el-card shadow="never" class="detail-section">
            <template #header>
              <div class="detail-section-heading"><h2>{{ t('partner.basic') }}</h2></div>
            </template>
              <div class="detail-field-grid">
                <template v-if="props.readonly">
                  <div v-for="field in readonlyFields" :key="field.key" class="detail-readonly-field" :class="{'detail-field-wide':field.key==='remark'}">
                    <span>{{ field.label }}</span>
                    <el-tag v-if="field.key==='status'" class="app-status-tag" type="info">{{ t('inboundNotice.states.'+header.status) }}</el-tag>
                    <strong v-else>{{ field.value }}</strong>
                  </div>
                </template>
                <template v-else>
                <div v-if="props.id" class="detail-readonly-field"><span>{{ t('masterData.status') }}</span><el-tag class="app-status-tag" type="info">{{ t('inboundNotice.states.'+header.status) }}</el-tag></div>
                <el-form-item :label="label('noticeNo')">
                  <CodingInput :model-value="noticeNo" operation="BulkInboundReceipt.issue" :max-length="64"
                    :placeholder="label('noticeNoPlaceholder')" :disabled="pending || !!props.id"
                    :options="() => listCodingOptions('BulkInboundReceipt', 'noticeNo')"
                    :issue="(key, requestId) => issueObjectCode('BulkInboundReceipt', key, requestId)"
                    :error-text="requestErrorMessage" @update:model-value="value => noticeNo = value.trim()" />
                </el-form-item>
                <el-form-item :label="label('appointmentNo')"><el-input v-model.trim="appointmentNo" :placeholder="label('appointmentNoPlaceholder')" maxlength="64" /></el-form-item>
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
                <el-form-item :label="label('plannedConveyance')"><el-input v-model.trim="plannedConveyance" :placeholder="label('plannedConveyancePlaceholder')" maxlength="64" /></el-form-item>
                <el-form-item :label="label('expectedArrivalAt')">
                  <el-date-picker v-model="expectedArrivalAt" type="datetime" />
                </el-form-item>
                <template v-for="field in ['driverName','driverPhone','driverIdNumber']" :key="field">
                  <el-form-item v-if="driverRestrictions[field]!=='HIDDEN'" :label="label(field)">
                    <el-input v-if="field==='driverName'" v-model.trim="driverName" maxlength="64" :readonly="!!driverRestrictions[field]" autocomplete="off" />
                    <el-input v-else-if="field==='driverPhone'" v-model.trim="driverPhone" maxlength="32" :readonly="!!driverRestrictions[field]" autocomplete="off" />
                    <el-input v-else v-model.trim="driverIdNumber" maxlength="64" :readonly="!!driverRestrictions[field]" autocomplete="off" />
                  </el-form-item>
                </template>
                <template v-if="props.id">
                  <div v-for="field in readonlyFields.filter(field=>['issuedAt','flowVersion'].includes(field.key))" :key="field.key" class="detail-readonly-field"><span>{{ field.label }}</span><strong>{{ field.value }}</strong></div>
                </template>
                <el-form-item :label="label('remark')" class="detail-field-wide"><el-input v-model.trim="remark" maxlength="500" /></el-form-item>
                </template>
              </div>
          </el-card>
        </div>
      </template>
      <template #system>
        <h2 class="detail-section-title">{{ t('partner.system') }}</h2>
        <RecordAuditDetails :audit="audit" :pending="!props.id" inline />
      </template>
      <template #tabs>
        <el-tabs model-value="lines">
          <el-tab-pane name="lines" :label="label('lines')" />
        </el-tabs>
        <ManagementTable :readonly-view="props.readonly" :title="label('lines')" :rows="lineRows" :columns="lineColumns" :busy="pending" :refreshable="false" heading-level="h2" embedded>
          <template v-if="!props.readonly" #actions>
            <el-button v-operation="operation" type="primary" :disabled="pending || lines.length >= 100" @click="editLine()">
              {{ label('addLine') }}
            </el-button>
          </template>
          <template #operations="{ row }">
            <el-button v-operation="operation" link type="primary" :disabled="pending" @click="editLine(String(row.key))">
              {{ t('masterData.edit') }}
            </el-button>
            <el-button v-operation="operation" link type="danger" :disabled="pending" @click="removeLine(String(row.key))">
              {{ label('removeLine') }}
            </el-button>
          </template>
        </ManagementTable>
      </template>
    </TabbedDetailTemplate>
    <ManagedDialog v-model="taskDialog" :title="text('入库车次', 'Inbound arrivals')" :busy="taskPending">
      <FloatingError :title="taskError" />
      <el-alert v-if="createdTask" type="success" :closable="false"
        :title="text('已创建车次 ', 'Arrival registered ') + createdTask" />
      <el-form v-if="taskContext" label-position="top" class="business-border-label-form" :disabled="taskPending || !taskContext.creatable"
        @submit.stop.prevent="saveTask">
        <el-form-item :label="text('来源通知', 'Source notice')">
          <el-input :model-value="noticeNo" readonly />
        </el-form-item>
        <el-form-item :label="text('品类模板', 'Commodity template')" required>
          <el-input v-if="taskContext.template" :model-value="templateLabel(taskContext.template)" readonly />
          <el-select v-else v-model="taskTemplateId" filterable
            :placeholder="text('首次登记请选择已发布模板', 'Select a published template for the first arrival')">
            <el-option v-for="item in taskContext.templates" :key="item.id" :label="templateLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-alert v-if="!taskContext.creatable || (!taskContext.template && !taskContext.templates.length)"
          type="warning" :closable="false" :title="!taskContext.creatable
            ? text('当前通知状态不允许登记', 'This notice is closed for arrivals')
            : text('当前租户暂无可用的已发布品类模板', 'No published commodity template is available')" />
        <el-form-item :label="text('车辆类型', 'Vehicle type')" required>
          <el-select v-model="taskIdentity" @change="taskIdentifier = ''">
            <el-option v-if="taskContext.identities.includes('PLATED')" value="PLATED" :label="text('有牌车辆', 'Plated vehicle')" />
            <el-option v-if="taskContext.identities.includes('UNPLATED')" value="UNPLATED" :label="text('无牌车辆', 'Unplated vehicle')" />
            <el-option value="OTHER" :label="text('非车辆作业', 'Non-vehicle arrival')" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="taskIdentity !== 'OTHER'" :label="taskIdentity === 'PLATED'
          ? text('实际车牌', 'Vehicle plate') : text('现场识别号', 'Site identifier')" required>
          <el-input v-model.trim="taskIdentifier" :maxlength="taskIdentity === 'PLATED' ? 32 : 64" />
        </el-form-item>
      </el-form>
      <el-table v-if="taskContext?.tasks.length" :data="taskContext.tasks" :aria-label="text('最近 20 条车次', 'Latest 20 arrivals')">
        <el-table-column prop="number" :label="text('车次号', 'Arrival number')" min-width="200" />
        <el-table-column prop="stage" :label="text('作业节点', 'Work stage')" min-width="115" />
      </el-table>
      <template #footer>
        <el-button :disabled="taskPending" @click="taskDialog = false">{{ text("关闭", "Close") }}</el-button>
        <el-button v-operation="taskOperation" type="primary" :disabled="!canSaveTask" :loading="taskPending"
          @click="saveTask">{{ text("创建车次", "Register arrival") }}</el-button>
      </template>
    </ManagedDialog>
    <ManagedDialog v-model="lineDialog" :title="label('line')">
      <FloatingError :title="lineError" />
      <el-form v-if="lineDraft" label-position="top" class="business-border-label-form" @submit.stop.prevent="applyLine">
        <el-form-item :label="t('coilFlow.product')" required>
          <el-select v-model="lineDraft.productId" filterable remote :remote-method="searchProducts" @change="changeProduct(lineDraft)">
            <el-option v-for="item in products" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="lineSpecifications.length" :label="t('productStructure.specification')" required>
          <el-select v-model="lineDraft.specificationId" filterable @change="selectSpecification(lineDraft)">
            <el-option v-for="item in lineSpecifications" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="lineSkus.length" :label="label('sku')">
          <el-select :model-value="lineDraft.itemId === lineDraft.specificationId ? '' : lineDraft.itemId"
            filterable clearable @update:model-value="id => selectSku(lineDraft, id)">
            <el-option v-for="item in lineSkus" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('coilFlow.unit')" required>
          <el-select v-model="lineDraft.unitId" :disabled="!availableUnit(lineDraft)">
            <el-option v-if="availableUnit(lineDraft)" :label="availableUnit(lineDraft)!.label" :value="availableUnit(lineDraft)!.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="label('plannedQuantity')"><el-input v-model.trim="lineDraft.plannedQuantity" maxlength="31" /></el-form-item>
        <el-form-item :label="label('approvedQuantity')" required>
          <template #label>{{ label('approvedQuantity') }} <InfoHint :content="label('approvedQuantityHint')" /></template>
          <el-input v-model.trim="lineDraft.limit" maxlength="31" />
        </el-form-item>
        <el-form-item :label="label('contractNo')"><el-input v-model.trim="lineDraft.contractNo" maxlength="64" /></el-form-item>
        <el-form-item :label="label('remark')"><el-input v-model.trim="lineDraft.remark" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineDialog = false">{{ t('masterData.cancel') }}</el-button>
        <el-button v-operation="operation" type="primary" :disabled="!lineDraft || !validLine(lineDraft)" @click="applyLine">
          {{ t('masterData.save') }}
        </el-button>
      </template>
    </ManagedDialog>
  </el-form>
</template>
