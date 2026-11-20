<script setup lang="ts">
import {useI18n} from "vue-i18n";
import {computed,onBeforeUnmount,ref,watch} from "vue";
import {FloatingError,InfoHint,setErrorFeedback} from "@appnexus/ui";
import {useDataRecordSearch} from "./dataRecordSearch";
import {ruleDraftErrors} from "./dataGrantDraft";
import type {AuthorizationOption,DataDimension,DataGrantRule,DataRecordSearch} from "./index";

/** 角色与成员复用的组合授权编辑器；仅编排声明维度，执行与校验以平台后端为准。 */
const props=defineProps<{modelValue:DataGrantRule[];dimensions:DataDimension[];organizations:AuthorizationOption[];members:AuthorizationOption[];disabled?:boolean;resource?:string;recordSearch?:DataRecordSearch;errorText?:(error:unknown)=>string}>();
const emit=defineEmits<{"update:modelValue":[value:DataGrantRule[]]}>();
const {t,locale}=useI18n();
const error=ref("");
const recordState=useDataRecordSearch(async cause=>{await setErrorFeedback(error,props.errorText?.(cause)??t('authorizationCenter.rules.searchFailed'));});
const {items:recordItems,labels:selectedLabels,loading,query,page,total}=recordState;
const searchable=computed(()=>!!props.resource&&!!props.recordSearch?.resources.includes(props.resource));
const recordOptions=computed(()=>{
 const options=new Map(recordItems.value.map(item=>[item.id,item]));
 for(const id of props.modelValue.flatMap(rule=>rule.recordIds))if(!options.has(id))options.set(id,{id,name:selectedLabels.value[id]??id});
 return [...options.values()];
});
/** 只在当前资源上下文回显候选，过期请求及失败不能覆盖草稿或保留他域目录。 */
async function searchRecords(keyword:string,nextPage=1){
 if(!searchable.value||!props.recordSearch||!props.resource||props.disabled)return;
 error.value="";
 await recordState.search(props.recordSearch,props.resource,keyword,nextPage);
}
function moreRecords(){void searchRecords(query.value,page.value+1);}
watch([()=>props.resource,()=>props.recordSearch,()=>props.disabled],()=>recordState.reset());
onBeforeUnmount(()=>recordState.reset());
/** 摘要仅解释草稿集合关系，不作为授权结果或后台执行凭据。 */
function summary(rule:DataGrantRule){return t('authorizationCenter.rules.summary',{records:rule.recordIds.length,conditions:rule.conditions.length});}
/** 先复制草稿再提交，防止修改继承预览或其他角色的快照。 */
function update(action:(rules:DataGrantRule[])=>void){const rules:DataGrantRule[]=JSON.parse(JSON.stringify(props.modelValue));action(rules);emit("update:modelValue",rules);}
function add(){update(rules=>rules.push({subjectType:"ROLE_MEMBERS",subjectIds:[],recordIds:[],conditions:[],allRecords:false}));}
function subject(index:number,value:DataGrantRule["subjectType"]){update(rules=>{rules[index].subjectType=value;rules[index].subjectIds=[];});}
function field(index:number,condition:number,value:string){update(rules=>{rules[index].conditions[condition]={field:value,operator:"EQ",values:[]};});}
function remove(index:number){update(rules=>rules.splice(index,1));}
function removeCondition(index:number,condition:number){update(rules=>rules[index].conditions.splice(condition,1));}
function addCondition(index:number){if(props.dimensions.length)update(rules=>rules[index].conditions.push({field:props.dimensions[0].field,operator:"EQ",values:[]}));}
</script>

<template>
 <section class="grant-rules">
  <FloatingError v-if="error" :title="error" scope="record-search" />
  <div class="grant-heading"><strong>{{t('authorizationCenter.rules.title')}}</strong><InfoHint :content="t('authorizationCenter.rules.hint')" /></div>
  <div v-for="(rule,index) in modelValue" :key="index" class="grant-rule">
   <div class="grant-heading"><strong>{{t('authorizationCenter.rules.branch',{index:index+1})}}</strong><el-button :disabled="disabled" @click="remove(index)">{{t('authorizationCenter.rules.remove')}}</el-button></div>
   <el-form-item :label="t('authorizationCenter.rules.subject')">
    <el-select :model-value="rule.subjectType" :disabled="disabled" @change="value=>subject(index,value)"><el-option v-for="type in ['ROLE_MEMBERS','MEMBER','ORGANIZATION']" :key="type" :value="type" :label="t('authorizationCenter.rules.subjects.'+type)" /></el-select>
   </el-form-item>
   <el-form-item v-if="rule.subjectType!=='ROLE_MEMBERS'" :label="t('authorizationCenter.rules.subjectValues')">
    <el-select :model-value="rule.subjectIds" multiple filterable :disabled="disabled" :multiple-limit="500" @update:model-value="value=>update(rules=>{rules[index].subjectIds=value})"><el-option v-for="option in rule.subjectType==='MEMBER'?members:organizations" :key="option.id" :value="option.id" :label="option.name" /></el-select>
   </el-form-item>
   <el-form-item :label="t('authorizationCenter.rules.records')">
    <el-select :model-value="rule.recordIds" multiple filterable :allow-create="!searchable" :remote="searchable" :remote-method="searchRecords" :loading="loading" default-first-option :multiple-limit="500" :disabled="disabled||rule.allRecords" :placeholder="t(searchable?'authorizationCenter.rules.recordSearch':'authorizationCenter.rules.recordHint')" @visible-change="visible=>{if(visible&&searchable&&!recordItems.length)searchRecords('')}" @update:model-value="value=>update(rules=>{rules[index].recordIds=value})">
     <el-option v-for="item in recordOptions" :key="item.id" :value="item.id" :label="item.name" />
     <template v-if="searchable&&page*20<total" #footer><el-button :disabled="loading" @click="moreRecords">{{t('authorizationCenter.rules.more')}}</el-button></template>
    </el-select>
   </el-form-item>
   <el-checkbox :model-value="rule.allRecords" :disabled="disabled||!!rule.recordIds.length||!!rule.conditions.length" @update:model-value="value=>update(rules=>{rules[index].allRecords=Boolean(value)})">{{t('authorizationCenter.rules.all')}}</el-checkbox>
   <div v-for="(condition,ci) in rule.conditions" :key="ci" class="grant-condition">
    <el-select :model-value="condition.field" :disabled="disabled" :aria-label="t('authorizationCenter.rules.field')" @change="value=>field(index,ci,value)"><el-option v-for="dimension in dimensions" :key="dimension.field" :value="dimension.field" :label="locale.startsWith('zh')?dimension.nameZh:dimension.nameEn" /></el-select>
    <el-select :model-value="condition.operator" :disabled="disabled" :aria-label="t('authorizationCenter.rules.operator')" @update:model-value="value=>update(rules=>{rules[index].conditions[ci].operator=value;rules[index].conditions[ci].values=[]})"><el-option v-for="operator in dimensions.find(d=>d.field===condition.field)?.operators??[]" :key="operator" :value="operator" :label="t('authorizationCenter.rules.operators.'+operator)" /></el-select>
    <el-select :model-value="condition.values" multiple filterable :allow-create="condition.field!=='organizationId'" default-first-option :multiple-limit="condition.operator==='EQ'?1:100" :disabled="disabled" :aria-label="t('authorizationCenter.rules.values')" @update:model-value="value=>update(rules=>{rules[index].conditions[ci].values=value})"><el-option v-for="option in condition.field==='organizationId'?organizations:condition.values.map(value=>({id:value,name:value}))" :key="option.id" :value="option.id" :label="option.name" /><el-option v-if="condition.field==='organizationId'" v-for="value in condition.values.filter(id=>!organizations.some(o=>o.id===id))" :key="value" :value="value" :label="value" /></el-select>
    <el-button :disabled="disabled" @click="removeCondition(index,ci)">{{t('authorizationCenter.rules.remove')}}</el-button>
   </div>
   <el-button :disabled="disabled||rule.allRecords||rule.conditions.length===8||!dimensions.length" @click="addCondition(index)">{{t('authorizationCenter.rules.addCondition')}}</el-button>
   <p class="grant-summary">{{rule.allRecords?t('authorizationCenter.rules.all'):summary(rule)}}</p>
   <p v-for="issue in ruleDraftErrors(rule,dimensions)" :key="issue" class="grant-error" role="status">{{t('authorizationCenter.rules.errors.'+issue)}}</p>
  </div>
  <el-button :disabled="disabled||modelValue.length===20" @click="add">{{t('authorizationCenter.rules.add')}}</el-button>
 </section>
</template>

<style scoped>
.grant-rules{width:100%;min-width:0;display:flex;flex-direction:column;gap:8px}
.grant-heading{display:flex;align-items:center;justify-content:space-between;gap:8px}
.grant-rule{padding:8px;border:1px solid var(--el-border-color);border-radius:var(--el-border-radius-base);display:flex;flex-direction:column;gap:8px}
.grant-rule .el-form-item{margin-bottom:0}.grant-rule .el-select{width:100%}
.grant-summary,.grant-error{margin:0;overflow-wrap:anywhere;font-size:var(--el-font-size-small)}
.grant-summary{color:var(--el-text-color-secondary)}.grant-error{color:var(--el-color-danger)}
.grant-condition{display:grid;grid-template-columns:minmax(100px,1fr) 100px minmax(120px,2fr) auto;gap:8px;align-items:start}
@media(max-width:1000px){.grant-condition{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.grant-condition>:nth-child(3){grid-column:1/-1}}
</style>
