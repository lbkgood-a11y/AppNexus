<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import { onBeforeRouteLeave, useRoute } from "vue-router";
import { businessModel } from './businessConfiguration';
import { ManagedDialog } from "@appnexus/ui";
import { layoutGraph } from "./layout";
import { connectionError } from "./graphEditing";
import WorkflowCanvas from "./WorkflowCanvas.vue";
import BusinessWorkflowConfiguration from "./BusinessWorkflowConfiguration.vue";
import {
  emptyModel,
  newNode,
  type Definition,
  type WorkflowModel,
  type WorkflowApi,
  type Capability,
  type Kind,
  type BusinessConfiguration,
} from "./index";
/** 整页流程设计器：节点库、拓扑画布和上下文属性复用Element Plus；发布以服务端校验为准。 */
const props = defineProps<{
  definition?: Definition;
  api: WorkflowApi;
  businesses: Capability[];
  canEdit: boolean;
  canPublish: boolean;
  errorText: (e: unknown) => string;
}>();
const emit = defineEmits<{ saved: []; close: [] }>();
const { t, locale } = useI18n();
const route=useRoute();
const catalog = ref<BusinessConfiguration>();
const configurationLoading = ref(false);
const view = ref("business");
const currentProfile = computed(
  () =>
    catalog.value?.documents.find(
      (d) => d.capability === model.value.entryCapability,
    )?.profile,
);
/** 目录加载失败保留原草稿；业务配置不在前端伪造单据或选人数据。 */
async function loadConfiguration() {
  configurationLoading.value = true;
  try {
    catalog.value = await props.api.configuration();
    if(!props.definition&&typeof route.query.documentType==='string'){
      const entry=catalog.value.documents.find(d=>d.profile.objectType===route.query.documentType);
      if(entry)model.value=businessModel(entry.capability,entry.profile,{title:locale.value.startsWith('zh')?entry.profile.nameZh:entry.profile.nameEn,start:t('workflow.kinds.START'),review:t('workflow.kinds.REVIEW'),end:t('workflow.kinds.END')});
    }
    if (
      (model.value.entryCapability &&
        !catalog.value.documents.some(
          (d) => d.capability === model.value.entryCapability,
        )) ||
      model.value.mode === "BUSINESS" ||
      model.value.nodes.some((n) =>
        ["ACTION", "WAIT", "HANDLE", "FORK", "JOIN"].includes(n.kind),
      )
    )
      view.value = "advanced";
  } catch (e) {
    error.value = props.errorText(e);
  } finally {
    configurationLoading.value = false;
  }
}
onMounted(loadConfiguration);
const model = ref<WorkflowModel>(
  props.definition ? JSON.parse(props.definition.model) : emptyModel(),
);
const previewOpen = ref(false),
  sample = ref<Record<string, string>>({}),
  trace = ref<string[]>([]);
const id = ref(props.definition?.id || crypto.randomUUID()),
  revision = ref(props.definition?.revision ?? -1),
  selected = ref(model.value.nodes[0]?.id || ""),
  busy = ref(false),
  error = ref(""),
  validation = ref<string[] | null>(null),
  versions = ref<any[]>([]),
  history = ref(false),
  kind = ref<Kind>("REVIEW");
const node = computed(() =>
  model.value.nodes.find((n) => n.id === selected.value),
);
const capability = computed(() =>
  props.businesses.find((c) => c.key === node.value?.capability),
);
const fields = computed(() => [
  ...new Set([
    ...props.businesses.flatMap((b) => b.outputs),
    ...model.value.nodes
      .filter((n) => n.kind === "REVIEW")
      .map((n) => n.id + "_decision"),
  ]),
]);
const inspector = ref("flow"),
  paletteQuery = ref(""),
  zoom = ref(1);
const baseline = ref(JSON.stringify(model.value));
const dirty = computed(() => JSON.stringify(model.value) !== baseline.value);
const paletteKinds = computed(() =>
  kinds.filter((k) =>
    t("workflow.kinds." + k)
      .toLowerCase()
      .includes(paletteQuery.value.toLowerCase()),
  ),
);
const selectedName = computed(
  () => node.value?.name || t("workflow.selectNode"),
);
const kinds: Kind[] = [
  "START",
  "END",
  "REVIEW",
  "HANDLE",
  "ACTION",
  "WAIT",
  "CHOICE",
  "MERGE",
  "FORK",
  "JOIN",
];
function add(value: Kind = kind.value, position?: { x: number; y: number }) {
  if (!props.canEdit || busy.value || model.value.nodes.length >= 100) return;
  kind.value = value;
  const id = "node_" + crypto.randomUUID().slice(0, 8);
  if (value === "START" && model.value.nodes.some((n) => n.kind === "START"))
    return;
  freezePositions();
  const added = newNode(id, kind.value);
  added.name = t("workflow.kinds." + value);
  if (position) added.position = position;
  model.value.nodes.push(added);
  selected.value = id;
  inspector.value = "node";
  validation.value = null;
}
function remove() {
  if (!props.canEdit || busy.value) return;
  const id = selected.value;
  model.value.nodes = model.value.nodes.filter((n) => n.id !== id);
  model.value.edges = model.value.edges.filter(
    (e) => e.from !== id && e.to !== id,
  );
  selected.value = model.value.nodes[0]?.id || "";
  validation.value = null;
}
async function run(action: () => Promise<unknown>) {
  if (busy.value) return;
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
async function preview() {
  await run(async () => {
    trace.value = await props.api.preview(model.value, sample.value);
  });
}
async function save() {
  await run(async () => {
    const result = await props.api.save(id.value, revision.value, model.value);
    revision.value = result.revision;
    baseline.value = JSON.stringify(model.value);
    emit("saved");
  });
}
async function validate() {
  await run(async () => {
    validation.value = await props.api.validate(model.value);
  });
}
async function publish() {
  await run(async () => {
    const saved = await props.api.save(id.value, revision.value, model.value);
    revision.value = saved.revision;
    baseline.value = JSON.stringify(model.value);
    const result = await props.api.publish(id.value, revision.value);
    revision.value = result.revision;
    baseline.value = JSON.stringify(model.value);
    emit("saved");
  });
}
async function showVersions() {
  await run(async () => {
    versions.value = await props.api.versions(id.value);
    history.value = true;
  });
}
function changeCapability() {
  if (node.value) {
    node.value.input = {};
    node.value.required = [];
  }
}

/** 最近30次编辑保留在内存，涵盖节点、连接及属性，不写入浏览器持久存储。 */
const undoStack = ref<string[]>([]),
  redoStack = ref<string[]>([]);
const lastSnapshot = ref(JSON.stringify(model.value));
function restore(source: string[], target: string[]) {
  if (!props.canEdit || busy.value || !source.length) return;
  selectedEdge.value = -1;
  target.push(JSON.stringify(model.value));
  model.value = JSON.parse(source.pop()!);
  lastSnapshot.value = JSON.stringify(model.value);
  if (!model.value.nodes.some((n) => n.id === selected.value))
    selected.value = model.value.nodes[0]?.id || "";
  validation.value = null;
}
const selectedEdge = ref(-1);
/** 首次手动排布冻结其他节点当前位置，避免修改连接后其余节点跳动。 */
function freezePositions() {
  const layout = layoutGraph(model.value);
  for (const node of model.value.nodes)
    if (!node.position) node.position = { ...layout.positions[node.id] };
}
function moveNode(id: string, position: { x: number; y: number }) {
  if (!props.canEdit || busy.value) return;
  freezePositions();
  const n = model.value.nodes.find((n) => n.id === id);
  if (n) n.position = position;
}
function wireNodes(from: string, to: string) {
  if (!props.canEdit || busy.value) return;
  const issue = connectionError(model.value, from, to);
  if (issue) {
    ElMessage.warning(t("workflow.codes." + issue));
    return;
  }
  freezePositions();
  model.value.edges.push({
    from,
    to,
    field: null,
    value: null,
    defaultEdge: false,
  });
  selectedEdge.value = model.value.edges.length - 1;
  inspector.value = "connections";
}
/** 指针方式支持鼠标和触摸拖入；松手目标必须位于当前画布，不能把页面坐标直接写入模型。 */
const palettePointer = ref<{
  kind: Kind;
  x: number;
  y: number;
  startX: number;
  startY: number;
} | null>(null);
function paletteBegin(event: PointerEvent, value: Kind) {
  if (!props.canEdit || busy.value || event.button !== 0) return;
  event.preventDefault();
  palettePointer.value = {
    kind: value,
    x: event.clientX,
    y: event.clientY,
    startX: event.clientX,
    startY: event.clientY,
  };
  window.addEventListener("pointermove", paletteMove);
  window.addEventListener("pointerup", paletteEnd);
  window.addEventListener("pointercancel", paletteCancel);
}
function paletteMove(event: PointerEvent) {
  if (palettePointer.value) {
    palettePointer.value.x = event.clientX;
    palettePointer.value.y = event.clientY;
  }
}
function paletteEnd(event: PointerEvent) {
  const drag = palettePointer.value;
  if (
    drag &&
    Math.hypot(event.clientX - drag.startX, event.clientY - drag.startY) > 5
  ) {
    const canvas = document
      .elementFromPoint(event.clientX, event.clientY)
      ?.closest(".canvas");
    if (canvas) {
      const rect = canvas.getBoundingClientRect();
      add(drag.kind, {
        x: Math.max(
          0,
          Math.min(20000, Math.round((event.clientX - rect.left) / zoom.value)),
        ),
        y: Math.max(
          0,
          Math.min(20000, Math.round((event.clientY - rect.top) / zoom.value)),
        ),
      });
    }
  }
  paletteCancel();
}
function paletteCancel() {
  palettePointer.value = null;
  window.removeEventListener("pointermove", paletteMove);
  window.removeEventListener("pointerup", paletteEnd);
  window.removeEventListener("pointercancel", paletteCancel);
}
onBeforeUnmount(paletteCancel);
function paletteDrag(event: DragEvent, value: Kind) {
  if (!props.canEdit || busy.value) {
    event.preventDefault();
    return;
  }
  event.dataTransfer?.setData("application/x-appnexus-workflow-node", value);
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "copy";
}
function selectEdge(index: number) {
  selectedEdge.value = index;
  inspector.value = "connections";
}
function selectNode(id: string) {
  selectedEdge.value = -1;
  selected.value = id;
  inspector.value = "node";
}
function connect() {
  model.value.edges.push({
    from: selected.value || model.value.nodes[0]?.id || "",
    to: model.value.nodes.find((n) => n.id !== selected.value)?.id || "",
    field: null,
    value: null,
    defaultEdge: false,
  });
  inspector.value = "connections";
}
function removeEdge(index: number) {
  if (!props.canEdit || busy.value) return;
  freezePositions();
  selectedEdge.value = -1;
  model.value.edges.splice(index, 1);
}
async function confirmLeave() {
  if (busy.value) return false;
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm(
      t("workflow.unsavedPrompt"),
      t("workflow.unsavedTitle"),
      {
        type: "warning",
        confirmButtonText: t("workflow.leave"),
        cancelButtonText: t("workflow.keepEditing"),
      },
    );
    return true;
  } catch {
    return false;
  }
}
async function close() {
  if (await confirmLeave()) emit("close");
}
onBeforeRouteLeave(confirmLeave);
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault();
    event.returnValue = "";
  }
}
onMounted(() => window.addEventListener("beforeunload", beforeUnload));
onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));
watch(
  () => JSON.stringify(model.value),
  (value) => {
    validation.value = null;
    if (value === lastSnapshot.value) return;
    undoStack.value.push(lastSnapshot.value);
    if (undoStack.value.length > 30) undoStack.value.shift();
    redoStack.value = [];
    lastSnapshot.value = value;
  },
);
</script>
<template>
  <section
    class="designer"
    v-loading="busy"
    :aria-label="t('workflow.designer')"
  >
    <Teleport to="body"
      ><div
        v-if="palettePointer"
        class="palette-ghost"
        :style="{
          left: palettePointer.x + 12 + 'px',
          top: palettePointer.y + 12 + 'px',
        }"
      >
        {{ t("workflow.kinds." + palettePointer.kind) }}
      </div></Teleport
    >
    <header class="designer-header">
      <div class="designer-heading">
        <el-button @click="close" :disabled="busy">{{
          t("workflow.backToFlows")
        }}</el-button
        ><strong>{{ model.name || t("workflow.untitled") }}</strong
        ><el-tag :type="dirty ? 'warning' : 'info'">{{
          t(
            dirty
              ? "workflow.unsaved"
              : revision < 0
                ? "workflow.newDraft"
                : "workflow.savedState",
          )
        }}</el-tag
        ><el-tag v-if="!canEdit" type="info">{{
          t("workflow.readOnly")
        }}</el-tag>
      </div>
      <div class="toolbar designer-actions">
        <el-button :disabled="!canEdit" @click="previewOpen = true">{{
          t("workflow.preview")
        }}</el-button
        ><el-button v-if="revision >= 0" @click="showVersions">{{
          t("workflow.versions")
        }}</el-button
        ><el-button :disabled="!canEdit" @click="validate">{{
          t("workflow.validate")
        }}</el-button
        ><el-button :disabled="!canEdit" @click="save">{{
          t("workflow.saveDraft")
        }}</el-button
        ><el-popconfirm :title="t('workflow.confirmPublish')" @confirm="publish"
          ><template #reference
            ><el-button
              type="primary"
              :disabled="!canEdit || !canPublish"
              :loading="busy"
              >{{ t("workflow.publish") }}</el-button
            ></template
          ></el-popconfirm
        >
      </div>
    </header>
    <div class="designer-feedback">
      <el-radio-group
        v-model="view"
        :disabled="busy || configurationLoading"
        :aria-label="t('businessWorkflow.view')"
      >
        <el-radio-button value="business">{{
          t("businessWorkflow.businessView")
        }}</el-radio-button>
        <el-radio-button value="advanced">{{
          t("businessWorkflow.advancedView")
        }}</el-radio-button>
      </el-radio-group>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
      /><el-alert
        v-if="!businesses.length"
        :title="t('workflow.noCapabilities')"
        type="info"
        :closable="false"
      />
      <el-alert
        v-if="validation"
        :type="validation.length ? 'warning' : 'success'"
        :title="
          validation.length
            ? t('workflow.validationFailed')
            : t('workflow.validationPassed')
        "
        :closable="false"
        ><ul v-if="validation.length">
          <li v-for="(issue, i) in validation" :key="i">
            <el-button
              link
              @click="
                model.nodes.some((n) => n.id === issue.split(':')[1])
                  ? selectNode(issue.split(':')[1])
                  : (inspector = 'flow')
              "
              >{{
                t("workflow.codes." + issue.split(":")[0], issue.split(":")[0])
              }}
              {{ issue.split(":")[1] || "" }}</el-button
            >
          </li>
        </ul></el-alert
      >
    </div>
    <template v-if="view === 'business'">
      <el-skeleton v-if="configurationLoading" :rows="5" animated />
      <BusinessWorkflowConfiguration
        v-else-if="catalog"
        v-model="model"
        :catalog="catalog"
        :disabled="!canEdit || busy"
      />
      <el-empty v-else :description="t('businessWorkflow.catalogUnavailable')"
        ><el-button @click="loadConfiguration">{{
          t("management.refresh")
        }}</el-button></el-empty
      >
    </template>
    <div v-else class="designer-layout">
      <aside class="designer-palette" :aria-label="t('workflow.nodeLibrary')">
        <h2>{{ t("workflow.nodeLibrary") }}</h2>
        <el-input
          v-model="paletteQuery"
          clearable
          :placeholder="t('workflow.searchNodes')"
          :aria-label="t('workflow.searchNodes')"
        />
        <div class="palette-list">
          <el-button
            v-for="value in paletteKinds"
            :key="value"
            :disabled="
              !canEdit ||
              busy ||
              (value === 'START' && model.nodes.some((n) => n.kind === 'START'))
            "
            :draggable="canEdit && !busy"
            @dragstart="paletteDrag($event, value)"
            @pointerdown="paletteBegin($event, value)"
            @click="add(value)"
            >{{ t("workflow.kinds." + value) }}</el-button
          >
        </div>
        <el-divider />
        <h2>{{ t("workflow.outline") }}</h2>
        <div class="node-outline">
          <el-button
            v-for="n in model.nodes"
            :key="n.id"
            :type="selected === n.id ? 'primary' : 'default'"
            text
            @click="selectNode(n.id)"
            >{{ n.name }}</el-button
          >
        </div>
      </aside>
      <main class="designer-stage">
        <div class="canvas-tools">
          <span
            >{{ model.nodes.length }} {{ t("workflow.nodeCount") }} ·
            {{ model.edges.length }} {{ t("workflow.edgeCount") }}</span
          >
          <div>
            <el-button
              :disabled="!canEdit || !undoStack.length"
              @click="restore(undoStack, redoStack)"
              >{{ t("workflow.undoStructure") }}</el-button
            ><el-button
              :disabled="!canEdit || !redoStack.length"
              @click="restore(redoStack, undoStack)"
              >{{ t("workflow.redoStructure") }}</el-button
            ><el-button
              :disabled="zoom <= 0.5"
              @click="zoom = Math.max(0.5, zoom - 0.1)"
              :aria-label="t('workflow.zoomOut')"
              >−</el-button
            ><el-button @click="zoom = 1" :aria-label="t('workflow.resetZoom')"
              >{{ Math.round(zoom * 100) }}%</el-button
            ><el-button
              :disabled="zoom >= 1.5"
              @click="zoom = Math.min(1.5, zoom + 0.1)"
              :aria-label="t('workflow.zoomIn')"
              >+</el-button
            >
          </div>
        </div>
        <div class="canvas-help">{{ t("workflow.dragHint") }}</div>
        <div class="canvas-scroll">
          <WorkflowCanvas
            :model="model"
            :selected="selected"
            :zoom="zoom"
            :editable="canEdit && !busy"
            :selected-edge="selectedEdge"
            @move="moveNode"
            @connect="wireNodes"
            @drop-node="add"
            @select-edge="selectEdge"
            @delete-edge="removeEdge"
            @select="selectNode"
          />
        </div>
        <footer class="canvas-footer">
          <el-button
            v-if="selectedEdge >= 0"
            type="danger"
            :disabled="!canEdit || busy"
            @click="removeEdge(selectedEdge)"
            >{{ t("workflow.deleteConnection") }}</el-button
          >
          <span>{{ selectedName }}</span
          ><el-button :disabled="!canEdit || !node" @click="connect">{{
            t("workflow.addConnection")
          }}</el-button
          ><el-popconfirm
            :title="t('workflow.confirmRemoveNode')"
            @confirm="remove"
            ><template #reference
              ><el-button :disabled="!canEdit || !node" type="danger" plain>{{
                t("workflow.removeNode")
              }}</el-button></template
            ></el-popconfirm
          >
        </footer>
      </main>
      <aside class="designer-inspector" :aria-label="t('workflow.properties')">
        <el-tabs v-model="inspector" stretch
          ><el-tab-pane name="flow" :label="t('workflow.flowSettings')"
            ><div class="inspector-body">
              <el-form
                label-position="top"
                :disabled="!canEdit || busy"
                class="definition-form"
                ><el-form-item :label="t('workflow.name')" required
                  ><el-input
                    v-model="model.name"
                    maxlength="100" /></el-form-item
                ><el-form-item :label="t('workflow.objectType')" required
                  ><el-input
                    v-model="model.objectType"
                    maxlength="64" /></el-form-item
                ><el-form-item :label="t('workflow.purpose')" required
                  ><el-input
                    v-model="model.purpose"
                    maxlength="64" /></el-form-item
                ><el-form-item :label="t('workflow.mode')"
                  ><el-select v-model="model.mode"
                    ><el-option
                      v-for="mode in ['APPROVAL', 'BUSINESS']"
                      :key="mode"
                      :value="mode"
                      :label="
                        t('workflow.modes.' + mode)
                      " /></el-select></el-form-item
                ><el-form-item :label="t('workflow.entryCapability')" required
                  ><el-select v-model="model.entryCapability" filterable
                    ><el-option
                      v-for="b in businesses"
                      :key="b.key"
                      :value="b.key"
                      :label="b.name" /></el-select></el-form-item
              ></el-form>
              <el-alert
                :title="
                  t(
                    model.mode === 'APPROVAL'
                      ? 'workflow.approvalBoundary'
                      : 'workflow.businessBoundary',
                  )
                "
                type="info"
                :closable="false"
              />
              <h3>{{ t("workflow.matches") }}</h3>
              <div v-for="(match, i) in model.matches" :key="i" class="toolbar">
                <el-select v-model="match.field" :disabled="!canEdit"
                  ><el-option
                    v-for="f in fields"
                    :key="f"
                    :value="f"
                    :label="f" /></el-select
                ><el-input
                  v-model="match.value"
                  :disabled="!canEdit"
                  :aria-label="t('workflow.equals')"
                /><el-button
                  :disabled="!canEdit"
                  @click="model.matches.splice(i, 1)"
                  >{{ t("workflow.remove") }}</el-button
                >
              </div>
              <el-button
                :disabled="!canEdit"
                @click="model.matches.push({ field: '', value: '' })"
                >{{ t("workflow.addMatch") }}</el-button
              >
              <el-form v-if="model.mode === 'APPROVAL'" label-position="top"
                ><h3>{{ t("workflow.terminalActions") }}</h3>
                <el-form-item
                  v-for="result in ['APPROVED', 'REJECTED', 'WITHDRAWN']"
                  :key="result"
                  :label="t('workflow.states.' + result)"
                  ><el-select
                    :model-value="model.terminalActions[result]"
                    clearable
                    :disabled="!canEdit"
                    @update:model-value="
                      (value) => {
                        if (value) model.terminalActions[result] = value;
                        else delete model.terminalActions[result];
                      }
                    "
                    ><el-option
                      v-for="b in businesses"
                      :key="b.key"
                      :value="b.key"
                      :label="b.name" /></el-select></el-form-item
              ></el-form>
              <el-form label-position="top"
                ><el-form-item :label="t('workflow.withdrawalCapability')"
                  ><el-select
                    v-model="model.withdrawalCapability"
                    clearable
                    :disabled="!canEdit"
                    ><el-option
                      v-for="b in businesses"
                      :key="b.key"
                      :value="b.key"
                      :label="b.name" /></el-select
                ></el-form-item>
                <h3>{{ t("workflow.copies") }}</h3>
                <div
                  v-for="(person, i) in model.copies || []"
                  :key="i"
                  class="toolbar"
                >
                  <el-select v-model="person.kind" :disabled="!canEdit"
                    ><el-option
                      v-for="value in ['MEMBER', 'LEADER', 'POSITION']"
                      :key="value"
                      :value="value"
                      :label="t('workflow.peopleKinds.' + value)" /></el-select
                  ><el-input
                    v-if="person.kind !== 'LEADER'"
                    v-model="person.reference"
                    :disabled="!canEdit"
                    :aria-label="t('workflow.reference')"
                  /><el-button
                    :disabled="!canEdit"
                    @click="model.copies?.splice(i, 1)"
                    >{{ t("workflow.remove") }}</el-button
                  >
                </div>
                <el-button
                  :disabled="!canEdit"
                  @click="
                    (model.copies ??= []).push({
                      kind: 'LEADER',
                      reference: '',
                    })
                  "
                  >{{ t("workflow.addPerson") }}</el-button
                ></el-form
              >
            </div></el-tab-pane
          ><el-tab-pane name="node" :label="t('workflow.nodeProperties')"
            ><div class="inspector-body">
              <h3>{{ selectedName }}</h3>
              <el-form
                v-if="node"
                label-position="top"
                :disabled="!canEdit || busy"
                class="node-form"
                ><el-form-item :label="t('workflow.nodeId')"
                  ><el-input :model-value="node.id" readonly /></el-form-item
                ><el-form-item :label="t('workflow.name')" required
                  ><el-input
                    v-model="node.name"
                    maxlength="100" /></el-form-item
                ><el-form-item :label="t('workflow.nodeType')"
                  ><el-select v-model="node.kind"
                    ><el-option
                      v-for="value in kinds"
                      :key="value"
                      :value="value"
                      :label="t('workflow.kinds.' + value)" /></el-select
                ></el-form-item>
                <template v-if="['REVIEW', 'HANDLE'].includes(node.kind)"
                  ><el-form-item :label="t('workflow.completion')"
                    ><el-select v-model="node.completion"
                      ><el-option
                        v-for="value in ['ALL', 'ANY', 'SEQUENTIAL']"
                        :key="value"
                        :value="value"
                        :label="
                          t('workflow.completions.' + value)
                        " /></el-select></el-form-item
                  ><el-form-item :label="t('workflow.allowSelf')"
                    ><el-switch v-model="node.allowSelf" /></el-form-item
                  ><el-form-item
                    v-if="node.kind === 'HANDLE'"
                    :label="t('workflow.separate')"
                    ><el-switch v-model="node.separate"
                  /></el-form-item>
                  <div class="full">
                    <h3>{{ t("workflow.people") }}</h3>
                    <div
                      v-for="(person, i) in node.people"
                      :key="i"
                      class="toolbar"
                    >
                      <el-select v-model="person.kind"
                        ><el-option
                          v-for="value in ['MEMBER', 'LEADER', 'POSITION']"
                          :key="value"
                          :value="value"
                          :label="
                            t('workflow.peopleKinds.' + value)
                          " /></el-select
                      ><el-input
                        v-if="person.kind !== 'LEADER'"
                        v-model="person.reference"
                        :aria-label="t('workflow.reference')"
                        :placeholder="t('workflow.reference')"
                      /><el-button @click="node.people.splice(i, 1)">{{
                        t("workflow.remove")
                      }}</el-button>
                    </div>
                    <el-button
                      @click="
                        node.people.push({ kind: 'LEADER', reference: '' })
                      "
                      >{{ t("workflow.addPerson") }}</el-button
                    >
                  </div></template
                >
                <el-form-item
                  v-if="
                    ['REVIEW', 'HANDLE', 'ACTION', 'WAIT'].includes(node.kind)
                  "
                  :label="t('workflow.capability')"
                  ><el-select
                    v-model="node.capability"
                    clearable
                    filterable
                    @change="changeCapability"
                    ><el-option
                      v-for="b in businesses"
                      :key="b.key"
                      :value="b.key"
                      :label="b.name" /></el-select
                ></el-form-item>
                <el-form-item :label="t('workflow.timeoutSeconds')"
                  ><el-input-number
                    v-model="node.timeoutSeconds"
                    :min="60"
                    :max="2592000" /></el-form-item
                ><el-form-item
                  v-if="['ACTION', 'WAIT'].includes(node.kind)"
                  :label="t('workflow.retries')"
                  ><el-input-number
                    v-model="node.retries"
                    :min="0"
                    :max="5" /></el-form-item
                ><el-form-item
                  v-if="node.kind === 'FORK'"
                  :label="t('workflow.join')"
                  ><el-select v-model="node.join"
                    ><el-option
                      v-for="n in model.nodes.filter((n) => n.kind === 'JOIN')"
                      :key="n.id"
                      :value="n.id"
                      :label="n.name" /></el-select
                ></el-form-item>
                <el-form-item
                  v-if="['REVIEW', 'HANDLE'].includes(node.kind) && capability"
                  :label="t('workflow.requiredFields')"
                  ><el-select v-model="node.required" multiple
                    ><el-option
                      v-for="field in capability.inputs"
                      :key="field"
                      :value="field"
                      :label="field" /></el-select
                ></el-form-item>
                <div
                  v-if="capability && ['ACTION', 'WAIT'].includes(node.kind)"
                  class="full"
                >
                  <h3>{{ t("workflow.inputMapping") }}</h3>
                  <el-form-item
                    v-for="field in capability.inputs"
                    :key="field"
                    :label="field"
                    ><el-select
                      v-model="node.input[field]"
                      filterable
                      allow-create
                      clearable
                      :placeholder="t('workflow.mappingHint')"
                      ><el-option
                        v-for="f in fields"
                        :key="f"
                        :value="f"
                        :label="f" /></el-select
                  ></el-form-item></div
              ></el-form>
              <el-empty
                v-if="!node"
                :description="t('workflow.selectNode')"
              /></div></el-tab-pane
          ><el-tab-pane name="connections" :label="t('workflow.connections')"
            ><div class="inspector-body">
              <div class="connections-heading">
                <h3>{{ t("workflow.connections") }}</h3>
                <el-button :disabled="!canEdit" @click="connect">{{
                  t("workflow.addConnection")
                }}</el-button>
              </div>
              <el-empty
                v-if="!model.edges.length"
                :description="t('workflow.noConnections')"
              />
              <el-form
                v-for="(edge, i) in model.edges"
                :key="i"
                class="edge-card"
                :class="{ 'selected-edge': selectedEdge === i }"
                label-position="top"
                :disabled="!canEdit || busy"
                ><div class="connections-heading">
                  <strong>{{ i + 1 }}</strong
                  ><el-button type="danger" link @click="removeEdge(i)">{{
                    t("workflow.remove")
                  }}</el-button>
                </div>
                <el-form-item :label="t('workflow.from')"
                  ><el-select v-model="edge.from"
                    ><el-option
                      v-for="n in model.nodes"
                      :key="n.id"
                      :value="n.id"
                      :label="n.name" /></el-select></el-form-item
                ><el-form-item :label="t('workflow.to')"
                  ><el-select v-model="edge.to"
                    ><el-option
                      v-for="n in model.nodes"
                      :key="n.id"
                      :value="n.id"
                      :label="n.name" /></el-select></el-form-item
                ><template
                  v-if="
                    model.nodes.find((n) => n.id === edge.from)?.kind ===
                    'CHOICE'
                  "
                  ><el-form-item :label="t('workflow.defaultEdge')"
                    ><el-switch v-model="edge.defaultEdge" /></el-form-item
                  ><template v-if="!edge.defaultEdge"
                    ><el-form-item :label="t('workflow.condition')"
                      ><el-select v-model="edge.field" clearable
                        ><el-option
                          v-for="f in fields"
                          :key="f"
                          :value="f"
                          :label="f" /></el-select></el-form-item
                    ><el-form-item :label="t('businessWorkflow.operator')"
                      ><el-select
                        :model-value="edge.operator || 'EQ'"
                        @update:model-value="edge.operator = $event"
                        ><el-option
                          v-for="op in ['EQ', 'NE', 'NUM_EQ', 'NUM_NE', 'GT', 'GE', 'LT', 'LE']"
                          :key="op"
                          :value="op"
                          :label="
                            t('businessWorkflow.operators.' + op)
                          " /></el-select
                    ></el-form-item>
                    <el-form-item :label="t('workflow.equals')"
                      ><el-input
                        v-model="
                          edge.value
                        " /></el-form-item></template></template
              ></el-form></div></el-tab-pane
        ></el-tabs>
      </aside>
    </div>
    <ManagedDialog
      v-model="previewOpen"
      :title="t('workflow.preview')"
      width="min(700px, calc(100vw - 32px))"
      ><el-alert
        :title="t('workflow.previewHint')"
        type="info"
        :closable="false" /><el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false" /><el-form label-position="top"
        ><el-form-item
          v-for="field in view === 'business' && currentProfile
            ? currentProfile.fields.map((f) => f.key)
            : fields"
          :key="field"
          :label="
            (locale.startsWith('en')
              ? currentProfile?.fields.find((f) => f.key === field)?.nameEn
              : currentProfile?.fields.find((f) => f.key === field)?.nameZh) ||
            field
          "
          ><el-input v-model="sample[field]" /></el-form-item
        ><el-button :loading="busy" @click="preview">{{
          t("workflow.preview")
        }}</el-button></el-form
      ><el-steps direction="vertical"
        ><el-step
          v-for="(id, i) in trace"
          :key="i"
          :title="model.nodes.find((n) => n.id === id)?.name || id"
          status="success" /></el-steps
    ></ManagedDialog>
    <ManagedDialog
      v-model="history"
      :title="t('workflow.versions')"
      width="min(1000px, calc(100vw - 32px))"
      ><el-tabs
        ><el-tab-pane
          v-for="version in versions"
          :key="version.version"
          :label="String(version.version)"
          ><WorkflowCanvas
            :model="JSON.parse(version.model)"
            selected=""
            @select="() => {}"
          />
          <pre class="version-json">{{
            JSON.stringify(JSON.parse(version.model), null, 2)
          }}</pre>
        </el-tab-pane></el-tabs
      ></ManagedDialog
    >
  </section>
</template>
<style scoped>
.palette-ghost {
  position: fixed;
  z-index: 3000;
  pointer-events: none;
  padding: 12px 20px;
  border: 1px solid var(--el-color-primary);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
}
.palette-list .el-button {
  touch-action: none;
}
.designer {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  min-width: 0;
}
.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
}
.designer-heading,
.toolbar,
.canvas-tools,
.canvas-footer,
.connections-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.designer-heading strong {
  max-width: 240px;
  overflow-wrap: anywhere;
}
.designer-feedback {
  padding: 0 16px;
}
.designer-feedback:has(.el-alert) {
  padding-top: 12px;
  padding-bottom: 12px;
}
.designer-layout {
  display: grid;
  grid-template-columns: 180px minmax(300px, 1fr) 340px;
  min-height: 620px;
  height: calc(100vh - 240px);
  max-height: 1000px;
}
.designer-palette {
  padding: 16px;
  border-right: 1px solid var(--el-border-color-lighter);
  overflow: auto;
}
.designer-palette h2 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
}
.palette-list,
.node-outline {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.palette-list .el-button,
.node-outline .el-button {
  margin: 0;
  justify-content: flex-start;
  min-height: 36px;
  white-space: normal;
}
.designer-stage {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--el-fill-color-lighter);
}
.canvas-tools,
.canvas-footer {
  padding: 12px;
  justify-content: space-between;
  background: var(--el-bg-color);
}
.canvas-tools {
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.canvas-tools > div {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.canvas-tools .el-button + .el-button {
  margin-left: 0;
}
.canvas-tools > span,
.canvas-footer > span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.canvas-help {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.canvas-scroll {
  flex: 1;
  min-height: 250px;
  overflow: auto;
  padding: 24px;
}
.canvas-footer {
  border-top: 1px solid var(--el-border-color-lighter);
}
.designer-inspector {
  border-left: 1px solid var(--el-border-color-lighter);
  min-width: 0;
  overflow: auto;
}
.designer-inspector :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--el-bg-color);
}
.inspector-body {
  padding: 16px;
}
.definition-form,
.node-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0 12px;
}
.full {
  grid-column: 1/-1;
}
.toolbar > .el-select,
.toolbar > .el-input {
  width: 100%;
  min-width: 0;
}
.toolbar > .el-select {
  flex: 1;
}
.toolbar + .toolbar {
  margin-top: 8px;
}
.connections-heading {
  justify-content: space-between;
}
.edge-card.selected-edge {
  border-color: var(--el-color-primary);
}
.edge-card {
  padding: 12px;
  margin-top: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}
.edge-card :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}
.version-json {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 360px;
  overflow: auto;
}
.designer h3 {
  font-size: 14px;
  font-weight: 600;
  margin: 16px 0 12px;
}
@media (max-width: 1200px) {
  .designer-layout {
    grid-template-columns: 150px minmax(220px, 1fr) 300px;
  }
  .designer-header {
    padding: 12px;
  }
  .designer-actions {
    justify-content: flex-end;
  }
}
@media (max-width: 900px) {
  .designer-layout {
    height: auto;
    max-height: none;
    grid-template-columns: 150px minmax(0, 1fr);
  }
  .designer-inspector {
    grid-column: 1/-1;
    border-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
    max-height: none;
    overflow: visible;
  }
  .designer-stage {
    min-height: 420px;
  }
  .designer-palette {
    max-height: 480px;
  }
  .definition-form,
  .node-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .designer-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .designer-palette {
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
    max-height: none;
  }
  .palette-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .node-outline {
    flex-direction: row;
    flex-wrap: wrap;
  }
  .designer-stage {
    min-height: 380px;
  }
  .designer-inspector {
    grid-column: auto;
  }
  .definition-form,
  .node-form {
    grid-template-columns: minmax(0, 1fr);
  }
  .designer-actions .el-button {
    margin-left: 0;
    min-height: 44px;
  }
  .canvas-scroll {
    padding: 12px;
  }
  .canvas-tools {
    gap: 12px;
  }
  .canvas-tools .el-button {
    min-height: 36px;
  }
}
</style>
