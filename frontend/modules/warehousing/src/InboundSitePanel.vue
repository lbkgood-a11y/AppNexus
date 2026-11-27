<script setup lang="ts">
import { onUnmounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { InboundSiteReading } from "./inboundWorkbench";

/** 独立轮询现场数据，避免刷新或覆盖正在填写的业务磅票。 */
const props=defineProps<{taskId:string;read:(id:string)=>Promise<InboundSiteReading>}>();
const {locale}=useI18n();
const text=(zh:string,en:string)=>locale.value.startsWith("zh")?zh:en;
const reading=ref<InboundSiteReading>();
let revision=0;
let timer:ReturnType<typeof setTimeout>|undefined;

async function poll(id:string,generation:number){
  let retry=true;
  try {
    const result=await props.read(id);
    if(generation===revision)reading.value=result;
  }catch(cause){
    if(generation===revision)reading.value=undefined;
    const status=(cause as {response?:{status?:number}})?.response?.status;
    retry=status!==401 && status!==403 && status!==404;
  }finally{
    if(generation===revision && retry)timer=setTimeout(()=>poll(id,generation),3000);
  }
}
watch(()=>props.taskId,id=>{
  revision++;
  clearTimeout(timer);
  reading.value=undefined;
  if(id)void poll(id,revision);
},{immediate:true});
onUnmounted(()=>{
  revision++;
  clearTimeout(timer);
});
</script>

<template>
  <section :aria-label="text('现场设备','Site devices')" class="flex min-w-0 flex-col gap-2">
    <el-tag v-if="reading?.simulated" type="warning">{{ text("模拟设备","Simulated device") }}</el-tag>
    <template v-if="reading?.state==='LIVE'">
      <div class="flex flex-wrap items-center gap-2" role="status">
        <strong>{{ reading.kg }} kg</strong>
        <el-tag :type="reading.stable?'success':'warning'">
          {{ reading.stable ? text('稳定','Stable') : text('未稳定','Unstable') }}
        </el-tag>
      </div>
      <span>{{ reading.station }}</span>
      <time v-if="reading.observedAt" :datetime="reading.observedAt">{{ new Date(reading.observedAt).toLocaleString(locale) }}</time>
      <el-image v-if="reading.capture" :src="reading.capture" fit="contain" :alt="text('当前车次抓拍','Current task capture')" />
    </template>
    <el-empty v-else :image-size="56" :description="reading?.state==='STALE'
      ? text('现场读数已过期','Site reading expired') : text('当前无可用设备数据','No site data available')" />
  </section>
</template>
