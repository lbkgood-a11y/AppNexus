<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { FloatingError } from "@appnexus/ui";
import type { InspectionAttachmentOption } from "./inboundDocuments";

/** 在当前作业的已授权附件中选择相互独立的 MTC 和外观照片。 */
const props=defineProps<{taskId?:string;load:(taskId:string)=>Promise<InspectionAttachmentOption[]>;errorText:(error:unknown)=>string}>();
const {locale}=useI18n();
const text=(zh:string,en:string)=>locale.value.startsWith("zh")?zh:en;
const items=ref<InspectionAttachmentOption[]>([]);
const certificate=ref("");
const appearance=ref("");
const loading=ref(false);
const error=ref("");
const images=new Set(["image/jpeg","image/png"]);
const photos=computed(()=>items.value.filter(item=>images.has(item.mediaType)));
const certificates=computed(()=>items.value.filter(item=>item.mediaType==="application/pdf" || images.has(item.mediaType)));
let revision=0;

async function refresh(){
  const id=props.taskId;
  const request=++revision;
  error.value="";
  items.value=[];
  if(!id)return;
  loading.value=true;
  try{
    const result=await props.load(id);
    if(revision===request)items.value=result;
  }catch(cause){if(revision===request)error.value=props.errorText(cause);}
  finally{if(revision===request)loading.value=false;}
}
watch(()=>props.taskId,()=>{
  certificate.value="";
  appearance.value="";
  void refresh();
},{immediate:true});

/** 用户选择来自列表，写接口继续复核附件归属、状态和类型。 */
function payload(){
  if(loading.value || certificate.value===appearance.value ||
    !certificates.value.some(item=>item.id===certificate.value) || !photos.value.some(item=>item.id===appearance.value)) {
    throw new Error(text("请选择不同的已就绪质保书和外观照片","Select distinct available certificate and appearance photo"));
  }
  return {certificateId:certificate.value,appearanceId:appearance.value};
}
defineExpose({payload});
</script>

<template>
  <section v-loading="loading" class="min-h-12">
    <FloatingError :title="error" />
    <el-text v-if="!taskId">{{ text("选择作业后加载附件","Select a task to load attachments") }}</el-text>
    <template v-else>
      <slot name="upload" :task-id="taskId" :refresh="refresh" />
      <el-form-item :label="text('质保书附件','Certificate attachment')" required>
        <el-select v-model="certificate" filterable>
          <el-option v-for="item in certificates" :key="item.id" :label="item.fileName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="text('外观照片','Appearance photograph')" required>
        <el-select v-model="appearance" filterable>
          <el-option v-for="item in photos" :key="item.id" :label="item.fileName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-button v-operation="'BulkInboundQualityOrder.inspect_steel'" link type="primary" @click="refresh">{{ text("刷新已就绪附件","Refresh available attachments") }}</el-button>
    </template>
  </section>
</template>
