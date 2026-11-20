<script setup lang="ts">
import {computed,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,type FormInstance,type FormRules} from "element-plus";
import {AppIcon} from "@appnexus/icons";
import {FloatingError,InfoHint,ManagementTable,ManagedDialog,RecordAuditDetails,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import type {BusinessObjectApi,CodingRuleInput,CodingRuleRow,CodingSegment} from "./index";
/**
 * 独立编码规则版本管理；复用原分段编辑能力和公共表格、弹窗。
 * 所有已保存版本不可变，编辑仅追加版本；不会升级既有对象绑定。
 */
const props=defineProps<{api:BusinessObjectApi;errorText:(e:unknown)=>string}>();
const {t}=useI18n();
const rows=ref<CodingRuleRow[]>([]),loading=ref(false),busy=ref(false),error=ref(""),key=ref(""),existing=ref<CodingRuleRow>(),form=ref<FormInstance>();
const draft=reactive<CodingRuleInput>({revision:-1,name:"",rule:{segments:[{kind:"TEXT",value:"PU",width:0},{kind:"DATE",value:"yyyyMMdd",width:0},{kind:"SEQUENCE",value:"",width:6}],reset:"DAY",zoneId:"Asia/Shanghai"}});
const rules=computed<FormRules>(()=>({name:[{required:true,whitespace:true,max:100,message:t('coding.required'),trigger:'blur'}],}));
/** 预览使用固定示例日期和流水1，不调用发号 API、不消耗号码。 */
const preview=computed(()=>draft.rule.segments.map(s=>s.kind==='TEXT'?s.value:s.kind==='DATE'?({yyyy:'2026',yyyyMM:'202611',yyyyMMdd:'20261109'}[s.value]??''):"1".padStart(s.width,'0')).join(''));
async function load(){loading.value=true;error.value="";try{rows.value=await props.api.codingRules();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
const dialog=ref(false);
const columns=computed<ManagementColumn[]>(()=>[
 {key:'ruleKey',label:t('coding.ruleKey'),minWidth:160},{key:'name',label:t('businessObject.name'),minWidth:160},
 {key:'ruleVersion',label:t('coding.version'),width:100},{key:'operations',label:t('businessObject.operations'),width:160,fixed:'right'}
]);
function select(row?:CodingRuleRow){existing.value=row;key.value=row?.ruleKey??"";
 Object.assign(draft,row?{revision:Math.max(...rows.value.filter(r=>r.ruleKey===row.ruleKey).map(r=>r.ruleVersion)),name:row.name,rule:JSON.parse(JSON.stringify(row.rule))}:{revision:-1,name:"",rule:{segments:[{kind:"TEXT",value:"PU",width:0},{kind:"DATE",value:"yyyyMMdd",width:0},{kind:"SEQUENCE",value:"",width:6}],reset:"DAY",zoneId:"Asia/Shanghai"}});
 form.value?.clearValidate();dialog.value=true;
}
function change(segment:CodingSegment){segment.value=segment.kind==='DATE'?'yyyyMMdd':segment.kind==='TEXT'?'PU':'';segment.width=segment.kind==='SEQUENCE'?6:0;}
function move(index:number,delta:number){const other=index+delta;if(other<0||other>=draft.rule.segments.length)return;const segments=draft.rule.segments;[segments[index],segments[other]]=[segments[other],segments[index]];}
async function save(){
 if(!await form.value?.validate().catch(()=>false))return;
 if(!/^[A-Za-z][A-Za-z0-9_]{0,39}$/.test(key.value)){await setErrorFeedback(error,t('coding.keyRule'));return;}
 busy.value=true;error.value="";try{await props.api.saveCodingRule(key.value,{revision:draft.revision,name:draft.name,rule:JSON.parse(JSON.stringify(draft.rule))});ElMessage.success(t('businessObject.saved'));await load();dialog.value=false;}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
onMounted(load);
</script>
<template>
 <div>
 <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" />
 <ManagementTable :title="t('coding.rulesTitle')" :rows="rows" :columns="columns" :loading="loading" :busy="busy" embedded @refresh="load">
  <template #titleHelp><InfoHint :content="t('coding.rulesHint')" /></template>
  <template #actions><el-button v-operation="'CodingRule.save'" type="primary" :disabled="busy" @click="select()">{{t('coding.newRule')}}</el-button></template>
  <template #operations="{row}"><el-button v-operation="'CodingRule.save'" link type="primary" :disabled="busy" @click="select(row)">{{t('coding.newVersion')}}</el-button></template>
 </ManagementTable>
 <ManagedDialog v-model="dialog" :title="t(existing?'coding.newVersion':'coding.newRule')" width="min(1000px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!busy" :busy="busy">
  <el-form ref="form" :model="draft" :rules="rules" label-position="top" class="business-border-label-form encoder-form">
   <h3 class="encoder-heading">{{t('coding.basicInfo')}}</h3>
   <div class="encoder-fields">
    <el-form-item :label="t('coding.ruleKey')" required><el-input v-model="key" :disabled="!!existing||busy" maxlength="40" /></el-form-item>
    <el-form-item :label="t('businessObject.name')" prop="name"><el-input v-model="draft.name" maxlength="100" :disabled="busy" /></el-form-item>
    <el-form-item :label="t('coding.reset')"><el-select v-model="draft.rule.reset" :disabled="busy"><el-option v-for="value in ['NEVER','YEAR','MONTH','DAY'] as const" :key="value" :value="value" :label="t('coding.resets.'+value)" /></el-select></el-form-item>
    <el-form-item :label="t('coding.zone')"><el-input v-model="draft.rule.zoneId" :disabled="busy" maxlength="64" /></el-form-item>
   </div>
   <div class="encoder-section-heading"><h3 class="encoder-heading">{{t('coding.segmentsTitle')}}</h3></div>
   <div class="encoder-segments">
   <div v-for="(segment,index) in draft.rule.segments" :key="index" class="encoder-segment">
    <el-form-item :label="t('coding.segment')+' '+(index+1)"><el-select v-model="segment.kind" :disabled="busy" @change="change(segment)"><el-option v-for="kind in ['TEXT','DATE','SEQUENCE'] as const" :key="kind" :label="t('coding.kinds.'+kind)" :value="kind" /></el-select></el-form-item>
    <el-form-item v-if="segment.kind==='TEXT'" :label="t('coding.text')"><el-input v-model="segment.value" maxlength="32" :disabled="busy" /></el-form-item>
    <el-form-item v-else-if="segment.kind==='DATE'" :label="t('coding.format')"><el-select v-model="segment.value" :disabled="busy"><el-option v-for="value in ['yyyyMMdd','yyyyMM','yyyy']" :key="value" :label="value" :value="value" /></el-select></el-form-item>
    <el-form-item v-else :label="t('coding.width')"><el-input-number v-model="segment.width" :min="1" :max="12" :disabled="busy" /></el-form-item>
    <div class="encoder-tools"><el-button v-operation="'CodingRule.save'" :aria-label="t('coding.up')" :disabled="busy||index===0" @click="move(index,-1)"><AppIcon name="arrowUp" :size="18" /></el-button><el-button v-operation="'CodingRule.save'" :aria-label="t('coding.down')" :disabled="busy||index===draft.rule.segments.length-1" @click="move(index,1)"><AppIcon name="arrowDown" :size="18" /></el-button><el-button v-operation="'CodingRule.save'" text type="danger" :disabled="busy||draft.rule.segments.length===1" @click="draft.rule.segments.splice(index,1)">{{t('coding.remove')}}</el-button></div>
   </div>
   </div>
   <div class="encoder-add"><el-button v-operation="'CodingRule.save'" :disabled="busy||draft.rule.segments.length>=10" @click="draft.rule.segments.push({kind:'TEXT',value:'PU',width:0})">{{t('coding.addSegment')}}</el-button></div>
  </el-form>
  <div class="encoder-preview"><div class="encoder-preview-label">{{t('coding.preview')}}</div><output class="encoder-preview-value" aria-live="polite">{{preview}}</output></div>
  <section v-if="existing" class="master-data-detail encoder-audit">
   <el-card class="detail-system" shadow="never">
    <h3 class="detail-section-title">{{t('partner.system')}}</h3>
    <RecordAuditDetails :audit="existing" inline />
   </el-card>
  </section>
  <template #footer>
   <div class="encoder-footer">
    <el-button :disabled="busy" @click="dialog=false">{{t('coding.cancel')}}</el-button>
    <el-button v-operation="'CodingRule.save'" type="primary" :loading="busy" @click="save">{{t('businessObject.save')}}</el-button>
   </div>
  </template>
 </ManagedDialog>
 </div>
</template>
<style scoped>
/* 字段间距独立于全局区块间距，避免设置为零时标签与校验信息相互挤压。 */
.encoder-form{container-type:inline-size}
.encoder-heading{margin:0;color:var(--el-text-color-primary);font-size:14px;font-weight:600;line-height:24px}
.encoder-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:16px;row-gap:0;padding-top:8px}
.encoder-section-heading{margin-block:var(--app-section-gap,8px)}
.encoder-segments{display:grid;gap:var(--app-section-gap,8px)}
.encoder-segment{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr) auto;align-items:start;column-gap:16px;padding:var(--app-content-padding,8px) var(--app-content-padding,8px) 0;border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base);background:var(--el-fill-color-blank)}
.encoder-tools,.encoder-footer{display:flex;align-items:center;gap:8px}
/* 公共边框标签在控件上方预留8px，操作区同步偏移以对齐控件中心。 */
.encoder-tools{min-height:36px;padding-top:8px;box-sizing:content-box;white-space:nowrap}
.encoder-tools .el-button,.encoder-footer .el-button{margin:0}
.encoder-tools .el-button:not(.is-text){padding-inline:8px}
.encoder-segment .el-input-number{width:100%}
.encoder-add{display:flex;justify-content:flex-start;margin-top:var(--app-section-gap,8px)}
.encoder-preview{display:grid;gap:8px;margin-top:var(--app-section-gap,8px);padding:var(--app-content-padding,8px);border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base);background:var(--el-fill-color-light)}
.encoder-preview-label{font-size:12px;color:var(--el-text-color-secondary)}
.encoder-preview-value{font-family:var(--el-font-family);font-size:18px;font-weight:600;letter-spacing:1px;color:var(--el-text-color-primary);overflow-wrap:anywhere}
.encoder-footer{justify-content:flex-end;flex-wrap:wrap}
/* 复用主数据系统信息样板，区块间距跟随全局设置，审计内容始终只读。 */
.encoder-audit{margin-top:var(--app-section-gap,8px)}
/* 根据弹窗实际可用宽度折行，兼容桌面手动缩放和手机视口。 */
@container (max-width:600px){
 .encoder-fields{grid-template-columns:minmax(0,1fr)}
 .encoder-segment{grid-template-columns:minmax(0,1fr);padding-bottom:var(--app-content-padding,8px)}
 .encoder-tools{justify-content:flex-end}
}
@media (pointer:coarse){.encoder-tools .el-button,.encoder-footer .el-button,.encoder-add .el-button{min-height:44px;min-width:44px}}
</style>
