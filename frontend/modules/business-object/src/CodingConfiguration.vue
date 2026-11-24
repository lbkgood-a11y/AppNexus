<script setup lang="ts">
import {onMounted,reactive,ref,computed} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,type FormInstance} from "element-plus";
import {FloatingError,InfoHint,RecordAuditDetails,ManagementTable,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import type {BusinessObjectApi,Descriptor,EncoderInput,EncoderRow,CodingRuleRow} from "./index";
/** 对象字段绑定只引用明确规则版本；格式编辑在独立规则入口，流水仍按对象内编码器隔离。 */
const props=defineProps<{descriptor:Descriptor;api:BusinessObjectApi;errorText:(e:unknown)=>string}>();
const {t}=useI18n();
/** 绑定列表只展示服务端当前租户配置，固定规则版本不随新版本自动升级。 */
const columns=computed<ManagementColumn[]>(()=>[
 {key:'enabled',label:t('businessObject.state'),width:100},{key:'encoderKey',prop:'encoderKey',label:t('coding.key'),minWidth:140},
 {key:'name',prop:'name',label:t('businessObject.name'),minWidth:160},
 {key:'field',prop:'field',label:t('coding.field'),minWidth:120},{key:'ruleKey',prop:'ruleKey',label:t('coding.ruleKey'),minWidth:180},
 {key:'ruleVersion',prop:'ruleVersion',label:t('coding.version'),width:100},{key:'operations',label:t('businessObject.operations'),width:120,fixed:'right'}]);const rows=ref<EncoderRow[]>([]),rules=ref<CodingRuleRow[]>([]),loading=ref(false),busy=ref(false),error=ref(""),key=ref(""),existing=ref<EncoderRow>(),form=ref<FormInstance>();
const draft=reactive<EncoderInput>({revision:-1,field:props.descriptor.codingFields?.[0]?.field??"",name:"",enabled:true,ruleKey:"",ruleVersion:0});
const ruleId=computed({get:()=>draft.ruleKey?`${draft.ruleKey}:${draft.ruleVersion}`:"",set:(value:string)=>{const row=rules.value.find(r=>`${r.ruleKey}:${r.ruleVersion}`===value);draft.ruleKey=row?.ruleKey??"";draft.ruleVersion=row?.ruleVersion??0;}});
const chosen=computed(()=>rules.value.find(r=>r.ruleKey===draft.ruleKey&&r.ruleVersion===draft.ruleVersion));
/** 固定示例日期预览，不调用发号接口、不消耗号码。 */
const preview=computed(()=>chosen.value?.rule.segments.map(s=>s.kind==='TEXT'?s.value:s.kind==='DATE'?({yyyy:'2026',yyyyMM:'202611',yyyyMMdd:'20261109'}[s.value]??''):"1".padStart(s.width,'0')).join('')??'—');
async function load(){loading.value=true;error.value="";try{[rows.value,rules.value]=await Promise.all([props.api.encoders(props.descriptor.objectType),props.api.codingRuleOptions(props.descriptor.objectType)]);}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
function select(row?:EncoderRow){existing.value=row;key.value=row?.encoderKey??"";Object.assign(draft,row?{revision:row.revision,field:row.field,name:row.name,enabled:row.enabled,ruleKey:row.ruleKey,ruleVersion:row.ruleVersion}:{revision:-1,field:props.descriptor.codingFields?.[0]?.field??"",name:"",enabled:true,ruleKey:"",ruleVersion:0});form.value?.clearValidate();}
async function save(){if(!await form.value?.validate().catch(()=>false))return;
 if(!/^[A-Za-z][A-Za-z0-9_]{0,39}$/.test(key.value)){await setErrorFeedback(error,t('coding.keyRule'));return;}
 busy.value=true;error.value="";try{await props.api.saveEncoder(props.descriptor.objectType,key.value,{...draft});ElMessage.success(t('businessObject.saved'));await load();const row=rows.value.find(r=>r.encoderKey===key.value);if(row)select(row);}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
onMounted(load);
</script>
<template>
 <div class="coding-configuration" v-loading="loading">
  <ManagementTable :title="t('coding.bindingsTitle')" :rows="rows" :columns="columns" :loading="loading" :busy="busy" embedded @refresh="load">
   <template #titleHelp><InfoHint :content="t('coding.bindingHint')" /></template>
   <template #actions><el-button v-operation="'CodingEncoder.save'" :disabled="busy" @click="select()">{{t('coding.newBinding')}}</el-button></template>
   <template #enabled="{row}"><el-tag class="app-status-tag" :type="row.enabled?'success':'info'">{{t(row.enabled?'businessObject.enabled':'businessObject.disabled')}}</el-tag></template>
   <template #operations="{row}"><el-button v-operation="'CodingEncoder.save'" link type="primary" :disabled="busy" @click="select(row)">{{t('businessObject.configure')}}</el-button></template>
  </ManagementTable>
  <section class="coding-binding-editor">
   <header><span class="info-hint-title">{{existing?existing.name:t('coding.newBinding')}}<InfoHint :content="t('coding.saveBindingHint')" /></span></header>
  <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" />

  <el-form ref="form" :model="draft" label-position="top" class="business-border-label-form coding-binding-form" :disabled="busy">
   <el-form-item :label="t('coding.key')" required><el-input v-model="key" :disabled="!!existing||busy" maxlength="40" /></el-form-item>
   <el-form-item :label="t('businessObject.state')"><el-switch v-model="draft.enabled" :disabled="busy" :active-text="t('businessObject.enabled')" :inactive-text="t('businessObject.disabled')" /></el-form-item>
   <el-form-item :label="t('businessObject.name')" prop="name" :rules="[{required:true,whitespace:true,max:100,message:t('coding.required')}]"><el-input v-model="draft.name" maxlength="100" :disabled="busy" /></el-form-item>
   <el-form-item :label="t('coding.field')" prop="field" :rules="[{required:true,message:t('coding.required')}]"><el-select v-model="draft.field" :disabled="busy"><el-option v-for="field in descriptor.codingFields" :key="field.field" :label="field.field" :value="field.field" /></el-select></el-form-item>
   <el-form-item :label="t('coding.ruleVersion')" prop="ruleKey" :rules="[{required:true,message:t('coding.required')}]"><el-select v-model="ruleId" filterable :disabled="busy"><el-option v-for="row in rules" :key="`${row.ruleKey}:${row.ruleVersion}`" :label="`${row.name} · ${row.ruleKey} · v${row.ruleVersion}`" :value="`${row.ruleKey}:${row.ruleVersion}`" /></el-select></el-form-item>
   <el-form-item><el-button v-operation="'CodingEncoder.save'" type="primary" :loading="busy" @click="save">{{t('coding.saveBinding')}}</el-button></el-form-item>
  </el-form>
  <el-descriptions :column="1" border><el-descriptions-item :label="t('coding.preview')">{{preview}}</el-descriptions-item></el-descriptions>
  <RecordAuditDetails v-if="existing" :audit="existing" />
  </section>
 </div>
</template>
<style scoped>
/* 列表与编辑分区继承全局区块间距；字段保留公共恒显标签及校验间距。 */
.coding-configuration{display:flex;flex-direction:column;gap:var(--app-section-gap);min-width:0}
.coding-binding-editor{padding:var(--app-content-padding);border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base)}
.coding-binding-editor>header{margin-bottom:var(--app-section-gap);font-weight:600}
.coding-binding-form{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,220px),1fr));column-gap:16px;align-items:start}
@media(max-width:700px){.coding-binding-form{grid-template-columns:minmax(0,1fr)}}
</style>
