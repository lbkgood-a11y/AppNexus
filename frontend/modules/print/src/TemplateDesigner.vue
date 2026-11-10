<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { createPrintDesigner, copyTemplate, type PrintDesigner, type PrintTemplate } from "./index";

/** 完整 HiPrint 设计器外壳；拖拽、选中、属性、表格及历史记录由上游原生能力负责。 */
const props=defineProps<{modelValue:PrintTemplate;language:"cn"|"en"}>();
const emit=defineEmits<{"update:modelValue":[value:PrintTemplate];dirty:[]}>();
const {t}=useI18n();const canvas=ref<HTMLElement>(),session=ref<PrintDesigner>(),mode=ref<"design"|"preview">("design"),sample=ref("示例数据"),busy=ref(false);
async function initialize(){session.value=await createPrintDesigner(canvas.value!,{template:props.modelValue,language:props.language})}
onMounted(initialize);onBeforeUnmount(()=>session.value?.destroy());
async function capture(){const value=await session.value!.snapshot();emit("update:modelValue",copyTemplate(value));emit("dirty")}
defineExpose({snapshot:capture});
async function action(run:()=>Promise<void>){busy.value=true;try{await run();await capture()}finally{busy.value=false}}
async function preview(){await capture();const fields=new Set<string>();for(const panel of props.modelValue.panels){const values=(panel as Record<string,unknown>).printElements;if(Array.isArray(values))for(const element of values){const field=String((element as {options?:Record<string,unknown>}).options?.field||"");if(field)fields.add(field)}}const data=Object.fromEntries([...fields].map(field=>[field,sample.value]));await session.value?.preview(data);mode.value="preview"}
async function design(){await session.value?.design();mode.value="design"}
</script>

<template>
  <div class="native-designer">
    <header class="native-toolbar">
      <div class="toolbar-group"><el-button v-if="mode==='design'" type="primary" :loading="busy" @click="preview">{{t('printing.preview')}}</el-button><el-button v-else type="primary" @click="design">{{t('printing.backToDesign')}}</el-button><el-input v-model="sample" :placeholder="t('printing.sampleValue')" class="sample-input"/></div>
      <el-button-group v-if="mode==='design'"><el-button :disabled="busy" @click="action(()=>session!.undo())">{{t('printing.undo')}}</el-button><el-button :disabled="busy" @click="action(()=>session!.redo())">{{t('printing.redo')}}</el-button><el-button :disabled="busy" @click="capture">{{t('printing.syncDesign')}}</el-button></el-button-group>
    </header>
    <div ref="canvas" class="native-canvas"/>
  </div>
</template>

<style scoped>
.native-designer{display:flex;height:100%;min-height:600px;flex-direction:column}.native-toolbar{display:flex;flex:none;align-items:center;justify-content:space-between;gap:12px;padding:8px 12px;border-bottom:1px solid var(--el-border-color-lighter);background:var(--el-bg-color)}.toolbar-group{display:flex;align-items:center;gap:8px}.sample-input{width:180px}.native-canvas{flex:1;min-height:0;overflow:hidden}@media(max-width:700px){.native-toolbar{align-items:flex-start;flex-direction:column}.sample-input{width:160px}.native-canvas{min-height:640px}}
</style>
