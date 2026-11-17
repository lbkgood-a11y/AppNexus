<script setup lang="ts">
import {computed} from "vue";
import {useI18n} from "vue-i18n";
import type {ProductField} from "./product";
/** 领域动态表单只组合现有Element Plus，required仅提示启用要求，不阻断合法不完整草稿保存。 */
const props=defineProps<{fields:ProductField[];values:Record<string,string|string[]>;readonly?:boolean;disabled?:boolean}>();
const emit=defineEmits<{change:[id:string,value:string|string[]]}>();
const {t}=useI18n();
const groups=computed(()=>{const groups=new Map<string,{label:string;fields:ProductField[]}>();for(const field of props.fields){const key=field.purpose+":"+field.groupCode;if(!groups.has(key))groups.set(key,{label:t("productCatalog."+field.purpose)+(field.groupName?" / "+field.groupName:""),fields:[]});groups.get(key)!.fields.push(field);}return [...groups.values()];});
/** false、0按真实值展示，枚举使用固定修订标签，空值才显示占位。 */
function display(field:ProductField){const value=props.values[field.templateItemId];if(Array.isArray(value))return value.map(id=>field.options.find(o=>o.id===id)?.label??t("recordAudit.unknown")).join(" / ")||"—";if(value===undefined||value==="")return "—";if(field.dataType==="BOOLEAN")return t(value==="true"?"productV1.yes":"productV1.no");return value+(field.unitCode?" "+field.unitCode:"");}
function change(field:ProductField,value:unknown){emit("change",field.templateItemId,Array.isArray(value)?value.map(String):value==null?"":String(value));}
</script>
<template>
<section v-for="group in groups" :key="group.label" class="product-attribute-group">
<h3>{{group.label}}</h3>
<el-descriptions v-if="readonly" :column="1" border><el-descriptions-item v-for="field in group.fields" :key="field.templateItemId" :label="field.name">{{display(field)}}</el-descriptions-item></el-descriptions>
<div v-else class="product-attribute-grid">
<el-form-item v-for="field in group.fields" :key="field.templateItemId">
<template #label>{{field.name}}<span v-if="field.requiredFlag" class="required-hint"> · {{t('productV1.requiredToEnable')}}</span><span v-if="field.unitCode"> ({{field.unitCode}})</span></template>
<el-select v-if="field.dataType==='BOOLEAN'" :model-value="values[field.templateItemId]??''" clearable :disabled="disabled" @update:model-value="v=>change(field,v)"><el-option value="true" :label="t('productV1.yes')"/><el-option value="false" :label="t('productV1.no')"/></el-select>
<el-select v-else-if="field.dataType==='SINGLE_SELECT'||field.dataType==='MULTI_SELECT'" :model-value="field.dataType==='SINGLE_SELECT'?((values[field.templateItemId] as string[])?.[0]??''):(values[field.templateItemId]??[])" :multiple="field.dataType==='MULTI_SELECT'" filterable clearable :disabled="disabled" @update:model-value="v=>change(field,field.dataType==='SINGLE_SELECT'?(v?[v]:[]):v)"><el-option v-for="option in field.options" :key="option.id" :value="option.id" :label="option.label" :disabled="!option.enabled"/></el-select>
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
