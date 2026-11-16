<script setup lang="ts">
import { AppPagination } from "@appnexus/ui";
import { ManagedDialog, RecordAuditDetails, type RecordAudit } from "@appnexus/ui";
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { VxeTable, VxeColumn } from "vxe-table";
import VxeUI from "vxe-pc-ui";
import zhCN from "vxe-table/es/locale/lang/zh-CN";
import enUS from "vxe-table/es/locale/lang/en-US";
import { AppIcon } from "@appnexus/icons";
import type { ManagementColumn } from "@appnexus/ui";
import type { DictionaryItem } from "./index";
import "vxe-table/lib/style.css";

/** 字典页局部试用虚拟表格；保留真实行数据及权限插槽，不承担请求、授权或写入职责。 */
const props = defineProps<{
  /** 是否嵌入工作区；默认 false。启用时去除重复边框和留白，全屏仍保留独立表面。 */
  embedded?: boolean;
  /** 国际化列定义；沿用公共表格列契约，单位 px。 */
  columns: ManagementColumn[];
  /** 当前筛选后的真实字典条目，不在组件内生成演示数据。 */
  rows: DictionaryItem[];
  /** 读取期间显示加载态，刷新保留已有行。 */
  loading: boolean;
  /** 写入期间禁用刷新，避免与保存竞争。 */
  busy: boolean;
  /** 已翻译的列表标题。 */
  title: string;
  /** 已翻译的无数据或未选范围提示。 */
  emptyText: string;
}>();
const emit = defineEmits<{ refresh: [] }>();
const { t, locale } = useI18n();
/** 字典虚拟表格复用公共审计详情；仅读取原授权响应，不请求新接口或编辑审计值。 */
const auditOpen=ref(false),selectedAudit=ref<RecordAudit>();
const hasAudit=computed(()=>props.rows.some(row=>Boolean((row as unknown as {audit?:RecordAudit}).audit)));
function showAudit(row:DictionaryItem){selectedAudit.value=(row as unknown as {audit?:RecordAudit}).audit;auditOpen.value=Boolean(selectedAudit.value);}
watch(()=>props.rows,()=>{auditOpen.value=false;selectedAudit.value=undefined;});
const size = ref<"small" | "default" | "large">("small");
const fullscreen = ref(false);
const fullscreenHost = ref<HTMLElement>();
/** 全屏移动同一表格实例；弹窗负责 Esc、焦点恢复和滚动锁，保持分页及列偏好。 */
function toggleFullscreen() { fullscreen.value = !fullscreen.value; }
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

/** 客户端分页只作用于当前已加载筛选结果；不伪造服务端总数，筛选或数据变化重置首页。 */
const page = ref(1), pageSize = ref(10);
const pageRows = computed(() => props.rows.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
watch(() => props.rows, () => { page.value = 1; });
watch(pageSize, () => { page.value = 1; });

/** Vxe 独立语言字典跟随现有 i18n；不创建第二套业务语言状态。 */
watch(locale, value => {
  VxeUI.setI18n("zh-CN", zhCN);
  VxeUI.setI18n("en-US", enUS);
  VxeUI.setLanguage(value.startsWith("zh") ? "zh-CN" : "en-US");
}, { immediate: true });
</script>
<template>
  <el-dialog v-model="fullscreen" :title="title" fullscreen append-to-body :show-close="false" class="dictionary-fullscreen-dialog"><div ref="fullscreenHost" class="dictionary-fullscreen-host" /></el-dialog>
  <Teleport :to="fullscreenHost || 'body'" :disabled="!fullscreen || !fullscreenHost">
  <section :class="{ 'dictionary-grid-fullscreen': fullscreen, 'is-embedded': embedded && !fullscreen }" class="dictionary-grid" :aria-label="title">
    <header class="dictionary-grid-toolbar">
      <div class="dictionary-grid-heading"><h2>{{ title }}</h2><span class="dictionary-grid-count">{{ t('management.totalRecords', { total: rows.length }) }}</span></div>
      <div class="dictionary-grid-actions"><slot name="actions" /><div class="management-table-tools">
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
            <el-tooltip :teleported="!fullscreen" :content="t(fullscreen ? 'management.exitFullscreen' : 'management.fullscreen')"><el-button text :aria-label="t(fullscreen ? 'management.exitFullscreen' : 'management.fullscreen')" @click="toggleFullscreen"><AppIcon :name="fullscreen ? 'fullscreenExit' : 'fullscreen'" :size="18" /></el-button></el-tooltip></div></div>
    </header>
    <!-- 独立定位容器隔离表格固有高度，避免 auto 测量把工具栏高度反复计入父容器。 -->
    <div class="dictionary-grid-viewport">
    <VxeTable :data="pageRows" :loading="loading" height="100%" :size="size === 'small' ? 'mini' : size === 'large' ? 'large' : 'medium'" border="none" show-overflow :seq-config="{ startIndex: (page - 1) * pageSize }" :row-config="{ keyField: 'id', isHover: true }" :column-config="{ resizable: true }" :scroll-y="{ enabled: true, gt: 50 }" :empty-text="emptyText">
      <VxeColumn type="seq" width="56"  />
      <VxeColumn v-for="column in visible" :key="column.key" :field="column.prop" :title="column.label" :width="column.width" :min-width="column.minWidth || 140" :fixed="fixed[column.key]" :show-overflow="column.key !== 'operations'">
        <template #default="{ row }"><slot :name="column.key" :row="row">{{ column.prop ? row[column.prop] ?? '—' : '—' }}</slot></template>
      </VxeColumn>
      <VxeColumn v-if="hasAudit" :title="t('recordAudit.title')" :width="130" fixed="right"><template #default="{row}"><el-button v-if="row.audit" link type="primary" @click="showAudit(row)">{{t('recordAudit.view')}}</el-button></template></VxeColumn>
    </VxeTable>
    </div>
    <footer class="dictionary-grid-pagination"><AppPagination size="small" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="rows.length" layout="total, sizes, prev, pager, next" background /></footer>
  </section>
  </Teleport>
  <ManagedDialog v-model="auditOpen" :title="t('recordAudit.title')"><RecordAuditDetails v-if="selectedAudit" :audit="selectedAudit" /></ManagedDialog>
</template>
<style>
/* 全屏弹窗仅承载同一表格，不产生额外实例或重复请求。 */
.dictionary-fullscreen-dialog .el-dialog__header{display:none}
.dictionary-fullscreen-dialog .el-dialog__body{height:100%;padding:0}
.dictionary-fullscreen-host{height:100%;min-height:0}
</style>
<style scoped>
.dictionary-grid-fullscreen{height:100%;box-sizing:border-box}
.management-table-tools{display:flex;align-items:center;gap:4px}
.management-table-tools :deep(.el-button){width:32px;height:32px;padding:0}
.management-column-heading,.management-column-row{display:flex;align-items:center;gap:6px}
.management-column-heading{justify-content:space-between;margin-bottom:8px}
.management-column-row .el-checkbox{flex:1;min-width:0;margin:0}
.management-column-row :deep(.el-checkbox__label){overflow:hidden;text-overflow:ellipsis}
.management-column-row .el-button{padding:4px;margin:0}

/* 表格脱离尺寸计算流，父级剩余高度仅由 flex 分配，切断 ResizeObserver 高度反馈循环。 */
.dictionary-grid{display:flex;flex-direction:column;min-height:0;overflow:hidden}
.dictionary-grid-toolbar{flex:none}
.dictionary-grid-viewport{position:relative;flex:1;min-height:0;overflow:hidden}
.dictionary-grid-viewport > :deep(.vxe-table){position:absolute;inset:0;width:100%;height:100%}

/* 默认紧凑行高与横向分隔；工具按钮沿用 Element Plus，避免重绘交互。 */
.dictionary-grid-count{font-size:12px;color:var(--el-text-color-secondary)}
.dictionary-grid :deep(.vxe-header--column){font-weight:500;color:var(--el-text-color-regular)}
.dictionary-grid :deep(.vxe-body--column){border-bottom:1px solid var(--el-border-color-lighter)}
.dictionary-grid :deep(.vxe-table--header-wrapper){border-radius:var(--el-border-radius-base)}

/* 仅映射试用表格局部主题，沿用 Element Plus 主色及深浅色变量，不替换应用主题。 */
.dictionary-grid{min-width:0;--vxe-ui-font-color:var(--el-text-color-primary);--vxe-ui-primary-color:var(--el-color-primary);--vxe-ui-layout-background-color:var(--el-bg-color);--vxe-ui-table-border-color:var(--el-border-color-lighter);--vxe-ui-table-header-background-color:var(--el-fill-color-light);--vxe-ui-table-row-hover-background-color:var(--el-fill-color-light);--vxe-ui-table-row-current-background-color:var(--el-color-primary-light-9)}
.dictionary-grid-toolbar,.dictionary-grid-heading,.dictionary-grid-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.dictionary-grid-toolbar{justify-content:space-between;margin-bottom:16px}
.dictionary-grid-heading h2{margin:0;font-size:14px;font-weight:600}
.dictionary-grid-actions :deep(.el-button + .el-button){margin-left:0}
@media(max-width:600px){.dictionary-grid-actions{width:100%}.dictionary-grid-actions :deep(.el-button){min-height:44px}}

/* 表格继承页面字体；填充使用中性表面，避免正文和辅助信息同时抢占视觉焦点。 */
.dictionary-grid{--vxe-ui-font-family:inherit;--vxe-ui-font-size-mini:13px;--vxe-ui-font-size-medium:14px;--vxe-ui-font-color:var(--el-text-color-regular);--vxe-ui-table-header-background-color:color-mix(in srgb,var(--el-fill-color-light) 65%,var(--el-bg-color));--vxe-ui-table-row-hover-background-color:color-mix(in srgb,var(--el-fill-color-light) 70%,var(--el-bg-color))}
.dictionary-grid :deep(.vxe-table){font-family:inherit}
.dictionary-grid :deep(.vxe-header--column){font-size:12px;font-weight:500;color:var(--el-text-color-regular)}
.dictionary-grid-count{color:var(--el-text-color-regular)}

/* 表格卡片与分页分开分配高度，防止页码被固定表体挤出可视区域。 */
.dictionary-grid{padding:12px;border:1px solid var(--el-border-color-lighter);border-radius:var(--app-card-radius, 8px);background:var(--el-bg-color)}
.dictionary-grid-toolbar{margin-bottom:var(--app-section-gap,8px)}
.dictionary-grid-heading h2{font-size:15px}
/* 分页使用组件 small 密度，桌面顶部间距 6px；移动端保留可触达的页码按钮。 */
.dictionary-grid-pagination{flex:none;padding-top:6px;overflow-x:auto}
.dictionary-grid-pagination :deep(.el-pagination){justify-content:flex-end}
@media(max-width:600px){.dictionary-grid-pagination :deep(.el-pager li),.dictionary-grid-pagination :deep(.btn-prev),.dictionary-grid-pagination :deep(.btn-next){min-width:44px;height:44px}}
.dictionary-grid :deep(.vxe-header--column){font-size:13px}
/* 嵌入模式复用宿主工作区表面；全屏 Teleport 后恢复卡片边距与分页空间。 */
.dictionary-grid.is-embedded { padding:0; border:0; border-radius:0; }
</style>
