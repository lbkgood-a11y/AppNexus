<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ManagedDialog } from "@appnexus/ui";
import type { PrintApi, PrintRow } from "./index";

/** 统一预览入口：认证读取 PDF，显式确认提交；轮询停止于终态，卸载释放内容和定时器。 */
const props=defineProps<{api:PrintApi;outputId:string;errorText:(error:unknown)=>string}>();
const emit=defineEmits<{close:[];confirmed:[]}>();
const {t}=useI18n();const request=ref<PrintRow>(),devices=ref<PrintRow[]>([]),device=ref(""),url=ref(""),busy=ref(false),error=ref("");
let disposed=false,timer:ReturnType<typeof setTimeout>|undefined;const confirmationKey=crypto.randomUUID();
async function refresh(){error.value="";try{
  const value=await props.api.outputStatus(props.outputId);if(disposed)return;request.value=value;
  if(value.deviceId&&!device.value)device.value=String(value.deviceId);
  if(value.state==="SUCCEEDED"&&!url.value){const blob=await props.api.outputPdf(props.outputId);if(disposed)return;url.value=URL.createObjectURL(blob)}
  if(!["SUCCEEDED","FAILED","UNKNOWN","CANCELLED"].includes(String(value.state)))timer=setTimeout(refresh,2000);
}catch(e){if(!disposed)error.value=props.errorText(e)}}
async function confirm(){if(!device.value||busy.value)return;busy.value=true;error.value="";try{request.value=await props.api.confirmOutput(props.outputId,device.value,confirmationKey);emit("confirmed")}catch(e){error.value=props.errorText(e)}finally{busy.value=false}}
onMounted(async()=>{try{devices.value=await props.api.devices()}catch(e){error.value=props.errorText(e)}await refresh()});
onBeforeUnmount(()=>{disposed=true;if(timer)clearTimeout(timer);if(url.value)URL.revokeObjectURL(url.value)});
</script>
<template>
  <ManagedDialog :model-value="true" :title="t('printing.preview')" width="calc(100vw - 48px)" height="calc(100vh - 48px)" :busy="busy" @update:model-value="value=>{if(!value)emit('close')}">
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="refresh">{{t('account.retry')}}</el-button></el-alert>
    <el-alert v-if="request?.confirmedJobId" :title="t('printing.flow.queued')" type="success" :closable="false"/>
    <el-alert v-if="request&&['FAILED','UNKNOWN','CANCELLED'].includes(String(request.state))" :title="t('printing.states.'+request.state)" type="warning" :closable="false"/>
    <el-skeleton v-if="!url&&!error&&request?.state!=='FAILED'&&request?.state!=='UNKNOWN'" :rows="6" animated/>
    <iframe v-if="url" :src="url" :title="t('printing.preview')" class="pdf-preview"/>
    <template #footer><el-select v-model="device" :disabled="busy||!!request?.confirmedJobId" :placeholder="t('printing.device')" :aria-label="t('printing.device')"><el-option v-for="item in devices.filter(value=>value.enabled)" :key="item.id" :value="item.id" :label="String(item.name)"/></el-select><el-button @click="emit('close')">{{t('organization.cancel')}}</el-button><el-button type="primary" :loading="busy" :disabled="!url||!device||!!request?.confirmedJobId" @click="confirm">{{t('printing.confirmPrint')}}</el-button></template>
  </ManagedDialog>
</template>
<style scoped>.pdf-preview{width:100%;height:calc(100% - 24px);min-height:400px;border:1px solid var(--el-border-color)}</style>
