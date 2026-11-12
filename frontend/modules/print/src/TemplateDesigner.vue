<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ManagedDialog } from "@appnexus/ui";
import { ElMessageBox } from "element-plus";
import { autoArrange, fieldElement, overflowCount, paperTemplate, previewData, starterTemplate, type DesignContract } from "./authoring";
import { useI18n } from "vue-i18n";
import { createPrintDesigner, copyTemplate, type PrintDesigner, type PrintTemplate } from "./index";

/** 隔离设计器外壳；加载失败可重试，保存失败保留画布，异步卸载不得遗留 iframe。 */
const props=defineProps<{modelValue:PrintTemplate;language:"cn"|"en";contract?:DesignContract}>();
const emit=defineEmits<{"update:modelValue":[value:PrintTemplate]}>();
const {t}=useI18n();
const canvas=ref<HTMLElement>(),session=ref<PrintDesigner>(),mode=ref<"design"|"preview">("design");
const sample=ref(""),busy=ref(false),loading=ref(true),error=ref("");
/** 快捷配置默认关闭，只展开当前工具，避免表单长期挤占纸张画布。 */
const activeTool=ref<"field"|"text"|"paper"|"">("");
function toggleTool(value:"field"|"text"|"paper"){activeTool.value=activeTool.value===value?"":value}
const selectedField=ref(""),fixedText=ref(""),previewRows=ref(3),paper=ref("A4"),paperWidth=ref(210),paperHeight=ref(297);
const english=computed(()=>props.language==="en");
const overflow=ref(0);
const ready=computed(()=>!busy.value&&!loading.value&&!!session.value&&mode.value==="design");
let disposed=false;
let baseline=JSON.stringify(props.modelValue);
/** 加载期间的组件销毁在初始化返回后补做清理，不向已离开的页面安装会话。 */
async function initialize(){
  if(!canvas.value||busy.value)return;
  loading.value=true;error.value="";session.value?.destroy();session.value=undefined;
  try{
    const created=await createPrintDesigner(canvas.value,{template:props.modelValue,language:props.language});
    if(disposed){created.destroy();return}
    session.value=created;mode.value="design";baseline=JSON.stringify(await created.snapshot());
  }catch{session.value?.destroy();session.value=undefined;if(!disposed)error.value=t("printing.engineLoadFailed")}
  finally{if(!disposed)loading.value=false}
}
onMounted(initialize);
onBeforeUnmount(()=>{disposed=true;session.value?.destroy()});
/** 获取经过校验的快照；未就绪必须报错，禁止父页把旧模板误认为已同步的新设计。 */
async function capture():Promise<PrintTemplate>{
  if(!session.value||loading.value)throw new Error("PRINT_DESIGNER_NOT_READY");
  const value=copyTemplate(await session.value.snapshot());overflow.value=overflowCount(value);emit("update:modelValue",value);return value;
}
/** 关闭前比较实际引擎快照，不依赖工具栏点击推断原生拖拽是否修改。 */
async function hasChanges(){if(!session.value)return false;return JSON.stringify(await capture())!==baseline}
function markSaved(){baseline=JSON.stringify(props.modelValue)}
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
async function useStarter(){
  if(!props.contract)return;
  try{await ElMessageBox.confirm(t("printing.starterConfirm"),t("printing.useStarter"),{type:"warning"})}catch{return}
  await action(async()=>{await session.value!.replace(starterTemplate(props.contract!,english.value));await capture()});
}
function selectPaper(value:string){const presets:Record<string,[number,number]>={A4:[210,297],A5:[148,210],LABEL:[100,60],RECEIPT:[80,200]};if(presets[value])[paperWidth.value,paperHeight.value]=presets[value]}
async function applyPaper(){await action(async()=>{await session.value!.replace(paperTemplate(await capture(),paperWidth.value,paperHeight.value));await capture()})}
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
async function design(){await action(async()=>{await session.value!.design();mode.value="design"})}
</script>

<template>
  <div v-loading="loading" class="native-designer">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button v-if="!session" link :disabled="loading" @click="initialize">{{t('account.retry')}}</el-button>
    </el-alert>

    <el-alert v-if="contract?.designOnly" :title="t('printing.designOnlyHint')" type="warning" :closable="false"/>
    <el-alert v-if="overflow" :title="t('printing.overflowHint',{count:overflow})" type="warning" :closable="false"/>
    <ManagedDialog :model-value="!!activeTool" :title="t('printing.quickTools')" :modal="false" :maximizable="false" :close-on-click-modal="false" :close-on-press-escape="false" :busy="busy" width="340px" top="190px" class="print-floating-tools" move-target=".native-designer" @update:model-value="value=>{if(!value)activeTool=''}"><div class="authoring-tools">
      <div class="quick-actions" v-if="mode==='design'">
        <el-button :type="activeTool==='field'?'primary':'default'" plain :aria-expanded="activeTool==='field'" :disabled="!ready||!contract" @click="toggleTool('field')">{{t('printing.businessField')}}</el-button>
        <el-button :type="activeTool==='text'?'primary':'default'" plain :aria-expanded="activeTool==='text'" :disabled="!ready" @click="toggleTool('text')">{{t('printing.addText')}}</el-button>
        <el-button :type="activeTool==='paper'?'primary':'default'" plain :aria-expanded="activeTool==='paper'" :disabled="!ready" @click="toggleTool('paper')">{{t('printing.pageSettings')}}</el-button>
        <span class="paper-summary">{{paperWidth}} × {{paperHeight}} mm</span>
      </div>
        <el-form v-if="activeTool&&mode==='design'" label-position="top" class="authoring-grid" @submit.prevent>
          <el-form-item v-if="activeTool==='field'" :label="t('printing.businessField')"><el-select v-model="selectedField" filterable :disabled="!ready||!contract" :placeholder="t('printing.chooseField')"><el-option v-for="field in contract?.fields??[]" :key="field.key" :value="field.key" :label="english?field.labelEn:field.labelZh"/></el-select><el-button :disabled="!ready||!selectedField" @click="insertField">{{t('printing.addField')}}</el-button></el-form-item>
          <el-form-item v-if="activeTool==='text'" :label="t('printing.fixedText')"><el-input v-model="fixedText" maxlength="200" :disabled="!ready"/><el-button :disabled="!ready||!fixedText.trim()" @click="insertText">{{t('printing.addText')}}</el-button></el-form-item>
          <el-form-item v-if="activeTool==='paper'" :label="t('printing.paperPreset')"><el-select v-model="paper" :disabled="!ready" @change="selectPaper"><el-option v-for="value in ['A4','A5','LABEL','RECEIPT','CUSTOM']" :key="value" :value="value" :label="t('printing.papers.'+value)"/></el-select></el-form-item>
          <el-form-item v-if="activeTool==='paper'" :label="t('printing.paperWidth')+' (mm)'"><el-input-number v-model="paperWidth" :disabled="!ready" :min="20" :max="1000"/></el-form-item>
          <el-form-item v-if="activeTool==='paper'" :label="t('printing.paperHeight')+' (mm)'"><el-input-number v-model="paperHeight" :disabled="!ready" :min="20" :max="2000"/></el-form-item>
          <el-form-item v-if="activeTool==='paper'" :label="t('printing.pageSettings')"><el-button :disabled="!ready" @click="applyPaper">{{t('printing.applyPaper')}}</el-button><el-button v-if="contract" :disabled="!ready" @click="useStarter">{{t('printing.useStarter')}}</el-button></el-form-item>
        </el-form>

    </div>
    </ManagedDialog>
    <header class="native-toolbar" role="toolbar" :aria-label="t('printing.quickTools')">
      <div class="toolbar-group" v-if="mode==='design'"><el-button :disabled="!ready" @click="toggleTool('field')">{{t('printing.quickTools')}}</el-button><el-button-group>
        <el-button :disabled="busy||loading||!session" @click="action(()=>session!.undo())">{{t('printing.undo')}}</el-button>
        <el-button :disabled="busy||loading||!session" @click="action(()=>session!.redo())">{{t('printing.redo')}}</el-button>
        <el-button :disabled="busy||loading||!session" @click="action(async()=>{await capture()})">{{t('printing.syncDesign')}}</el-button>
      </el-button-group></div>
      <div class="toolbar-group toolbar-preview">
        <el-button v-if="mode==='design'" :disabled="!ready" @click="rotatePaper">{{t('printing.rotatePaper')}}</el-button><el-button v-if="mode==='design'" :disabled="!ready" @click="arrangePaper">{{t('printing.autoArrange')}}</el-button>
        <div v-if="mode==='design'" class="preview-control">
          <el-button type="primary" :loading="busy" :disabled="loading||!session" @click="preview">{{t('printing.preview')}}</el-button>
          <el-tooltip v-if="contract" :content="t('printing.previewRows')"><el-input-number v-model="previewRows" controls-position="right" :min="1" :max="100" :disabled="busy||loading||!session" :aria-label="t('printing.previewRows')"/></el-tooltip>
          <el-input v-else v-model="sample" :aria-label="t('printing.sampleValue')" :placeholder="t('printing.sampleValue')" class="sample-input"/>
        </div>
        <el-button v-else type="primary" :disabled="busy" @click="design">{{t('printing.backToDesign')}}</el-button>
      </div>
    </header>
    <div class="canvas-area">
      <aside class="canvas-info" :aria-label="t('printing.pageSettings')">
        <div class="canvas-info-row"><el-tooltip :content="t('printing.selfServiceHint')"><el-tag type="info" effect="plain">{{paperWidth}} × {{paperHeight}} mm</el-tag></el-tooltip>        </div>

      </aside>
      <div ref="canvas" class="native-canvas"/>
    </div>
  </div>
</template>

<style scoped>
.native-designer{display:flex;height:100%;min-height:0;flex-direction:column;overflow:hidden;border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base);background:var(--el-bg-color)}
.designer-guide{flex:none;margin:0;padding:10px 16px;color:var(--el-text-color-secondary);font-size:var(--el-font-size-small);line-height:1.6;background:var(--el-fill-color-light);border-bottom:1px solid var(--el-border-color-lighter)}
.authoring-tools{padding:0 16px;flex:none;border-top:0;border-bottom:1px solid var(--el-border-color-lighter)}
.authoring-tools :deep(.el-collapse-item__header){height:40px;font-weight:500;border-bottom:0}
.authoring-tools :deep(.el-collapse-item__wrap){border-bottom:0}
.authoring-tools :deep(.el-collapse-item__content){padding:4px 0 12px}
.quick-actions{display:flex;align-items:center;gap:8px;padding:8px 0}.quick-actions :deep(.el-button){margin-left:0}.paper-summary{margin-left:auto;color:var(--el-text-color-secondary);font-size:var(--el-font-size-small)}.authoring-grid{display:flex;flex-wrap:wrap;align-items:center;gap:8px 16px;padding:8px 0;border-top:1px solid var(--el-border-color-lighter)}
.authoring-grid :deep(.el-form-item){margin:0;max-width:100%}.authoring-grid :deep(.el-select){width:240px}.authoring-grid :deep(.el-input){min-width:160px}
.authoring-grid :deep(.el-form-item__label){padding-bottom:6px;font-size:var(--el-font-size-small);color:var(--el-text-color-secondary)}
.authoring-grid :deep(.el-form-item__content){display:flex;flex-wrap:nowrap;gap:8px}
.authoring-grid :deep(.el-input),.authoring-grid :deep(.el-select){min-width:0;flex:1}
.authoring-grid :deep(.el-button){margin-left:0;flex:none}
.native-toolbar{display:flex;flex:none;align-items:center;justify-content:center;flex-wrap:wrap;gap:12px;padding:10px 16px;border-bottom:1px solid var(--el-border-color-lighter);background:var(--el-bg-color)}
.toolbar-preview{justify-content:center}.toolbar-group{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.sample-input{width:180px}
.canvas-info{position:absolute;top:12px;right:352px;z-index:2;display:grid;gap:8px;max-width:280px;padding:10px 12px;border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base);background:color-mix(in srgb,var(--el-bg-color) 65%,transparent);backdrop-filter:blur(10px);box-shadow:none}.canvas-info-row{display:flex;align-items:center;justify-content:space-between;gap:12px}.info-label{color:var(--el-text-color-secondary);font-size:var(--el-font-size-small)}.preview-settings{display:flex;align-items:center;gap:8px}.preview-settings :deep(.el-input-number){width:118px}.preview-settings .info-label{max-width:140px;line-height:1.4}.canvas-info :deep(.el-tag){border:0;background:transparent}.canvas-info .sample-input{width:130px}.canvas-area{position:relative;display:flex;flex:1;min-height:0}.floating-tools-trigger{position:absolute;right:296px;top:12px;z-index:2}.native-canvas{flex:1;min-height:320px;overflow:hidden;background:var(--el-fill-color-light)}
@media(max-width:1000px){.canvas-info{right:252px}.authoring-grid{grid-template-columns:1fr 1fr}}
@media(max-width:700px){.canvas-info{right:12px;max-width:240px}.native-toolbar{justify-content:center}.authoring-tools{max-height:35vh;overflow:auto}.authoring-grid{grid-template-columns:1fr}.native-toolbar{align-items:flex-start;gap:8px}.sample-input{width:140px}.native-canvas{min-height:360px}}
.authoring-grid{flex-direction:column;align-items:stretch}.authoring-grid :deep(.el-form-item){width:100%}.quick-actions{flex-wrap:wrap}.paper-summary{width:100%;margin-left:0}@media(max-width:700px){.floating-tools-trigger{right:12px}}
.preview-control{display:inline-flex;align-items:stretch;gap:0}.preview-control :deep(.el-button){margin:0;border-top-right-radius:0;border-bottom-right-radius:0}.preview-control :deep(.el-input-number){width:80px;margin-left:-1px}.preview-control :deep(.el-input__wrapper){border-top-left-radius:0;border-bottom-left-radius:0}.preview-control .sample-input{width:140px}
</style>
<style>/* 非模态工具窗首次位于画布右侧；拖动和尺寸边界复用 ManagedDialog。 */
.print-floating-tools.el-dialog{margin-right:368px;margin-left:auto}.print-floating-tools .el-dialog__body{padding:16px;max-height:60vh;overflow:auto}.print-floating-tools .authoring-tools{padding:0;border:0}.print-floating-tools .authoring-grid{gap:14px;padding-top:14px}.print-floating-tools .authoring-grid .el-form-item__content{display:flex;flex-direction:column;align-items:stretch;gap:10px}.print-floating-tools .authoring-grid .el-input,.print-floating-tools .authoring-grid .el-select{width:100%;min-width:0;flex:none}.print-floating-tools .authoring-grid .el-button{align-self:flex-end}.print-floating-tools .quick-actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px}.print-floating-tools .quick-actions .el-button{padding:8px 6px}.print-floating-tools .paper-summary{grid-column:1/-1;padding-top:6px}@media(max-width:1100px){.print-floating-tools.el-dialog{margin-right:270px}}@media(max-width:700px){.print-floating-tools.el-dialog{margin-right:auto;width:calc(100vw - 24px)!important}}
</style>