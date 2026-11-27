<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { FloatingError } from "@appnexus/ui";
import type { LabMetricOption } from "./inboundDocuments";

/** 现行样品的化验表单只显示模板项目和单位。 */
const props=defineProps<{id?:string;load:(id:string)=>Promise<LabMetricOption[]>;errorText:(error:unknown)=>string}>();
const {locale}=useI18n();
const text=(zh:string,en:string)=>locale.value.startsWith("zh")?zh:en;
const fields=ref<LabMetricOption[]>([]);
const values=ref<Record<string,string>>({});
const loading=ref(false);
const error=ref("");
let revision=0;
watch(()=>props.id,async id=>{
  const request=++revision;
  values.value={};
  fields.value=[];
  error.value="";
  loading.value=Boolean(id);
  if(!id)return;
  try{
    const result=await props.load(id);
    if(request===revision) fields.value=result;
  }catch(cause){
    if(request===revision)error.value=props.errorText(cause) ||
      text("化验项目加载失败", "Unable to load lab metrics");
  }
  finally{if(request===revision)loading.value=false;}
},{immediate:true});

/** 用十进制文本提交，模板合格区间始终在后台执行。 */
function payload(){
  if(!fields.value.length || fields.value.some(item=>! /^-?\d{1,18}(?:\.\d{1,12})?$/.test(values.value[item.code]??""))) {
    throw new Error(text("请填写每项有效的化验读数","Enter a valid reading for each lab metric"));
  }
  return {metrics:Object.fromEntries(fields.value.map(item=>[item.code,values.value[item.code]]))};
}
/** 项目尚未就绪时同时拦截按钮和表单提交。 */
function canSubmit() {
  return !loading.value && !error.value && fields.value.length > 0;
}
defineExpose({canSubmit, payload});
</script>

<template>
  <section v-loading="loading" class="min-h-12">
    <FloatingError :title="error" />
    <p v-if="loading" role="status">{{ text("正在加载化验项目", "Loading lab metrics") }}</p>
    <el-empty v-else-if="!error && !fields.length" :image-size="56"
      :description="id ? text('当前样品没有可录入的化验项目', 'No lab metrics are available for this sample')
        : text('当前样品缺少质检单标识', 'The quality order ID is missing for this sample')" />
    <el-form-item v-for="field in fields" :key="field.code" :label="`${field.code}（${field.unit}）`" required>
      <el-input v-model.trim="values[field.code]" inputmode="decimal" maxlength="32" />
    </el-form-item>
  </section>
</template>
