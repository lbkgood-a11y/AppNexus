<script setup lang="ts">
import { nextTick,onBeforeUnmount, onMounted, ref } from "vue";
import {getDocument,GlobalWorkerOptions} from 'pdfjs-dist';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import { useI18n } from "vue-i18n";
import { ManagedDialog } from "@appnexus/ui";
import type { PrintApi, PrintRow } from "./index";
import {outputStage} from './outputStage';

/** 统一预览入口：认证读取 PDF，显式确认提交；轮询停止于终态，卸载释放内容和定时器。 */
const props=defineProps<{api:PrintApi;outputId:string;errorText:(error:unknown)=>string}>();
const emit=defineEmits<{close:[];confirmed:[]}>();
const {t}=useI18n();const request=ref<PrintRow>(),devices=ref<PrintRow[]>([]),device=ref(""),url=ref(""),busy=ref(false),error=ref("");
let disposed=false,timer:ReturnType<typeof setTimeout>|undefined;const confirmationKey=crypto.randomUUID();
const pages=ref<HTMLCanvasElement[]>([]),pageCount=ref(0),rendered=ref(false);
GlobalWorkerOptions.workerSrc=pdfWorkerUrl;
/** 内嵌浏览器可能不带原生 PDF 插件，使用本地 PDF.js 渲染真实文件，仅渲染页面，不装配 PDF 脚本执行器。 */
async function renderPdf(blob:Blob){
  if(blob.size>20*1024*1024)throw new Error('PDF_SIZE_LIMIT');
  const loadingTask=getDocument({data:new Uint8Array(await blob.arrayBuffer())});const pdf=await loadingTask.promise;
  try{if(pdf.numPages>100)throw new Error('PDF_PAGE_LIMIT');pageCount.value=pdf.numPages;await nextTick();
    for(let i=1;i<=pdf.numPages;i++){if(disposed)return;const page=await pdf.getPage(i);const viewport=page.getViewport({scale:1.3});const canvas=pages.value[i-1];canvas.width=viewport.width;canvas.height=viewport.height;await page.render({canvas,viewport}).promise;}
    rendered.value=true;
  }finally{await loadingTask.destroy();}
}
async function refresh(){if(timer){clearTimeout(timer);timer=undefined;}error.value="";try{
  const value=await props.api.outputStatus(props.outputId);if(disposed)return;request.value=value;
  if(value.deviceId&&!device.value)device.value=String(value.deviceId);
  if(value.state==="SUCCEEDED"&&!rendered.value){const blob=await props.api.outputPdf(props.outputId);if(disposed)return;if(url.value)URL.revokeObjectURL(url.value);url.value=URL.createObjectURL(blob);await renderPdf(blob)}
  if((value.confirmedJobId&&!['SUBMITTED','SUCCEEDED','FAILED','UNKNOWN','CANCELLED'].includes(String(value.deliveryState)))||!["SUCCEEDED","FAILED","UNKNOWN","CANCELLED"].includes(String(value.state)))timer=setTimeout(refresh,2000);
}catch(e){if(!disposed)error.value=props.errorText(e)}}
async function confirm(){if(!device.value||busy.value)return;busy.value=true;error.value="";try{request.value=await props.api.confirmOutput(props.outputId,device.value,confirmationKey);emit("confirmed");await refresh()}catch(e){error.value=props.errorText(e)}finally{busy.value=false}}
onMounted(async()=>{try{devices.value=await props.api.devices()}catch(e){error.value=props.errorText(e)}await refresh()});
onBeforeUnmount(()=>{disposed=true;if(timer)clearTimeout(timer);if(url.value)URL.revokeObjectURL(url.value)});
</script>
<template>
  <ManagedDialog :model-value="true" :title="t('printing.preview')" width="calc(100vw - 48px)" height="calc(100vh - 48px)" :busy="busy" @update:model-value="value=>{if(!value)emit('close')}">
    <el-alert v-if="request" :title="t('printing.outputStages.'+outputStage(request))" :type="outputStage(request)==='RENDERER_OFFLINE'?'warning':'info'" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="refresh">{{t('account.retry')}}</el-button></el-alert>
    <el-alert v-if="request?.confirmedJobId" :title="t('printing.flow.queued')" type="success" :closable="false"/>
    <el-alert v-if="request&&['FAILED','UNKNOWN','CANCELLED'].includes(String(request.state))" :title="t('printing.states.'+request.state)" type="warning" :closable="false"/>
    <el-skeleton v-if="!url&&!error&&request?.state!=='FAILED'&&request?.state!=='UNKNOWN'" :rows="6" animated/>
    <div v-if="url" class="pdf-preview" :aria-label="t('printing.preview')"><canvas v-for="n in pageCount" :key="n" :ref="el=>{if(el)pages[n-1]=el as HTMLCanvasElement}" :aria-label="t('printing.preview')+' '+n"/></div>
    <template #footer><el-select v-model="device" :disabled="busy||!!request?.confirmedJobId" :placeholder="t('printing.device')" :aria-label="t('printing.device')"><el-option v-for="item in devices.filter(value=>value.enabled)" :key="item.id" :value="item.id" :label="String(item.name)"/></el-select><el-button @click="emit('close')">{{t('organization.cancel')}}</el-button><el-button type="primary" :loading="busy" :disabled="!rendered||!device||!!request?.confirmedJobId" @click="confirm">{{t('printing.confirmPrint')}}</el-button></template>
  </ManagedDialog>
</template>
<style scoped>.pdf-preview{width:100%;height:calc(100% - 24px);min-height:400px;overflow:auto;border:1px solid var(--el-border-color);background:var(--el-fill-color)}.pdf-preview canvas{display:block;max-width:100%;height:auto;margin:0 auto 16px}</style>
