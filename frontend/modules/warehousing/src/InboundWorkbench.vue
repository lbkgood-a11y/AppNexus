<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import { FloatingError, ManagedDialog } from "@appnexus/ui";
import { newWeighTicket, normalizedWeighTicket, workbenchStages, type InboundQueueItem, type InboundWorkbenchApi, type InboundWorkbenchDetail,
  type UnloadingOutcome, type WeighTicket, type WorkbenchNumberOperation, type WorkbenchStage } from "./inboundWorkbench";
import { workbenchMessages } from "./inboundWorkbenchMessages";
import { documentActions, inboundDocuments, type DocumentAction } from "./inboundDocuments";
import InboundLabFields from "./InboundLabFields.vue";
import InboundInspectionAttachments from "./InboundInspectionAttachments.vue";
import InboundWeighTicketFields from "./InboundWeighTicketFields.vue";
import InboundSitePanel from "./InboundSitePanel.vue";
import InboundSiteCommands from "./InboundSiteCommands.vue";

const props = defineProps<{ api: InboundWorkbenchApi; errorText: (cause: unknown) => string; taskId?: string;
  stockGranularity?: string; meteringStrategy?: string; qualityStrategy?: string }>();
const emit = defineEmits<{ changed: [] }>();
defineSlots<{
  "document-number"(props: { operation: WorkbenchNumberOperation; value: string;
    update: (value: string) => void; disabled: boolean }): unknown;
  "open-receipt"(props: { detail: InboundWorkbenchDetail; can: (id: string) => boolean }): unknown;
  "inspection-upload"(props: {taskId:string;refresh:()=>void}): unknown;
}>();
const { locale } = useI18n();
const vocabulary = computed(() => locale.value.toLowerCase().startsWith("zh") ? workbenchMessages.zh : workbenchMessages.en);
const label = (key: string) => vocabulary.value[key as keyof typeof workbenchMessages.en] ?? key;

const activeStage = ref<WorkbenchStage>();
const unopened = ref(false);
const stageFilters = ["allActive", ...workbenchStages] as const;
const rows = ref<InboundQueueItem[]>([]);
const counts = ref<Partial<Record<WorkbenchStage, number>>>();
const nextCursor = ref<string | null>(null);
const totalCount = computed(() => workbenchStages.reduce((total, stage) => total + (counts.value?.[stage] ?? 0), 0));
const selected = ref<string>();
const detail = ref<InboundWorkbenchDetail>();
const allowed = ref(new Set<string>());
const loadingQueue = ref(false);
const loadingDetail = ref(false);
const saving = ref(false);
const queueIssue = ref("");
const detailIssue = ref("");
const commandIssue = ref("");
const now = ref(Date.now());
const action = ref<"sample" | "external" | "gross" | "tare" | "unload">();
const sampleInput = reactive({ qualityNo: "", barcode: "" });
const externalInput = reactive({
  meteringNo: "", source: "RAIL_SCALE" as "RAIL_SCALE" | "DRAFT_SURVEY", externalReference: "", netKg: ""
});
const ticketInput = reactive<WeighTicket>(newWeighTicket());
const grossInput = reactive({ meteringNo: "" });
const unloadInput = reactive({ outcome: "ACCEPTED" as UnloadingOutcome, extraDeductionKg: "0" });
const unloadOptions = ["ACCEPTED", "ACCEPTED_WITH_DEDUCTION", "RETURN"] as const;
let querySequence = 0;
let detailSequence = 0;
let refreshTimer: ReturnType<typeof setInterval> | undefined;
let mounted = false;
let hasExtendedQueue = false;

const can = (id: string) => allowed.value.has(id);
const steel = computed(() => props.stockGranularity === "LPN");
const language = computed(() => locale.value.startsWith("zh") ? 0 : 1);
const actionDialog = ref<{ kind: "quality" | "metering"; command: DocumentAction }>();
/** 对话框确认按钮绑定实际单据提交操作，空对话框不开放操作。 */
const documentOperation = computed(() => {
  const selection = actionDialog.value;
  return selection ? `${inboundDocuments[selection.kind].object}.${selection.command.operation}` : "";
});
const labEditor=ref<InstanceType<typeof InboundLabFields>>();
const attachmentEditor=ref<InstanceType<typeof InboundInspectionAttachments>>();
const commandFields = reactive<Record<string, string | number | boolean | undefined>>({});
const commandTickets = ref<Record<string, WeighTicket>>({});
const dialogError = ref("");

/** 使用单据已有输入定义，补齐入库草稿内的化验和钢铁操作。 */
function beginDocument(kind: "quality" | "metering", operation: string) {
  const command = documentActions[kind].find(item => item.operation === operation);
  if (!command) return;
  for (const key of Object.keys(commandFields)) delete commandFields[key];
  commandTickets.value = Object.fromEntries(command.fields.filter(([, , , type]) => type === "ticket")
    .map(([key]) => [key, newWeighTicket()]));
  dialogError.value = "";
  actionDialog.value = { kind, command };
}

/** 版本与关联记录来自已授权的作业快照。 */
async function submitDocument() {
  const selection = actionDialog.value;
  const current = detail.value;
  if (!current || !selection || saving.value) return;
  const { command, kind } = selection;
  const payload: Record<string, unknown> = { taskId: current.taskId, taskVersion: current.taskVersion };
  try {
    if (command.operation === "decide_quality") {
      if (!labEditor.value?.canSubmit()) return;
      Object.assign(payload, labEditor.value.payload());
    }
    if(command.operation==="inspect_steel" && attachmentEditor.value)Object.assign(payload,attachmentEditor.value.payload());
    for (const [key, zh, en, type, optional] of command.fields) {
      if (type === "ticket") {
        if (optional && commandFields.basis !== "SCALE") continue;
        const ticket = normalizedWeighTicket(commandTickets.value[key]);
        if (!ticket) throw new Error(language.value === 0 ? `请补齐${zh}` : `Complete ${en}`);
        payload[key] = ticket;
        continue;
      }
      const value = commandFields[key];
      if (value == null || value === "") {
        if (optional) continue;
        throw new Error(language.value === 0 ? `请填写${zh}` : `Enter ${en}`);
      }
      payload[key] = value;
    }
    if (command.operation === "decide_quality") payload.qualityRowVersion = current.quality.rowVersion;
  } catch (cause) {
    dialogError.value = (cause as Error).message;
    return;
  }
  saving.value = true;
  dialogError.value = "";
  try {
    await props.api.executeDocument(kind,command.path.replace("{id}",encodeURIComponent(current.quality.id ?? "")),payload);
    actionDialog.value = undefined;
    emit("changed");
  } catch (cause) {
    dialogError.value = props.errorText(cause);
  } finally {
    saving.value = false;
  }
}
const canSample = computed(() => detail.value && can("BulkInboundQualityOrder.register_sample")
  && ["SAMPLED", "QUALIFIED_AND_UNLOADED"].includes(props.qualityStrategy ?? "")
  && can("BulkInboundQualityOrder.read_workbench_quality") &&
  (!detail.value.quality.id || detail.value.quality.status === "DECIDED") &&
  ["ARRIVED", "WORKING", "READY_TO_RECEIVE", "CORRECTION_REQUIRED"].includes(detail.value.status));
const canExternal = computed(() => detail.value?.status === "WORKING"
  && ["EXTERNAL_NET", "SCALE_OR_EXTERNAL"].includes(props.meteringStrategy ?? "")
  && detail.value.quality.sampleBarcode && detail.value.quality.decision !== "FAIL" && !detail.value.metering.id
  && can("BulkInboundMeteringOrder.register_external") && can("BulkInboundMeteringOrder.read_workbench_meter"));
const canGross = computed(() => detail.value && ["ARRIVED", "WORKING"].includes(detail.value.status)
  && !detail.value.metering.id && !steel.value
  && ["SCALE", "SCALE_DIFFERENCE", "SCALE_OR_EXTERNAL"].includes(props.meteringStrategy ?? "")
  && can("BulkInboundMeteringOrder.record_gross") && can("BulkInboundMeteringOrder.read_workbench_meter"));
const canTare = computed(() => detail.value?.stage === "WAIT_TARE" && detail.value.metering.status === "GROSS_RECORDED"
  && can("BulkInboundMeteringOrder.record_tare"));
const canUnload = computed(() => detail.value?.stage === "UNLOADING" && detail.value.metering.status === "GROSS_RECORDED"
  && can("BulkInboundTask.confirm_unloading"));
const path = computed(() => {
  const current = detail.value;
  if (!current) return [];
  if (current.metering.method === "EXTERNAL_NET" || current.metering.method === "THEORETICAL") {
    return ["WAIT_QUALITY", "metering", "WAIT_RECEIPT"];
  }
  if (current.metering.method === "SCALE_DIFFERENCE" || current.stage === "UNLOADING") {
    return current.metering.packages ? ["WAIT_QUALITY", "WAIT_GROSS", "WAIT_TARE", "WAIT_RECEIPT"] : [...workbenchStages];
  }
  return ["WAIT_QUALITY", "metering", "WAIT_RECEIPT"];
});
const steps = computed(() => {
  const current = detail.value?.stage;
  if (!current) return detail.value?.status === "RECEIVED" ? path.value.length : 0;
  const exact = path.value.indexOf(current);
  return exact >= 0 ? exact : Math.max(0, path.value.indexOf("metering"));
});
const identity = computed(() => Object.entries(detail.value?.identity ?? {}));
const totals = computed(() => Object.entries(detail.value?.totals ?? {}));
const showSite = computed(() => Boolean(detail.value && (
  can("BulkInboundMeteringOrder.read_workbench_meter") || can("BulkInboundTask.raise_site_gate") ||
  can("BulkInboundTask.call_site_number") || can("BulkInboundTask.print_site_ticket"))));

/** 游标与请求序列按队列阶段隔离，避免慢响应覆盖当前选择。 */
async function loadQueue(append = false) {
  const request = ++querySequence;
  const stage = activeStage.value;
  const cursor = append ? nextCursor.value ?? undefined : undefined;
  loadingQueue.value = true;
  queueIssue.value = "";
  try {
    const page = await props.api.queue(stage, cursor, unopened.value);
    if (request !== querySequence) return;
    rows.value = append ? [...rows.value, ...page.rows] : page.rows;
    counts.value = page.counts;
    nextCursor.value = page.nextAfterId;
    hasExtendedQueue = append;
    selectFirstTask();
  } catch (cause) {
    if (request === querySequence) queueIssue.value = props.errorText(cause);
  } finally {
    if (request === querySequence) loadingQueue.value = false;
  }
}

/** 队列和操作决定可分别返回，只有获准查看详情时才自动打开首条作业。 */
function selectFirstTask() {
  const first = rows.value[0];
  if (!selected.value && first && !queueIssue.value && can("BulkInboundTask.read_workbench_detail")) {
    void loadDetail(first.taskId);
  }
}

/** 选中作业后始终重读服务端权限投影，无权字段不会进入本地状态。 */
async function loadDetail(id: string) {
  const request = ++detailSequence;
  if (selected.value !== id) {
    detail.value = undefined;
    action.value = undefined;
    commandIssue.value = "";
    Object.assign(sampleInput, { qualityNo: "", barcode: "" });
    Object.assign(externalInput, { meteringNo: "", source: "RAIL_SCALE", externalReference: "", netKg: "" });
    Object.assign(ticketInput, newWeighTicket());
    Object.assign(grossInput, { meteringNo: "" });
    Object.assign(unloadInput, { outcome: "ACCEPTED", extraDeductionKg: "0" });
  }
  selected.value = id;
  loadingDetail.value = true;
  detailIssue.value = "";
  try {
    const next = await props.api.detail(id);
    if (request === detailSequence) detail.value = next;
  } catch (cause) {
    if (request === detailSequence) {
      detail.value = undefined;
      detailIssue.value = props.errorText(cause);
    }
  } finally {
    if (request === detailSequence) loadingDetail.value = false;
  }
}

/** 切换范围时清除旧游标和详情，避免跨筛选显示旧作业。 */
function changeQueue(stage: WorkbenchStage | undefined, onlyUnopened = unopened.value) {
  if (stage === activeStage.value && onlyUnopened === unopened.value) return;
  if (onlyUnopened !== unopened.value) counts.value = undefined;
  activeStage.value = stage;
  unopened.value = onlyUnopened;
  rows.value = [];
  nextCursor.value = null;
  selected.value = undefined;
  detail.value = undefined;
  detailIssue.value = "";
  loadingDetail.value = false;
  detailSequence++;
  void loadQueue();
}

function waitText(row: InboundQueueItem) {
  if (!row.stageStartedAt) return "—";
  const minutes = Math.max(0, Math.floor((now.value - Date.parse(row.stageStartedAt)) / 60000));
  return label("wait").replace("{value}", String(minutes));
}

/** 成功后从原聚合接口重读作业及队列，失败保留当前表单输入。 */
async function commit(work: () => Promise<unknown>, taskId: string) {
  saving.value = true;
  commandIssue.value = "";
  try {
    await work();
    action.value = undefined;
    ElMessage.success(label("success"));
    if (props.taskId) {
      emit("changed");
    } else {
      await Promise.all([loadQueue(), selected.value === taskId ? loadDetail(taskId) : Promise.resolve()]);
    }
  } catch (cause) {
    commandIssue.value = props.errorText(cause);
  } finally {
    saving.value = false;
  }
}

function submitForm() {
  const current = detail.value;
  if (!current || saving.value) return;
  const task = { taskId: current.taskId, taskVersion: current.taskVersion };
  if (action.value === "sample" && canSample.value) {
    const qualityNo = sampleInput.qualityNo.trim(), barcode = sampleInput.barcode.trim();
    if (!qualityNo || !barcode) {
      commandIssue.value = label("invalid");
      return;
    }
    void commit(() => props.api.sample({ ...task, qualityNo, barcode,
      priorQualityId: current.quality.id, priorRowVersion: current.quality.rowVersion }), current.taskId);
  }
  if (action.value === "external" && canExternal.value) {
    const { source, netKg } = externalInput;
    const meteringNo = externalInput.meteringNo.trim(), externalReference = externalInput.externalReference.trim();
    if (!meteringNo || !externalReference || !/^\d{1,14}(\.\d{1,4})?$/.test(netKg)) {
      commandIssue.value = label("invalid");
      return;
    }
    void commit(() => props.api.external({ ...task, source, netKg, meteringNo, externalReference }), current.taskId);
  }
  if (action.value === "gross" && canGross.value) {
    const ticket = verifiedTicket(), grossMeteringNo = grossInput.meteringNo.trim();
    if (!ticket || !grossMeteringNo) { commandIssue.value = label("invalid"); return; }
    void commit(() => props.api.gross({ ...task, grossMeteringNo, ticket }), current.taskId);
  }
  const meterVersion = current.metering.rowVersion;
  if (action.value === "tare" && canTare.value && meterVersion !== undefined) {
    const ticket = verifiedTicket();
    if (!ticket) { commandIssue.value = label("invalid"); return; }
    void commit(() => props.api.tare({ ...task, meterVersion, ticket }), current.taskId);
  }
  if (action.value === "unload" && canUnload.value && meterVersion !== undefined) {
    const { outcome } = unloadInput;
    const extraDeductionKg = outcome === "ACCEPTED_WITH_DEDUCTION" ? unloadInput.extraDeductionKg : "0";
    if (!weightText(extraDeductionKg)) { commandIssue.value = label("invalid"); return; }
    void commit(() => props.api.unload(current.taskId, { taskVersion: current.taskVersion,
      meterVersion, outcome, extraDeductionKg }), current.taskId);
  }
}

function weightText(value: string) { return /^\d{1,14}(\.\d{1,4})?$/.test(value); }
function verifiedTicket(): WeighTicket | undefined {
  return normalizedWeighTicket(ticketInput);
}

async function refresh() {
  const permissions = props.api.operations().then(result => {
    allowed.value = new Set(result);
    if (!props.taskId && !loadingQueue.value) selectFirstTask();
  }, cause => {
    allowed.value = new Set();
    commandIssue.value = props.errorText(cause);
  });
  const current = props.taskId ?? selected.value;
  await Promise.all([permissions, props.taskId ? Promise.resolve() : loadQueue(), current ? loadDetail(current) : Promise.resolve()]);
}

onMounted(async () => {
  mounted = true;
  await refresh();
  if (!mounted) return;
  refreshTimer = setInterval(() => {
    now.value = Date.now();
    if (!props.taskId && !loadingQueue.value && !saving.value && !action.value && !actionDialog.value && !hasExtendedQueue) {
      void loadQueue();
    }
  }, 30000);
});
onUnmounted(() => {
  mounted = false;
  querySequence++;
  detailSequence++;
  if (refreshTimer) clearInterval(refreshTimer);
});
</script>

<template>
  <el-card shadow="never" class="inbound-workbench">
    <template #header>
      <div class="workbench-header">
        <component :is="taskId ? 'h2' : 'h1'">{{ taskId ? (language === 0 ? "入库执行" : "Receipt execution") : label("title") }}</component>
        <el-button v-if="!taskId" v-operation="'BulkInboundTask.read_workbench_queue'"
          :loading="loadingQueue" @click="refresh">{{ label("refresh") }}</el-button>
        <el-button v-else v-operation="'BulkInboundTask.read_workbench_detail'"
          :loading="loadingDetail" @click="refresh">{{ label("refresh") }}</el-button>
      </div>
    </template>
    <div v-if="!taskId" class="workbench-overview">
      <div class="workbench-scope" role="group" :aria-label="label('queueScope')">
        <el-button v-operation="'BulkInboundTask.read_workbench_queue'" :type="unopened ? 'default' : 'primary'" :aria-pressed="!unopened"
          @click="changeQueue(undefined, false)">{{ label("allActive") }}</el-button>
        <el-button v-operation="'BulkInboundTask.read_workbench_queue'" :type="unopened ? 'primary' : 'default'" :aria-pressed="unopened"
          @click="changeQueue(undefined, true)">{{ label("awaitingDraft") }}</el-button>
      </div>
      <div class="workbench-stages" role="group" :aria-label="label('stage')">
        <el-button v-for="stage in stageFilters" :key="stage" class="workbench-stage"
          v-operation="'BulkInboundTask.read_workbench_queue'"
          :type="(activeStage ?? 'allActive') === stage ? 'primary' : 'default'" plain
          :aria-pressed="(activeStage ?? 'allActive') === stage"
          @click="changeQueue(stage === 'allActive' ? undefined : stage)">
          <span class="stage-summary">
            <span>{{ label(stage === "allActive" ? "allStages" : stage) }}</span>
            <strong>{{ queueIssue || !counts ? "—" : stage === "allActive" ? totalCount : counts[stage] ?? 0 }}</strong>
          </span>
        </el-button>
      </div>
    </div>
    <div class="workbench-grid" :class="{ 'workbench-single': taskId || !rows.length, 'workbench-with-site': showSite }">
      <section v-if="!taskId" v-loading="loadingQueue" class="workbench-area queue-area" :aria-label="label('queue')">
        <div class="workbench-header">
          <h2>{{ label("queue") }}</h2>
          <span>{{ label(activeStage ?? (unopened ? "awaitingDraft" : "allActive")) }}</span>
        </div>
        <FloatingError v-if="queueIssue" :title="queueIssue" />
        <el-empty v-else-if="!loadingQueue && rows.length === 0"
          :description="label(activeStage ? 'empty' : unopened ? 'emptyDraft' : 'emptyActive')" :image-size="72" />
        <div class="queue-rows">
          <article v-for="row in rows" :key="row.taskId" class="queue-item"
            :class="{ 'queue-item-selected': selected === row.taskId }">
            <div class="queue-item-heading">
              <strong>{{ row.taskNo ?? '—' }}</strong>
              <el-button v-operation="'BulkInboundTask.read_workbench_detail'" link type="primary"
                :aria-label="row.taskNo ? `${label('viewTask')} ${row.taskNo}` : label('viewTask')"
                :aria-pressed="selected === row.taskId" @click="loadDetail(row.taskId)">{{ label("viewTask") }}</el-button>
            </div>
            <div class="queue-item-meta">
              <el-tag size="small" effect="plain">{{ label(row.stage) }}</el-tag>
              <small>{{ waitText(row) }}</small>
            </div>
          </article>
        </div>
        <el-button v-if="nextCursor && !queueIssue" v-operation="'BulkInboundTask.read_workbench_queue'" link type="primary" :disabled="loadingQueue" @click="loadQueue(true)">{{ label("more") }}</el-button>
      </section>

      <section v-if="taskId || selected" v-loading="loadingDetail" class="workbench-area" :aria-label="label('detail')">
        <FloatingError v-if="detailIssue" :title="detailIssue" />
        <el-empty v-else-if="!detail && !loadingDetail && (taskId || rows.length)"
          :description="label('select')" :image-size="72" />
        <template v-else-if="detail">
          <div class="workbench-header">
            <h2>{{ label("detail") }}</h2>
            <el-tag v-if="detail.blind" type="warning">{{ label("blind") }}</el-tag>
            <el-tag v-else>{{ label(detail.status) }}</el-tag>
          </div>
          <el-steps :active="steps" simple>
            <el-step v-for="stage in path" :key="stage" :title="label(stage)" />
          </el-steps>
          <el-descriptions v-if="identity.length" :column="2">
            <el-descriptions-item v-for="[key, value] in identity" :key="key" :label="label(key)">{{ value }}</el-descriptions-item>
          </el-descriptions>
          <FloatingError v-if="commandIssue" :title="commandIssue" />
          <slot v-if="!taskId" name="open-receipt" :detail="detail" :can="can" />
          <template v-if="taskId">
          <el-card shadow="never">
            <template #header>{{ label("quality") }}</template>
            <el-descriptions v-if="detail.quality.id" :column="2">
              <el-descriptions-item :label="label('qualityNo')">{{ detail.quality.qualityNo }}</el-descriptions-item>
              <el-descriptions-item :label="label('status')">{{ label(detail.quality.status ?? "—") }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.quality.sampleBarcode" :label="label('sampleBarcode')">{{ detail.quality.sampleBarcode }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.quality.decision" :label="label('decision')">{{ label(detail.quality.decision) }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else :description="label('hidden')" :image-size="56" />
            <el-button v-operation="'BulkInboundQualityOrder.inspect_steel'" v-if="steel && detail.stage === 'WAIT_QUALITY' && !detail.quality.id && can('BulkInboundQualityOrder.inspect_steel')"
              link type="primary" @click="beginDocument('quality','inspect_steel')">{{ language === 0 ? "钢铁质保书与外观核验" : "Steel inspection" }}</el-button>
            <el-button v-operation="'BulkInboundQualityOrder.decide_quality'" v-if="detail.quality.status === 'REGISTERED' && can('BulkInboundQualityOrder.decide_quality')"
              link type="primary" @click="beginDocument('quality','decide_quality')">{{ language === 0 ? "录入化验结果" : "Record lab results" }}</el-button>
            <el-button v-operation="'BulkInboundQualityOrder.register_sample'" v-if="canSample && action !== 'sample'" type="primary" link @click="action = 'sample'">
              {{ label(detail.quality.id ? "retest" : "sample") }}
            </el-button>
            <el-form v-if="action === 'sample' && canSample" :model="sampleInput" label-position="top" @submit.prevent="submitForm">
              <el-form-item :label="label('qualityNo')" required>
                <slot name="document-number" operation="BulkInboundQualityOrder.register_sample" :value="sampleInput.qualityNo"
                  :disabled="saving" :update="value => sampleInput.qualityNo = value" />
              </el-form-item>
              <el-form-item :label="label('sampleBarcode')" required><el-input v-model="sampleInput.barcode" maxlength="64" /></el-form-item>
              <el-button v-operation="'BulkInboundQualityOrder.register_sample'" type="primary" native-type="button" :loading="saving" @click="submitForm">{{ label("confirm") }}</el-button>
              <el-button :disabled="saving" @click="action = undefined">{{ label("cancel") }}</el-button>
            </el-form>
          </el-card>

          <el-card shadow="never">
            <template #header>{{ label("metering") }}</template>
            <el-button v-operation="'BulkInboundMeteringOrder.confirm_steel'" v-if="steel && detail.stage === 'WAIT_GROSS' && !detail.metering.id && can('BulkInboundMeteringOrder.confirm_steel')"
              link type="primary" @click="beginDocument('metering','confirm_steel')">{{ language === 0 ? "钢铁双计量" : "Steel measurement" }}</el-button>
            <el-descriptions v-if="detail.metering.id" :column="2">
              <el-descriptions-item :label="label('meteringNo')">{{ detail.metering.meteringNo }}</el-descriptions-item>
              <el-descriptions-item :label="label('method')">{{ label(detail.metering.method ?? "—") }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.metering.physicalNetKg" :label="label('physicalNetKg')">{{ detail.metering.physicalNetKg }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.metering.packages" :label="label('packages')">{{ detail.metering.packages }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else :description="label('hidden')" :image-size="56" />
            <el-button v-operation="'BulkInboundMeteringOrder.register_external'" v-if="canExternal && action !== 'external'" type="primary" link @click="action = 'external'">{{ label("external") }}</el-button>
            <el-button v-operation="'BulkInboundMeteringOrder.record_gross'" v-if="canGross && action !== 'gross'" type="primary" link @click="action = 'gross'">{{ label("gross") }}</el-button>
            <el-button v-operation="'BulkInboundMeteringOrder.record_tare'" v-if="canTare && action !== 'tare'" type="primary" link @click="action = 'tare'">{{ label("tare") }}</el-button>
            <el-button v-operation="'BulkInboundTask.confirm_unloading'" v-if="canUnload && action !== 'unload'" type="primary" link @click="action = 'unload'">{{ label("unload") }}</el-button>
            <el-form v-if="action === 'external' && canExternal" :model="externalInput" label-position="top" @submit.prevent="submitForm">
              <el-form-item :label="label('meteringNo')" required>
                <slot name="document-number" operation="BulkInboundMeteringOrder.register_external" :value="externalInput.meteringNo"
                  :disabled="saving" :update="value => externalInput.meteringNo = value" />
              </el-form-item>
              <el-form-item :label="label('source')" required>
                <el-select v-model="externalInput.source">
                  <el-option :label="label('rail')" value="RAIL_SCALE" /><el-option :label="label('survey')" value="DRAFT_SURVEY" />
                </el-select>
              </el-form-item>
              <el-form-item :label="label('externalReference')" required><el-input v-model="externalInput.externalReference" maxlength="96" /></el-form-item>
              <el-form-item :label="label('netKg')" required><el-input v-model="externalInput.netKg" inputmode="decimal" maxlength="19" /></el-form-item>
              <el-button v-operation="'BulkInboundMeteringOrder.register_external'" type="primary" native-type="button" :loading="saving" @click="submitForm">{{ label("confirm") }}</el-button>
              <el-button :disabled="saving" @click="action = undefined">{{ label("cancel") }}</el-button>
            </el-form>
            <el-form v-if="(action === 'gross' && canGross) || (action === 'tare' && canTare)"
              :model="ticketInput" label-position="top" @submit.prevent="submitForm">
              <el-form-item v-if="action === 'gross'" :label="label('meteringNo')" required>
                <slot name="document-number" operation="BulkInboundMeteringOrder.record_gross" :value="grossInput.meteringNo"
                  :disabled="saving" :update="value => grossInput.meteringNo = value" />
              </el-form-item>
              <InboundWeighTicketFields :model-value="ticketInput" @update:model-value="value => Object.assign(ticketInput, value)" />
              <el-button v-operation="action === 'gross' ? 'BulkInboundMeteringOrder.record_gross' : 'BulkInboundMeteringOrder.record_tare'" type="primary" native-type="button" :loading="saving" @click="submitForm">{{ label("confirm") }}</el-button>
              <el-button :disabled="saving" @click="action = undefined">{{ label("cancel") }}</el-button>
            </el-form>
            <el-form v-if="action === 'unload' && canUnload" :model="unloadInput" label-position="top" @submit.prevent="submitForm">
              <el-form-item :label="label('outcome')" required>
                <el-select v-model="unloadInput.outcome">
                  <el-option v-for="value in unloadOptions" :key="value" :label="label(value)" :value="value" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="unloadInput.outcome === 'ACCEPTED_WITH_DEDUCTION'" :label="label('extraDeductionKg')" required>
                <el-input v-model="unloadInput.extraDeductionKg" inputmode="decimal" maxlength="19" />
              </el-form-item>
              <el-button v-operation="'BulkInboundTask.confirm_unloading'" type="primary" native-type="button" :loading="saving" @click="submitForm">{{ label("confirm") }}</el-button>
              <el-button :disabled="saving" @click="action = undefined">{{ label("cancel") }}</el-button>
            </el-form>
          </el-card>

          <el-card v-if="totals.length" shadow="never" class="workbench-totals">
            <template #header>{{ label("totals") }}</template>
            <el-descriptions :column="2">
              <el-descriptions-item v-for="[key, value] in totals" :key="key" :label="label(key)">
                {{ key === "qualityState" ? label(value ?? "—") : value }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
          </template>
        </template>
      </section>

      <section v-if="detail && showSite" class="workbench-area" :aria-label="label('instruments')">
        <h2>{{ label("instruments") }}</h2>
        <el-descriptions v-if="detail?.risk.unloadDisposition" :column="1">
          <el-descriptions-item :label="label('unloadDisposition')">{{ label(detail.risk.unloadDisposition) }}</el-descriptions-item>
        </el-descriptions>
        <InboundSitePanel v-if="can('BulkInboundMeteringOrder.read_workbench_meter')" :task-id="detail.taskId" :read="api.site" />
        <InboundSiteCommands :task-id="detail.taskId" :status="detail.status" :can="can" :api="api" :error-text="errorText" />
      </section>
    </div>
    <ManagedDialog :model-value="Boolean(actionDialog)" :title="actionDialog?.command.title[language] ?? ''" :busy="saving"
      @update:model-value="value => { if (!value) actionDialog = undefined; }">
      <FloatingError :title="dialogError" />
      <el-form v-if="actionDialog" label-position="top" @submit.stop.prevent="submitDocument">
        <el-form-item v-for="[key, zh, en, type, optional] in actionDialog.command.fields.filter(field =>
          field[3] !== 'ticket' || !field[4] || commandFields.basis === 'SCALE')" :key="key"
          :label="language === 0 ? zh : en" :required="!optional">
          <InboundWeighTicketFields v-if="type === 'ticket'" v-model="commandTickets[key]" />
          <el-select v-else-if="type === 'boolean'" v-model="commandFields[key]">
            <el-option :label="language === 0 ? '通过' : 'Accepted'" :value="true" />
            <el-option :label="language === 0 ? '不通过' : 'Rejected'" :value="false" />
          </el-select>
          <el-select v-else-if="Array.isArray(type)" v-model="commandFields[key]">
            <el-option v-for="value in type" :key="value" :label="value" :value="value" />
          </el-select>
          <el-input-number v-else-if="type === 'integer'" :model-value="commandFields[key] as number | undefined"
            :precision="0" :min="0" @update:model-value="value => commandFields[key] = value ?? undefined" />
          <el-input v-else :model-value="commandFields[key] as string | undefined" @update:model-value="value => commandFields[key] = value" />
        </el-form-item>
        <InboundLabFields v-if="actionDialog.command.operation === 'decide_quality'" ref="labEditor"
          :id="detail?.quality.id" :load="api.labMetrics" :error-text="errorText" />
        <InboundInspectionAttachments v-if="actionDialog.command.operation === 'inspect_steel'" ref="attachmentEditor"
          :task-id="detail?.taskId" :load="api.attachmentOptions" :error-text="errorText">
          <template #upload="scope"><slot name="inspection-upload" v-bind="scope" /></template>
        </InboundInspectionAttachments>
      </el-form>
      <template #footer>
        <el-button v-operation="documentOperation" type="primary" :loading="saving"
          :disabled="actionDialog?.command.operation === 'decide_quality' && !labEditor?.canSubmit()"
          @click="submitDocument">{{ language === 0 ? "确认提交" : "Submit" }}</el-button>
      </template>
    </ManagedDialog>
  </el-card>
</template>

<style scoped>
.inbound-workbench :deep(.el-card__body),
.inbound-workbench :deep(.el-card__header) { padding: var(--app-content-padding); }
.inbound-workbench :deep(.el-card__body) { display: grid; gap: var(--app-section-gap); }
.workbench-header { display: flex; align-items: center; justify-content: space-between; gap: var(--app-section-gap); }
.workbench-header h1 { margin: 0; font-size: var(--el-font-size-extra-large); }
.workbench-overview { display: grid; gap: var(--app-section-gap); }
.workbench-scope { display: flex; flex-wrap: wrap; gap: var(--app-section-gap); }
.workbench-stages { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: var(--app-section-gap); }
.workbench-stage { height: auto; padding: var(--app-content-padding); }
.workbench-overview :deep(.el-button + .el-button) { margin-inline-start: 0; }
.stage-summary { display: grid; gap: var(--app-section-gap); white-space: normal; }
.stage-summary strong { font-size: var(--el-font-size-extra-large); font-variant-numeric: tabular-nums; }
.workbench-grid { display: grid; grid-template-columns: minmax(15rem, 1fr) minmax(0, 2fr); gap: var(--app-section-gap); }
.workbench-grid.workbench-with-site { grid-template-columns: minmax(15rem, 1fr) minmax(0, 2fr) minmax(13rem, 1fr); }
.workbench-grid.workbench-single { grid-template-columns: minmax(0, 1fr); }
.workbench-area { display: flex; flex-direction: column; gap: var(--app-section-gap); min-width: 0; }
.queue-area { background: var(--el-fill-color-light); padding: var(--app-content-padding); }
.queue-area .workbench-header > span { color: var(--el-text-color-secondary); font-size: var(--el-font-size-small); }
.workbench-area h2 { margin: 0; font-size: var(--el-font-size-medium); }
.workbench-area :deep(.el-steps--simple) { overflow-x: auto; }
.queue-rows { display: grid; gap: var(--app-section-gap); }
.queue-item {
  display: grid;
  gap: var(--app-section-gap);
  padding: var(--app-content-padding);
  background: var(--el-bg-color);
  border: var(--el-border);
  border-radius: var(--el-border-radius-base);
  overflow-wrap: anywhere;
}
.queue-item-selected { border-color: var(--el-color-primary); }
.queue-item-heading, .queue-item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-section-gap);
}
.queue-item-meta small { color: var(--el-text-color-secondary); }
.workbench-totals { position: sticky; bottom: 0; z-index: 1; }
@media (max-width: 1200px) {
  .workbench-grid.workbench-with-site { grid-template-columns: minmax(15rem, 1fr) minmax(0, 2fr); }
  .workbench-grid.workbench-single { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 1024px) { .workbench-stages { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 768px) {
  .workbench-grid, .workbench-grid.workbench-with-site { grid-template-columns: minmax(0, 1fr); }
  .workbench-totals { position: static; }
}
</style>
