<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { AppIcon } from "@appnexus/icons";

/**
 * 统一业务弹出框：复用 Element Plus 焦点、遮罩和键盘管理，补充统一标题栏、最大化及桌面端双向调节尺寸。
 * 弹出框只管理容器交互；保存、校验、权限和关闭前业务约束仍由调用页负责。
 */
const props = withDefaults(defineProps<{
  /** 是否显示弹出框。 */
  modelValue: boolean;
  /** 弹出框标题，必须使用已国际化的用户可见文案。 */
  title: string;
  /** 初始宽度，支持 CSS 长度；默认 min(680px, calc(100vw - 32px))。 */
  width?: string | number;
  /** 初始高度，支持 CSS 长度；缺省时按内容自适应。 */
  height?: string | number;
  /** 是否允许最大化，默认 true。 */
  maximizable?: boolean;
  /** 是否允许拖动标题栏移动窗口，默认 true；移动范围限制在后台内容区。 */
  draggable?: boolean;
  /** 桌面端是否允许从右下角手动调节宽高，默认 true。 */
  resizable?: boolean;
  /** 普通窗口移动边界的内容区 CSS 选择器，默认 .app-main；找不到时退回当前视口。 */
  moveTarget?: string;
  /** 是否处于写入状态；为 true 时阻止关闭与重复交互。 */
  busy?: boolean;
  /** 关闭时是否销毁内容，默认 true，避免敏感表单残留。 */
  destroyOnClose?: boolean;
  /** 是否允许点击遮罩关闭；默认 true，busy 时始终禁止。 */
  closeOnClickModal?: boolean;
  /** 是否允许按 Esc 关闭；默认 true，busy 时始终禁止。 */
  closeOnPressEscape?: boolean;
  /** 是否显示模态遮罩，默认 true；画布工具窗口可设 false 以继续操作底层画布。 */
  modal?: boolean;
  /** 初始垂直位置，缺省沿用 Element Plus；仅首次布局使用，不在动画后重新定位。 */
  top?: string;
}>(), {
  width: "min(680px, calc(100vw - 32px))",
  maximizable: true,
  draggable: true,
  resizable: true,
  moveTarget: ".app-main",
  busy: false,
  destroyOnClose: true,
  closeOnClickModal: true,
  closeOnPressEscape: true
});
const emit = defineEmits<{ "update:modelValue": [value: boolean]; opened: []; closed: [] }>();
defineSlots<{ title?: () => unknown; default: () => unknown; footer?: () => unknown }>();
const { t } = useI18n();
const maximized = ref(false);
const dialogStyle = computed(() => props.height && !maximized.value ? { height: typeof props.height === "number" ? `${props.height}px` : props.height } : undefined);
let stopPointer: (() => void) | undefined,stopMaximizeObserver:(()=>void)|undefined;
let restoreStyle:Partial<Record<"position"|"left"|"top"|"width"|"height"|"margin"|"transform",string>>|undefined;
const restoreKeys=["position","left","top","width","height","margin","transform"] as const;
watch(() => props.modelValue, value => { if (!value) { maximized.value = false; stopPointer?.(); stopMaximizeObserver?.(); } });
function beforeClose(done: () => void) { if (!props.busy) done(); }
function close() { if (!props.busy) emit("update:modelValue", false); }

/** 内容边界排除应用壳顶部 padding 和侧栏宽度；独立夹具找不到目标时使用完整视口。 */
function contentBounds() {
  const target=document.querySelector<HTMLElement>(props.moveTarget)||document.querySelector<HTMLElement>(".app-main-nofixed-header");
  if(!target)return {left:0,top:0,right:window.innerWidth,bottom:window.innerHeight,width:window.innerWidth,height:window.innerHeight,target:null as HTMLElement|null};
  const rect=target.getBoundingClientRect(),style=getComputedStyle(target);
  const left=Math.max(0,rect.left+parseFloat(style.paddingLeft||"0")),top=Math.max(0,rect.top+parseFloat(style.paddingTop||"0"));
  const right=Math.min(window.innerWidth,rect.right-parseFloat(style.paddingRight||"0")),bottom=Math.min(window.innerHeight,rect.bottom-parseFloat(style.paddingBottom||"0"));
  return {left,top,right,bottom,width:Math.max(0,right-left),height:Math.max(0,bottom-top),target};
}
/** 调节结果限制在内容区和最小可操作尺寸内，避免标题栏或关闭按钮移出工作区。 */
function setSize(dialog: HTMLElement, width: number, height: number) {
  const rect=dialog.getBoundingClientRect(),bounds=contentBounds();
  dialog.style.width = `${Math.min(bounds.right-rect.left, Math.max(360, width))}px`;
  dialog.style.height = `${Math.min(bounds.bottom-rect.top, Math.max(240, height))}px`;
}
/** 指针拖动仅改变当前弹出框 DOM 尺寸，不持久化到账号或浏览器存储。 */
function startResize(event: PointerEvent) {
  if (maximized.value || !props.resizable || props.busy) return;
  const handle=event.currentTarget as HTMLElement,dialog=handle.closest(".app-managed-dialog") as HTMLElement|null;
  if(!dialog)return;
  event.preventDefault();
  const start=dialog.getBoundingClientRect(),startX=event.clientX,startY=event.clientY;
  const move=(current:PointerEvent)=>setSize(dialog,start.width+current.clientX-startX,start.height+current.clientY-startY);
  const finish=()=>{window.removeEventListener("pointermove",move);window.removeEventListener("pointerup",finish);stopPointer=undefined;};
  stopPointer?.();stopPointer=finish;window.addEventListener("pointermove",move);window.addEventListener("pointerup",finish,{once:true});
}
/** 键盘方向键每次调整 16px，为无法使用指针的用户保留等价操作。 */
function resizeByKeyboard(event: KeyboardEvent) {
  if(props.busy||!["ArrowLeft","ArrowRight","ArrowUp","ArrowDown"].includes(event.key))return;
  const dialog=(event.currentTarget as HTMLElement).closest(".app-managed-dialog") as HTMLElement|null;
  if(!dialog)return;
  event.preventDefault();const rect=dialog.getBoundingClientRect();
  setSize(dialog,rect.width+(event.key==="ArrowRight"?16:event.key==="ArrowLeft"?-16:0),rect.height+(event.key==="ArrowDown"?16:event.key==="ArrowUp"?-16:0));
}
/** 标题栏拖动保持整个窗口位于内容区；操作按钮区域不会触发移动。 */
function startMove(event:PointerEvent){
  if(maximized.value||!props.draggable||props.busy||(event.target as HTMLElement).closest(".app-dialog-actions"))return;
  const dialog=(event.currentTarget as HTMLElement).closest(".app-managed-dialog") as HTMLElement|null;if(!dialog)return;
  event.preventDefault();const start=dialog.getBoundingClientRect(),startX=event.clientX,startY=event.clientY,bounds=contentBounds();
  dialog.style.position="fixed";dialog.style.left=`${start.left}px`;dialog.style.top=`${start.top}px`;dialog.style.margin="0";dialog.style.transform="none";
  const move=(current:PointerEvent)=>{const left=Math.min(bounds.right-start.width,Math.max(bounds.left,start.left+current.clientX-startX));const top=Math.min(bounds.bottom-start.height,Math.max(bounds.top,start.top+current.clientY-startY));dialog.style.left=`${left}px`;dialog.style.top=`${top}px`;};
  const finish=()=>{window.removeEventListener("pointermove",move);window.removeEventListener("pointerup",finish);stopPointer=undefined;};
  stopPointer?.();stopPointer=finish;window.addEventListener("pointermove",move);window.addEventListener("pointerup",finish,{once:true});
}
function applyMaximizedBounds(dialog:HTMLElement){Object.assign(dialog.style,{position:"fixed",left:"0",top:"0",width:`${window.innerWidth}px`,height:`${window.innerHeight}px`,margin:"0",transform:"none"});}
/** 最大化占据完整浏览器视口；还原时恢复最大化前已拖动、调节后的窗口位置与尺寸。 */
async function toggleMaximize(event:MouseEvent){
  const dialog=(event.currentTarget as HTMLElement).closest(".app-managed-dialog") as HTMLElement|null;if(!dialog)return;
  stopPointer?.();
  if(!maximized.value){
    restoreStyle=Object.fromEntries(restoreKeys.map(key=>[key,dialog.style[key]]));
    maximized.value=true;await nextTick();applyMaximizedBounds(dialog);
    const resize=()=>applyMaximizedBounds(dialog);window.addEventListener("resize",resize);stopMaximizeObserver=()=>{window.removeEventListener("resize",resize);stopMaximizeObserver=undefined;};
  }else{
    stopMaximizeObserver?.();maximized.value=false;await nextTick();
    if(restoreStyle)for(const key of restoreKeys)dialog.style[key]=restoreStyle[key]||"";
  }
}
onBeforeUnmount(()=>{stopPointer?.();stopMaximizeObserver?.();});
</script>

<template>
  <el-dialog
    :modal="modal ?? true"
    :modal-penetrable="modal === false"
    :lock-scroll="modal !== false"
    :top="top"
    :model-value="modelValue"
    :title="title"
    :width="width"
    :style="dialogStyle"
    :class="['app-managed-dialog', { 'is-dialog-maximized': maximized, 'is-dialog-resizable': resizable }]"
    :show-close="false"
    :append-to-body="true"
    :destroy-on-close="destroyOnClose"
    :close-on-click-modal="closeOnClickModal && !busy"
    :close-on-press-escape="closeOnPressEscape && !busy"
    :before-close="beforeClose"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="emit('opened')"
    @closed="emit('closed')"
  >
    <template #header="{ titleId, titleClass }">
      <div class="app-dialog-header" :class="{ 'is-dialog-draggable': draggable&&!maximized }" @pointerdown="startMove">
        <div :id="titleId" :class="['app-dialog-title', titleClass]"><slot name="title">{{ title }}</slot></div>
        <div class="app-dialog-actions">
          <el-tooltip v-if="maximizable" :content="t(maximized ? 'management.restoreDialog' : 'management.maximizeDialog')">
            <el-button text :aria-label="t(maximized ? 'management.restoreDialog' : 'management.maximizeDialog')" :disabled="busy" @click="toggleMaximize">
              <AppIcon :name="maximized ? 'fullscreenExit' : 'fullscreen'" :size="18" />
            </el-button>
          </el-tooltip>
          <el-tooltip :content="t('management.closeDialog')">
            <el-button text :aria-label="t('management.closeDialog')" :disabled="busy" @click="close"><AppIcon name="close" :size="20" /></el-button>
          </el-tooltip>
        </div>
      </div>
    </template>
    <slot />
    <button v-if="resizable&&!maximized" type="button" class="app-dialog-resize-handle" :aria-label="t('management.resizeDialog')" :disabled="busy" @pointerdown="startResize" @keydown="resizeByKeyboard" />
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </el-dialog>
</template>

<style>
.app-managed-dialog.el-dialog { position: relative; display: flex; flex-direction: column; max-width: calc(100vw - 32px); max-height: calc(100dvh - 32px); overflow: hidden; padding: 0; }
.app-managed-dialog.is-dialog-resizable:not(.is-dialog-maximized) { min-width: min(360px, calc(100vw - 32px)); min-height: 240px; }
.app-managed-dialog .el-dialog__header { flex: none; margin: 0; padding: 0; border-bottom: 1px solid var(--el-border-color-lighter); background: color-mix(in srgb, var(--el-bg-color) 94%, var(--el-color-primary) 6%); }
.app-dialog-header { display: flex; min-height: 56px; align-items: center; justify-content: space-between; gap: 16px; padding: 0 12px 0 24px; }
.app-dialog-header.is-dialog-draggable { cursor: move; user-select: none; }
.app-dialog-title { min-width: 0; overflow: hidden; color: var(--el-text-color-primary); font-size: var(--el-font-size-large); font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.app-dialog-actions { display: flex; flex: none; align-items: center; gap: 2px; }
.app-dialog-actions .el-button { width: 36px; height: 36px; margin: 0; padding: 0; }
.app-managed-dialog .el-dialog__body { flex: 1; min-height: 0; overflow: auto; padding: 20px 24px; }
.app-managed-dialog .el-dialog__footer { flex: none; padding: 16px 24px; border-top: 1px solid var(--el-border-color-lighter); }
.app-managed-dialog.is-dialog-maximized { max-width: none; max-height: none; resize: none; }
.app-dialog-resize-handle { position: absolute; z-index: 2; right: 2px; bottom: 2px; width: 20px; height: 20px; padding: 0; border: 0; color: var(--el-text-color-secondary); background: linear-gradient(135deg, transparent 45%, currentcolor 46%, currentcolor 52%, transparent 53%, transparent 63%, currentcolor 64%, currentcolor 70%, transparent 71%); cursor: nwse-resize; }
.app-dialog-resize-handle:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
@media (max-width: 760px) {
  .app-managed-dialog.el-dialog:not(.is-dialog-maximized) { width: calc(100vw - 32px) !important; max-height: calc(100dvh - 32px); margin: 16px auto; resize: none; }
  .app-dialog-header { min-height: 56px; padding-left: 16px; }
  .app-dialog-actions .el-button { width: 44px; height: 44px; }
  .app-managed-dialog .el-dialog__body { padding: 16px; }
  .app-dialog-resize-handle { display: none; }
}
</style>
