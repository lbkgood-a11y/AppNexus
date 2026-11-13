<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  BusinessWorkspace,
  ManagementTable,
  ManagedDialog,
  type ManagementColumn,
} from "@appnexus/ui";
import type { WorkflowApi, Row } from "./index";
import { type LeaveApi } from "./leave";

/** 请假 POC 使用标准列表与表单组合；审批动作仍由流程工作台承担，页面不写入终态。 */
const props = defineProps<{
  api: LeaveApi;
  workflow: WorkflowApi;
  errorText: (e: unknown) => string;
}>();
/** 输出预览由应用装配公开打印组件，不强制进入模板管理路由或扩大模板权限。 */
const emit=defineEmits<{printOutput:[id:string]}>();
const { t,locale } = useI18n();
const actions=ref<Awaited<ReturnType<LeaveApi['actions']>>>([]);
const submitAction=computed(()=>actions.value.find(a=>a.operationKey==='LeaveRequest.submit'&&a.actionCode==='submit'));
const printAction=computed(()=>actions.value.find(a=>a.operationKey==='LeaveRequest.print'&&a.allowed));
const detailPrintAction=computed(()=>actions.value.find(a=>a.operationKey==='LeaveRequest.detail.print'&&a.allowed));
/** 列表和详情别名进入同一业务打印入口，预览只使用后端返回的受控输出标识。 */
async function printDocument(id:string,detail=false){busy.value=true;error.value='';try{const output=await props.api.print(id,detail?'LeaveRequest.detail.print':'LeaveRequest.print');emit('printOutput',output.id);}catch(e){error.value=props.errorText(e);}finally{busy.value=false;}}
const route = useRoute(),
  router = useRouter();
const rows = ref<Row[]>([]),
  caps = ref<string[]>([]),
  selected = ref<Row>();
const loading = ref(false),
  busy = ref(false),
  error = ref("");
const createOpen = ref(false),
  detailOpen = ref(false);
/** 草稿错误只展示在编辑弹窗，避免与列表请求错误重复呈现。 */
const formError=ref("");
const form = ref<FormInstance>();
const editing=ref<Row>();
const history=ref<Row[]>([]), historyOpen=ref(false);
const lifecycle=ref<Row[]>([]);
const actionAvailability=ref<Awaited<ReturnType<LeaveApi['availability']>>>([]);
/** 修改使用业务返回的依据版本，前端不自行解除审批冻结。 */
function edit(row:Row){formError.value="";editing.value=row;Object.assign(input,{organizationId:row.organization_id,leaveType:row.leave_type,startDate:String(row.start_date).slice(0,10),endDate:String(row.end_date).slice(0,10),reason:row.reason});createOpen.value=true;}
async function showCycles(id:string){busy.value=true;error.value='';try{[history.value,lifecycle.value]=await Promise.all([props.api.cycles(id),props.api.timeline(id)]);historyOpen.value=true;}catch(e){error.value=props.errorText(e);}finally{busy.value=false;}}
const organizations = ref<{ id: string; name: string }[]>([]);
const query = ref(""),
  submitted = ref(""),
  page = ref(1),
  size = ref(20);
const input = reactive({
  organizationId: "",
  leaveType: "PERSONAL",
  startDate: "",
  endDate: "",
  reason: "",
});
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const required = () => ({
  required: true,
  message: t("leavePoc.required"),
  trigger: "change",
});
const rules = computed<FormRules>(() => ({
  organizationId: [
    required(),
    { pattern: uuid, message: t("leavePoc.uuid"), trigger: "blur" },
  ],
  leaveType: [required()],
  startDate: [required()],
  endDate: [required()],
  reason: [
    required(),
    { min: 1, max: 2000, message: t("leavePoc.reasonLimit"), trigger: "blur" },
  ],
}));
const filtered = computed(() =>
  rows.value.filter(
    (row) =>
      !submitted.value ||
      [row.id, row.state, row.leave_type, row.reason].some((v) =>
        String(v).toLowerCase().includes(submitted.value.toLowerCase()),
      ),
  ),
);
const paged = computed(() =>
  filtered.value.slice((page.value - 1) * size.value, page.value * size.value),
);
const columns = computed<ManagementColumn[]>(() => [
  { key: "id", prop: "id", label: t("leavePoc.id"), minWidth: 220 },
  { key: "leave_type", label: t("leavePoc.type"), minWidth: 110 },
  {
    key: "start_date",
    prop: "start_date",
    label: t("leavePoc.start"),
    minWidth: 120,
  },
  {
    key: "end_date",
    prop: "end_date",
    label: t("leavePoc.end"),
    minWidth: 120,
  },
  { key: "days", label: t("leavePoc.days"), width: 90 },
  { key: "state", label: t("workflow.state"), minWidth: 120 },
  {
    key: "operations",
    label: t("workflow.operations"),
    width: 420,
    fixed: "right",
  },
]);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    [rows.value, caps.value,actions.value] = await Promise.all([
      props.api.list(),
      props.workflow.capabilities(),
      props.api.actions(),
    ]);
    organizations.value = caps.value.includes("start")
      ? await props.api.organizations()
      : [];
  } catch (e) {
    error.value = props.errorText(e);
  } finally {
    loading.value = false;
  }
}
async function openDocument(id: string, taskId?: string) {
  busy.value = true;
  error.value = "";
  try {
    selected.value = await props.api.read(id, taskId);
    // 有效任务原文读取不授予本人管理诊断，审批人不请求本人动作接口。
    actionAvailability.value=taskId?[]:await props.api.availability(id);
    detailOpen.value = true;
  } catch (e) {
    error.value = props.errorText(e);
  } finally {
    busy.value = false;
  }
}
async function create() {
  if (!(await form.value?.validate().catch(() => false))) return;
  busy.value = true;
  formError.value = "";
  try {
    if(editing.value)await props.api.update(editing.value.id,editing.value.basis_version,{...input});
    else await props.api.create({ ...input });
    editing.value=undefined;
    createOpen.value = false;
    Object.assign(input, {
      organizationId: "",
      leaveType: "PERSONAL",
      startDate: "",
      endDate: "",
      reason: "",
    });
    ElMessage.success(t("leavePoc.saved"));
    await load();
  } catch (e) {
    formError.value = props.errorText(e);
  } finally {
    busy.value = false;
  }
}
async function submit(id: string) {
  busy.value = true;
  error.value = "";
  try {
    if(!submitAction.value?.allowed)throw new Error(t('account.forbidden'));
    await props.api.submit(id,submitAction.value.operationKey);
    ElMessage.success(t("leavePoc.submitted"));
    await load();
  } catch (e) {
    error.value = props.errorText(e);
  } finally {
    busy.value = false;
  }
}
async function fromRoute() {
  if (typeof route.query.objectId === "string")
    await openDocument(
      route.query.objectId,
      typeof route.query.taskId === "string" ? route.query.taskId : undefined,
    );
}
watch(() => route.query, fromRoute);
onMounted(async () => {
  await load();
  await fromRoute();
});
</script>

<template>
  <BusinessWorkspace :title="t('leavePoc.title')">
    <template #feedback>
      <el-alert v-if="error" :title="error" type="error" :closable="false"
        ><el-button text @click="load">{{
          t("management.refresh")
        }}</el-button></el-alert
      >
      <el-alert :title="t('leavePoc.hint')" type="info" :closable="false" />
    </template>
    <template #search>
      <el-form
        inline
        @submit.prevent="
          submitted = query;
          page = 1;
        "
      >
        <el-form-item :label="t('leavePoc.search')"
          ><el-input v-model="query" clearable
        /></el-form-item>
        <div class="leave-search-actions">
          <el-button type="primary" native-type="submit">{{
            t("administration.search")
          }}</el-button
          ><el-button
            @click="
              query = '';
              submitted = '';
              page = 1;
            "
            >{{ t("management.reset") }}</el-button
          >
        </div>
      </el-form>
    </template>
    <template #data>
      <ManagementTable
        embedded
        :title="t('leavePoc.title')"
        :rows="paged"
        :columns="columns"
        :total="filtered.length"
        :loading="loading"
        :busy="busy"
        :empty-text="t('workflow.empty')"
        @refresh="load"
      >
        <template #actions>
          <el-button
            v-if="caps.includes('edit') && caps.includes('read')"
            :disabled="busy"
            @click="router.push('/workflow/designer')"
            >{{ t("leavePoc.configure") }}</el-button
          >
          <el-button
            v-if="caps.includes('start')"
            type="primary"
            :disabled="busy"
            @click="editing=undefined; formError=''; createOpen = true"
            >{{ t("leavePoc.new") }}</el-button
          >
        </template>
        <template #leave_type="{ row }">{{
          t("leavePoc.types." + row.leave_type)
        }}</template>
        <template #days="{ row }"
          ><span class="leave-days">{{ row.days }}</span></template
        >
        <template #state="{ row }"
          ><el-tag>{{ t("leavePoc.states." + row.state) }}</el-tag></template
        >
        <template #operations="{ row }">
          <el-button v-if="printAction" link :disabled="busy" @click="printDocument(row.id)">{{locale.startsWith('zh')?printAction.nameZh:printAction.nameEn}}</el-button>
          <el-button v-if="['DRAFT','REJECTED','WITHDRAWN'].includes(row.state)&&caps.includes('start')" link :disabled="busy" @click="edit(row)">{{t('document.editLeave')}}</el-button>
          <el-button link :disabled="busy" @click="showCycles(row.id)">{{t('document.cycleHistory')}}</el-button>
          <el-button link :disabled="busy" @click="openDocument(row.id)">{{
            t("leavePoc.detail")
          }}</el-button>
          <el-button
            v-if="row.state === 'DRAFT' && submitAction?.allowed"
            link
            type="primary"
            :disabled="busy"
            @click="submit(row.id)"
            >{{ locale.startsWith('zh')?submitAction.nameZh:submitAction.nameEn }}</el-button
          >
        </template>
        <template #pagination
          ><el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            :total="filtered.length"
        /></template>
      </ManagementTable>
      <el-alert
        v-if="rows.length === 100"
        :title="t('workflow.limitHint')"
        type="info"
        :closable="false"
      />
    </template>
  </BusinessWorkspace>
  <ManagedDialog v-model="historyOpen" :title="t('document.cycleHistory')" width="900px"><el-table :data="lifecycle"><el-table-column :label="t('document.lifecycleEvent')"><template #default="{row}">{{t('document.events.'+row.kind)}}</template></el-table-column><el-table-column prop="reference" :label="t('document.checkTarget')" min-width="260"/><el-table-column prop="state" :label="t('document.state')"/><el-table-column prop="at" :label="t('document.occurredAt')"/></el-table><el-table :data="history"><el-table-column prop="basis_version" :label="t('document.contractVersion')" width="140"/><el-table-column prop="instance_id" :label="t('workflow.objectId')" min-width="260"/><el-table-column prop="configuration_version" :label="t('document.publishedVersion')" min-width="160"/><el-table-column prop="created_at" :label="t('leavePoc.start')" min-width="180"/></el-table></ManagedDialog>
  <ManagedDialog
    v-model="createOpen"
    :title="t('leavePoc.new')"
    :busy="busy"
    width="min(640px, calc(100vw - 32px))"
  >
    <el-alert v-if="formError" :title="formError" type="error" :closable="false" />
    <el-form ref="form" :model="input" :rules="rules" label-position="top">
      <el-form-item :label="t('leavePoc.organization')" prop="organizationId"
        ><el-select v-model="input.organizationId" filterable
          ><el-option
            v-for="organization in organizations"
            :key="organization.id"
            :value="organization.id"
            :label="organization.name"
        /></el-select>
        <div>{{ t("leavePoc.organizationHint") }}</div></el-form-item
      >
      <el-form-item :label="t('leavePoc.type')" prop="leaveType"
        ><el-select v-model="input.leaveType"
          ><el-option
            v-for="value in ['PERSONAL', 'ANNUAL', 'SICK']"
            :key="value"
            :value="value"
            :label="t('leavePoc.types.' + value)" /></el-select
      ></el-form-item>
      <el-form-item :label="t('leavePoc.start')" prop="startDate"
        ><el-date-picker
          v-model="input.startDate"
          type="date"
          value-format="YYYY-MM-DD"
      /></el-form-item>
      <el-form-item :label="t('leavePoc.end')" prop="endDate"
        ><el-date-picker
          v-model="input.endDate"
          type="date"
          value-format="YYYY-MM-DD"
      /></el-form-item>
      <el-form-item :label="t('leavePoc.reason')" prop="reason"
        ><el-input
          v-model="input.reason"
          type="textarea"
          :maxlength="2000"
          show-word-limit
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button :disabled="busy" @click="createOpen = false">{{
        t("leavePoc.cancel")
      }}</el-button
      ><el-button type="primary" :loading="busy" @click="create">{{
        t("leavePoc.saveDraft")
      }}</el-button></template
    >
  </ManagedDialog>
  <ManagedDialog
    v-model="detailOpen"
    :title="t('leavePoc.detail')"
    width="min(720px, calc(100vw - 32px))"
  >
    <el-descriptions v-if="selected" :column="1" border>
      <el-descriptions-item v-if="actionAvailability.length" :label="t('document.actionAvailability')"><el-tag v-for="a in actionAvailability" :key="a.operationKey" :type="a.allowed?'success':'info'">{{a.operationKey}}：{{t('document.availability.'+a.reasonCode)}}</el-tag></el-descriptions-item>
      <el-descriptions-item v-if="detailPrintAction" :label="t('document.operations')"><el-button :loading="busy" @click="printDocument(selected.id,true)">{{locale.startsWith('zh')?detailPrintAction.nameZh:detailPrintAction.nameEn}}</el-button></el-descriptions-item>
      <el-descriptions-item :label="t('leavePoc.id')">{{
        selected.id
      }}</el-descriptions-item>
      <el-descriptions-item :label="t('leavePoc.type')">{{
        t("leavePoc.types." + selected.leave_type)
      }}</el-descriptions-item>
      <el-descriptions-item :label="t('leavePoc.start')">{{
        selected.start_date
      }}</el-descriptions-item>
      <el-descriptions-item :label="t('leavePoc.end')">{{
        selected.end_date
      }}</el-descriptions-item>
      <el-descriptions-item :label="t('leavePoc.days')">{{
        selected.days
      }}</el-descriptions-item>
      <el-descriptions-item :label="t('workflow.state')">{{
        t("leavePoc.states." + selected.state)
      }}</el-descriptions-item>
      <el-descriptions-item :label="t('leavePoc.reason')"
        ><span class="leave-reason">{{
          selected.reason
        }}</span></el-descriptions-item
      >
    </el-descriptions>
  </ManagedDialog>
</template>

<style scoped>
.leave-search-actions {
  display: flex;
  justify-content: flex-end;
  flex: 1;
}
:deep(.el-form--inline) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.leave-reason {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.leave-days {
  display: block;
  text-align: right;
}
</style>
