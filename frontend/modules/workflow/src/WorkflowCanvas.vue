<script setup lang="ts">
import { computed, useId, ref, onBeforeUnmount } from "vue";
import { useI18n } from "vue-i18n";
import { layoutGraph } from "./layout";
import { canvasPoint } from "./graphEditing";
import type { WorkflowModel, Kind } from "./index";
/** 指针拖动、触摸和键盘共用受控事件；只读版本不允许改图，位置在松手后一次提交。 */
const props = withDefaults(
  defineProps<{
    model: WorkflowModel;
    selected: string;
    zoom?: number;
    editable?: boolean;
    selectedEdge?: number;
  }>(),
  { zoom: 1, editable: false, selectedEdge: -1 },
);
const emit = defineEmits<{
  select: [id: string];
  move: [id: string, position: { x: number; y: number }];
  connect: [from: string, to: string];
  dropNode: [kind: Kind, position: { x: number; y: number }];
  selectEdge: [index: number];
  deleteEdge: [index: number];
}>();
const { t } = useI18n();
const host = ref<HTMLElement>();
const marker = "wf-" + useId().replace(/[^a-zA-Z0-9_-]/g, "");
const dragging = ref<{
    id: string;
    x: number;
    y: number;
    startX: number;
    startY: number;
    originX: number;
    originY: number;
  } | null>(null),
  wire = ref<{ from: string; x: number; y: number } | null>(null),
  keyboardFrom = ref("");
const base = computed(() => layoutGraph(props.model));
const positions = computed(() => {
  const points = { ...base.value.positions };
  if (dragging.value)
    points[dragging.value.id] = { x: dragging.value.x, y: dragging.value.y };
  return points;
});
const width = computed(() =>
    Math.max(
      base.value.width,
      ...Object.values(positions.value).map((p) => p.x + 230),
    ),
  ),
  height = computed(() =>
    Math.max(
      base.value.height,
      ...Object.values(positions.value).map((p) => p.y + 120),
    ),
  );
function point(event: { clientX: number; clientY: number }) {
  const rect = host.value!.getBoundingClientRect();
  return canvasPoint(
    (event.clientX - rect.left) / props.zoom,
    (event.clientY - rect.top) / props.zoom,
  );
}
function path(from: string, to: string) {
  const a = positions.value[from],
    b = positions.value[to];
  if (!a || !b) return "";
  const x = a.x + 170,
    y = a.y + 30,
    endX = b.x,
    endY = b.y + 30,
    mid = Math.max(x + 30, (x + endX) / 2);
  return `M ${x} ${y} H ${mid} V ${endY} H ${endX}`;
}
function begin(event: PointerEvent, id: string) {
  emit("select", id);
  if (!props.editable || event.button !== 0) return;
  const p = positions.value[id];
  dragging.value = {
    id,
    x: p.x,
    y: p.y,
    originX: p.x,
    originY: p.y,
    startX: event.clientX,
    startY: event.clientY,
  };
  listen();
  event.preventDefault();
}
function startWire(event: PointerEvent, id: string) {
  if (!props.editable || event.button !== 0) return;
  const p = point(event);
  wire.value = { from: id, ...p };
  listen();
  event.stopPropagation();
  event.preventDefault();
}
function motion(event: PointerEvent) {
  if (dragging.value) {
    const d = dragging.value;
    Object.assign(
      d,
      canvasPoint(
        d.originX + (event.clientX - d.startX) / props.zoom,
        d.originY + (event.clientY - d.startY) / props.zoom,
      ),
    );
  }
  if (wire.value) Object.assign(wire.value, point(event));
}
function finish(event: PointerEvent) {
  if (dragging.value) {
    const d = dragging.value;
    if (d.x !== d.originX || d.y !== d.originY)
      emit("move", d.id, { x: d.x, y: d.y });
  }
  if (wire.value) {
    const target = document
      .elementFromPoint(event.clientX, event.clientY)
      ?.closest("[data-input-node]");
    if (target)
      emit("connect", wire.value.from, target.getAttribute("data-input-node")!);
  }
  cancel();
}
function listen() {
  window.addEventListener("pointermove", motion);
  window.addEventListener("pointerup", finish);
  window.addEventListener("pointercancel", cancel);
  window.addEventListener("keydown", escape);
}
function cancel() {
  dragging.value = null;
  wire.value = null;
  window.removeEventListener("pointermove", motion);
  window.removeEventListener("pointerup", finish);
  window.removeEventListener("pointercancel", cancel);
  window.removeEventListener("keydown", escape);
}
function escape(event: KeyboardEvent) {
  if (event.key === "Escape") {
    cancel();
    keyboardFrom.value = "";
  }
}
onBeforeUnmount(cancel);
function anchor(id: string, input: boolean) {
  if (!props.editable) return;
  if (!input) keyboardFrom.value = id;
  else if (keyboardFrom.value) {
    emit("connect", keyboardFrom.value, id);
    keyboardFrom.value = "";
  }
}
function moveKey(event: KeyboardEvent, id: string) {
  if (!props.editable) return;
  const offsets: Record<string, number[]> = {
    ArrowLeft: [-10, 0],
    ArrowRight: [10, 0],
    ArrowUp: [0, -10],
    ArrowDown: [0, 10],
  };
  const o = offsets[event.key];
  if (o) {
    event.preventDefault();
    const p = positions.value[id];
    emit("move", id, canvasPoint(p.x + o[0], p.y + o[1]));
  }
}
function drop(event: DragEvent) {
  if (!props.editable) return;
  event.preventDefault();
  const kind = event.dataTransfer?.getData(
    "application/x-appnexus-workflow-node",
  );
  if (
    kind &&
    [
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
    ].includes(kind)
  )
    emit("dropNode", kind as Kind, point(event));
}
</script>
<template>
  <div
    ref="host"
    class="canvas"
    :class="{ editable }"
    :style="{ width: width * zoom + 'px', height: height * zoom + 'px' }"
    :aria-label="t('workflow.graph')"
    @dragover="editable && $event.preventDefault()"
    @drop="drop"
  >
    <div
      class="canvas-content"
      :style="{
        width: width + 'px',
        height: height + 'px',
        transform: `scale(${zoom})`,
      }"
    >
      <svg :viewBox="`0 0 ${width} ${height}`">
        <defs>
          <marker
            :id="marker"
            markerWidth="8"
            markerHeight="8"
            refX="7"
            refY="3"
            orient="auto"
          >
            <path d="M0,0 L0,6 L8,3 z" fill="currentColor" />
          </marker>
        </defs>
        <g v-for="(edge, i) in model.edges" :key="i">
          <path
            :d="path(edge.from, edge.to)"
            class="edge"
            :class="{ active: selectedEdge === i }"
            :marker-end="`url(#${marker})`"
          />
          <path
            v-if="editable"
            :d="path(edge.from, edge.to)"
            class="edge-hit"
            tabindex="0"
            role="button"
            :aria-label="
              t('workflow.selectConnection', {
                from:
                  model.nodes.find((n) => n.id === edge.from)?.name ||
                  edge.from,
                to: model.nodes.find((n) => n.id === edge.to)?.name || edge.to,
              })
            "
            @click="emit('selectEdge', i)"
            @keydown.enter="emit('selectEdge', i)"
            @keydown.delete.prevent="emit('deleteEdge', i)"
            @keydown.backspace.prevent="emit('deleteEdge', i)"
          />
        </g>
        <path
          v-if="wire"
          class="wire-preview"
          :d="`M ${positions[wire.from].x + 170} ${positions[wire.from].y + 30} L ${wire.x} ${wire.y}`"
        />
      </svg>
      <div
        v-for="node in model.nodes"
        :key="node.id"
        class="graph-node"
        :style="{
          left: positions[node.id].x + 'px',
          top: positions[node.id].y + 'px',
        }"
      >
        <el-button
          class="node-body"
          :type="selected === node.id ? 'primary' : 'default'"
          :aria-pressed="selected === node.id"
          @pointerdown="begin($event, node.id)"
          @click="emit('select', node.id)"
          @keydown="moveKey($event, node.id)"
          ><span
            >{{ node.name
            }}<small>{{ t("workflow.kinds." + node.kind) }}</small></span
          ></el-button
        ><el-button
          v-if="editable && node.kind !== 'START'"
          circle
          class="port input"
          :data-input-node="node.id"
          :aria-label="t('workflow.inputPort', { name: node.name })"
          @pointerdown.stop
          @click.stop="anchor(node.id, true)"
          >●</el-button
        ><el-button
          v-if="editable && node.kind !== 'END'"
          circle
          class="port output"
          :aria-label="t('workflow.outputPort', { name: node.name })"
          :class="{ armed: keyboardFrom === node.id }"
          @pointerdown="startWire($event, node.id)"
          @click.stop="anchor(node.id, false)"
          >●</el-button
        >
      </div>
    </div>
    <span class="sr-only" aria-live="polite">{{
      keyboardFrom ? t("workflow.pickTarget") : ""
    }}</span>
  </div>
</template>
<style scoped>
.canvas {
  position: relative;
  flex-shrink: 0;
  min-width: 100%;
  min-height: 100%;
}
.canvas-content {
  position: relative;
  transform-origin: top left;
}
.canvas svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  color: var(--el-text-color-secondary);
  overflow: visible;
}
.edge {
  stroke: var(--el-text-color-placeholder);
  stroke-width: 1.5;
  fill: none;
  pointer-events: none;
}
.edge.active {
  stroke: var(--el-color-primary);
  stroke-width: 3;
}
.edge-hit {
  stroke: transparent;
  stroke-width: 18;
  fill: none;
  cursor: pointer;
  pointer-events: stroke;
}
.edge-hit:focus {
  stroke: var(--el-color-primary-light-7);
  outline: none;
}
.wire-preview {
  stroke: var(--el-color-primary);
  stroke-dasharray: 6 4;
  fill: none;
  pointer-events: none;
}
.graph-node {
  position: absolute;
  width: 170px;
  height: 60px;
}
.node-body {
  margin: 0;
  width: 170px;
  height: 60px;
  white-space: normal;
  text-align: left;
  justify-content: flex-start;
  padding: 8px 18px;
  touch-action: none;
  user-select: none;
}
.editable .node-body {
  cursor: grab;
}
.editable .node-body:active {
  cursor: grabbing;
}
.node-body small {
  display: block;
  font-size: 12px;
  margin-top: 4px;
  opacity: 0.8;
}
.node-body span {
  overflow-wrap: anywhere;
  display: block;
  overflow: hidden;
  max-height: 44px;
}
.port.el-button {
  position: absolute;
  top: 17px;
  width: 26px;
  height: 26px;
  min-width: 26px;
  padding: 0;
  margin: 0;
  font-size: 10px;
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  touch-action: none;
}
.port.input {
  left: -13px;
}
.port.output {
  right: -13px;
}
.port.armed {
  background: var(--el-color-primary-light-7);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
</style>
