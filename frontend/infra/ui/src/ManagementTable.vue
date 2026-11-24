<script setup lang="ts" generic="Row extends object">
import { VxeTable, VxeColumn } from "vxe-table";
import AppPagination from "./AppPagination.vue";
import ManagedDialog from "./ManagedDialog.vue";
import RecordAuditDetails, { type RecordAudit } from "./RecordAuditDetails.vue";
import { useSlots, computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
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
  /** 是否提供重新加载按钮；本地明细草稿没有独立读取接口时传 false。 */
  refreshable?: boolean;
  /** 无数据文案；缺省使用统一空态，错误必须在调用页单独呈现。 */
  emptyText?: string;
  /** 服务端返回的记录总数；省略时标题区不展示数量，禁止用当前页行数替代。 */
  total?: number;
  /** 标题层级，默认 h1；在成员弹出框等已有标题的区域使用 h2。 */
  headingLevel?: "h1" | "h2";
  /** 嵌入业务工作区时移除重复卡片边界；全屏模式仍生成独立表面。 */
  embedded?: boolean;
  /** 查看详情中的附属表格省略工具栏操作，默认 false；分页及表格数据照常显示。 */
  readonlyView?: boolean;
  /** 树形表格稳定行标识字段；缺省为普通列表，业务方保证整树唯一。 */
  rowKey?: string;
  /** 是否填满父级分配的表体高度，默认 false；仅在已建立 flex 高度链的弹框或面板中启用，分页仍独立置底。 */
  fitHeight?: boolean;
  /** 树形表格展开行标识；缺省由用户交互维护，不改变业务数据。 */
  expandedRowKeys?: string[];
  /** 当前高亮行标识；缺省不启用行选中，须与 rowKey 配合使用。 */
  currentRowKey?: string;
}>(), { loading: false, busy: false, refreshable: true, headingLevel: "h1", embedded: false, fitHeight: false });
/** 异步树数据渲染后同步展开与首行高亮；不依赖组件首次挂载时的数据快照。 */
const tableRef = ref<{ setTreeExpand: (row: Row, expanded: boolean) => void; setCurrentRow: (row?: Row) => void }>();
watch(() => [props.rows, props.expandedRowKeys, props.currentRowKey, tableRef.value], async () => {
  await nextTick();
  if (!props.rowKey || !tableRef.value) return;
  const key = props.rowKey;
  const visit = (rows: Row[]) => rows.forEach(row => {
    const record = row as Record<string, unknown>;
    if (props.expandedRowKeys) tableRef.value?.setTreeExpand(row, props.expandedRowKeys.includes(String(record[key])));
    if (props.currentRowKey !== undefined && String(record[key]) === props.currentRowKey) tableRef.value?.setCurrentRow(row);
    if (Array.isArray(record.children)) visit(record.children as Row[]);
  });
  visit(props.rows);
}, { flush: "post" });
/** 完整平面列表缺省本地分页；显式分页插槽和树数据由业务方保持原契约。 */
const slots = useSlots();
const localPage = ref(1);
const localPageSize = ref(20);
const localPagination = computed(() => !slots.pagination && !props.expandedRowKeys
  && !props.rows.some(row => Array.isArray((row as Record<string, unknown>).children))
  && (props.total === undefined || props.total === props.rows.length));
const displayedRows = computed(() => localPagination.value
  ? props.rows.slice((localPage.value - 1) * localPageSize.value, localPage.value * localPageSize.value)
  : props.rows);
/** 筛选及删除后修正页码，避免落在空页；容量改变回到第一页。 */
watch(() => props.rows, () => { localPage.value = 1; });
watch(localPageSize, () => { localPage.value = 1; });
const emit = defineEmits<{ refresh: []; expandChange: [row: Row, expanded: boolean] }>();
/** VxeTable 树展开事件统一转为业务行和展开状态，不改变服务端树数据。 */
function handleExpandChange({ row, expanded }: { row: Row; expanded: boolean }) {
  emit('expandChange', row, expanded);
}

defineSlots<{
  /** 列表标题旁的对象说明入口，不接收行数据。 */
  titleHelp?: () => unknown;
  /** 主列表展开详情；仅展示已授权行，不改变行数据和业务分页。 */
  expandContent?: (scope: { row: Row }) => unknown;
  actions?: () => unknown;
  before?: () => unknown;
  pagination?: () => unknown;
  [name: string]: ((scope: { row: Row }) => unknown) | undefined;
}>();
const { t } = useI18n();
/** 仅展示服务端原授权响应已携带的审计，不根据行 ID 发起额外查询。 */
const selectedAudit = ref<RecordAudit>();
const auditOpen = ref(false);
const hasAudit = computed(() => props.rows.some(row => Boolean((row as Record<string, unknown>).audit)));
/** 本地只读详情不触发业务写入，切换租户或列表时清理已选记录。 */
function showAudit(row: Row) {
  selectedAudit.value = (row as Record<string, unknown>).audit as RecordAudit | undefined;
  auditOpen.value = Boolean(selectedAudit.value);
}
watch(() => props.rows, () => { auditOpen.value = false; selectedAudit.value = undefined; });
const fullscreenHost = ref<HTMLElement>();
const tableRegion = ref<HTMLElement>();
const fullscreen = ref(false);
const tableHeight = ref<number>();
const size = ref<"small" | "default" | "large">("small");
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
/** 全屏、受约束工作区及显式自适应面板按剩余高度渲染，普通列表保持自然高度。 */
function measure() {
  const region = tableRegion.value;
  // 弹框显式声明 fitHeight 后复用同一测量路径，避免依赖应用视口祖先选择器。
  // 只有直接父容器实际分配 flex 高度时才测量；流式包装层按自然高度渲染，避免测量与表高互相撑大。
  const parent = region?.closest('.management-table')?.parentElement;
  const allocated = parent && !parent.classList.contains('management-table-stack') && getComputedStyle(parent).display === 'flex';
  const constrained = fullscreen.value || props.fitHeight || Boolean(allocated && region?.closest(".viewport-page-host .business-workspace, .viewport-page-host .tree-list-template, .viewport-page-host .standard-list-template"));
  // 高度链尚未就绪时回退自然表高，禁止把瞬时 0px 写成 1px 导致表头和数据被裁切。
  tableHeight.value = constrained && region && region.clientHeight >= 80 ? region.clientHeight : undefined;
}
watch([fullscreen, () => props.fitHeight],async()=>{await nextTick();measure();});
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
    <component :is="embedded && !fullscreen ? 'div' : 'el-card'" class="management-table-surface" shadow="never">
      <div class="management-toolbar">
        <div class="management-table-heading">
          <component :is="headingLevel" class="management-table-title">{{ title }}</component>
          <!-- 对象级说明紧邻列表标题，避免混入查询字段或工具操作。 -->
          <slot name="titleHelp" />
          <span v-if="total !== undefined" class="management-table-total">{{ t('management.totalRecords', { total }) }}</span>
        </div>
        <div v-if="!readonlyView" class="management-toolbar-actions">
          <slot name="actions" />
          <div class="management-table-tools">
            <el-tooltip v-if="refreshable" :teleported="!fullscreen" :content="t('management.refresh')"><el-button text :aria-label="t('management.refresh')" :disabled="loading || busy" @click="emit('refresh')"><AppIcon name="refresh" :size="18" /></el-button></el-tooltip>
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
        <vxe-table :column-config="{ resizable: true }" ref="tableRef" @toggle-tree-expand="handleExpandChange" :row-config="{ keyField: rowKey, isCurrent: currentRowKey !== undefined }" :tree-config="rowKey ? { childrenField: 'children' } : undefined" :loading="loading" :data="displayedRows" :size="size === 'default' ? 'medium' : size" :height="tableHeight" :empty-text="emptyText || t('management.empty')" border="inner">
          <vxe-column v-if="$slots.expandContent" type="expand" width="48"><template #content="{ row }"><slot name="expandContent" :row="row" /></template></vxe-column>
          <vxe-column v-for="column in visible" :key="column.key" :field="column.prop" :title="column.label" :width="column.width" :min-width="column.minWidth || 140" :fixed="fixed[column.key]" :tree-node="!!rowKey && column.key === visible[0]?.key" :show-overflow="column.showOverflow ?? (column.key !== 'operations')">
            <template #default="{ row }"><slot :name="column.key" :row="row">{{ column.prop ? (row[column.prop] ?? '—') : '—' }}</slot></template>
          </vxe-column>
          <vxe-column v-if="hasAudit" :title="t('recordAudit.title')" :width="130" fixed="right">
            <template #default="{ row }"><el-button v-if="row.audit" link type="primary" @click="showAudit(row)">{{ t('recordAudit.view') }}</el-button><span v-else>—</span></template>
          </vxe-column>
        </vxe-table>
      </div>
      <div v-if="$slots.pagination || localPagination" class="management-pagination"><slot name="pagination"><AppPagination v-model:current-page="localPage" v-model:page-size="localPageSize" :total="rows.length" :disabled="loading || busy" /></slot></div>
    </component>
  </section>
  </Teleport>
  <ManagedDialog v-model="auditOpen" :title="t('recordAudit.title')"><RecordAuditDetails v-if="selectedAudit" :audit="selectedAudit" /></ManagedDialog>
</template>

<style scoped>
.management-table { min-width: 0; }
.management-table-surface { display: flex; flex-direction: column; min-width: 0; }
.management-table :deep(.el-card__body) { display: flex; flex-direction: column; }
.management-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.management-table-heading { display: flex; align-items: baseline; gap: 8px; min-width: 0; }
.management-table-title { margin: 0; font-size: var(--el-font-size-large); font-weight: 600; }
.management-table-total { color: var(--el-text-color-secondary); font-size: var(--el-font-size-small); white-space: nowrap; }
.management-toolbar-actions, .management-table-tools { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.management-table-tools { gap: 2px; }
.management-table-tools :deep(.el-button) { width: 34px; height: 32px; padding: 0; }
.management-toolbar-actions :deep(.el-button + .el-button) { margin-left: 0; }
.management-table-region { min-width: 0; }
.management-pagination { flex-shrink: 0; min-height: 32px; display: flex; justify-content: flex-end; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); overflow-x: auto; }
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
  .management-table-tools :deep(.el-button) { min-width: 44px; min-height: 44px; }
  .management-toolbar-actions { width: 100%; justify-content: flex-end; }
}
</style>
