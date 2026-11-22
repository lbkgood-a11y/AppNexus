<script setup lang="ts">
import {useI18n} from "vue-i18n";
import {computed,onBeforeUnmount,ref,watch} from "vue";
import {FloatingError,InfoHint,setErrorFeedback} from "@appnexus/ui";
import {useDataRecordSearch} from "./dataRecordSearch";
import {ruleDraftErrors} from "./dataGrantDraft";
import type {AuthorizationOption,DataConditionOptions,DataConditionParameter,DataDimension,DataGrantRule,DataRecordSearch} from "./index";

/** 角色与成员复用的组合授权编辑器；仅编排声明维度，执行与校验以平台后端为准。 */
const props=defineProps<{modelValue:DataGrantRule[];dimensions:DataDimension[];conditionOptions?:DataConditionOptions;organizations:AuthorizationOption[];members:AuthorizationOption[];disabled?:boolean;resource?:string;recordSearch?:DataRecordSearch;allowRecords?:boolean;recordOnly?:boolean;errorText?:(error:unknown)=>string}>();
const emit=defineEmits<{"update:modelValue":[value:DataGrantRule[]]}>();
const {t,locale}=useI18n();
const error=ref("");
const recordState=useDataRecordSearch(async cause=>{await setErrorFeedback(error,props.errorText?.(cause)??t('authorizationCenter.rules.searchFailed'));});
const {items:recordItems,labels:selectedLabels,loading,query,page,total}=recordState;
/** 未开放条件的操作仅编辑一个记录 UUID 集合，沿用统一 RULES 保存协议。 */
const recordsOnly=computed(()=>props.recordOnly||props.dimensions.length===0);
const emptyRecordRule=():DataGrantRule=>({subjectType:"ROLE_MEMBERS",subjectIds:[],recordIds:[],conditions:[],allRecords:false});
const visibleRules=computed(()=>{
 if(!recordsOnly.value)return props.modelValue;
 return [{...emptyRecordRule(),recordIds:[...new Set(props.modelValue.flatMap(rule=>rule.recordIds))]}];
});
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
const selectedIds=computed(()=>[...new Set(props.modelValue.flatMap(rule=>rule.recordIds))].sort().join(","));
/** 刷新后补查已授权的记录名，每批最多八条；会话切换、卸载或失败时保留 UUID 回显。 */
watch([selectedIds,()=>props.resource,()=>props.recordSearch,()=>props.disabled,recordsOnly],async ([ids,resource,source,_disabled,only],_,onCleanup)=>{
 if(!only||!ids||!resource||!source?.resolve||!source.resources.includes(resource))return;
 let active=true;onCleanup(()=>{active=false;});
 const missing=ids.split(",").filter(id=>!selectedLabels.value[id]);
 for(let i=0;i<missing.length&&active;i+=8){
  const results=await Promise.allSettled(missing.slice(i,i+8).map(id=>source.resolve!(resource,id)));
  if(!active)return;
  for(const result of results)if(result.status==="fulfilled"&&result.value){
   selectedLabels.value[result.value.id]=result.value.name;
  }
 }
},{immediate:true});
onBeforeUnmount(()=>recordState.reset());
/** 摘要仅解释草稿集合关系，不作为授权结果或后台执行凭据。 */
function summary(rule:DataGrantRule){return t('authorizationCenter.rules.summary',{records:rule.recordIds.length,conditions:rule.conditions.length});}
/** 先复制草稿再提交，防止修改继承预览或其他角色的快照。 */
function update(action:(rules:DataGrantRule[])=>void){const rules:DataGrantRule[]=JSON.parse(JSON.stringify(visibleRules.value));action(rules);emit("update:modelValue",rules);}
function add(){update(rules=>rules.push({subjectType:"ROLE_MEMBERS",subjectIds:[],recordIds:[],conditions:[],allRecords:false}));}
function subject(index:number,value:DataGrantRule["subjectType"]){update(rules=>{rules[index].subjectType=value;rules[index].subjectIds=[];});}
/** 字段改变时清空旧值；创建账号和人员字段仅接收当前上下文参数。 */
function newCondition(field:string):DataGrantRule["conditions"][number]{
 const dimension=conditionSource(field);
 return {field,operator:"EQ",values:[],parameter:dynamicOnly(field)?dimension?.parameters?.[0]:undefined};
}
function field(index:number,condition:number,value:string){update(rules=>{rules[index].conditions[condition]=newCondition(value);});}
function dynamicOnly(field:string){return ['ACCOUNT','PERSON'].includes(conditionSource(field)?.source??'');}
function parameter(index:number,condition:number,value:DataConditionParameter|'FIXED'){
 update(rules=>{const target=rules[index].conditions[condition];target.parameter=value==='FIXED'?null:value;target.operator='EQ';target.values=[];});
}
function remove(index:number){update(rules=>rules.splice(index,1));}
function removeCondition(index:number,condition:number){update(rules=>rules[index].conditions.splice(condition,1));}
/** 根据对象声明选值，历史值仍可见，字典禁用值不可重新选择。 */
function conditionSource(field:string){return props.dimensions.find(dimension=>dimension.field===field);}
function conditionChoices(field:string,selected:string[]){
 const dimension=conditionSource(field);
 const options=dimension?.source==="ORGANIZATION"?props.organizations.map(item=>({value:item.id,name:item.name,enabled:true})):
   dimension?.source==="DICTIONARY"?props.conditionOptions?.[dimension.dictionary??""]??[]:[];
 const known=new Set(options.map(option=>option.value));
 return [...options,...selected.filter(value=>!known.has(value)).map(value=>({value,name:value,enabled:true}))];
}
function addCondition(index:number){if(props.dimensions.length)update(rules=>rules[index].conditions.push(newCondition(props.dimensions[0].field)));}
</script>

<template>
 <section class="grant-rules">
  <FloatingError v-if="error" :title="error" scope="record-search" />
  <div v-if="!recordsOnly" class="grant-heading"><strong>{{t('authorizationCenter.rules.title')}}</strong><InfoHint :content="t('authorizationCenter.rules.hint')" /></div>
  <div v-for="(rule,index) in visibleRules" :key="index" class="grant-rule">
   <div v-if="!recordsOnly" class="grant-heading"><strong>{{t('authorizationCenter.rules.branch',{index:index+1})}}</strong><el-button :disabled="disabled" @click="remove(index)">{{t('authorizationCenter.rules.remove')}}</el-button></div>
   <el-form-item v-if="!recordsOnly" :label="t('authorizationCenter.rules.subject')">
    <el-select :model-value="rule.subjectType" :disabled="disabled" @change="value=>subject(index,value)"><el-option v-for="type in ['ROLE_MEMBERS','MEMBER','ORGANIZATION']" :key="type" :value="type" :label="t('authorizationCenter.rules.subjects.'+type)" /></el-select>
   </el-form-item>
   <el-form-item v-if="rule.subjectType!=='ROLE_MEMBERS'" :label="t('authorizationCenter.rules.subjectValues')">
    <el-select :model-value="rule.subjectIds" multiple filterable :disabled="disabled" :multiple-limit="500" @update:model-value="value=>update(rules=>{rules[index].subjectIds=value})"><el-option v-for="option in rule.subjectType==='MEMBER'?members:organizations" :key="option.id" :value="option.id" :label="option.name" /></el-select>
   </el-form-item>
   <el-form-item v-if="allowRecords!==false" :label="t(recordsOnly?'authorizationCenter.scopes.RECORDS':'authorizationCenter.rules.records')">
    <el-select :model-value="rule.recordIds" multiple filterable :allow-create="!searchable" :remote="searchable" :remote-method="searchRecords" :loading="loading" default-first-option :multiple-limit="500" :disabled="disabled||rule.allRecords" :placeholder="t(searchable?'authorizationCenter.rules.recordSearch':'authorizationCenter.rules.recordHint')" @visible-change="visible=>{if(visible&&searchable&&!recordItems.length)searchRecords('')}" @update:model-value="value=>update(rules=>{rules[index].recordIds=value})">
     <template #label="{value,label}">{{selectedLabels[value]??label}}</template>
     <el-option v-for="item in recordOptions" :key="item.id" :value="item.id" :label="item.name" />
     <template v-if="searchable&&page*20<total" #footer><el-button :disabled="loading" @click="moreRecords">{{t('authorizationCenter.rules.more')}}</el-button></template>
    </el-select>
   </el-form-item>
   <el-checkbox v-if="!recordsOnly" :model-value="rule.allRecords" :disabled="disabled||!!rule.recordIds.length||!!rule.conditions.length" @update:model-value="value=>update(rules=>{rules[index].allRecords=Boolean(value)})">{{t('authorizationCenter.rules.all')}}</el-checkbox>
   <div v-for="(condition,ci) in rule.conditions" :key="ci" class="grant-condition">
    <el-select :model-value="condition.field" :disabled="disabled" :aria-label="t('authorizationCenter.rules.field')" @change="value=>field(index,ci,value)"><el-option v-for="dimension in dimensions" :key="dimension.field" :value="dimension.field" :label="locale.startsWith('zh')?dimension.nameZh:dimension.nameEn" /></el-select>
    <el-select :model-value="condition.operator" :disabled="disabled||!!condition.parameter" :aria-label="t('authorizationCenter.rules.operator')" @update:model-value="value=>update(rules=>{rules[index].conditions[ci].operator=value;rules[index].conditions[ci].values=[]})"><el-option v-for="operator in dimensions.find(d=>d.field===condition.field)?.operators??[]" :key="operator" :value="operator" :label="t('authorizationCenter.rules.operators.'+operator)" /></el-select>
    <el-select v-if="conditionSource(condition.field)?.parameters?.length" :model-value="condition.parameter||'FIXED'" :disabled="disabled" :aria-label="t('authorizationCenter.rules.parameter')" @update:model-value="value=>parameter(index,ci,value)">
     <el-option v-if="!dynamicOnly(condition.field)" value="FIXED" :label="t('authorizationCenter.rules.parameters.FIXED')" />
     <el-option v-for="item in conditionSource(condition.field)?.parameters" :key="item" :value="item" :label="t('authorizationCenter.rules.parameters.'+item)" />
    </el-select>
    <el-select v-if="!condition.parameter&&!dynamicOnly(condition.field)" :model-value="condition.values" multiple filterable :allow-create="!conditionSource(condition.field)?.source||conditionSource(condition.field)?.source==='TEXT'" default-first-option :multiple-limit="condition.operator==='EQ'?1:100" :disabled="disabled" :aria-label="t('authorizationCenter.rules.values')" @update:model-value="value=>update(rules=>{rules[index].conditions[ci].values=value})">
     <el-option v-for="option in conditionChoices(condition.field,condition.values)" :key="option.value" :value="option.value" :label="option.name" :disabled="!option.enabled&&!condition.values.includes(option.value)" />
    </el-select>
    <el-button :disabled="disabled" @click="removeCondition(index,ci)">{{t('authorizationCenter.rules.remove')}}</el-button>
   </div>
   <el-button v-if="!recordsOnly" :disabled="disabled||rule.allRecords||rule.conditions.length===8||!dimensions.length" @click="addCondition(index)">{{t('authorizationCenter.rules.addCondition')}}</el-button>
   <p v-if="!recordsOnly" class="grant-summary">{{rule.allRecords?t('authorizationCenter.rules.all'):summary(rule)}}</p>
   <p v-for="issue in ruleDraftErrors(rule,dimensions)" :key="issue" class="grant-error" role="status">{{t('authorizationCenter.rules.errors.'+issue)}}</p>
  </div>
  <el-button v-if="!recordsOnly" :disabled="disabled||modelValue.length===20" @click="add">{{t('authorizationCenter.rules.add')}}</el-button>
 </section>
</template>

<style scoped>
.grant-rules{width:100%;min-width:0;display:flex;flex-direction:column;gap:8px}
.grant-heading{display:flex;align-items:center;justify-content:space-between;gap:8px}
.grant-rule{padding:8px;border:1px solid var(--el-border-color);border-radius:var(--el-border-radius-base);display:flex;flex-direction:column;gap:8px}
.grant-rule .el-form-item{margin-bottom:0}.grant-rule .el-select{width:100%}
.grant-summary,.grant-error{margin:0;overflow-wrap:anywhere;font-size:var(--el-font-size-small)}
.grant-summary{color:var(--el-text-color-secondary)}.grant-error{color:var(--el-color-danger)}
.grant-condition{display:flex;flex-wrap:wrap;align-items:flex-start;gap:8px;align-items:start}
.grant-condition .el-select{flex:1 1 125px;min-width:0}
</style>
