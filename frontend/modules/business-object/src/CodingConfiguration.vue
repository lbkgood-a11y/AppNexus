<script setup lang="ts">
import {computed,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,type FormInstance,type FormRules} from "element-plus";
import {AppIcon} from "@appnexus/icons";
import {FloatingError,RecordAuditDetails,setErrorFeedback} from "@appnexus/ui";
import type {BusinessObjectApi,Descriptor,EncoderInput,EncoderRow,CodingSegment} from "./index";
/**
 * 对象内编码器分段编辑器；已有配置页没有顺序段编辑能力，因此组合 Element Plus 表单而非创建新 UI 体系。
 * 发号后的规则由后端冻结，修改名称和启停仍使用原配置版本。
 */
const props=defineProps<{descriptor:Descriptor;api:BusinessObjectApi;errorText:(e:unknown)=>string}>();
const {t}=useI18n();
const rows=ref<EncoderRow[]>([]),loading=ref(false),busy=ref(false),error=ref(""),key=ref(""),existing=ref<EncoderRow>(),form=ref<FormInstance>();
const draft=reactive<EncoderInput>({revision:-1,field:props.descriptor.codingFields?.[0]?.field??"",name:"",enabled:true,rule:{segments:[{kind:"TEXT",value:"PU",width:0},{kind:"DATE",value:"yyyyMMdd",width:0},{kind:"SEQUENCE",value:"",width:6}],reset:"DAY",zoneId:"Asia/Shanghai"}});
const rules=computed<FormRules>(()=>({name:[{required:true,whitespace:true,max:100,message:t('coding.required'),trigger:'blur'}],field:[{required:true,message:t('coding.required'),trigger:'change'}]}));
/** 预览使用固定示例日期和流水1，不调用发号 API、不消耗号码。 */
const preview=computed(()=>draft.rule.segments.map(s=>s.kind==='TEXT'?s.value:s.kind==='DATE'?({yyyy:'2026',yyyyMM:'202611',yyyyMMdd:'20261109'}[s.value]??''):"1".padStart(s.width,'0')).join(''));
async function load(){loading.value=true;error.value="";try{rows.value=await props.api.encoders(props.descriptor.objectType);}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
function select(row?:EncoderRow){existing.value=row;key.value=row?.encoderKey??"";Object.assign(draft,row?{revision:row.revision,field:row.field,name:row.name,enabled:row.enabled,rule:JSON.parse(JSON.stringify(row.rule))}:{revision:-1,field:props.descriptor.codingFields?.[0]?.field??"",name:"",enabled:true,rule:{segments:[{kind:"TEXT",value:"PU",width:0},{kind:"DATE",value:"yyyyMMdd",width:0},{kind:"SEQUENCE",value:"",width:6}],reset:"DAY",zoneId:"Asia/Shanghai"}});form.value?.clearValidate();}
function change(segment:CodingSegment){segment.value=segment.kind==='DATE'?'yyyyMMdd':segment.kind==='TEXT'?'PU':'';segment.width=segment.kind==='SEQUENCE'?6:0;}
function move(index:number,delta:number){const other=index+delta;if(other<0||other>=draft.rule.segments.length)return;const segments=draft.rule.segments;[segments[index],segments[other]]=[segments[other],segments[index]];}
async function save(){
 if(!await form.value?.validate().catch(()=>false))return;
 if(!/^[A-Za-z][A-Za-z0-9_]{0,39}$/.test(key.value)){error.value=t('coding.keyRule');return;}
 busy.value=true;error.value="";try{await props.api.saveEncoder(props.descriptor.objectType,key.value,{revision:draft.revision,field:draft.field,name:draft.name,enabled:draft.enabled,rule:JSON.parse(JSON.stringify(draft.rule))});ElMessage.success(t('businessObject.saved'));await load();const row=rows.value.find(r=>r.encoderKey===key.value);if(row)select(row);}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
onMounted(load);
</script>
<template>
 <el-card shadow="never" v-loading="loading">
  <template #header><h3>{{t('coding.title')}}</h3></template>
  <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" />
  <el-alert :title="t('coding.hint')" type="info" :closable="false" />
  <div class="encoder-tools"><el-select :model-value="existing?.encoderKey??''" :aria-label="t('coding.select')" @change="value=>select(rows.find(r=>r.encoderKey===value))"><el-option v-for="row in rows" :key="row.encoderKey" :label="row.name" :value="row.encoderKey" /></el-select><el-button :disabled="busy" @click="select()">{{t('coding.new')}}</el-button><el-button v-operation="'CodingEncoder.list'" :disabled="busy" @click="load">{{t('businessObject.refresh')}}</el-button></div>
  <el-empty v-if="!rows.length" :description="t('coding.empty')" />
  <el-form ref="form" :model="draft" :rules="rules" label-position="top" class="business-border-label-form">
   <div class="encoder-fields">
    <el-form-item :label="t('coding.key')" required><el-input v-model="key" :disabled="!!existing||busy" maxlength="40" /></el-form-item>
    <el-form-item :label="t('businessObject.name')" prop="name"><el-input v-model="draft.name" maxlength="100" :disabled="busy" /></el-form-item>
    <el-form-item :label="t('coding.field')" prop="field"><el-select v-model="draft.field" :disabled="busy"><el-option v-for="field in descriptor.codingFields" :key="field.field" :label="field.field" :value="field.field" /></el-select></el-form-item>
    <el-form-item :label="t('businessObject.state')"><el-switch v-model="draft.enabled" :disabled="busy" :active-text="t('businessObject.enabled')" :inactive-text="t('businessObject.disabled')" /></el-form-item>
    <el-form-item :label="t('coding.reset')"><el-select v-model="draft.rule.reset" :disabled="busy"><el-option v-for="value in ['NEVER','YEAR','MONTH','DAY'] as const" :key="value" :value="value" :label="t('coding.resets.'+value)" /></el-select></el-form-item>
    <el-form-item :label="t('coding.zone')"><el-input v-model="draft.rule.zoneId" :disabled="busy" maxlength="64" /></el-form-item>
   </div>
   <div v-for="(segment,index) in draft.rule.segments" :key="index" class="encoder-segment">
    <el-form-item :label="t('coding.segment')+' '+(index+1)"><el-select v-model="segment.kind" :disabled="busy" @change="change(segment)"><el-option v-for="kind in ['TEXT','DATE','SEQUENCE'] as const" :key="kind" :label="t('coding.kinds.'+kind)" :value="kind" /></el-select></el-form-item>
    <el-form-item v-if="segment.kind==='TEXT'" :label="t('coding.text')"><el-input v-model="segment.value" maxlength="32" :disabled="busy" /></el-form-item>
    <el-form-item v-else-if="segment.kind==='DATE'" :label="t('coding.format')"><el-select v-model="segment.value" :disabled="busy"><el-option v-for="value in ['yyyyMMdd','yyyyMM','yyyy']" :key="value" :label="value" :value="value" /></el-select></el-form-item>
    <el-form-item v-else :label="t('coding.width')"><el-input-number v-model="segment.width" :min="1" :max="12" :disabled="busy" /></el-form-item>
    <div class="encoder-tools"><el-button :aria-label="t('coding.up')" :disabled="busy||index===0" @click="move(index,-1)"><AppIcon name="arrowUp" :size="18" /></el-button><el-button :aria-label="t('coding.down')" :disabled="busy||index===draft.rule.segments.length-1" @click="move(index,1)"><AppIcon name="arrowDown" :size="18" /></el-button><el-button :disabled="busy||draft.rule.segments.length===1" @click="draft.rule.segments.splice(index,1)">{{t('coding.remove')}}</el-button></div>
   </div>
   <div class="encoder-tools"><el-button :disabled="busy||draft.rule.segments.length>=10" @click="draft.rule.segments.push({kind:'TEXT',value:'PU',width:0})">{{t('coding.addSegment')}}</el-button><el-button v-operation="'CodingEncoder.save'" type="primary" :loading="busy" @click="save">{{t('businessObject.save')}}</el-button></div>
  </el-form>
  <el-descriptions :column="1" border><el-descriptions-item :label="t('coding.preview')">{{preview}}</el-descriptions-item></el-descriptions>
  <RecordAuditDetails v-if="existing" :audit="existing" />
 </el-card>
</template>
<style scoped>
.encoder-tools{display:flex;align-items:center;gap:var(--app-section-gap,8px);flex-wrap:wrap;margin-block:var(--app-section-gap,8px)}
.encoder-fields{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:var(--app-section-gap,8px)}
.encoder-segment{display:flex;align-items:center;gap:var(--app-section-gap,8px);flex-wrap:wrap}.encoder-segment>.el-form-item{flex:1;min-width:160px}
</style>
