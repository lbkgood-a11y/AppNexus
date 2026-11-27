<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import InboundWeighTicketFields from "./InboundWeighTicketFields.vue";
import InboundLabFields from "./InboundLabFields.vue";
import InboundInspectionAttachments from "./InboundInspectionAttachments.vue";
import InboundReceiptLineEditor from "./InboundReceiptLineEditor.vue";
import { newWeighTicket, normalizedWeighTicket, type WeighTicket } from "./inboundWorkbench";
import { AppPagination, BusinessWorkspace, FloatingError, ManagedDialog, ManagementTable, PageHeader, RecordAuditDetails, TabbedDetailTemplate, type ManagementColumn, type RecordAudit } from "@appnexus/ui";
import { documentActions, inboundDocuments, type DocumentAction, type InboundDocument,
  type InboundDocumentKind, type InboundDocumentsApi, type InboundTaskOption } from "./inboundDocuments";

/** 三单共享列表和操作结构，业务字段和后端操作编号仍按单据管理。 */
const props = defineProps<{
  kind: InboundDocumentKind;
  api: InboundDocumentsApi;
  detailId?: string;
  errorText: (cause: unknown) => string;
}>();
const emit = defineEmits<{ select: [id?: string] }>();
const { locale, t } = useI18n();
const language = computed(() => locale.value.startsWith("zh") ? 0 : 1);
const label = (zh: string, en: string) => [zh, en][language.value];
const title = computed(() => inboundDocuments[props.kind].title[language.value]);
const operation = (name: string) => name.includes(".") ? name : `${inboundDocuments[props.kind].object}.${name}`;
const allowed = ref(new Set<string>());
const can = (name: string) => allowed.value.has(operation(name));

const rows = ref<InboundDocument[]>([]);
const record = ref<InboundDocument>();
const keyword = ref("");
const applied = ref("");
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const issue = ref("");
let request = 0;

const action = ref<DocumentAction>();
const labEditor=ref<InstanceType<typeof InboundLabFields>>();
const attachmentEditor=ref<InstanceType<typeof InboundInspectionAttachments>>();
const receiptEditor = ref<InstanceType<typeof InboundReceiptLineEditor>>();
const editsReceiptLines = computed(() => ["create_bulk_draft", "create_steel_draft"].includes(action.value?.operation ?? ""));
const inputs = ref<Record<string, string | number | boolean | undefined>>({});
const weighTickets = ref<Record<string, WeighTicket>>({});
const busy = ref(false);
const actionError = ref("");
let attempt: { body: string; key: string } | undefined;
const taskOptions = ref<InboundTaskOption[]>([]);
const selectedTask = ref<InboundTaskOption>();
const tasksLoading = ref(false);
const tasksError = ref("");
let taskRequest = 0;

const headers: Record<string, readonly [string, string]> = {
  number: ["单号", "Number"], status: ["状态", "Status"], taskId: ["作业 ID", "Task ID"],
  taskVersion: ["作业版本", "Task version"], rowVersion: ["单据版本", "Document version"],
  qualityVersion: ["质检版本", "Quality version"], active: ["当前版本", "Active revision"],
  sampleBarcode: ["样品条码", "Sample barcode"], decision: ["质检判定", "Decision"],
  sampledAt: ["采样时间", "Sampled at"], labMetrics: ["实测指标", "Measured values"],
  mtcVerified: ["质保书核验", "Certificate verification"], appearanceVerified: ["外观核验", "Appearance verification"],
  method: ["计量方式", "Measurement method"], packages: ["件数", "Packages"], grossKg: ["毛重（kg）", "Gross (kg)"],
  tareKg: ["皮重（kg）", "Tare (kg)"], physicalNetKg: ["实收净重（kg）", "Physical net (kg)"],
  moistureKg: ["扣水（kg）", "Moisture deduction (kg)"], impurityKg: ["扣杂（kg）", "Impurity deduction (kg)"],
  onSiteKg: ["现场扣重（kg）", "Site deduction (kg)"],
  theoreticalKg: ["理重（kg）", "Theoretical (kg)"], varianceRatio: ["磅差率", "Variance ratio"],
  qualityState: ["质量状态", "Quality state"], accountingKg: ["入账重（kg）", "Accounting weight (kg)"],
  deductionKg: ["扣重（kg）", "Deduction (kg)"], postedAt: ["过账时间", "Posted at"],
  lpn: ["卷包号", "LPN"], heatNo: ["炉批号", "Heat number"], specification: ["规格", "Specification"],
  material: ["材质", "Material"], batchNo: ["批次号", "Batch number"], inventoryQuantity: ["库存数量", "Inventory quantity"]
};
const listFields = {
  quality: ["status", "number", "sampleBarcode", "decision"],
  metering: ["status", "number", "method", "physicalNetKg"],
  receipt: ["status", "number", "qualityState", "physicalNetKg", "accountingKg"]
};
const column = (key: string): ManagementColumn => ({ key, prop: key, label: headers[key][language.value] });
const columns = computed<ManagementColumn[]>(() => [
  ...listFields[props.kind].map(column),
  ...(can("read_document") ? [{ key: "open", label: label("操作", "Actions"), width: 110, fixed: "right" as const }] : [])
]);
const lineColumns = ["lpn", "heatNo", "specification", "material", "batchNo", "theoreticalKg", "accountingKg", "inventoryQuantity"];
const actions = (row?: InboundDocument) => availableActions(row).filter(item =>
  Boolean(item.onRow) === Boolean(row) && can(item.operation) &&
  (!row || props.kind !== "quality" || (Boolean(row.active) && Boolean(row.sampleBarcode))) &&
  (item.operation !== "post" || Boolean(row && row.taskStatus === "READY_TO_RECEIVE" && !row.collecting && row.lines?.length)) &&
  (!item.statuses || (row && item.statuses.includes(row.status))));

/** 只开放与草稿事实匹配的明细收集入口，防止空草稿提前提交。 */
function availableActions(row?: InboundDocument): DocumentAction[] {
  const original = documentActions[props.kind];
  if (props.kind !== "receipt" || !row?.collecting || row.taskStatus !== "READY_TO_RECEIVE" || row.meteringVersion == null) return original;
  const steel = row.stockGranularity === "LPN";
  const action = original.find(item => item.operation === (steel ? "create_steel_draft" : "create_bulk_draft"));
  if (!action) return original;
  return [...original, {
    ...action, onRow: true, completeReceipt: true, statuses: ["DRAFT"],
    title: steel ? ["补齐卷包明细", "Complete LPN lines"] : ["确认库位和批次", "Confirm location and batch"],
    fields: action.fields.filter(([key]) => !["receiptNo", "steelReceiptNo", "replacesReceiptId"].includes(key))
  }];
}
const numbered = new Set(["qualityNo", "steelQualityNo", "meteringNo", "grossMeteringNo", "steelMeteringNo", "receiptNo", "steelReceiptNo"]);

async function load() {
  const generation = ++request;
  loading.value = true;
  issue.value = "";
  record.value = undefined;
  try {
    if (props.detailId) {
      const result = await props.api.detail(props.detailId);
      if (request === generation) record.value = result;
    } else {
      const result = await props.api.list(page.value, applied.value || undefined);
      if (request === generation) ({ rows: rows.value, total: total.value } = result);
    }
  } catch (cause) {
    if (request === generation) {
      rows.value = [];
      total.value = 0;
      issue.value = props.errorText(cause) || label("加载失败", "Unable to load");
    }
  } finally {
    if (request === generation) loading.value = false;
  }
}
watch(() => [props.kind, props.detailId], () => {
  if (!busy.value) action.value = undefined;
  void load();
}, { immediate: true });
void props.api.operations().then(values => { allowed.value = new Set(values); }).catch(() => {
  ElMessage.warning(label("操作权限加载失败", "Unable to load operation permissions"));
});

function search() {
  page.value = 1;
  applied.value = keyword.value.trim();
  void load();
}

function start(item: DocumentAction) {
  action.value = item;
  inputs.value = {};
  weighTickets.value = Object.fromEntries(item.fields.filter(([, , , type]) => type === "ticket")
    .map(([key]) => [key, newWeighTicket()]));
  actionError.value = "";
  attempt = undefined;
  selectedTask.value = undefined;
  taskOptions.value = [];
  tasksError.value = "";
  taskRequest++;
  if (!item.onRow) void searchTasks("");
}

/** 旧表单和旧搜索的响应不能覆盖当前命令的候选作业。 */
async function searchTasks(query: string) {
  const item = action.value;
  if (!item || item.onRow) return;
  const generation = ++taskRequest;
  tasksLoading.value = true;
  tasksError.value = "";
  taskOptions.value = [];
  try {
    const result = await props.api.taskOptions(item.path, query.trim().slice(0, 64));
    if (generation === taskRequest && action.value === item) taskOptions.value = result;
  } catch (cause) {
    if (generation === taskRequest && action.value === item) {
      tasksError.value = props.errorText(cause) || label("作业加载失败", "Unable to load tasks");
    }
  } finally {
    if (generation === taskRequest) tasksLoading.value = false;
  }
}

/** 从详情带入可信展示版本。请求内容未变时保留同一幂等键供网络失败重试。 */
async function submit() {
  const item = action.value;
  if (!item || busy.value || !can(item.operation)) return;
  const row = item.onRow ? record.value : undefined;
  if (item.onRow && !row) return;
  const input: Record<string, unknown> = {};
  try {
    if (item.operation === "decide_quality") {
      if (!labEditor.value?.canSubmit()) return;
      Object.assign(input, labEditor.value.payload());
    }
    if (item.operation==="inspect_steel" && attachmentEditor.value) Object.assign(input,attachmentEditor.value.payload());
    if (editsReceiptLines.value) {
      if (!receiptEditor.value) throw new Error(label("请先选择入库作业", "Select an inbound task first"));
      Object.assign(input, receiptEditor.value.payload());
    }
    for (const [key, zh, en, type, optional] of item.fields) {
      if (type === "ticket") {
        if (optional && inputs.value.basis !== "SCALE") continue;
        const ticket = normalizedWeighTicket(weighTickets.value[key]);
        if (!ticket) throw new Error(label(`请补齐${zh}，重量须为正数且最多四位小数`, `Complete ${en}. Weight must be positive with up to four decimal places.`));
        input[key] = ticket;
        continue;
      }
      const raw = inputs.value[key];
      const value = typeof raw === "string" ? raw.trim() : raw;
      if (value === "" || value == null) {
        if (optional) continue;
        throw new Error(label(`请填写${zh}`, `Enter ${en}`));
      }
      input[key] = value;
    }
    if (!item.onRow) {
      const task = selectedTask.value;
      if (!task) throw new Error(label("请选择入库作业", "Select an inbound task"));
      Object.assign(input, { taskId: task.taskId, taskVersion: task.taskVersion });
      if (props.kind === "receipt") input.meteringVersion = task.meteringVersion;
    }
    if (row) {
      Object.assign(input, { taskId: row.taskId, taskVersion: row.taskVersion });
      if (item.operation === "register_sample") Object.assign(input, { priorQualityId: row.id, priorRowVersion: row.rowVersion });
      if (item.operation === "decide_quality") input.qualityRowVersion = row.rowVersion;
      if (props.kind === "metering") input.meterVersion = row.rowVersion;
      if (item.completeReceipt) {
        input.meteringVersion = row.meteringVersion;
        input[item.operation === "create_steel_draft" ? "steelReceiptNo" : "receiptNo"] = row.number;
      } else if (props.kind === "receipt") input.receiptVersion = row.rowVersion;
    }
  } catch (cause) {
    actionError.value = (cause as Error).message;
    return;
  }
  const path = item.path.replace("{id}", encodeURIComponent(row?.id || "")).replace("{taskId}", encodeURIComponent(row?.taskId || ""));
  const body = JSON.stringify(input);
  if (attempt?.body !== body) attempt = { body, key: crypto.randomUUID() };
  busy.value = true;
  actionError.value = "";
  try {
    await props.api.execute(path, input, attempt.key);
    action.value = undefined;
    ElMessage.success(label("操作已提交", "Operation submitted"));
    await load();
  } catch (cause) {
    actionError.value = props.errorText(cause) || label("操作失败", "Operation failed");
    const code = (cause as { response?: { data?: { code?: string } } })?.response?.data?.code;
    if (!item.onRow && code?.endsWith("_VERSION_CONFLICT")) {
      selectedTask.value = undefined;
      await searchTasks("");
      actionError.value = label("作业状态或版本已变化，请重新选择后提交", "Task changed. Select it again before submitting.");
    }
  } finally {
    busy.value = false;
  }
}

function display(key: string, value: unknown) {
  if (value == null || value === "") return "—";
  if (key === "status" || key === "qualityState" || key === "decision") {
    const states: Record<string, [string, string]> = {
      DRAFT: ["草稿", "Draft"], POSTED: ["已入账", "Posted"], VOIDED: ["已作废", "Voided"],
      REVERSED: ["已冲红", "Reversed"], COLLECTING: ["收集要素中", "Collecting details"],
      PENDING_LAB: ["待化验", "Awaiting lab results"], QUALIFIED: ["合格", "Qualified"],
      REGISTERED: ["已登记", "Registered"], REJECTED: ["不合格", "Rejected"], SAMPLED: ["已采样", "Sampled"], DECIDED: ["已判定", "Decided"],
      PASS: ["合格", "Passed"], FAIL: ["不合格", "Failed"], QUALITY_FROZEN: ["质量冻结", "Quality frozen"],
      GROSS_RECORDED: ["已过毛重", "Gross recorded"], CONFIRMED: ["已确认", "Confirmed"]
    };
    const name = states[String(value)];
    if (name) return name[language.value];
  }
  if (["active", "mtcVerified", "appearanceVerified"].includes(key)) {
    return [true, 1, "true"].includes(value as boolean | number | string) ? label("是", "Yes") : label("否", "No");
  }
  if ((key === "sampledAt" || key === "postedAt") && typeof value === "string") {
    const timestamp = new Date(value);
    if (!Number.isNaN(timestamp.getTime())) return new Intl.DateTimeFormat(locale.value, {
      dateStyle: "medium", timeStyle: "medium"
    }).format(timestamp);
  }
  return String(value);
}
</script>

<template>
  <div class="management-page">
    <FloatingError :title="issue" />
    <TabbedDetailTemplate v-if="detailId" v-loading="loading" master-data system-last>
      <template #header>
        <PageHeader :back-label='label("返回列表", "Back to list")' @back="emit('select')" :back-disabled="busy" :title="title">
          <template v-if="record" #meta>
            <el-tag effect="plain">{{ record.number }}</el-tag>
            <el-tag class="app-status-tag" type="info">{{ display('status', record.status) }}</el-tag>
            <el-tag v-if="kind === 'receipt' && record.collecting" class="app-status-tag" type="info">
              {{ display('status', 'COLLECTING') }}
            </el-tag>
          </template>
          <template #actions>
            <el-button v-operation="operation(item.operation)" v-for="item in record ? actions(record) : []" :key="item.operation" :disabled="busy" @click="start(item)">
              {{ item.title[language] }}
            </el-button>
          </template>
        </PageHeader>
      </template>
      <template #content>
        <div v-if="record" class="detail-profile-form">
          <el-card shadow="never" class="detail-section">
            <template #header><div class="detail-section-heading"><h2>{{ t('partner.basic') }}</h2></div></template>
            <p v-if="kind === 'receipt' && record.collecting">{{
              label('正在收集入库要素，齐套后可提交入账', 'Collect the required receipt details before posting')
            }}</p>
            <div class="detail-field-grid">
              <template v-for="(names, key) in headers" :key="key">
                <div v-if="key in record" class="detail-readonly-field" :class="{ 'detail-field-wide': key === 'labMetrics' }">
                  <span>{{ names[language] }}</span>
                  <el-tag v-if="key === 'status' || (key === 'qualityState' && record[key])" class="app-status-tag" type="info">
                    {{ display(key, record[key]) }}
                  </el-tag>
                  <strong v-else>{{ display(key,
                    record.collecting && ['physicalNetKg','accountingKg','deductionKg'].includes(key) ? null : record[key]) }}</strong>
                </div>
              </template>
            </div>
          </el-card>
          <slot v-if="kind === 'receipt' && record.status === 'DRAFT'" name="receipt-execution" :record="record" :reload="load" />
        </div>
      </template>
      <template v-if="record" #system>
        <h2 class="detail-section-title">{{ t('partner.system') }}</h2>
        <RecordAuditDetails v-if="record.audit" :audit="record.audit as RecordAudit" inline />
        <p v-else>{{ label('当前单据详情未提供审计信息', 'Audit information is unavailable for this document') }}</p>
      </template>
      <template v-if="record?.lines?.length" #tabs>
        <el-tabs model-value="lines">
          <el-tab-pane name="lines" :label="label('入库明细', 'Receipt lines')">
            <ManagementTable embedded readonly-view
              :title="label('入库明细', 'Receipt lines')" :columns="lineColumns.map(column)" :rows="record.lines" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </TabbedDetailTemplate>
    <BusinessWorkspace v-else :title="title">
      <template #search>
        <el-form inline @submit.prevent="search">
          <el-form-item :label="label('单号', 'Document number')">
            <el-input v-model="keyword" clearable maxlength="64" />
          </el-form-item>
          <el-form-item class="management-filter-actions">
            <el-button v-operation="operation('list_documents')" native-type="submit" type="primary" :loading="loading">{{ t("masterData.search") }}</el-button>
            <el-button v-operation="operation('list_documents')" :disabled="loading" @click="keyword = ''; search()">{{ t("masterData.reset") }}</el-button>
          </el-form-item>
        </el-form>
      </template>
      <template #data>
        <ManagementTable embedded :title="title" :columns="columns" :rows="rows" :total="total" :loading="loading" @refresh="load">
          <template #actions>
            <el-button v-operation="operation(item.operation)" v-for="item in actions()" :key="item.operation" :disabled="busy" type="primary" @click="start(item)">
              {{ item.title[language] }}
            </el-button>
          </template>
          <template #status="{ row }">
            <el-tag class="app-status-tag" type="info">{{ display('status', row.status) }}</el-tag>
          </template>
          <template #decision="{ row }">{{ display('decision', row.decision) }}</template>
          <template #qualityState="{ row }">{{ display('qualityState', row.qualityState) }}</template>
          <template #open="{ row }">
            <el-button v-operation="operation('read_document')" link type="primary" :disabled="busy" @click="emit('select', row.id)">{{ label("详情", "Details") }}</el-button>
          </template>
          <template #pagination>
            <AppPagination :current-page="page" :page-size="20" :total="total"
              layout="total, prev, pager, next" :disabled="loading" @update:current-page="value => { page = value; load(); }" />
          </template>
        </ManagementTable>
      </template>
    </BusinessWorkspace>
  <ManagedDialog :model-value="Boolean(action)" :title="action?.title[language] ?? ''" :close-on-click-modal="false"
    :busy="busy" @update:model-value="open => { if (!open) action = undefined; }">
    <el-form v-if="action" class="business-border-label-form" label-position="top" :disabled="busy" @submit.prevent="submit">
      <FloatingError :title="actionError" />
      <el-form-item v-if="!action.onRow" :label="label('入库作业', 'Inbound task')" :error="tasksError" required>
        <el-select v-model="selectedTask" value-key="taskId" class="w-full" filterable remote clearable
          :loading="tasksLoading" :remote-method="searchTasks"
          :placeholder="label('选择作业或输入作业号', 'Select a task or enter its number')"
          :no-data-text="label('暂无符合条件的作业', 'No eligible tasks')"
          @visible-change="open => { if (open && !tasksLoading && !taskOptions.length) void searchTasks(''); }">
          <el-option v-for="task in taskOptions" :key="task.taskId" :label="task.taskNo" :value="task" />
        </el-select>
      </el-form-item>
      <el-form-item v-for="[key, zh, en, type, optional] in action.fields.filter(field =>
        field[3] !== 'ticket' || !field[4] || inputs.basis === 'SCALE')" :key="key"
        :label="label(zh, en)" :required="!optional">
        <slot v-if="numbered.has(key)" name="document-number" :field="key" :operation="operation(action.operation)"
          :value="String(inputs[key] ?? '')" :disabled="busy" :update="(value: string) => inputs[key] = value">
          <el-input :model-value="String(inputs[key] ?? '')" maxlength="64"
            @update:model-value="value => inputs[key] = value" />
        </slot>
        <InboundWeighTicketFields v-else-if="type === 'ticket'" v-model="weighTickets[key]" />
        <el-select v-else-if="type === 'boolean'" v-model="inputs[key]">
          <el-option :label="label('通过', 'Accepted')" :value="true" />
          <el-option :label="label('不通过', 'Rejected')" :value="false" />
        </el-select>
        <el-select v-else-if="Array.isArray(type)" v-model="inputs[key]">
          <el-option v-for="option in type" :key="option" :label="option" :value="option" />
        </el-select>
        <el-input-number v-else-if="type === 'integer'" :model-value="inputs[key] as number | undefined"
          :min="0" :precision="0" :max="Number.MAX_SAFE_INTEGER" @update:model-value="value => inputs[key] = value ?? undefined" />
        <el-input v-else :model-value="inputs[key] as string | undefined" @update:model-value="value => inputs[key] = value" />
      </el-form-item>
      <InboundLabFields v-if="action.operation === 'decide_quality'" ref="labEditor"
        :id="record?.id" :load="api.labMetrics" :error-text="errorText" />
      <InboundInspectionAttachments v-if="action.operation === 'inspect_steel'" ref="attachmentEditor"
        :task-id="selectedTask?.taskId" :load="api.attachmentOptions" :error-text="errorText">
        <template #upload="scope"><slot name="inspection-upload" v-bind="scope" /></template>
      </InboundInspectionAttachments>
      <InboundReceiptLineEditor v-if="editsReceiptLines" ref="receiptEditor"
        :notice-id="action.onRow ? (typeof record?.noticeId === 'string' ? record.noticeId : undefined) : selectedTask?.noticeId ?? undefined"
        :steel="action.operation === 'create_steel_draft'" :api="api" :error-text="errorText" />
      <p v-if="!editsReceiptLines && action.operation !== 'decide_quality' && !action.fields.length && record">{{ label("确认提交单据", "Confirm operation for") }} {{ record.number }}</p>
    </el-form>
    <template #footer>
      <el-button :disabled="busy" @click="action = undefined">{{ label("取消", "Cancel") }}</el-button>
      <el-button v-operation="action && operation(action.operation)" type="primary" :loading="busy" :disabled="Boolean(action && !action.onRow && !selectedTask)
        || (action?.operation === 'decide_quality' && !labEditor?.canSubmit())"
        @click="submit">{{ label("确认提交", "Submit") }}</el-button>
    </template>
  </ManagedDialog>
  </div>
</template>
