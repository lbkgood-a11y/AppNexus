<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { BusinessWorkspace, FloatingError, ManagementTable, PageHeader, RecordAuditDetails, TabbedDetailTemplate,
  type ManagementColumn, type RecordAudit } from "@appnexus/ui";
import type { InboundWorkbenchDetail, WorkbenchStage } from "./inboundWorkbench";
import { workbenchMessages } from "./inboundWorkbenchMessages";


/** 台账仅消费服务端已授权的作业字段。关联单号通过详情权限获取。 */
interface TaskRow {
  id: string;
  number: string;
  status: string;
  stage: WorkbenchStage | null;
  arrivedAt: string | null;
  audit: RecordAudit;
}

export interface TaskPage {
  rows: TaskRow[];
  nextAfterId: string | null;
}

/** 应用宿主负责第一方 HTTP 和会话，页面只消费授权结果。 */
export interface InboundTaskWorkspaceApi {
  list: (number?: string, status?: TaskStatus, afterId?: string) => Promise<TaskPage>;
  detail: (id: string) => Promise<InboundWorkbenchDetail>;
  operations: () => Promise<string[]>;
}
/** 查询枚举与台账接口一致，空值覆盖全部历史及在途作业。 */
const taskStatuses = [
  "ARRIVED", "WORKING", "READY_TO_RECEIVE", "RECEIVED", "RETURN_PENDING", "RETURNED", "CORRECTION_REQUIRED"
] as const;
type TaskStatus = (typeof taskStatuses)[number];

const props = defineProps<{ api: InboundTaskWorkspaceApi; errorText: (error: unknown) => string }>();

const { locale } = useI18n();
const zh = computed(() => locale.value.startsWith("zh"));
const text = (cn: string, en: string) => zh.value ? cn : en;
const title = computed(() => text("入库作业单", "Inbound tasks"));
const columns = computed<ManagementColumn[]>(() => [
  { key: "status", prop: "status", label: text("作业状态", "Status"), minWidth: 108 },
  { key: "number", prop: "number", label: text("作业单号", "Task number"), minWidth: 180 },
  { key: "stage", prop: "stage", label: text("执行阶段", "Stage"), minWidth: 108 },
  { key: "arrivedAt", prop: "arrivedAt", label: text("到场时间", "Arrival time"), minWidth: 176 }
]);
const keyword = ref("");
const statusFilter = ref<TaskStatus>();
const applied = ref<{ number?: string; status?: TaskStatus }>({});
const page = ref<TaskPage>({ rows: [], nextAfterId: null });
const cursors = ref<string[]>([]);
const loading = ref(false);
const issue = ref("");
const mayReadDetail = ref(false);
const selection = ref<TaskRow>();
const detail = ref<InboundWorkbenchDetail>();
const detailIssue = ref("");
const detailLoading = ref(false);
let request = 0;

/** 作业阶段和状态沿用工作台双语文案。 */
function state(value?: string | null) {
  if (!value) return "—";
  const labels: Record<string, string> = workbenchMessages[zh.value ? "zh" : "en"];
  return labels[value] ?? value;
}

/** 按作业结果区分完成、退车和待更正，历史未知状态使用中性标签。 */
function statusType(value: string) {
  switch (value) {
    case "RECEIVED": return "success";
    case "RETURNED": return "danger";
    case "RETURN_PENDING":
    case "CORRECTION_REQUIRED": return "warning";
    case "WORKING":
    case "READY_TO_RECEIVE": return "primary";
    default: return "info";
  }
}

/** 查询与分页关联同一筛选，迟到的请求不能覆盖当前列表。 */
async function load(filters = applied.value, stack = cursors.value) {
  const generation = ++request;
  loading.value = true;
  issue.value = "";
  try {
    const result = await props.api.list(filters.number, filters.status, stack.at(-1));
    if (generation !== request) return;
    page.value = result;
    cursors.value = stack;
    applied.value = filters;
  } catch (cause) {
    if (generation !== request) return;
    page.value = { rows: [], nextAfterId: null };
    cursors.value = [];
    applied.value = filters;
    issue.value = props.errorText(cause) || text("作业单加载失败", "Unable to load inbound tasks");
  } finally {
    if (generation === request) loading.value = false;
  }
}

/** 每次查看均由已有服务端详情操作重新授权。 */
watch(selection, async (row, _, onCleanup) => {
  detail.value = undefined;
  detailIssue.value = "";
  detailLoading.value = Boolean(row);
  if (!row) return;
  let current = true;
  onCleanup(() => { current = false; });
  try {
    const result = await props.api.detail(row.id);
    if (current) detail.value = result;
  } catch (cause) {
    if (current) detailIssue.value = props.errorText(cause) || text("作业详情加载失败", "Unable to load task details");
  } finally {
    if (current) detailLoading.value = false;
  }
});

onMounted(() => {
  void load();
  void props.api.operations().then(ids => {
    mayReadDetail.value = ids.includes("BulkInboundTask.read_workbench_detail");
  }).catch(() => { mayReadDetail.value = false; });
});
</script>

<template>
  <div class="management-page">
    <BusinessWorkspace v-if="!selection" :title="title">
      <template #search>
        <el-form inline @submit.prevent="load({ number: keyword.trim() || undefined, status: statusFilter || undefined }, [])">
          <el-form-item :label="text('作业单号', 'Task number')">
            <el-input v-model="keyword" clearable maxlength="64"
              :placeholder="text('输入完整作业单号', 'Enter the full task number')" />
          </el-form-item>
          <el-form-item :label="text('作业状态', 'Status')">
            <el-select v-model="statusFilter" clearable :placeholder="text('全部状态', 'All statuses')" style="width: 10rem">
              <el-option v-for="status in taskStatuses" :key="status" :label="state(status)" :value="status" />
            </el-select>
          </el-form-item>
          <el-button native-type="submit" type="primary" :loading="loading"
            v-operation="'BulkInboundTask.read_task_list'">{{ text("查询", "Search") }}</el-button>
        </el-form>
      </template>
      <template #data>
        <FloatingError :title="issue" />
        <ManagementTable embedded :title="title" :columns="columns" :rows="page.rows" :loading="loading"
          :empty-text="issue || text('暂无入库作业单', 'No inbound tasks')" @refresh="load()">
          <template #number="{ row }">
            <el-button v-if="mayReadDetail" link type="primary" v-operation="'BulkInboundTask.read_workbench_detail'"
              @click="selection = row">{{ row.number }}</el-button>
            <span v-else>{{ row.number }}</span>
          </template>
          <template #status="{ row }">
            <el-tag class="app-status-tag" :type="statusType(row.status)">{{ state(row.status) }}</el-tag>
          </template>
          <template #stage="{ row }">{{ state(row.stage) }}</template>
          <template #arrivedAt="{ row }">
            {{ row.arrivedAt ? new Date(row.arrivedAt).toLocaleString(locale) : "—" }}
          </template>
          <template #pagination>
            <div class="flex gap-2">
              <el-button v-if="cursors.length" :disabled="loading" v-operation="'BulkInboundTask.read_task_list'"
                @click="load(applied, cursors.slice(0, -1))">{{ text("上一页", "Previous") }}</el-button>
              <el-button v-if="page.nextAfterId" :disabled="loading" v-operation="'BulkInboundTask.read_task_list'"
                @click="load(applied, [...cursors, page.nextAfterId])">{{ text("下一页", "Next") }}</el-button>
            </div>
          </template>
        </ManagementTable>
      </template>
    </BusinessWorkspace>
    <TabbedDetailTemplate v-else master-data system-last>
      <template #header>
        <PageHeader :back-label='text("返回列表", "Back to list")' @back="selection = undefined" :title="title">
          <template #meta>
            <el-tag effect="plain">{{ selection.number }}</el-tag>
            <el-tag v-if="detail" class="app-status-tag" :type="statusType(detail.status)">{{ state(detail.status) }}</el-tag>
          </template>
        </PageHeader>
      </template>
      <template #content>
        <el-card v-loading="detailLoading" shadow="never" class="detail-section">
          <template #header>
            <div class="detail-section-heading"><h2>{{ text("作业信息", "Task information") }}</h2></div>
          </template>
          <FloatingError :title="detailIssue" />
          <p v-if="detailLoading" role="status">{{ text("正在加载作业详情", "Loading task details") }}</p>
          <div v-if="detail" class="detail-field-grid">
            <div class="detail-readonly-field">
              <span class="detail-field-label">{{ text('作业单号', 'Task number') }}</span>
              <span class="detail-field-value">{{ selection.number }}</span>
            </div>
            <div class="detail-readonly-field">
              <span class="detail-field-label">{{ text('作业状态', 'Status') }}</span>
              <span class="detail-field-value"><el-tag class="app-status-tag" :type="statusType(detail.status)">{{ state(detail.status) }}</el-tag></span>
            </div>
            <div class="detail-readonly-field">
              <span class="detail-field-label">{{ text('到场时间', 'Arrival time') }}</span>
              <span class="detail-field-value">{{ selection.arrivedAt ? new Date(selection.arrivedAt).toLocaleString(locale) : "—" }}</span>
            </div>
            <div class="detail-readonly-field">
              <span class="detail-field-label">{{ text('执行阶段', 'Stage') }}</span>
              <span class="detail-field-value">{{ state(detail.stage) }}</span>
            </div>
            <div v-if="detail.quality.qualityNo" class="detail-readonly-field">
              <span class="detail-field-label">{{ text('质检单号', 'Quality order') }}</span>
              <span class="detail-field-value">{{ detail.quality.qualityNo }}</span>
            </div>
            <div v-if="detail.metering.meteringNo" class="detail-readonly-field">
              <span class="detail-field-label">{{ text('计量单号', 'Metering order') }}</span>
              <span class="detail-field-value">{{ detail.metering.meteringNo }}</span>
            </div>
            <div v-if="detail.receipt.receiptNo" class="detail-readonly-field">
              <span class="detail-field-label">{{ text('入库单号', 'Receipt order') }}</span>
              <span class="detail-field-value">{{ detail.receipt.receiptNo }}</span>
            </div>
          </div>
        </el-card>
      </template>
      <template v-if="detail && selection.audit" #system>
        <h2 class="detail-section-title">{{ text("系统信息", "System information") }}</h2>
        <RecordAuditDetails :audit="selection.audit" inline />
      </template>
    </TabbedDetailTemplate>
  </div>
</template>

