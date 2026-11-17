<script setup lang="ts">
import {computed,ref} from "vue";
import {useI18n} from "vue-i18n";
import {geographyPath,geographyTree,type GeographyNode,type GeographySelection,type GeographyTree} from "./geography";
/** 国别/区划公共选择器；组合现有 Element Plus，网络、权限和重试由宿主统一处理。 */
const props=withDefaults(defineProps<{
 /** 保存稳定编码；未知历史文本可回显，组件不会自动改写或丢弃。 */
 modelValue?:string;
 /** 已授权当前租户的完整摘要，不得以标准快照代替启用业务目录。 */
 nodes:GeographyNode[];
 /** country 仅国别；region 为国家下真实区划树，默认 region。 */
 kind?:"country"|"region";
 /** 国家/祖先限制默认空；只过滤选项，不主动改写模型，联动由宿主显式完成。 */
 countryCode?:string;parentCode?:string;
 /** 默认 any 可选择任意区划；province/city 用于现有人员地址分栏。 */
 level?:"any"|"province"|"city";
 /** 默认 false，只读以文本显示；disabled/default false 用于提交和加载锁定。 */
 readonly?:boolean;disabled?:boolean;loading?:boolean;
}>(),{modelValue:"",kind:"region",countryCode:"",parentCode:"",level:"any",readonly:false,disabled:false,loading:false});
const emit=defineEmits<{"update:modelValue":[value:string];select:[selection:GeographySelection|null]}>();
const {t}=useI18n();
const query=ref("");
/** 名称、编码和祖先路径均可检索；仅过滤宿主已授权目录，不发起额外目录请求。 */
function filterRegions(value:string){query.value=value.trim().toLocaleLowerCase();}
const path=computed(()=>geographyPath(props.nodes,props.modelValue));
const display=computed(()=>path.value.length?path.value.map(n=>n.name).join("-"):props.modelValue||"—");
const countries=computed(()=>props.nodes.filter(n=>!n.parentCode).map(n=>({value:n.code,label:n.name+" · "+n.code,disabled:!n.enabled})).concat(props.modelValue&&!path.value.length?[{value:props.modelValue,label:props.modelValue+" · "+t("geography.legacy"),disabled:true}]:[]));
const options=computed(()=>{
 const names=new Map(props.nodes.map(node=>[node.code,node.name]));
 const ancestors=props.parentCode?geographyPath(props.nodes,props.parentCode).slice(0,-1).map(n=>n.name):[];
 const tree=geographyTree(props.nodes,props.countryCode,props.parentCode,props.level);
 const rows:Array<{value:string;label:string;name:string;path:string;search:string;disabled:boolean}>=[];
 /** 已选标签显示以短横线连接的完整目录名称；下拉首行保留末级名称，次行展示完整路径。 */
 function flatten(nodes:GeographyTree[],parents:string[]=[]){for(const node of nodes){const name=names.get(node.value)??node.label;const labels=[...parents,name];if(node.disabled===false||node.value===props.modelValue)rows.push({value:node.value,label:labels.join("-"),name,path:labels.join("-"),search:node.search.toLocaleLowerCase(),disabled:node.disabled});flatten(node.children,labels);}}
 flatten(tree,ancestors);
 if(props.modelValue&&!rows.some(n=>n.value===props.modelValue))rows.push({value:props.modelValue,label:display.value+" · "+t("geography.legacy"),name:display.value,path:display.value,search:display.value.toLocaleLowerCase(),disabled:true});
 return rows.filter(row=>!query.value||row.search.includes(query.value));
});
/** 选中才更新模型；停用或残缺路径禁止新选，清空输出空字符串，与既有可选字段契约一致。 */
function change(value:unknown){const code=typeof value==="string"?value:"";const resolved=geographyPath(props.nodes,code);if(code&&(!resolved.length||resolved.some(n=>!n.enabled)))return;emit("update:modelValue",code);emit("select",code?{node:resolved[resolved.length-1],path:resolved}:null);}
</script>
<template>
 <span v-if="readonly">{{display}}</span>
 <el-select-v2 v-else-if="kind==='country'" :model-value="modelValue||undefined" :options="countries" filterable clearable :loading="loading" :disabled="disabled||loading" :placeholder="t('geography.country')" :no-data-text="t('geography.empty')" @update:model-value="change" />
 <el-select-v2 v-else class="geography-picker" :model-value="modelValue||undefined" :options="options" filterable clearable :filter-method="filterRegions" :item-height="44" :height="264" :loading="loading" :disabled="disabled||loading" :title="display" :placeholder="t('geography.region')" :no-data-text="t('geography.empty')" @visible-change="query=''" @update:model-value="change">
  <template #default="{item}"><div class="geography-option" :title="item.path+' · '+item.value"><div class="geography-option-heading"><span>{{item.name}}</span><small>{{item.value}}</small></div><div class="geography-option-path">{{item.path}}</div></div></template>
 </el-select-v2>
</template>
<style scoped>
/* 弹层沿用 Element Plus 定位与主题；两行选项高44px、行高18px，与虚拟列表 item-height 保持一致，手机不撑破视口。 */
.geography-picker{width:100%;min-width:0;}
.geography-option{display:flex;flex-direction:column;justify-content:center;height:44px;min-width:0;line-height:18px;}
.geography-option-heading{display:flex;align-items:baseline;gap:8px;min-width:0;}
.geography-option-heading>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.geography-option-heading>small{flex-shrink:0;font-size:var(--el-font-size-extra-small);font-weight:normal;color:var(--el-text-color-secondary);}
.geography-option-path{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:var(--el-font-size-extra-small);font-weight:normal;color:var(--el-text-color-secondary);}
</style>
