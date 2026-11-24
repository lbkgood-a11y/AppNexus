<script setup lang="ts">
import {computed,watch} from "vue";
import {useI18n} from "vue-i18n";
import type {ProductField} from "./product";
/** 领域动态表单只组合现有Element Plus，required仅提示启用要求，不阻断合法不完整草稿保存。 */
const props=defineProps<{/** 商品详情使用公共字段网格；规格弹窗保留现有表单密度。 */detailProfile?:boolean;fields:ProductField[];values:Record<string,string|string[]>;readonly?:boolean;disabled?:boolean;requiredToSave?:boolean}>();
const emit=defineEmits<{change:[id:string,value:string|string[]];valid:[value:boolean]}>();
const {t}=useI18n();
const groups=computed(()=>{const groups=new Map<string,{label:string;fields:ProductField[]}>();for(const field of props.fields){const key=field.purpose+":"+field.groupCode;if(!groups.has(key))groups.set(key,{label:t("productCatalog."+field.purpose)+(field.groupName?" / "+field.groupName:""),fields:[]});groups.get(key)!.fields.push(field);}return [...groups.values()];});
/** false、0按真实值展示，枚举使用固定修订标签，空值才显示占位。 */
function display(field:ProductField){const value=props.values[field.templateItemId];if(Array.isArray(value))return value.map(id=>(id.startsWith("custom:")?id.slice(7):field.options.find(o=>o.id===id)?.label)??t("recordAudit.unknown")).join(" / ")||"—";if(value===undefined||value==="")return "—";if(field.dataType==="BOOLEAN")return t(value==="true"?"productV1.yes":"productV1.no");return value+(field.unitCode?" "+field.unitCode:"");}
function change(field:ProductField,value:unknown){if(Array.isArray(value)&&field.allowCustom)value=value.map(v=>field.options.some(o=>o.id===v)||String(v).startsWith("custom:")?v:"custom:"+v);emit("change",field.templateItemId,Array.isArray(value)?value.map(String):value==null?"":String(value));}

/** 精确数值比较用BigInt缩放到12位，不将后端精确小数转换为浮点数。 */
function exact(value:string){const match=value.match(/^(-?)(\d+)(?:\.(\d+))?$/);if(!match||((match[3]??"").length>12))throw new Error();return BigInt((match[1]||"")+match[2]+(match[3]??"").padEnd(12,"0"));}
function inputError(field:ProductField):string{
 const value=props.values[field.templateItemId];if(value===undefined||value===""||Array.isArray(value)&&!value.length)return "";
 try{
 const rules=JSON.parse(field.validationJson||"{}") as Record<string,string|number>;
 if(Array.isArray(value)){const min=Number(rules.minItems??0),max=Number(rules.maxItems??50);if(value.length<min||value.length>max||field.dataType==="SINGLE_SELECT"&&value.length!==1)return t("productV1.choiceCount",{min,max});return "";}
 if(field.dataType==="INTEGER"||field.dataType==="DECIMAL"){
  if(field.dataType==="INTEGER"&&!/^-?(0|[1-9][0-9]*)$/.test(value))return t("productV1.invalidNumber");
  const number=exact(value);
  if(field.dataType==="INTEGER"&&(BigInt(value)<BigInt("-9223372036854775808")||BigInt(value)>BigInt("9223372036854775807")))return t("productV1.invalidNumber");
  if(field.dataType==="DECIMAL"&&((value.split(".")[1]??"").length>(field.numericScale??0)||value.replace(/^-?0*/,"").split(".")[0].length>18))return t("productV1.scaleHint",{scale:field.numericScale});
  if(rules.min!==undefined&&number<exact(String(rules.min))||rules.max!==undefined&&number>exact(String(rules.max)))return t("productV1.rangeHint",{min:rules.min??"—",max:rules.max??"—"});
 }
 if(field.dataType==="TEXT"&&([...value].length<Number(rules.minLength??0)||[...value].length>Number(rules.maxLength??2000)))return t("productV1.lengthHint",{min:rules.minLength??0,max:rules.maxLength??2000});
 if(field.dataType==="DATE"&&(rules.minDate&&value<String(rules.minDate)||rules.maxDate&&value>String(rules.maxDate)))return t("productV1.rangeHint",{min:rules.minDate??"—",max:rules.maxDate??"—"});
 return "";
 }catch{return t("productV1.invalidNumber");}
}
watch(()=>[props.fields,props.values],()=>emit("valid",props.fields.every(field=>!inputError(field))),{deep:true,immediate:true});
</script>
<template>
<section v-for="group in groups" :key="group.label" class="product-attribute-group" :class="{'readonly-detail-section':readonly}">
<div v-if="detailProfile" class="detail-section-heading"><h2>{{group.label}}</h2></div><h3 v-else>{{group.label}}</h3>
<dl v-if="readonly" class="detail-field-grid">
 <div v-for="field in group.fields" :key="field.templateItemId" class="detail-readonly-field">
  <dt>{{field.name}}</dt>
  <dd>{{display(field)}}</dd>
 </div>
</dl>
<div v-else :class="detailProfile ? 'detail-field-grid' : 'product-attribute-grid'">
<el-form-item v-for="field in group.fields" :key="field.templateItemId" :error="inputError(field)">
<template #label>{{field.name}}<span v-if="field.requiredFlag" class="required-hint"> · {{t(requiredToSave?'masterData.required':'productV1.requiredToEnable')}}</span><span v-if="field.unitCode"> ({{field.unitCode}})</span></template>
<el-select v-if="field.dataType==='BOOLEAN'" :model-value="values[field.templateItemId]??''" clearable :disabled="disabled" @update:model-value="v=>change(field,v)"><el-option value="true" :label="t('productV1.yes')"/><el-option value="false" :label="t('productV1.no')"/></el-select>
<el-select v-else-if="field.dataType==='SINGLE_SELECT'||field.dataType==='MULTI_SELECT'" :model-value="field.dataType==='SINGLE_SELECT'?((values[field.templateItemId] as string[])?.[0]??''):(values[field.templateItemId]??[])" :multiple="field.dataType==='MULTI_SELECT'" :allow-create="field.allowCustom" filterable clearable :disabled="disabled" @update:model-value="v=>change(field,field.dataType==='SINGLE_SELECT'?(v?[v]:[]):v)"><el-option v-for="option in field.options" :key="option.id" :value="option.id" :label="option.label" :disabled="!option.enabled"/></el-select>
<el-date-picker v-else-if="field.dataType==='DATE'" :model-value="values[field.templateItemId]" type="date" value-format="YYYY-MM-DD" :disabled="disabled" @update:model-value="v=>change(field,v)"/>
<el-input v-else :model-value="values[field.templateItemId] as string" :maxlength="field.dataType==='TEXT'?2000:100" :disabled="disabled" @update:model-value="v=>change(field,v)"/>
<small v-if="field.dataType==='DECIMAL'">{{t('productV1.scaleHint',{scale:field.numericScale})}}</small>
</el-form-item>
</div>
</section>
</template>
<style scoped>
.product-attribute-group{margin-block:var(--app-section-gap,8px)}
.product-attribute-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,240px),1fr));gap:16px}
.required-hint,small{color:var(--el-text-color-secondary);font-size:var(--el-font-size-small)}
h3{font-size:var(--el-font-size-medium);font-weight:600}
</style>
