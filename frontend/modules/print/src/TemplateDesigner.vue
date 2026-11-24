<script setup lang="ts">
import { orientPaper } from "./paperOrientation";
import { AppIcon } from "@appnexus/icons";
import { FloatingError } from "@appnexus/ui";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { autoArrange, fieldElement, overflowCount, paperTemplate, previewData, type DesignContract } from "./authoring";
import { useI18n } from "vue-i18n";
import { createPrintDesigner, copyTemplate, type PrintDesigner, type PrintTemplate, type DesignerState } from "./index";

/** 隔离设计器外壳；加载失败可重试，保存失败保留画布，异步卸载不得遗留 iframe。 */
const props=defineProps<{modelValue:PrintTemplate;language:"cn"|"en";contract?:DesignContract;name?:string}>();
const emit=defineEmits<{"update:modelValue":[value:PrintTemplate]}>();
const {t}=useI18n();
const canvas=ref<HTMLElement>(),session=ref<PrintDesigner>(),mode=ref<"design"|"preview">("design");
const sample=ref(""),busy=ref(false),loading=ref(true),error=ref("");
/** 快捷配置默认关闭，只展开当前工具，避免表单长期挤占纸张画布。 */
const activeTool=ref<"field"|"text"|"paper"|"">("");
const fieldSearch=ref("");
const filteredFields=computed(()=>(props.contract?.fields??[]).filter(field=>(english.value?field.labelEn:field.labelZh).toLowerCase().includes(fieldSearch.value.toLowerCase())||field.key.toLowerCase().includes(fieldSearch.value.toLowerCase())));
/** 快捷表单与 iframe 分属隔离文档；停靠外壳右列时复用原属性列的预留空间，关闭后恢复原控件。 */
watch(()=>!!activeTool.value,async open=>{
  // 面板视图切换不走会跳过 busy 状态的业务操作锁，确保关闭后原生属性一定恢复。
  try{if(session.value)await session.value.workspace('quickPanel',open)}catch{error.value=t('printing.engineActionFailed')}
});
function toggleTool(value:"field"|"text"|"paper"){activeTool.value=activeTool.value===value?"":value;if(value==="paper")paper.value="CUSTOM"}
const selectedField=ref(""),fixedText=ref(""),previewRows=ref(3),paper=ref("A4"),paperWidth=ref(210),paperHeight=ref(297);
const english=computed(()=>props.language==="en");
const overflow=ref(0);
const state=ref<DesignerState>(),dirty=ref(false),grid=ref(true),ruler=ref(true),gridSpacing=ref(10);
let stateTimer:ReturnType<typeof setTimeout>|undefined;
/** 以真实引擎快照比较脏状态，覆盖原生拖拽与属性修改；单请求串行，卸载后停止。 */
async function refreshState(){
  try{if(session.value&&!busy.value){const next=await session.value.state();if(activeTool.value&&state.value&&next.selectionRevision!==state.value.selectionRevision)activeTool.value='';state.value=next;dirty.value=state.value.serialized!==baseline;if(activeTool.value!=="paper"){paperWidth.value=state.value.width;paperHeight.value=state.value.height}}}catch{/* 命令错误由实际操作反馈；状态轮询不覆盖用户错误。 */}
  finally{if(!disposed)stateTimer=setTimeout(refreshState,700)}
}
/** 删除只作用于引擎返回的选中元素；确认后仍进入统一快照历史，可撤销。 */
async function deleteSelection(){
  try{await ElMessageBox.confirm(t('printWorkbench.deleteConfirm'),t('printWorkbench.delete'),{type:'warning'})}catch{return}
  await workspace('delete');
}
async function workspace(command:"zoom"|"fit"|"align"|"grid"|"gridSpacing"|"quickPanel"|"ruler"|"copy"|"paste"|"delete",value?:number|string|boolean){await action(async()=>{await session.value!.workspace(command,value)});}

const ready=computed(()=>!busy.value&&!loading.value&&!!session.value&&mode.value==="design");
/** 对齐入口固定占位，仅多选时启用；HiPrint 的 vertical/horizontal 指对齐轴，不能按名称互换。 */
const alignmentTools = [
  { command: "left", icon: "alignLeft" },
  { command: "right", icon: "alignRight" },
  { command: "top", icon: "alignTop" },
  { command: "bottom", icon: "alignBottom" },
  { command: "vertical", icon: "alignHorizontalCenter" },
  { command: "horizontal", icon: "alignVerticalCenter" }
] as const;
const canAlign=computed(()=>ready.value&&(state.value?.selected??0)>=2);
let disposed=false;
let baseline=JSON.stringify(props.modelValue);
/** 加载期间的组件销毁在初始化返回后补做清理，不向已离开的页面安装会话。 */
async function initialize(){
  if(!canvas.value||busy.value)return;
  loading.value=true;error.value="";session.value?.destroy();session.value=undefined;
  try{
    const created=await createPrintDesigner(canvas.value,{template:props.modelValue,language:props.language,contract:props.contract});
    if(disposed){created.destroy();return}
    session.value=created;mode.value="design";const initial=await created.snapshot();baseline=JSON.stringify(initial);paperWidth.value=Number(initial.panels[0]?.width)||paperWidth.value;paperHeight.value=Number(initial.panels[0]?.height)||paperHeight.value;
  }catch{session.value?.destroy();session.value=undefined;if(!disposed)error.value=t("printing.engineLoadFailed")}
  finally{if(!disposed)loading.value=false}
}
onMounted(async()=>{await initialize();void refreshState()});
onBeforeUnmount(()=>{disposed=true;if(stateTimer)clearTimeout(stateTimer);session.value?.destroy()});
/** 获取经过校验的快照；未就绪必须报错，禁止父页把旧模板误认为已同步的新设计。 */
async function capture():Promise<PrintTemplate>{
  if(!session.value||loading.value)throw new Error("PRINT_DESIGNER_NOT_READY");
  const value=copyTemplate(await session.value.snapshot());overflow.value=overflowCount(value);emit("update:modelValue",value);return value;
}
/** 关闭前比较实际引擎快照，不依赖工具栏点击推断原生拖拽是否修改。 */
async function hasChanges(){if(!session.value)return false;return JSON.stringify(await capture())!==baseline}
/** 服务端保存成功后由父页提交基线；失败不清除未保存状态。 */
function markSaved(){baseline=JSON.stringify(props.modelValue);dirty.value=false}
defineExpose({snapshot:capture,hasChanges,markSaved});
async function action(run:()=>Promise<void>){
  if(busy.value||loading.value||!session.value)return;
  busy.value=true;error.value="";
  try{await run()}catch(cause){error.value=t(cause instanceof Error&&cause.message==="PRINT_ARRANGE_SPACE"?"printing.arrangeSpace":cause instanceof Error&&cause.message==="PRINT_ARRANGE_COMPLEX"?"printing.arrangeComplex":"printing.engineActionFailed")}finally{busy.value=false}
}
async function preview(){await action(async()=>{
  const template=await capture();
  const fields=new Set<string>();
  for(const panel of template.panels){
    const elements=panel.printElements;
    if(Array.isArray(elements))for(const element of elements){
      const field=(element as {options?:Record<string,unknown>}).options?.field;
      if(typeof field==="string"&&field)fields.add(field);
    }
  }
  // 示例值只用于隔离预览，不生成打印任务，也不冒充业务授权快照。
  const data=props.contract?previewData(props.contract,previewRows.value):Object.fromEntries([...fields].map(field=>[field,sample.value||t("printing.previewSample")]));
  await session.value!.preview(data);
  mode.value="preview";
})}
/** 向当前面板插入受控字段或固定文字；通过统一快照历史保留撤销能力。 */
async function insertField(){await action(async()=>{
  const field=props.contract?.fields.find(value=>value.key===selectedField.value);if(!field)return;
  const template=await capture(),panel=template.panels[0];
  const elements=Array.isArray(panel.printElements)?panel.printElements:[];
  panel.printElements=[...elements,fieldElement(field,english.value,40+(elements.length%15)*28)];
  await session.value!.replace(template);await capture();
})}
async function insertText(){if(!fixedText.value.trim())return;await action(async()=>{
  const template=await capture(),panel=template.panels[0];
  const elements=Array.isArray(panel.printElements)?panel.printElements:[];
  panel.printElements=[...elements,{options:{title:fixedText.value.trim(),top:40,left:24,width:250,height:24,fontSize:12},printElementType:{type:"text",title:""}}];
  await session.value!.replace(template);await capture();
})}
/** 各 Tab 应用原有业务逻辑；输入留在 Vue 状态，切换和关闭均不清空。 */
async function applyQuick(){if(activeTool.value==='paper')await applyPaper();else if(activeTool.value==='field')await insertField();else await insertText()}
/** 方向只交换纸张宽高；元素尺寸与坐标保持不变，显式应用后才进入模板历史。 */
function changeOrientation(value:string){[paperWidth.value,paperHeight.value]=orientPaper(paperWidth.value,paperHeight.value,value==='landscape')}
function selectPaper(value:string){const presets:Record<string,[number,number]>={A4:[210,297],A5:[148,210],B5:[176,250],LABEL:[100,60],RECEIPT:[80,200]};if(presets[value])[paperWidth.value,paperHeight.value]=presets[value]}
async function applyPaper(){await action(async()=>{await session.value!.replace(paperTemplate(await capture(),paperWidth.value,paperHeight.value));await capture();ElMessage.success(t("printing.applyPaper"))})}
/** 重新排布前明确提示范围，算法失败时不替换画布，成功后复用统一撤销历史。 */
async function arrangePaper(){
  try{await ElMessageBox.confirm(t("printing.arrangeConfirm"),t("printing.autoArrange"),{type:"warning"})}catch{return}
  await action(async()=>{await session.value!.replace(autoArrange(await capture()));await capture()});
}
/** 读取实际引擎纸张尺寸交换宽高，保留元素坐标，不使用未应用表单值。 */
async function rotatePaper(){await action(async()=>{
  const current=await capture(),panel=current.panels[0];
  const width=Number(panel.width),height=Number(panel.height);
  await session.value!.replace(paperTemplate(current,height,width));
  paperWidth.value=height;paperHeight.value=width;
  await capture();
})}
/** 低频画布命令统一分发；仅改变当前草稿或视图，复用原有命令与历史。 */
async function canvasCommand(command:string){
  if(command==='paper')toggleTool('paper');
  else if(command==='rotate')await rotatePaper();
}
/** 更多菜单保留原有快捷制作、自动排布及同步入口，不扩展业务能力。 */
async function moreCommand(command:string){
  if(command==='field'||command==='text')toggleTool(command);
  else if(command==='arrange')await arrangePaper();
  else await action(async()=>{await capture()});
}
async function design(){await action(async()=>{await session.value!.design();mode.value="design"})}
</script>

<template>
  <div v-loading="loading" class="native-designer">
    <header class="workbench-title">
      <div class="workbench-identity"><strong>{{name||t('printing.design')}}</strong><el-tag class="app-status-tag" v-if="session&&!loading" :type="dirty?'warning':'success'" role="status">{{t(dirty?'printWorkbench.unsaved':'printWorkbench.saved')}}</el-tag></div>
      <div class="title-actions"><el-tooltip v-if="contract?.designOnly" :content="t('printing.designOnlyHint')"><el-tag type="info" effect="plain">{{t('printWorkbench.preview')}}</el-tag></el-tooltip><slot name="actions"/></div>
    </header>
    <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" show-icon>
      <el-button v-if="!session" link :disabled="loading" @click="initialize">{{t('account.retry')}}</el-button>
    </FloatingError>


    <el-alert v-if="overflow" :title="t('printing.overflowHint',{count:overflow})" type="warning" :closable="false"/>
    <header class="native-toolbar" role="toolbar" :aria-label="t('printing.quickTools')">
      <div class="toolbar-group" v-if="mode==='design'">
        <el-button text :disabled="!ready||!state?.undo" @click="action(()=>session!.undo())">{{t('printing.undo')}}</el-button>
        <el-button text :disabled="!ready||!state?.redo" @click="action(()=>session!.redo())">{{t('printing.redo')}}</el-button>
        <el-button text :disabled="!ready||!state?.selected" @click="workspace('copy')">{{t('printWorkbench.copy')}}</el-button>
        <el-button text :disabled="!ready||!state?.clipboard" @click="workspace('paste')">{{t('printWorkbench.paste')}}</el-button>
        <el-button text :disabled="!ready||!state?.selected" @click="deleteSelection">{{t('printWorkbench.delete')}}</el-button>
      </div>
      <div v-if="mode==='design'" class="toolbar-group alignment-tools" role="group" :aria-label="t('printWorkbench.align')">
        <span class="tool-group-label">{{t('printWorkbench.alignment')}}</span>
        <el-tooltip v-for="tool in alignmentTools" :key="tool.command" :content="canAlign?t('printWorkbench.alignments.'+tool.command):t('printWorkbench.alignments.'+tool.command)+' · '+t('printWorkbench.alignHint')" placement="bottom">
          <span class="tool-button-anchor">
            <el-button text class="alignment-button" :disabled="!canAlign" :aria-label="t('printWorkbench.alignments.'+tool.command)" @click="workspace('align',tool.command)">
              <AppIcon :name="tool.icon" :size="18"/>
            </el-button>
          </span>
        </el-tooltip>
      </div>
      <el-dropdown v-if="mode==='design'" trigger="click" @command="canvasCommand"><el-button text :disabled="!ready">{{t('printWorkbench.canvas')}} <AppIcon name="arrowDown" :size="18" /></el-button><template #dropdown><el-dropdown-menu>
        <el-dropdown-item command="paper">{{t('printing.pageSettings')}}</el-dropdown-item>
        <el-dropdown-item command="rotate">{{t('printing.rotatePaper')}}</el-dropdown-item>
      </el-dropdown-menu></template></el-dropdown>
      <el-dropdown v-if="mode==='design'" trigger="click" @command="moreCommand"><el-button text :disabled="!ready">{{t('printWorkbench.more',english?'More':'更多')}} <AppIcon name="arrowDown" :size="18" /></el-button><template #dropdown><el-dropdown-menu>
        <el-dropdown-item command="field">{{t('printing.businessField')}}</el-dropdown-item><el-dropdown-item command="text">{{t('printing.addText')}}</el-dropdown-item><el-dropdown-item command="arrange">{{t('printing.autoArrange')}}</el-dropdown-item><el-dropdown-item command="sync">{{t('printing.syncDesign')}}</el-dropdown-item>
      </el-dropdown-menu></template></el-dropdown>
      <div class="toolbar-preview">
        <el-popover v-if="mode==='design'" trigger="click" width="280"><template #reference><el-button text :disabled="!ready">{{t('printWorkbench.sampleSettings',english?'Sample data':'示例数据')}}</el-button></template><el-form label-position="top"><el-form-item v-if="contract" :label="t('printing.previewRows')"><el-input-number v-model="previewRows" :min="1" :max="100"/></el-form-item><el-form-item v-else :label="t('printing.sampleValue')"><el-input v-model="sample"/></el-form-item></el-form><p>{{t('printing.designOnlyHint')}}</p></el-popover>
        <el-button v-if="mode==='design'" :loading="busy" :disabled="!ready" @click="preview">{{t('printing.preview')}}</el-button><el-button v-else :disabled="busy" @click="design">{{t('printing.backToDesign')}}</el-button>
      </div>
    </header>
    <div class="canvas-area">
      <div ref="canvas" class="native-canvas"/>
      <aside v-if="activeTool" class="quick-panel" :style="{width:(state?.rightWidth||360)+'px'}" :aria-label="t('printWorkbench.quickTitle')">
        <header class="quick-panel-header"><div><strong>{{t('printWorkbench.quickTitle')}}</strong><p>{{t('printWorkbench.quickSubtitle')}}</p></div><el-button text :disabled="busy" :aria-label="t('organization.cancel')" @click="activeTool=''">×</el-button></header>
        <el-tabs v-model="activeTool" stretch class="quick-panel-tabs"><el-tab-pane :label="t('printing.businessField')" name="field"/><el-tab-pane :label="t('printing.addText')" name="text"/><el-tab-pane :label="t('printing.pageSettings')" name="paper"/></el-tabs>
        <div class="quick-panel-content">
          <el-form label-position="top" @submit.prevent>
            <template v-if="activeTool==='field'"><el-input v-model="fieldSearch" clearable :placeholder="t('printing.chooseField')" :aria-label="t('printing.businessField')"/><p class="quick-note">{{contract?(english?contract.nameEn:contract.nameZh):t('printing.chooseField')}}</p><el-radio-group v-model="selectedField" class="field-list"><el-radio v-for="field in filteredFields" :key="field.key" :value="field.key"><span>{{english?field.labelEn:field.labelZh}}<small>{{field.key}}</small></span></el-radio></el-radio-group><el-empty v-if="!filteredFields.length" :image-size="48"/></template>
            <el-form-item v-if="activeTool==='text'" :label="t('printing.fixedText')"><el-input v-model="fixedText" type="textarea" :rows="5" maxlength="200" show-word-limit :disabled="!ready"/></el-form-item>
            <template v-if="activeTool==='paper'"><el-form-item :label="t('printing.paperPreset')"><el-select v-model="paper" :disabled="!ready" @change="selectPaper"><el-option v-for="value in ['A4','A5','B5','CUSTOM']" :key="value" :value="value" :label="value==='B5'?'B5':t('printing.papers.'+value)"/></el-select></el-form-item><el-form-item :label="t('printWorkbench.orientation')"><el-select :model-value="paperWidth>paperHeight?'landscape':'portrait'" :disabled="!ready" @change="changeOrientation"><el-option value="portrait" :label="t('printWorkbench.portrait')"/><el-option value="landscape" :label="t('printWorkbench.landscape')"/></el-select></el-form-item><div class="paper-dimensions"><el-form-item :label="t('printing.paperWidth')+' (mm)'"><el-input-number v-model="paperWidth" controls-position="right" :disabled="!ready||paper!=='CUSTOM'" :min="20" :max="1000"/></el-form-item><el-form-item :label="t('printing.paperHeight')+' (mm)'"><el-input-number v-model="paperHeight" controls-position="right" :disabled="!ready||paper!=='CUSTOM'" :min="20" :max="2000"/></el-form-item></div><p class="quick-note">{{t('printWorkbench.applyHint')}}</p></template>
          </el-form>
        </div>
        <footer class="quick-panel-footer"><el-button :disabled="busy" @click="activeTool=''">{{t('organization.cancel')}}</el-button><el-button type="primary" :loading="busy" :disabled="!ready||(activeTool==='field'&&!selectedField)||(activeTool==='text'&&!fixedText.trim())" @click="applyQuick">{{t(activeTool==='paper'?'printing.applyPaper':activeTool==='field'?'printing.addField':'printing.addText')}}</el-button></footer>
      </aside>
    </div>
    <footer class="workbench-status">
      <span class="status-summary">{{paperWidth}} × {{paperHeight}} mm · {{t('printWorkbench.panels',{count:state?.pages??modelValue.panels.length})}} · {{t('printWorkbench.selected',{count:state?.selected??0})}}</span>
      <div class="canvas-controls">
        <div class="canvas-aids" role="group" :aria-label="t('printWorkbench.canvasAids')">
          <el-checkbox v-model="grid" :disabled="!ready" @change="workspace('grid',grid)">{{t('printWorkbench.grid')}}</el-checkbox>
          <el-checkbox v-model="ruler" :disabled="!ready" @change="workspace('ruler',ruler)">{{t('printWorkbench.ruler')}}</el-checkbox>
          <el-popover v-if="mode==='design'" trigger="click" width="240" placement="top-end">
            <template #reference>
              <el-button text class="canvas-settings" :disabled="!ready" :aria-label="t('printWorkbench.gridSettings')" :title="t('printWorkbench.gridSettings')"><AppIcon name="settings" :size="16"/></el-button>
            </template>
            <el-form label-position="top" class="grid-settings-form">
              <el-form-item :label="t('printWorkbench.gridSpacing')+' (pt)'"><el-input-number v-model="gridSpacing" :min="2" :max="40" :disabled="!ready" @change="value=>workspace('gridSpacing',value??10)"/></el-form-item>
            </el-form>
          </el-popover>
        </div>
        <div class="zoom-controls" role="group" :aria-label="t('printWorkbench.zoom')">
          <el-button text :disabled="!ready||(state?.zoom??1)<=.25" :aria-label="t('printWorkbench.zoomOut')" @click="workspace('zoom',(state?.zoom??1)-.1)">−</el-button>
          <span class="zoom-percentage">{{Math.round((state?.zoom??1)*100)}}%</span>
          <el-button text :disabled="!ready||(state?.zoom??1)>=2" :aria-label="t('printWorkbench.zoomIn')" @click="workspace('zoom',(state?.zoom??1)+.1)">+</el-button>
          <el-button text :disabled="!ready" @click="workspace('fit')">{{t('printWorkbench.fit')}}</el-button>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* 页面外围留白引用全局内容与区块 Token，局部字段间距和触控高度按原交互保留。 */
.native-designer{display:flex;height:100%;min-height:0;flex-direction:column;overflow:hidden;background:var(--el-bg-color)}
.workbench-title{height:38px;flex:none;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:0 16px;border-bottom:1px solid var(--el-border-color-lighter)}
.workbench-title :deep(.el-button){height:28px;padding:4px 12px}.workbench-title :deep(.el-tag){height:22px}
.workbench-identity,.title-actions{display:flex;align-items:center;gap:8px;min-width:0}.workbench-identity strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.title-actions{flex:none}
.native-toolbar{height:50px;flex:none;display:flex;align-items:center;gap:12px;padding:0 12px;border-bottom:1px solid var(--el-border-color-lighter);overflow-x:auto;white-space:nowrap}
.toolbar-group{display:flex;align-items:center;flex:none;gap:4px;padding-right:12px;border-right:1px solid var(--el-border-color-lighter)}.native-toolbar :deep(.el-button){margin:0;height:32px}.toolbar-preview{display:flex;gap:8px;margin-left:auto;align-items:center;flex:none}
.canvas-area{position:relative;display:flex;flex:1;min-height:0;overflow:hidden}.native-canvas{flex:1;min-width:0;min-height:0;overflow:hidden;background:var(--el-fill-color-light)}
/* 辅助显示与缩放分别成组；只控制外壳排版，不改变 iframe 和纸张坐标。 */
.workbench-status{height:40px;flex:none;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:0 12px;border-top:1px solid var(--el-border-color-lighter);font-size:12px;color:var(--el-text-color-secondary)}
.status-summary{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.canvas-controls,.canvas-aids,.zoom-controls{display:flex;align-items:center;flex:none;gap:8px;white-space:nowrap}
.canvas-aids{gap:12px;padding-right:12px;border-right:1px solid var(--el-border-color-lighter)}
.canvas-aids :deep(.el-checkbox){height:28px;margin:0}.canvas-aids :deep(.el-checkbox__label){font-size:12px;padding-left:6px}
.canvas-controls :deep(.el-button){height:28px;margin:0;padding:4px 8px}.canvas-controls :deep(.canvas-settings){width:28px;padding:4px}
.zoom-controls{gap:4px}.zoom-percentage{min-width:44px;text-align:center;font-variant-numeric:tabular-nums}.grid-settings-form :deep(.el-form-item){margin-bottom:0}
.tool-group-label{margin-right:4px;color:var(--el-text-color-secondary);font-size:12px}.tool-button-anchor{display:inline-flex}.alignment-tools{gap:2px}.alignment-tools :deep(.alignment-button){width:32px;padding:0}

.authoring-grid{display:flex;flex-direction:column;gap:14px}.authoring-grid :deep(.el-form-item){margin:0}.quick-actions{display:flex;flex-wrap:wrap;gap:8px}.quick-actions :deep(.el-button){margin:0}.paper-summary{width:100%;color:var(--el-text-color-secondary)}
@media(max-width:700px){.workbench-title{padding:0 8px}.native-toolbar{gap:4px;padding:0 4px}.toolbar-group{gap:0;padding-right:4px}.workbench-status{height:auto;min-height:40px;align-items:stretch;flex-direction:column;gap:0;padding:4px 8px}.status-summary{line-height:24px}.canvas-controls{max-width:100%;overflow-x:auto;justify-content:flex-end}.canvas-aids{gap:8px;padding-right:8px}.title-actions{gap:4px}}
</style>

<style scoped>
.quick-panel{position:absolute;right:0;top:0;bottom:0;z-index:3;width:360px;flex:none;display:flex;flex-direction:column;min-height:0;border-left:1px solid var(--el-border-color-lighter);background:var(--el-bg-color)}
.quick-panel-header{height:64px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 16px;border-bottom:1px solid var(--el-border-color-lighter)}.quick-panel-header strong{font-size:16px;font-weight:600}.quick-panel-header p{margin:4px 0 0;font-size:12px;color:var(--el-text-color-secondary)}
.quick-panel-tabs{padding:0 16px;flex:none}.quick-panel-tabs :deep(.el-tabs__header){margin:0}.quick-panel-tabs :deep(.el-tabs__item){height:40px;font-size:13px}.quick-panel-tabs :deep(.el-tabs__item.is-active){background:var(--el-color-primary-light-9)}
.quick-panel-content{padding:var(--app-content-padding,12px) 16px;flex:1;min-height:0;overflow:auto}.quick-panel-content :deep(.el-input-number),.quick-panel-content :deep(.el-select){width:100%}.paper-dimensions{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px}.quick-note{font-size:12px;color:var(--el-text-color-secondary);line-height:1.6}.quick-panel-footer{min-height:56px;flex:none;display:flex;align-items:center;justify-content:flex-end;padding:8px 16px;border-top:1px solid var(--el-border-color-lighter)}.field-list{display:flex;flex-direction:column;align-items:stretch}.field-list :deep(.el-radio){height:auto;min-height:44px;margin:0;padding:8px 0}.field-list small{display:block;color:var(--el-text-color-secondary);font-size:12px;margin-top:4px}
</style>
