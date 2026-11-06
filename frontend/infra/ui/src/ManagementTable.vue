<script setup lang="ts" generic="Row extends object">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { AppIcon } from "@appnexus/icons";
import type { ManagementColumn } from "./types";

/**
 * 管理列表面板：统一工具栏、列配置、密度与全屏；分页与业务操作交由调用者注入。
 * 数据必须已通过真实接口授权；列隐藏仅为展示偏好，不能充当字段权限控制。
 */
const props = withDefaults(defineProps<{
  /** 当前数据区标题，调用者提供已翻译文案。 */
  title: string;
  /** 服务端授权后的行数据；工具栏配置不改变原始数据。 */
  rows: Row[];
  /** 当前可用列定义，权限变化时必须同步剔除不可见字段。 */
  columns: ManagementColumn[];
  /** 读取中，默认 false；阻止重复刷新并显示表体加载状态。 */
  loading?: boolean;
  /** 业务写入中，默认 false；阻止刷新与在途写入交错。 */
  busy?: boolean;
  /** 无数据文案；缺省使用统一空态，错误必须在调用页单独呈现。 */
  emptyText?: string;
  /** 标题层级，默认 h1；在成员抽屉等已有标题的区域使用 h2。 */
  headingLevel?: "h1" | "h2";
}>(), { loading: false, busy: false, headingLevel: "h1" });
const emit = defineEmits<{ refresh: [] }>();
defineSlots<{
  actions?: () => unknown;
  before?: () => unknown;
  pagination?: () => unknown;
  [name: string]: ((scope: { row: Row }) => unknown) | undefined;
}>();
const { t } = useI18n();
const fullscreenHost = ref<HTMLElement>();
const tableRegion = ref<HTMLElement>();
const fullscreen = ref(false);
const tableHeight = ref<number>();
const size = ref<"small" | "default" | "large">("default");
const order = ref<string[]>([]);
const hidden = ref<string[]>([]);
const fixed = ref<Record<string, "left" | "right" | undefined>>({});
const dragged = ref("");
const configured = computed(() => order.value.map(key => props.columns.find(column => column.key === key)).filter((column): column is ManagementColumn => Boolean(column)));
const visible = computed(() => configured.value.filter(column => !hidden.value.includes(column.key)));

/** 重置为当前业务列定义；不存入浏览器，避免不同账号或租户共享字段展示状态。 */
function resetColumns() {
  order.value = props.columns.map(column => column.key);
  hidden.value = [];
  fixed.value = Object.fromEntries(props.columns.map(column => [column.key, column.fixed]));
}
watch(() => props.columns.map(column => column.key).join("|"), resetColumns, { immediate: true });

/** 键盘按钮与拖放共用同一排序实现；越界移动忽略，不改变业务数据顺序。 */
function move(key: string, offset: number) {
  const index = order.value.indexOf(key), destination = index + offset;
  if (index < 0 || destination < 0 || destination >= order.value.length) return;
  const next = [...order.value];
  next.splice(index, 1); next.splice(destination, 0, key); order.value = next;
}
/** 拖放只调整列顺序；列固定由 Element Plus 管理左右吸附与宽度。 */
function drop(key: string) {
  if (!dragged.value) return;
  move(dragged.value, order.value.indexOf(key) - order.value.indexOf(dragged.value));
  dragged.value = "";
}
/** 隐藏最后一列会使操作不可发现，因此至少保留一个可见列。 */
function setVisible(key: string, value: boolean) {
  hidden.value = value ? hidden.value.filter(item => item !== key) : [...hidden.value, key];
}
/**
 * 全屏复用 Element Plus 全屏弹窗，避免嵌入式浏览器拒绝原生 Fullscreen API。
 * Teleport 移动同一张表格，不创建第二个实例；Esc、焦点恢复与滚动锁交给组件管理。
 */
async function toggleFullscreen() {
  fullscreen.value = !fullscreen.value;
  await nextTick(); measure();
}
/** 全屏时表体占据剩余高度，分页保持可见；普通布局不强制固定表格高度。 */
function measure() { tableHeight.value = fullscreen.value ? tableRegion.value?.clientHeight : undefined; }
watch(fullscreen,async()=>{await nextTick();measure();});
let observer: ResizeObserver | undefined;
onMounted(() => {
  observer = new ResizeObserver(measure);
  if (tableRegion.value) observer.observe(tableRegion.value);
});
onBeforeUnmount(() => observer?.disconnect());
</script>

<template>
  <el-dialog v-model="fullscreen" :title="title" fullscreen append-to-body :show-close="false" class="management-fullscreen-dialog" @opened="measure"><div ref="fullscreenHost" class="management-fullscreen-host" /></el-dialog>
  <Teleport :to="fullscreenHost || 'body'" :disabled="!fullscreen || !fullscreenHost">
  <section class="management-table" :class="{ 'is-fullscreen': fullscreen }" :aria-label="title">
    <el-card shadow="never">
      <div class="management-toolbar">
        <component :is="headingLevel" class="management-table-title">{{ title }}</component>
        <div class="management-toolbar-actions">
          <slot name="actions" />
          <div class="management-table-tools">
            <el-tooltip :teleported="!fullscreen" :content="t('management.refresh')"><el-button text :aria-label="t('management.refresh')" :disabled="loading || busy" @click="emit('refresh')"><AppIcon name="refresh" :size="18" /></el-button></el-tooltip>
            <el-dropdown trigger="click" :teleported="!fullscreen" @command="value => size = value">
              <el-button text :aria-label="t('management.density')"><AppIcon name="density" :size="18" /></el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="value in (['small','default','large'] as const)" :key="value" :command="value" :disabled="size === value">{{ t('management.' + value) }}</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
            <el-popover placement="bottom-end" :width="340" trigger="click" :teleported="!fullscreen" popper-class="management-column-popover">
              <template #reference><el-button text :aria-label="t('management.columns')"><AppIcon name="settings" :size="18" /></el-button></template>
              <div class="management-column-heading"><strong>{{ t('management.columns') }}</strong><el-button link type="primary" @click="resetColumns">{{ t('management.reset') }}</el-button></div>
              <div v-for="column in configured" :key="column.key" class="management-column-row" draggable="true" @dragstart="dragged = column.key" @dragover.prevent @drop.prevent="drop(column.key)">
                <AppIcon name="drag" :size="16" />
                <el-checkbox :model-value="!hidden.includes(column.key)" :disabled="visible.length === 1 && !hidden.includes(column.key)" @change="value => setVisible(column.key, Boolean(value))">{{ column.label }}</el-checkbox>
                <el-button text :aria-label="t('management.moveUp', { name: column.label })" :disabled="order[0] === column.key" @click="move(column.key, -1)"><AppIcon name="arrowUp" :size="16" /></el-button>
                <el-button text :aria-label="t('management.moveDown', { name: column.label })" :disabled="order[order.length - 1] === column.key" @click="move(column.key, 1)"><AppIcon name="arrowDown" :size="16" /></el-button>
                <el-dropdown trigger="click" :teleported="!fullscreen" @command="value => fixed[column.key] = value || undefined">
                  <el-button text :aria-label="t('management.pinColumn', { name: column.label })"><AppIcon name="pin" :size="16" /></el-button>
                  <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="value in ['left','right','']" :key="value" :command="value">{{ t('management.' + (value || 'unfixed')) }}<span v-if="(fixed[column.key] || '') === value"> ✓</span></el-dropdown-item></el-dropdown-menu></template>
                </el-dropdown>
              </div>
            </el-popover>
            <el-tooltip :teleported="!fullscreen" :content="t(fullscreen ? 'management.exitFullscreen' : 'management.fullscreen')"><el-button text :aria-label="t(fullscreen ? 'management.exitFullscreen' : 'management.fullscreen')" @click="toggleFullscreen"><AppIcon :name="fullscreen ? 'fullscreenExit' : 'fullscreen'" :size="18" /></el-button></el-tooltip>
          </div>
        </div>
      </div>
      <slot name="before" />
      <div ref="tableRegion" class="management-table-region">
        <el-table v-loading="loading" :data="rows" :size="size" :height="tableHeight" :header-cell-style="{background:'var(--el-fill-color-light)'}" :empty-text="emptyText || t('management.empty')">
          <el-table-column v-for="column in visible" :key="column.key" :prop="column.prop" :label="column.label" :width="column.width" :min-width="column.minWidth || 140" :fixed="fixed[column.key]" :show-overflow-tooltip="column.key !== 'operations'">
            <template #default="{ row }"><slot :name="column.key" :row="row">{{ column.prop ? (row[column.prop] ?? '—') : '—' }}</slot></template>
          </el-table-column>
        </el-table>
      </div>
      <div v-if="$slots.pagination" class="management-pagination"><slot name="pagination" /></div>
    </el-card>
  </section>
  </Teleport>
</template>

<style scoped>
.management-table { min-width: 0; }
.management-table :deep(.el-card__body) { display: flex; flex-direction: column; min-height: min(540px, calc(100dvh - 300px)); }
.management-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-bottom: 16px; }
.management-table-title { margin: 0; font-size: var(--el-font-size-large); font-weight: 600; }
.management-toolbar-actions, .management-table-tools { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.management-table-tools { gap: 0; }
.management-toolbar-actions :deep(.el-button + .el-button) { margin-left: 0; }
.management-table-region { min-width: 0; flex: 1; }
.management-pagination { display: flex; justify-content: flex-end; margin-top: 24px; padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); overflow-x: auto; }
.management-column-heading { display: flex; justify-content: space-between; margin-bottom: 8px; }
.management-column-row { display: flex; align-items: center; gap: 4px; }
.management-column-row .el-checkbox { flex: 1; min-width: 0; margin-right: 0; }
.management-column-row :deep(.el-checkbox__label) { overflow: hidden; text-overflow: ellipsis; }
.management-column-row .el-button { padding: 6px; margin: 0; }
.is-fullscreen { background: var(--el-bg-color); padding: 16px; height: 100%; }
.is-fullscreen .el-card { height: 100%; }
.is-fullscreen :deep(.el-card__body) { height: 100%; min-height: 0; }
.is-fullscreen .management-table-region { min-height: 0; }
@media (max-width: 600px) {
  .management-table :deep(.el-card__body) { min-height: 0; }
  .management-table-tools :deep(.el-button) { min-width: 44px; min-height: 44px; }
  .management-toolbar-actions { width: 100%; justify-content: flex-end; }
}
</style>
