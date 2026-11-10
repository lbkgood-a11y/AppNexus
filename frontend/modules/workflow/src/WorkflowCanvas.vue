<script setup lang="ts">
import { computed, useId, ref, watch, nextTick } from "vue";
import { useI18n } from "vue-i18n";
import { layoutGraph } from "./layout";
import type { WorkflowModel } from "./index";
/** 受控流程预览：SVG只绘制连接，节点使用Element Plus按钮保证键盘交互，不执行模型内容。 */
const props = withDefaults(
  defineProps<{ model: WorkflowModel; selected: string; zoom?: number }>(),
  { zoom: 1 },
);
const emit = defineEmits<{ select: [id: string] }>();
const { t } = useI18n();
const canvasHost = ref<HTMLElement>();
/** 仅滚动画布自身容器，不把页面或属性面板拖离当前上下文。 */
watch(
  () => props.selected,
  async (id) => {
    await nextTick();
    const point = layout.value.positions[id],
      viewport = canvasHost.value?.parentElement;
    if (point && viewport)
      viewport.scrollTo({
        left: Math.max(
          0,
          point.x * props.zoom - viewport.clientWidth / 2 + 85 * props.zoom,
        ),
        top: Math.max(
          0,
          point.y * props.zoom - viewport.clientHeight / 2 + 30 * props.zoom,
        ),
      });
  },
);
const layout = computed(() => layoutGraph(props.model));
const markerId = "workflow-arrow-" + useId().replace(/[^a-zA-Z0-9_-]/g, "");
const edges = computed(() =>
  props.model.edges.filter(
    (e) => layout.value.positions[e.from] && layout.value.positions[e.to],
  ),
);
function path(edge: { from: string; to: string }) {
  const a = layout.value.positions[edge.from],
    b = layout.value.positions[edge.to];
  const x = a.x + 170,
    y = a.y + 30,
    endX = b.x,
    endY = b.y + 30,
    mid = (x + endX) / 2;
  return `M ${x} ${y} H ${mid} V ${endY} H ${endX}`;
}
</script>
<template>
  <div
    ref="canvasHost"
    class="canvas"
    :style="{
      width: layout.width * zoom + 'px',
      height: layout.height * zoom + 'px',
    }"
    :aria-label="t('workflow.graph')"
  >
    <div
      class="canvas-content"
      :style="{
        width: layout.width + 'px',
        height: layout.height + 'px',
        transform: `scale(${zoom})`,
      }"
    >
      <svg :viewBox="`0 0 ${layout.width} ${layout.height}`" aria-hidden="true">
        <defs>
          <marker
            :id="markerId"
            markerWidth="8"
            markerHeight="8"
            refX="7"
            refY="3"
            orient="auto"
          >
            <path d="M0,0 L0,6 L8,3 z" fill="currentColor" />
          </marker>
        </defs>
        <path
          v-for="(edge, i) in edges"
          :key="i"
          :d="path(edge)"
          fill="none"
          stroke="currentColor"
          :marker-end="`url(#${markerId})`"
          :class="{ active: edge.from === selected || edge.to === selected }"
        /></svg
      ><el-button
        v-for="node in model.nodes"
        :key="node.id"
        :type="selected === node.id ? 'primary' : 'default'"
        :aria-pressed="selected === node.id"
        :style="{
          left: layout.positions[node.id].x + 'px',
          top: layout.positions[node.id].y + 'px',
        }"
        @click="emit('select', node.id)"
        ><span
          >{{ node.name
          }}<small>{{ t("workflow.kinds." + node.kind) }}</small></span
        ></el-button
      >
    </div>
  </div>
</template>
<style scoped>
.canvas {
  position: relative;
  flex-shrink: 0;
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
  color: var(--el-text-color-placeholder);
}
.canvas svg .active {
  stroke: var(--el-color-primary);
  stroke-width: 2;
}
.canvas .el-button {
  position: absolute;
  margin: 0;
  width: 170px;
  height: 60px;
  white-space: normal;
  text-align: left;
  justify-content: flex-start;
  padding: 8px 12px;
}
.canvas small {
  display: block;
  font-size: 12px;
  margin-top: 4px;
  opacity: 0.8;
}
.canvas span {
  overflow-wrap: anywhere;
  display: block;
  overflow: hidden;
  max-height: 44px;
}
</style>
