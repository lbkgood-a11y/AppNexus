<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import {
  BusinessWorkspace,
  ManagedDialog,
  ManagementTable,
  type ManagementColumn,
} from "@appnexus/ui";
import WorkflowDesigner from "./WorkflowDesigner.vue";
import type { WorkflowApi, Definition, Capability, Row, BusinessConfiguration } from "./index";
/** 三工作区共享模板表格与反馈；只有已授权且已接通的动作才显示，任务权由后端逐次复核。 */
const props = defineProps<{
  /** 独立设计器入口只显示流程定义，任务入口不再混入编排Tab。 */
  workspace?: "tasks" | "designer";
  api: WorkflowApi;
  errorText: (e: unknown) => string;
}>();
const { t, locale } = useI18n();
const router = useRouter();
const route=useRoute();
const comment = ref("");
const tab = ref(props.workspace === "designer" ? (route.query.create==='1'?"definitions":"contracts") : "todo"),
  caps = ref<string[]>([]),
  businesses = ref<Capability[]>([]),
  rows = ref<Row[]>([]),
  definitions = ref<Definition[]>([]),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  query = ref(""),
  submitted = ref(""),
  page = ref(1),
  size = ref(20);
const contracts=ref<BusinessConfiguration["documents"]>([]), contractDetail=ref<BusinessConfiguration["documents"][number]>();
const designer = ref(false),
  editing = ref<Definition>(),
  selectedTask = ref<Row>(),
  taskOpen = ref(false),
  detail = ref<Row>(),
  detailOpen = ref(false),
  values = ref<Record<string, string>>({}),
  recovery = ref(false),
  recoveryKind = ref("retry"),
  target = ref(""),
  actor = ref(""),
  reason = ref("");
const formatTime = (value: string) =>
  new Intl.DateTimeFormat(locale.value.startsWith("en") ? "en-US" : "zh-CN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
const can = (a: string) => caps.value.includes(a);
const tabs = computed(() =>
  props.workspace === "designer"
    ? ["contracts", "definitions"]
    : [
        "todo",
        "done",
        "started",
        "copied",

        ...(can("operate") ? ["instances"] : []),
      ],
);
const title = computed(() => t("workflow.tabs." + tab.value));
const filtered = computed(() => {
  const q = submitted.value.toLowerCase();
  return rows.value.filter(
    (r) =>
      !q ||
      [r.name, r.code,r.objectType, r.object_type, r.object_id, r.purpose, r.state, r.node_id].some(
        (v) =>
          String(v || "")
            .toLowerCase()
            .includes(q),
      ),
  );
});
const paged = computed(() =>
  filtered.value.slice((page.value - 1) * size.value, page.value * size.value),
);
const columns = computed<ManagementColumn[]>(() =>
  tab.value === "contracts" ? [
    {key:"name",prop:"name",label:t("workflow.name"),minWidth:160},
    {key:"code",prop:"code",label:t("document.code"),minWidth:190},
    {key:"version",prop:"version",label:t("document.contractVersion"),width:110},
    {key:"objectType",prop:"objectType",label:t("workflow.objectType"),minWidth:140},
    {key:"operations",label:t("workflow.operations"),width:180,fixed:"right"},
  ] : tab.value === "definitions"
    ? [
        { key: "name", prop: "name", label: t("workflow.name"), minWidth: 180 },
        {
          key: "objectType",
          prop: "objectType",
          label: t("workflow.objectType"),
          minWidth: 130,
        },
        { key: "mode", label: t("workflow.mode"), minWidth: 130 },
        {
          key: "published",
          prop: "published",
          label: t("workflow.publishedVersion"),
          width: 100,
        },
        { key: "enabled", label: t("workflow.state"), width: 100 },
        {
          key: "operations",
          label: t("workflow.operations"),
          width: 180,
          fixed: "right",
        },
      ]
    : [
        {
          key: "object_type",
          prop: "object_type",
          label: t("workflow.objectType"),
          minWidth: 140,
        },
        {
          key: "object_id",
          prop: "object_id",
          label: t("workflow.objectId"),
          minWidth: 180,
        },
        {
          key: "node_id",
          prop: "node_name",
          label: t("workflow.node"),
          minWidth: 130,
        },
        { key: "state", label: t("workflow.state"), minWidth: 110 },
        {
          key: "operations",
          label: t("workflow.operations"),
          width: 160,
          fixed: "right",
        },
      ],
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    caps.value = await props.api.capabilities();
    businesses.value = await props.api.businesses();
    if (!tabs.value.includes(tab.value))
      tab.value = props.workspace === "designer" ? (route.query.create==='1'?"definitions":"contracts") : "todo";
    if(tab.value === "contracts"){
      contracts.value=(await props.api.configuration()).documents;
      rows.value=contracts.value.map((entry,index)=>({id:String(index),name:locale.value.startsWith('zh')?entry.profile.nameZh:entry.profile.nameEn,code:entry.dataContract?.code||entry.capability,version:entry.dataContract?.version||1,objectType:entry.profile.objectType}));
    } else if (tab.value === "definitions") {
      definitions.value = await props.api.definitions();
      rows.value = definitions.value.map((d) => ({
        ...d,
        ...JSON.parse(d.model),
      }));
    } else
      rows.value =
        tab.value === "instances"
          ? await props.api.instances()
          : await props.api.tasks(tab.value);
  } catch (e) {
    error.value = props.errorText(e);
  } finally {
    loading.value = false;
  }
}
watch(tab, () => {
  page.value = 1;
  query.value = "";
  submitted.value = "";
  void load();
});
onMounted(async()=>{await load();if(props.workspace==='designer'&&route.query.create==='1'&&can('edit'))openDesigner();});
async function run(action: () => Promise<void>) {
  busy.value = true;
  error.value = "";
  try {
    await action();
  } catch (e) {
    error.value = props.errorText(e);
  } finally {
    busy.value = false;
  }
}
function openDesigner(id?: string) {
  editing.value = definitions.value.find((d) => d.id === id);
  designer.value = true;
}
async function openTask(row: Row) {
  await run(async () => {
    selectedTask.value = await props.api.task(row.id);
    values.value = {};
    comment.value = selectedTask.value?.task.comment || "";
    taskOpen.value = true;
  });
}
async function openDetail(row: Row) {
  await run(async () => {
    detail.value = await props.api.detail(row.instance_id || row.id);
    detailOpen.value = true;
  });
}
async function complete(action: string) {
  await run(async () => {
    await props.api.complete(
      selectedTask.value!.task.id,
      action,
      values.value,
      comment.value,
    );
    ElMessage.success(t("workflow.saved"));
    if (action !== "SAVE") taskOpen.value = false;
    await load();
  });
}
function businessRoute() {
  const b = selectedTask.value?.business;
  if (b?.route && b.route.startsWith("/") && !b.route.startsWith("//")) {
    const i = selectedTask.value!.instance;
    void router.push({
      path: b.route,
      query: { objectId: i.object_id, taskId: selectedTask.value!.task.id },
    });
    taskOpen.value = false;
  }
}
function openRecovery(kind: string, id: string) {
  recoveryKind.value = kind;
  target.value = id;
  reason.value = "";
  actor.value = "";
  recovery.value = true;
}
async function recover() {
  if (
    !reason.value.trim() ||
    (recoveryKind.value === "reassign" && !actor.value.trim())
  )
    return;
  await run(async () => {
    if (recoveryKind.value === "retry")
      await props.api.retry(target.value, reason.value);
    else if (recoveryKind.value === "reassign")
      await props.api.reassign(target.value, actor.value, reason.value);
    else if (recoveryKind.value === "withdraw")
      await props.api.withdraw(target.value, reason.value);
    else {
      const d = definitions.value.find((d) => d.id === target.value)!;
      await props.api.disable(d.id, d.revision, reason.value);
    }
    recovery.value = false;
    ElMessage.success(t("workflow.saved"));
    if (detailOpen.value)
      detail.value = await props.api.detail(detail.value!.instance.id);
    await load();
  });
}
</script>
<template>
  <div class="management-page">
    <WorkflowDesigner
      v-if="workspace === 'designer' && designer"
      :key="editing?.id || 'new'"
      :definition="editing"
      :api="api"
      :businesses="businesses"
      :can-edit="can('edit')"
      :can-publish="can('publish')"
      :error-text="errorText"
      @saved="load"
      @close="designer = false"
    />
    <ManagedDialog :model-value="!!contractDetail" :title="t('workflow.contractFields')" width="760px" @update:model-value="contractDetail=undefined">
      <div v-if="contractDetail" class="contract-detail">
        <p>{{contractDetail.dataContract?.code}} / v{{contractDetail.dataContract?.version}}</p>
        <el-table :data="contractDetail.profile.fields"><el-table-column prop="key" :label="t('document.code')"/><el-table-column :label="t('workflow.name')"><template #default="{row}">{{locale.startsWith('zh')?row.nameZh:row.nameEn}}</template></el-table-column><el-table-column prop="type" :label="t('workflow.contractType')"/><el-table-column prop="unitZh" :label="t('workflow.contractUnit')"/></el-table>
        <el-alert :title="t('workflow.contractOrderHint')" type="info" :closable="false"/>
      </div>
    </ManagedDialog>
    <BusinessWorkspace
      v-if="!designer"
      :title="
        t(workspace === 'designer' ? 'workflow.designer' : 'workflow.title')
      "
      ><template #navigation
        ><el-tabs v-model="tab"
          ><el-tab-pane
            v-for="view in tabs"
            :key="view"
            :name="view"
            :label="workspace==='designer'?`${view==='contracts'?1:2}. ${t('workflow.tabs.'+view)}`:t('workflow.tabs.'+view)" /></el-tabs></template
      ><template
        v-if="error && !designer && !taskOpen && !detailOpen && !recovery"
        #feedback
        ><el-alert type="error" :title="error" :closable="false"
          ><el-button link @click="load">{{
            t("account.retry")
          }}</el-button></el-alert
        ></template
      ><template #search
        ><div class="management-filter">
          <div class="management-filter-header"><h2 class="management-filter-title">{{ title }}</h2></div>
          <el-form
            inline
            @submit.prevent="
              submitted = query;
              page = 1;
            "
            ><el-form-item :label="t('workflow.query')"
              ><el-input v-model="query" clearable
            /></el-form-item>
            <div class="management-filter-actions">
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
            </div></el-form
          >
        </div></template
      ><template #data
        ><div class="workflow-data">
        <el-alert v-if="tab==='contracts'" class="contract-source-hint" :description="t('workflow.contractSourceHint')" type="info" :closable="false" />
        <ManagementTable
          embedded
          :title="title"
          :rows="paged"
          :columns="columns"
          :total="filtered.length"
          :loading="loading"
          :busy="busy"
          :empty-text="t('workflow.empty')"
          @refresh="load"
          ><template #actions
            ><el-button
              v-if="tab === 'definitions' && can('edit')"
              type="primary"
              @click="openDesigner()"
              >{{ t("workflow.newDefinition") }}</el-button
            ></template
          ><template #mode="{ row }">{{
            t("workflow.modes." + row.mode)
          }}</template
          ><template #enabled="{ row }"
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{
              t(row.enabled ? "workflow.enabled" : "workflow.disabled")
            }}</el-tag></template
          ><template #state="{ row }"
            ><el-tag>{{
              t("workflow.states." + row.state, row.state)
            }}</el-tag></template
          ><template #operations="{ row }"
            ><template v-if="tab==='contracts'"><el-button link @click="contractDetail=contracts[Number(row.id)]">{{t('workflow.contractFields')}}</el-button></template><template v-else-if="tab === 'definitions'"
              ><el-button link type="primary" @click="openDesigner(row.id)">{{
                t("workflow.design")
              }}</el-button
              ><el-button v-if="row.published" link @click="router.push({path:'/documents/workbench',query:{type:row.objectType}})">{{t('workflow.configureBinding')}}</el-button><el-button
                v-if="can('publish') && row.enabled"
                link
                type="danger"
                @click="openRecovery('disable', row.id)"
                >{{ t("workflow.disable") }}</el-button
              ></template
            ><template v-else
              ><el-button
                v-if="tab === 'todo'"
                link
                type="primary"
                @click="openTask(row)"
                >{{ t("workflow.process") }}</el-button
              ><el-button
                v-if="tab === 'started' && row.state === 'RUNNING'"
                link
                @click="openRecovery('withdraw', row.id)"
                >{{ t("workflow.withdraw") }}</el-button
              ><el-button link @click="openDetail(row)">{{
                t("workflow.history")
              }}</el-button></template
            ></template
          ><template #pagination
            ><el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              :total="filtered.length" /></template></ManagementTable
        ><el-alert
          v-if="rows.length === 100"
          :title="t('workflow.limitHint')"
          type="info"
          :closable="false" /></div></template
    ></BusinessWorkspace>
    <ManagedDialog
      v-model="taskOpen"
      :title="selectedTask?.node.name || t('workflow.process')"
      width="min(720px, calc(100vw - 32px))"
      :busy="busy"
      ><el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
      /><template v-if="selectedTask"
        ><el-descriptions :column="1" border
          ><el-descriptions-item :label="t('workflow.objectType')">{{
            selectedTask.instance.object_type
          }}</el-descriptions-item
          ><el-descriptions-item :label="t('workflow.objectId')">{{
            selectedTask.instance.object_id
          }}</el-descriptions-item
          ><el-descriptions-item :label="t('workflow.state')">{{
            t("workflow.states." + selectedTask.task.state)
          }}</el-descriptions-item></el-descriptions
        ><el-button
          v-if="selectedTask.business.route"
          class="business-link"
          @click="businessRoute"
          >{{ t("workflow.openDocument") }}</el-button
        ><el-form label-position="top"
          ><el-form-item
            v-for="field in selectedTask.business.inputs || []"
            :key="field"
            :label="field"
            :required="selectedTask.node.required?.includes(field)"
            ><el-input v-model="values[field]" maxlength="2000" /></el-form-item
        ></el-form>
        <el-form label-position="top"
          ><el-form-item :label="t('workflow.comment')"
            ><el-input
              v-model="comment"
              type="textarea"
              maxlength="2000"
              show-word-limit /></el-form-item
        ></el-form>
        <div class="actions">
          <template v-if="selectedTask.node.kind === 'HANDLE'"
            ><el-button :loading="busy" @click="complete('SAVE')">{{
              t("workflow.save")
            }}</el-button
            ><el-button
              type="primary"
              :loading="busy"
              @click="complete('SUBMIT')"
              >{{ t("workflow.submit") }}</el-button
            ></template
          ><template v-else
            ><el-popconfirm
              :title="t('workflow.confirmReject')"
              @confirm="complete('REJECT')"
              ><template #reference
                ><el-button type="danger" :disabled="busy">{{
                  t("workflow.reject")
                }}</el-button></template
              ></el-popconfirm
            ><el-button
              type="primary"
              :loading="busy"
              @click="complete('APPROVE')"
              >{{ t("workflow.approve") }}</el-button
            ></template
          >
        </div></template
      ></ManagedDialog
    >
    <ManagedDialog
      v-model="detailOpen"
      :title="t('workflow.history')"
      width="min(1000px, calc(100vw - 32px))"
      ><el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
      /><template v-if="detail"
        ><el-descriptions :column="1" border
          ><el-descriptions-item :label="t('workflow.objectId')">{{
            detail.instance.object_id
          }}</el-descriptions-item
          ><el-descriptions-item :label="t('workflow.state')"
            >{{ t("workflow.states." + detail.instance.state) }} /
            {{
              detail.instance.result
                ? t("workflow.states." + detail.instance.result)
                : "—"
            }}</el-descriptions-item
          ></el-descriptions
        >
        <h3>{{ t("workflow.tasks") }}</h3>
        <el-table :data="detail.tasks"
          ><el-table-column
            prop="node_name"
            :label="t('workflow.node')"
          /><el-table-column :label="t('workflow.actor')"
            ><template #default="{ row }"
              ><span v-if="row.actor">{{ row.actor }}</span
              ><el-tag v-else type="warning">{{
                t("workflow.noAssignee")
              }}</el-tag></template
            ></el-table-column
          ><el-table-column :label="t('workflow.state')"
            ><template #default="{ row }">{{
              t("workflow.states." + row.state, row.state)
            }}</template></el-table-column
          ><el-table-column
            prop="comment"
            :label="t('workflow.comment')"
            show-overflow-tooltip
          />
          <el-table-column
            v-if="can('operate')"
            :label="t('workflow.operations')"
            ><template #default="{ row }"
              ><el-button
                v-if="row.state === 'OPEN'"
                link
                @click="openRecovery('reassign', row.id)"
                >{{ t("workflow.reassign") }}</el-button
              ></template
            ></el-table-column
          ></el-table
        >
        <h3>{{ t("workflow.relatedInstances") }}</h3>
        <el-table :data="detail.relatedInstances || []"
          ><el-table-column
            prop="definition_version"
            :label="t('workflow.publishedVersion')"
          /><el-table-column
            prop="state"
            :label="t('workflow.state')"
          /><el-table-column
            prop="result"
            :label="t('workflow.result')"
          /><el-table-column :label="t('workflow.operations')"
            ><template #default="{ row }"
              ><el-button link @click="openDetail(row)">{{
                t("workflow.history")
              }}</el-button></template
            ></el-table-column
          ></el-table
        >
        <h3>{{ t("workflow.history") }}</h3>
        <el-timeline
          ><el-timeline-item
            v-for="(event, i) in detail.history"
            :key="i"
            :timestamp="formatTime(event.created_at)"
            >{{ t("workflow.audit." + event.action, event.action) }}
            {{ event.detail }}</el-timeline-item
          ></el-timeline
        ><template v-if="detail.jobs"
          ><h3>{{ t("workflow.deliveries") }}</h3>
          <el-table :data="detail.jobs"
            ><el-table-column
              prop="capability"
              :label="t('workflow.capability')"
            /><el-table-column
              prop="state"
              :label="t('workflow.state')"
            /><el-table-column
              prop="attempts"
              :label="t('workflow.attempts')"
            /><el-table-column
              prop="error_code"
              :label="t('workflow.errorCode')"
            /><el-table-column :label="t('workflow.operations')"
              ><template #default="{ row }"
                ><el-button
                  v-if="['MANUAL', 'TIMEOUT'].includes(row.state)"
                  link
                  @click="openRecovery('retry', row.id)"
                  >{{ t("workflow.retry") }}</el-button
                ></template
              ></el-table-column
            ></el-table
          ></template
        ></template
      ></ManagedDialog
    >
    <ManagedDialog
      v-model="recovery"
      :title="t('workflow.' + recoveryKind)"
      width="min(600px, calc(100vw - 32px))"
      :busy="busy"
      ><el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
      /><el-form label-position="top" @submit.prevent="recover"
        ><el-form-item
          v-if="recoveryKind === 'reassign'"
          :label="t('workflow.actor')"
          required
          ><el-input v-model="actor" /></el-form-item
        ><el-form-item :label="t('workflow.reason')" required
          ><el-input v-model="reason" type="textarea" maxlength="500"
        /></el-form-item>
        <div class="actions">
          <el-button
            type="primary"
            native-type="submit"
            :disabled="!reason.trim()"
            :loading="busy"
            >{{ t("workflow." + recoveryKind) }}</el-button
          >
        </div></el-form
      ></ManagedDialog
    >
  </div>
</template>
<style scoped>
/* 工作区与契约详情使用统一垂直节奏，避免表格、提示及摘要贴连；不覆盖公共容器边界。 */
.workflow-data,
.contract-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
/* 长说明按可用宽度换行，避免表格容器随不可断行文本向右撑开。 */
.contract-source-hint :deep(.el-alert__content) {
  min-width: 0;
}
.contract-source-hint :deep(.el-alert__description) {
  margin: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}
.contract-detail > p {
  margin: 0;
  overflow-wrap: anywhere;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.business-link {
  margin: 16px 0;
}
@media (max-width: 600px) {
  .actions {
    flex-wrap: wrap;
  }
  .actions .el-button {
    min-height: 44px;
  }
}
</style>
