<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessageBox } from "element-plus";
import { FloatingError } from "@appnexus/ui";
import type { InboundWorkbenchApi, SiteCommandAction, SiteCommandStatus } from "./inboundWorkbench";

/** 触发及刷新车次设备回执。每次重试使用原 UUID，不作为已执行确认。 */
const props=defineProps<{taskId:string;status:string;can:(permission:string)=>boolean;
  api:Pick<InboundWorkbenchApi,"siteCommand"|"siteCommandStatus">;errorText:(cause:unknown)=>string}>();
const {locale}=useI18n();
const text=(zh:string,en:string)=>locale.value.startsWith("zh")?zh:en;
const actions=[
  {key:"gate",operation:"BulkInboundTask.raise_site_gate",zh:"抬杆",en:"Raise gate"},
  {key:"call",operation:"BulkInboundTask.call_site_number",zh:"叫号",en:"Call number"},
  {key:"print",operation:"BulkInboundTask.print_site_ticket",zh:"打印作业票",en:"Print task slip"}
] as const;
const visible=computed(()=>actions.filter(action=>props.can(action.operation)));
const active=computed(()=>["ARRIVED","WORKING","READY_TO_RECEIVE"].includes(props.status));
type Attempt={id:string;status?:SiteCommandStatus;busy:boolean;error:string};
const attempts=ref<Partial<Record<SiteCommandAction,Attempt>>>({});
const labels=computed<Record<SiteCommandStatus["state"],string>>(()=>({
  PENDING:text("等待回执","Awaiting acknowledgement"),SUCCEEDED:text("设备执行成功","Device reported success"),
  FAILED:text("设备执行失败","Device reported failure"),TIMED_OUT:text("等待回执超时","Acknowledgement timed out")
}));
let revision=0;
const timers=new Map<SiteCommandAction,ReturnType<typeof setTimeout>>();

function clear(){
  revision++;
  for(const timer of timers.values())clearTimeout(timer);
  timers.clear();
}
watch(()=>props.taskId,()=>{
  clear();
  attempts.value={};
});
onUnmounted(clear);

function refreshLater(action:SiteCommandAction,id:string,generation:number){
  const current=attempts.value[action];
  if(revision!==generation||!current||current.status?.state!=="PENDING")return;
  clearTimeout(timers.get(action));
  timers.set(action,setTimeout(async()=>{
    if(revision!==generation)return;
    try{
      const result=await props.api.siteCommandStatus(props.taskId,id);
      if(revision===generation&&attempts.value[action]?.id===id) {
        current.status=result;
        current.error="";
      }
    }catch(cause){
      if(revision!==generation)return;
      current.error=props.errorText(cause);
      const status=(cause as {response?:{status?:number}}).response?.status;
      if(status===401||status===403||status===404)return;
    }
    refreshLater(action,id,generation);
  },1000));
}

async function send(action:typeof actions[number]){
  if(!active.value||!props.can(action.operation))return;
  const previous=attempts.value[action.key];
  if(previous?.busy||previous?.status?.state==="PENDING")return;
  const selected=props.taskId;
  const generation=revision;
  const retry=previous?.status?.state==="TIMED_OUT";
  const unresolved=previous&&!previous.status;
  if(action.key==="gate"&&!retry&&!unresolved) {
    const confirmed=await ElMessageBox.confirm(text("确认向当前车次发出抬杆指令？","Send a raise-gate command for the selected task?"),
      text("抬杆确认","Confirm gate command"),{type:"warning"}).then(()=>true,()=>false);
    if(!confirmed||revision!==generation||!active.value)return;
  }
  if(attempts.value[action.key]?.busy||attempts.value[action.key]?.status?.state==="PENDING")return;
  const id=retry||unresolved?previous!.id:crypto.randomUUID();
  attempts.value[action.key]={id,status:previous?.id===id?previous.status:undefined,busy:true,error:""};
  const entry=attempts.value[action.key]!;
  try{
    const result=await props.api.siteCommand(selected,action.key,id,retry);
    if(revision===generation){
      entry.status=result;
      refreshLater(action.key,id,generation);
    }
  }catch(cause){
    if(revision===generation)entry.error=props.errorText(cause);
  }finally{
    if(revision===generation)entry.busy=false;
  }
}
</script>

<template>
  <section v-if="visible.length" :aria-label="text('现场操作与回执','Site commands and acknowledgements')" class="flex min-w-0 flex-col gap-2">
    <h2>{{ text("现场操作与回执","Site commands and acknowledgements") }}</h2>
    <div v-for="action in visible" :key="action.key" class="flex flex-col gap-2">
      <el-button v-operation="action.operation" :loading="attempts[action.key]?.busy" :disabled="!active || attempts[action.key]?.status?.state==='PENDING'"
        @click="send(action)">
        {{ (action.zh && text(action.zh,action.en)) + (attempts[action.key]?.status?.state==='TIMED_OUT' ? text(' · 重试',' · Retry') : '') }}
      </el-button>
      <div v-if="attempts[action.key]?.status" class="flex flex-wrap gap-2" role="status">
        <el-tag :type="attempts[action.key]?.status?.state==='SUCCEEDED'?'success':'info'">
          {{ labels[attempts[action.key]!.status!.state] }}
        </el-tag>
        <el-tag v-if="attempts[action.key]?.status?.simulated" type="warning">{{ text("模拟回执","Simulated acknowledgement") }}</el-tag>
      </div>
      <FloatingError :title="attempts[action.key]?.error" />
    </div>
  </section>
</template>
