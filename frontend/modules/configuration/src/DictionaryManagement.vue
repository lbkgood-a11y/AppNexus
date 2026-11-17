<script setup lang="ts">
import { setErrorFeedback } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import VxeDictionaryTable from "./VxeDictionaryTable.vue";
import {computed,onMounted,reactive,ref,watch} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage} from "element-plus";
import {AppIcon} from "@appnexus/icons";
import {AppPagination,ManagedDialog,TreeListTemplate,type ManagementColumn} from "@appnexus/ui";
import type {BusinessDictionary,DictionaryItem,DictionaryManagementApi,DictionaryStatus} from "./index";

/** 页面复用左树右表、统一弹窗和 Element Plus；字典类型适合作为右侧数据范围选择器。 */
const props=defineProps<{api:DictionaryManagementApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();
/** 左树折叠状态默认展开；仅控制布局，保留已选字典及右侧查询结果。 */
const treeCollapsed=ref(false);
/** 本人能力失败时保持空集合，不能回退为管理员；真实接口仍逐请求鉴权。 */
const capabilities=ref<string[]>([]);
const can=(action:string)=>capabilities.value.includes(action);
const dictionaries=ref<BusinessDictionary[]>([]),items=ref<DictionaryItem[]>([]),selectedId=ref(""),loading=ref(false),saving=ref(false),error=ref(""),query=ref(""),submitted=ref("");
const dictionaryDialog=ref<"create"|"edit"|"">(""),itemDialog=ref<"create"|"edit"|"">(""),editingItem=ref<DictionaryItem>();
const dictionaryForm=reactive({code:"",name:"",description:"",status:"ENABLED" as DictionaryStatus,sortOrder:100,version:0});
const itemForm=reactive({parentId:null as string|null,code:"",value:"",name:"",description:"",status:"ENABLED" as DictionaryStatus,sortOrder:100,version:0});
/** 类型查询仅过滤完整的已授权目录；不改变当前选中类型和右侧数据范围。 */
const typeQuery=ref("");
/** 类型页码从 1 开始，每页固定 10 条；仅本地展示分页，不向接口虚构分页参数或总数。 */
const typePage=ref(1),typePageSize=10;
const filteredTypes=computed(()=>{const word=typeQuery.value.trim().toLocaleLowerCase();return dictionaries.value.filter(value=>!word||[value.name,value.code,value.description].some(text=>text?.toLocaleLowerCase().includes(word)));});
const pageTypes=computed(()=>filteredTypes.value.slice((typePage.value-1)*typePageSize,typePage.value*typePageSize));
/** 查询或目录刷新后回到首页，避免删除、筛选后停留在空页。 */
watch([typeQuery,dictionaries],()=>{typePage.value=1;});
const selected=computed(()=>dictionaries.value.find(value=>value.id===selectedId.value));
const filtered=computed(()=>{const word=submitted.value.trim().toLocaleLowerCase();return items.value.filter(item=>!word||[item.code,item.value,item.name,item.description].some(value=>value?.toLocaleLowerCase().includes(word)));});
/** 操作列预留 240px，容纳中英文三个平铺按钮；窄屏由表格内部滚动承载。 */
const columns=computed<ManagementColumn[]>(()=>[{key:"name",prop:"name",label:t("dictionary.name"),minWidth:160},{key:"code",prop:"code",label:t("dictionary.code"),minWidth:130},{key:"value",prop:"value",label:t("dictionary.value"),minWidth:130},{key:"parent",label:t("dictionary.parent"),minWidth:140},{key:"status",label:t("dictionary.status"),width:100},{key:"sortOrder",prop:"sortOrder",label:t("dictionary.sortOrder"),width:100},{key:"operations",label:t("tenant.operation"),width:240,fixed:"right"}]);
const parentOptions=computed(()=>items.value.filter(item=>item.id!==editingItem.value?.id&&!descendants(editingItem.value?.id).has(item.id)));
function descendants(id?:string){const result=new Set<string>();if(!id)return result;const visit=(parent:string)=>items.value.filter(item=>item.parentId===parent).forEach(item=>{result.add(item.id);visit(item.id);});visit(id);return result;}
function parentName(id?:string|null){return items.value.find(item=>item.id===id)?.name||"—";}
// 切换编辑上下文清理旧失败，防止关闭弹窗后将保存错误误当成列表加载错误。
watch([dictionaryDialog, itemDialog], () => { error.value = ""; });
async function loadDictionaries(){loading.value=true;error.value="";try{capabilities.value=await props.api.capabilities();dictionaries.value=await props.api.list();if(!dictionaries.value.some(value=>value.id===selectedId.value))selectedId.value=dictionaries.value[0]?.id||"";}catch(cause){capabilities.value=[];await setErrorFeedback(error, props.errorText(cause));}finally{loading.value=false;}}
async function loadItems(){if(!selectedId.value){items.value=[];return;}loading.value=true;error.value="";try{items.value=await props.api.items(selectedId.value);}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{loading.value=false;}}
watch(selectedId,loadItems);onMounted(loadDictionaries);
function search(reset=false){if(reset)query.value="";submitted.value=query.value;}
function openDictionary(value?:BusinessDictionary){dictionaryDialog.value=value?"edit":"create";Object.assign(dictionaryForm,value?{code:value.code,name:value.name,description:value.description||"",status:value.status,sortOrder:value.sortOrder,version:value.version}:{code:"",name:"",description:"",status:"ENABLED",sortOrder:100,version:0});}
function openItem(value?:DictionaryItem,parentId:string|null=null){editingItem.value=value;itemDialog.value=value?"edit":"create";Object.assign(itemForm,value?{parentId:value.parentId||null,code:value.code,value:value.value,name:value.name,description:value.description||"",status:value.status,sortOrder:value.sortOrder,version:value.version}:{parentId,code:"",value:"",name:"",description:"",status:"ENABLED",sortOrder:100,version:0});}
/** 保存前执行命名约束；编辑态保持稳定编码只读，不阻止历史项维护名称和停用状态。 */
async function saveDictionary(){if(dictionaryDialog.value==="create"&&!validCode(dictionaryForm.code)){await setErrorFeedback(error,t("dictionary.invalidCode"));return;}saving.value=true;error.value="";try{if(dictionaryDialog.value==="edit"&&selected.value)await props.api.update(selected.value.id,{name:dictionaryForm.name,description:dictionaryForm.description,status:dictionaryForm.status,sortOrder:dictionaryForm.sortOrder,version:dictionaryForm.version});else await props.api.create({code:dictionaryForm.code,name:dictionaryForm.name,description:dictionaryForm.description,sortOrder:dictionaryForm.sortOrder});dictionaryDialog.value="";ElMessage.success(t("dictionary.saved"));await loadDictionaries();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{saving.value=false;}}
/** 新增使用标准语义码，禁止静默 trim 或转换大小写；后端仍独立复核。 */
async function saveItem(){if(!selected.value)return;if(itemDialog.value==="create"){if(!validCode(itemForm.code)){await setErrorFeedback(error,t("dictionary.invalidCode"));return;}if(!validValue(itemForm.value)){await setErrorFeedback(error,t("dictionary.invalidValue"));return;}}saving.value=true;error.value="";try{if(itemDialog.value==="edit"&&editingItem.value)await props.api.updateItem(selected.value.id,editingItem.value.id,{parentId:itemForm.parentId,name:itemForm.name,description:itemForm.description,status:itemForm.status,sortOrder:itemForm.sortOrder,version:itemForm.version});else await props.api.createItem(selected.value.id,{parentId:itemForm.parentId,code:itemForm.code,value:itemForm.value,name:itemForm.name,description:itemForm.description,sortOrder:itemForm.sortOrder});itemDialog.value="";ElMessage.success(t("dictionary.saved"));await loadItems();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{saving.value=false;}}
async function removeDictionary(){if(!selected.value)return;saving.value=true;try{await props.api.remove(selected.value.id,selected.value.version);ElMessage.success(t("dictionary.deleted"));await loadDictionaries();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{saving.value=false;}}
async function removeItem(item:DictionaryItem){saving.value=true;try{await props.api.removeItem(item.dictionaryId,item.id,item.version);ElMessage.success(t("dictionary.deleted"));await loadItems();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{saving.value=false;}}
/** 与后端 DictionaryValueStandard 对齐：编码最长64字符，值最长100字符，拒绝技术主键与空白。 */
const opaqueCode=/^(?:v_)?[0-9a-f]{32,64}$/i;
function validCode(value:string){return value.length<=64&&/^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$/.test(value)&&!opaqueCode.test(value);}
function validValue(value:string){return value.length<=100&&/^(?:[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*|[0-9]+)$/.test(value)&&!opaqueCode.test(value);}
const required=[{required:true,whitespace:true,max:100,message:t("dictionary.required"),trigger:"blur"}];
const codeRules=[{required:true,validator:(_rule:unknown,value:string,callback:(error?:Error)=>void)=>callback(validCode(value)?undefined:new Error(t("dictionary.invalidCode"))),trigger:"blur"}];
const valueRules=[{required:true,validator:(_rule:unknown,value:string,callback:(error?:Error)=>void)=>callback(validValue(value)?undefined:new Error(t("dictionary.invalidValue"))),trigger:"blur"}];
</script>
<template><div class="management-page dictionary-sample"><TreeListTemplate v-model:collapsed="treeCollapsed" :title="t('dictionary.title')" :search-title="t('dictionary.itemQuery')" :tree-label="t('dictionary.types')">
 <template #navigation><div class="workspace-banner" data-page-banner><div class="workspace-banner-icon"><AppIcon name="layers" :size="26" /></div><div><h1>{{t('dictionary.title')}}</h1><p>{{t('dictionary.visualSubtitle')}}</p></div></div></template>
 <template #treeHeader><div class="tree-heading"><strong>{{t('dictionary.types')}}</strong><el-tooltip :content="t('dictionary.createType')"><el-button v-operation="'BusinessDictionary.create'" v-if="can('create')" text class="dictionary-tree-add" :aria-label="t('dictionary.createType')" @click="openDictionary()"><AppIcon name="folderAdd" :size="18" /></el-button></el-tooltip></div></template>
 <template #tree><div class="dictionary-type-browser"><el-input v-model="typeQuery" clearable :aria-label="t('dictionary.typeQuery')" :placeholder="t('dictionary.typeQueryPlaceholder')"><template #prefix><AppIcon name="search" :size="16" /></template></el-input><el-menu :default-active="selectedId" @select="selectedId=$event"><el-menu-item v-for="value in pageTypes" :key="value.id" :index="value.id"><AppIcon name="folder" :size="18" /><span class="sample-type"><span>{{value.name}}</span></span><el-tag class="ml-auto" size="small" :type="value.status==='ENABLED'?'success':'info'">{{t('dictionary.statuses.'+value.status)}}</el-tag></el-menu-item></el-menu><el-empty v-if="!filteredTypes.length" :description="t(typeQuery.trim()?'dictionary.noMatchingTypes':'dictionary.emptyTypes')" :image-size="56" /><footer class="dictionary-type-pagination"><AppPagination v-model:current-page="typePage" :page-size="typePageSize" :total="filteredTypes.length" :pager-count="5" layout="prev, pager, next" :disabled="loading" /></footer></div></template>
 <template v-if="error&&!dictionaryDialog&&!itemDialog" #feedback><FloatingError type="error" :title="error" scope="error" :closable="false" show-icon><el-button link type="primary" @click="loadDictionaries">{{t('account.retry')}}</el-button></FloatingError></template>
 <template #header><div class="sample-summary"><div class="management-filter-header"><div class="sample-selection"><h2 class="management-filter-title">{{selected?.name||t('dictionary.items')}}</h2><code v-if="selected">{{selected.code}}</code></div><div v-if="selected" class="type-actions"><el-button v-operation="'BusinessDictionary.update'" v-if="can('update')" link type="primary" @click="openDictionary(selected)"><AppIcon name="edit" :size="17" />{{t('dictionary.editType')}}</el-button><el-popconfirm v-if="can('delete')" :title="t('dictionary.confirmDeleteType')" @confirm="removeDictionary"><template #reference><el-button v-operation="'BusinessDictionary.delete'" link type="danger"><AppIcon name="delete" :size="17" />{{t('dictionary.delete')}}</el-button></template></el-popconfirm></div></div><p v-if="selected?.description" class="sample-description">{{selected.description}}</p></div></template>
 <template #search><div class="management-filter"><el-form inline @submit.prevent="search()"><el-form-item :label="t('dictionary.keywordLabel')"><el-input v-model="query" clearable :placeholder="t('dictionary.queryPlaceholder')"><template #suffix><AppIcon name="search" :size="16" /></template></el-input></el-form-item><div class="management-filter-actions"><el-button type="primary" native-type="submit"><AppIcon name="search" :size="16" />{{t('administration.search')}}</el-button><el-button @click="search(true)"><AppIcon name="refresh" :size="16" />{{t('management.reset')}}</el-button></div></el-form></div></template>
 <template #table><VxeDictionaryTable embedded :title="t('dictionary.items')" :columns="columns" :rows="filtered" :loading="loading" :busy="saving" :empty-text="selected?t('dictionary.emptyItems'):t('dictionary.selectType')" @refresh="loadItems"><template #actions><el-button v-operation="'DictionaryItem.create'" v-if="can('item_create')" type="primary" :disabled="!selected" @click="openItem()"><AppIcon name="add" :size="16" />{{t('dictionary.createItem')}}</el-button></template><template #name="{row}"><strong class="sample-name">{{row.name}}</strong><span v-if="row.description" class="sample-description">{{row.description}}</span></template><template #code="{row}"><code class="sample-code">{{row.code}}</code></template><template #parent="{row}">{{parentName(row.parentId)}}</template><template #status="{row}"><el-tag :type="row.status==='ENABLED'?'success':'info'">{{t('dictionary.statuses.'+row.status)}}</el-tag></template><template #operations="{row}"><div class="dictionary-item-actions"><el-button v-operation="'DictionaryItem.update'" v-if="can('item_update')" link type="primary" @click="openItem(row)">{{t('dictionary.edit')}}</el-button><el-button v-operation="'DictionaryItem.create'" v-if="can('item_create')" link type="primary" @click="openItem(undefined,row.id)">{{t('dictionary.addChild')}}</el-button><el-popconfirm v-if="can('item_delete')" :title="t('dictionary.confirmDeleteItem')" @confirm="removeItem(row)"><template #reference><el-button v-operation="'DictionaryItem.delete'" link type="danger" :disabled="row.referenced">{{t('dictionary.delete')}}</el-button></template></el-popconfirm></div></template></VxeDictionaryTable></template>
 </TreeListTemplate>
 <ManagedDialog :model-value="Boolean(dictionaryDialog)" :title="t(dictionaryDialog==='edit'?'dictionary.editType':'dictionary.createType')" width="min(600px, calc(100vw - 32px))" :busy="saving" @update:model-value="value=>{if(!value)dictionaryDialog=''}"><FloatingError v-if="error" type="error" :title="error" scope="error" :closable="false" class="mb-4"/><el-form class="business-border-label-form dictionary-editor" :model="dictionaryForm" label-position="top" @submit.prevent="saveDictionary"><el-form-item :label="t('dictionary.code')" :rules="codeRules" prop="code"><el-input v-model="dictionaryForm.code" maxlength="64" :placeholder="t('dictionary.codeHint')" :disabled="dictionaryDialog==='edit'||saving" /></el-form-item><el-form-item :label="t('dictionary.name')" :rules="required" prop="name"><el-input v-model="dictionaryForm.name" /></el-form-item><el-form-item :label="t('dictionary.description')"><el-input v-model="dictionaryForm.description" type="textarea" maxlength="500" /></el-form-item><div class="form-row"><el-form-item :label="t('dictionary.status')"><el-select v-model="dictionaryForm.status" :disabled="dictionaryDialog==='create'"><el-option v-for="status in ['ENABLED','DISABLED']" :key="status" :value="status" :label="t('dictionary.statuses.'+status)" /></el-select></el-form-item><el-form-item :label="t('dictionary.sortOrder')"><el-input-number v-model="dictionaryForm.sortOrder" :min="0" :max="9999" /></el-form-item></div><div class="dialog-actions"><el-button @click="dictionaryDialog=''">{{t('organization.cancel')}}</el-button><el-button v-operation="dictionaryDialog==='create'?'BusinessDictionary.create':'BusinessDictionary.update'" type="primary" native-type="submit" :loading="saving">{{t('organization.save')}}</el-button></div></el-form></ManagedDialog>
 <ManagedDialog :model-value="Boolean(itemDialog)" :title="t(itemDialog==='edit'?'dictionary.editItem':'dictionary.createItem')" width="min(680px, calc(100vw - 32px))" :busy="saving" @update:model-value="value=>{if(!value)itemDialog=''}"><FloatingError v-if="error" type="error" :title="error" scope="error" :closable="false" class="mb-4"/><div class="sample-context"><span>{{t('dictionary.types')}}</span><strong>{{selected?.name}}</strong><code>{{selected?.code}}</code></div><el-form class="business-border-label-form dictionary-editor" :model="itemForm" label-position="top" @submit.prevent="saveItem"><div class="form-grid"><el-form-item :label="t('dictionary.parent')"><el-select v-model="itemForm.parentId" clearable><el-option v-for="item in parentOptions" :key="item.id" :value="item.id" :label="item.name+' ('+item.code+')'" /></el-select></el-form-item><el-form-item :label="t('dictionary.sortOrder')"><el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" /></el-form-item><el-form-item :label="t('dictionary.code')" :rules="codeRules" prop="code"><el-input v-model="itemForm.code" maxlength="64" :placeholder="t('dictionary.codeHint')" :disabled="itemDialog==='edit'" /></el-form-item><el-form-item :label="t('dictionary.value')" :rules="valueRules" prop="value"><el-input v-model="itemForm.value" :placeholder="t('dictionary.valueHint')" :disabled="itemDialog==='edit'" maxlength="100" /></el-form-item><el-form-item :label="t('dictionary.name')" :rules="required" prop="name"><el-input v-model="itemForm.name" /></el-form-item><el-form-item :label="t('dictionary.status')"><el-select v-model="itemForm.status" :disabled="itemDialog==='create'"><el-option v-for="status in ['ENABLED','DISABLED']" :key="status" :value="status" :label="t('dictionary.statuses.'+status)" /></el-select></el-form-item></div><el-form-item :label="t('dictionary.description')"><el-input v-model="itemForm.description" type="textarea" maxlength="500" /></el-form-item><div class="dialog-actions"><el-button @click="itemDialog=''">{{t('organization.cancel')}}</el-button><el-button v-operation="itemDialog==='create'?'DictionaryItem.create':'DictionaryItem.update'" type="primary" native-type="submit" :loading="saving">{{t('organization.save')}}</el-button></div></el-form></ManagedDialog>
</div></template>
<style scoped>
/* 字典与全局共用横幅、查询、树和表格容器；此处仅维护业务摘要、字段及操作排布。 */
.sample-type { display:flex; flex:1; min-width:0; flex-direction:column; line-height:1.5; }
.sample-type span { overflow:hidden; text-overflow:ellipsis; }
.sample-name { font-weight:500; color:var(--el-text-color-primary); }
.sample-code,.sample-selection code { font-size:12px; color:var(--el-text-color-secondary); }
/* 对象摘要位于查询区之前，编辑和删除不属于筛选条件，折叠查询时仍保留这些操作。 */
/* 类型检索与分页复用公共控件，窄栏允许分页内部滚动，避免撑宽整个工作区。 */
.dictionary-type-browser {display:flex;flex:1;flex-direction:column;gap:var(--app-section-gap,8px);min-width:0;min-height:0;}
.dictionary-sample :deep(.tree-list-template__tree) {display:flex;flex-direction:column;min-height:0;}
.dictionary-type-browser :deep(.el-menu) {flex:1;min-height:0;overflow:auto;}
.dictionary-type-pagination {overflow-x:auto;flex:none;}
.sample-summary { min-width:0; }
.sample-summary .management-filter-header { margin:0; min-height:32px; }
.sample-selection { display:flex; align-items:center; flex-wrap:wrap; gap:8px; min-width:0; }
.sample-selection code { overflow-wrap:anywhere; }
.sample-description { display:block; margin:4px 0 0; font-size:13px; line-height:20px; color:var(--el-text-color-secondary); }
.tree-heading,.management-filter-header,.type-actions,.dialog-actions { display:flex; align-items:center; }
.tree-heading,.management-filter-header { justify-content:space-between; gap:8px; }
.type-actions,.dialog-actions { gap:8px; }
.type-actions { flex-shrink:0; }
.type-actions :deep(.el-button) { margin:0; }
.dictionary-item-actions { display:flex; align-items:center; gap:8px; white-space:nowrap; }
.dictionary-item-actions :deep(.el-button) { flex-shrink:0; margin-left:0; }
.dialog-actions { justify-content:flex-end; border-top:1px solid var(--el-border-color-lighter); margin-top:var(--app-section-gap,8px); padding-top:var(--app-content-padding,8px); }
.form-row,.form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 16px; }
.sample-context { display:flex; align-items:center; flex-wrap:wrap; gap:8px; margin-bottom:var(--app-section-gap,8px); padding-bottom:var(--app-section-gap,8px); border-bottom:1px solid var(--el-border-color-lighter); }
.sample-context span,.sample-context code { font-size:12px; color:var(--el-text-color-secondary); }
.dictionary-sample :deep(.management-filter .el-form-item) { margin-right:0; }
.dictionary-sample :deep(.management-filter .el-input) { width: min(360px,100%); }
/* 专用 Vxe 表体接收模板分配的剩余高度，分页与全屏继续由原适配器管理。 */
.dictionary-sample :deep(.dictionary-grid) { display:flex; flex-direction:column; flex:1; min-height:0; }
.dictionary-sample :deep(.dictionary-grid-toolbar) { flex-shrink:0; }
.dictionary-tree-add { width:32px; height:32px; padding:0; }
@media(max-width:600px) {
  .form-row,.form-grid { grid-template-columns:1fr; }
  .management-filter-header { flex-wrap:wrap; }
  .dictionary-sample :deep(.management-filter .el-form-item),.dictionary-sample :deep(.management-filter .el-input) { width:100%; }
  :deep(.el-button) { min-height:44px; }
  .dictionary-tree-add { width:44px; height:44px; }
}
</style>
