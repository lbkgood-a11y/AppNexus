<script setup lang="ts">
import { setErrorFeedback } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import VxeDictionaryTable from "./VxeDictionaryTable.vue";
import {computed,onMounted,reactive,ref,watch} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage} from "element-plus";
import {AppIcon} from "@appnexus/icons";
import {ManagedDialog,TreeListTemplate,type ManagementColumn} from "@appnexus/ui";
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
async function saveDictionary(){saving.value=true;error.value="";try{if(dictionaryDialog.value==="edit"&&selected.value)await props.api.update(selected.value.id,{name:dictionaryForm.name,description:dictionaryForm.description,status:dictionaryForm.status,sortOrder:dictionaryForm.sortOrder,version:dictionaryForm.version});else await props.api.create({code:dictionaryForm.code,name:dictionaryForm.name,description:dictionaryForm.description,sortOrder:dictionaryForm.sortOrder});dictionaryDialog.value="";ElMessage.success(t("dictionary.saved"));await loadDictionaries();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{saving.value=false;}}
async function saveItem(){if(!selected.value)return;saving.value=true;error.value="";try{if(itemDialog.value==="edit"&&editingItem.value)await props.api.updateItem(selected.value.id,editingItem.value.id,{parentId:itemForm.parentId,name:itemForm.name,description:itemForm.description,status:itemForm.status,sortOrder:itemForm.sortOrder,version:itemForm.version});else await props.api.createItem(selected.value.id,{parentId:itemForm.parentId,code:itemForm.code,value:itemForm.value,name:itemForm.name,description:itemForm.description,sortOrder:itemForm.sortOrder});itemDialog.value="";ElMessage.success(t("dictionary.saved"));await loadItems();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{saving.value=false;}}
async function removeDictionary(){if(!selected.value)return;saving.value=true;try{await props.api.remove(selected.value.id,selected.value.version);ElMessage.success(t("dictionary.deleted"));await loadDictionaries();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{saving.value=false;}}
async function removeItem(item:DictionaryItem){saving.value=true;try{await props.api.removeItem(item.dictionaryId,item.id,item.version);ElMessage.success(t("dictionary.deleted"));await loadItems();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{saving.value=false;}}
const required=[{required:true,whitespace:true,max:100,message:t("dictionary.required"),trigger:"blur"}],codeRules=[{required:true,pattern:/^[a-z][a-z0-9_]{1,63}$/,message:t("dictionary.invalidCode"),trigger:"blur"}];
</script>
<template><div class="management-page dictionary-sample"><TreeListTemplate v-model:collapsed="treeCollapsed" :title="t('dictionary.title')" :tree-label="t('dictionary.types')">
 <template #navigation><div class="sample-heading" data-page-banner><div class="sample-banner-icon"><AppIcon name="layers" :size="32" /></div><div><h1>{{t('dictionary.title')}}</h1><p>{{t('dictionary.visualSubtitle')}}</p></div></div></template>
 <template #treeHeader><div class="tree-heading"><strong>{{t('dictionary.types')}}</strong><el-tooltip :content="t('dictionary.createType')"><el-button v-operation="'BusinessDictionary.create'" v-if="can('create')" text class="dictionary-tree-add" :aria-label="t('dictionary.createType')" @click="openDictionary()"><AppIcon name="folderAdd" :size="18" /></el-button></el-tooltip></div></template>
 <template #tree><el-menu :default-active="selectedId" @select="selectedId=$event"><el-menu-item v-for="value in dictionaries" :key="value.id" :index="value.id"><AppIcon name="folder" :size="18" /><span class="sample-type"><span>{{value.name}}</span></span><el-tag class="ml-auto" size="small" :type="value.status==='ENABLED'?'success':'info'">{{t('dictionary.statuses.'+value.status)}}</el-tag></el-menu-item></el-menu><el-empty v-if="!dictionaries.length" :description="t('dictionary.emptyTypes')" :image-size="56" /></template>
 <template v-if="error&&!dictionaryDialog&&!itemDialog" #feedback><FloatingError type="error" :title="error" scope="error" :closable="false" show-icon><el-button link type="primary" @click="loadDictionaries">{{t('account.retry')}}</el-button></FloatingError></template>
 <template #search><div class="management-filter"><div class="sample-summary"><div class="management-filter-header"><div class="sample-selection"><h2 class="management-filter-title">{{selected?.name||t('dictionary.items')}}</h2><code v-if="selected">{{selected.code}}</code></div><div v-if="selected" class="type-actions"><el-button v-operation="'BusinessDictionary.update'" v-if="can('update')" link type="primary" @click="openDictionary(selected)"><AppIcon name="edit" :size="17" />{{t('dictionary.editType')}}</el-button><el-popconfirm v-if="can('delete')" :title="t('dictionary.confirmDeleteType')" @confirm="removeDictionary"><template #reference><el-button v-operation="'BusinessDictionary.delete'" link type="danger"><AppIcon name="delete" :size="17" />{{t('dictionary.delete')}}</el-button></template></el-popconfirm></div></div><p v-if="selected?.description" class="sample-description">{{selected.description}}</p></div><el-form inline @submit.prevent="search()"><el-form-item :label="t('dictionary.itemQuery')"><el-input v-model="query" clearable :placeholder="t('dictionary.queryPlaceholder')"><template #suffix><AppIcon name="search" :size="16" /></template></el-input></el-form-item><div class="management-filter-actions"><el-button type="primary" native-type="submit"><AppIcon name="search" :size="16" />{{t('administration.search')}}</el-button><el-button @click="search(true)"><AppIcon name="refresh" :size="16" />{{t('management.reset')}}</el-button></div></el-form></div></template>
 <template #table><VxeDictionaryTable :title="t('dictionary.items')" :columns="columns" :rows="filtered" :loading="loading" :busy="saving" :empty-text="selected?t('dictionary.emptyItems'):t('dictionary.selectType')" @refresh="loadItems"><template #actions><el-button v-operation="'DictionaryItem.create'" v-if="can('item_create')" type="primary" :disabled="!selected" @click="openItem()"><AppIcon name="add" :size="16" />{{t('dictionary.createItem')}}</el-button></template><template #name="{row}"><strong class="sample-name">{{row.name}}</strong><span v-if="row.description" class="sample-description">{{row.description}}</span></template><template #code="{row}"><code class="sample-code">{{row.code}}</code></template><template #parent="{row}">{{parentName(row.parentId)}}</template><template #status="{row}"><el-tag :type="row.status==='ENABLED'?'success':'info'">{{t('dictionary.statuses.'+row.status)}}</el-tag></template><template #operations="{row}"><div class="dictionary-item-actions"><el-button v-operation="'DictionaryItem.update'" v-if="can('item_update')" link type="primary" @click="openItem(row)">{{t('dictionary.edit')}}</el-button><el-button v-operation="'DictionaryItem.create'" v-if="can('item_create')" link type="primary" @click="openItem(undefined,row.id)">{{t('dictionary.addChild')}}</el-button><el-popconfirm v-if="can('item_delete')" :title="t('dictionary.confirmDeleteItem')" @confirm="removeItem(row)"><template #reference><el-button v-operation="'DictionaryItem.delete'" link type="danger" :disabled="row.referenced">{{t('dictionary.delete')}}</el-button></template></el-popconfirm></div></template></VxeDictionaryTable></template>
 </TreeListTemplate>
 <ManagedDialog :model-value="Boolean(dictionaryDialog)" :title="t(dictionaryDialog==='edit'?'dictionary.editType':'dictionary.createType')" width="min(600px, calc(100vw - 32px))" :busy="saving" @update:model-value="value=>{if(!value)dictionaryDialog=''}"><FloatingError v-if="error" type="error" :title="error" scope="error" :closable="false" class="mb-4"/><el-form class="dictionary-editor" :model="dictionaryForm" label-position="top" @submit.prevent="saveDictionary"><el-form-item :label="t('dictionary.code')" :rules="codeRules" prop="code"><el-input v-model="dictionaryForm.code" :disabled="dictionaryDialog==='edit'||saving" /></el-form-item><el-form-item :label="t('dictionary.name')" :rules="required" prop="name"><el-input v-model="dictionaryForm.name" /></el-form-item><el-form-item :label="t('dictionary.description')"><el-input v-model="dictionaryForm.description" type="textarea" maxlength="500" /></el-form-item><div class="form-row"><el-form-item :label="t('dictionary.status')"><el-select v-model="dictionaryForm.status" :disabled="dictionaryDialog==='create'"><el-option v-for="status in ['ENABLED','DISABLED']" :key="status" :value="status" :label="t('dictionary.statuses.'+status)" /></el-select></el-form-item><el-form-item :label="t('dictionary.sortOrder')"><el-input-number v-model="dictionaryForm.sortOrder" :min="0" :max="9999" /></el-form-item></div><div class="dialog-actions"><el-button @click="dictionaryDialog=''">{{t('organization.cancel')}}</el-button><el-button v-operation="dictionaryDialog==='create'?'BusinessDictionary.create':'BusinessDictionary.update'" type="primary" native-type="submit" :loading="saving">{{t('organization.save')}}</el-button></div></el-form></ManagedDialog>
 <ManagedDialog :model-value="Boolean(itemDialog)" :title="t(itemDialog==='edit'?'dictionary.editItem':'dictionary.createItem')" width="min(680px, calc(100vw - 32px))" :busy="saving" @update:model-value="value=>{if(!value)itemDialog=''}"><FloatingError v-if="error" type="error" :title="error" scope="error" :closable="false" class="mb-4"/><div class="sample-context"><span>{{t('dictionary.types')}}</span><strong>{{selected?.name}}</strong><code>{{selected?.code}}</code></div><el-form class="dictionary-editor" :model="itemForm" label-position="top" @submit.prevent="saveItem"><div class="form-grid"><el-form-item :label="t('dictionary.parent')"><el-select v-model="itemForm.parentId" clearable><el-option v-for="item in parentOptions" :key="item.id" :value="item.id" :label="item.name+' ('+item.code+')'" /></el-select></el-form-item><el-form-item :label="t('dictionary.sortOrder')"><el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" /></el-form-item><el-form-item :label="t('dictionary.code')" :rules="codeRules" prop="code"><el-input v-model="itemForm.code" :disabled="itemDialog==='edit'" /></el-form-item><el-form-item :label="t('dictionary.value')" :rules="required" prop="value"><el-input v-model="itemForm.value" :disabled="itemDialog==='edit'" maxlength="100" /></el-form-item><el-form-item :label="t('dictionary.name')" :rules="required" prop="name"><el-input v-model="itemForm.name" /></el-form-item><el-form-item :label="t('dictionary.status')"><el-select v-model="itemForm.status" :disabled="itemDialog==='create'"><el-option v-for="status in ['ENABLED','DISABLED']" :key="status" :value="status" :label="t('dictionary.statuses.'+status)" /></el-select></el-form-item></div><el-form-item :label="t('dictionary.description')"><el-input v-model="itemForm.description" type="textarea" maxlength="500" /></el-form-item><div class="dialog-actions"><el-button @click="itemDialog=''">{{t('organization.cancel')}}</el-button><el-button v-operation="itemDialog==='create'?'DictionaryItem.create':'DictionaryItem.update'" type="primary" native-type="submit" :loading="saving">{{t('organization.save')}}</el-button></div></el-form></ManagedDialog>
</div></template>
<style scoped>
/* 用户指定 Vben 式紧凑方向：仅调整字典样板，继续复用应用壳和主题，不引入另一套全站体系。 */

/* 字典管理视觉样板：限定页面作用域，复用模板组件与主题变量，兼容深色及用户主色。 */
.sample-heading{display:flex;align-items:center;gap:16px;padding:24px}
.sample-mark{display:grid;place-items:center;width:48px;height:48px;flex-shrink:0;background:var(--el-color-primary-light-9);color:var(--el-color-primary);border-radius:var(--el-border-radius-base)}
.sample-heading h1{margin:0;font-size:22px;font-weight:600;color:var(--el-text-color-primary)}
.sample-heading p{margin:6px 0 0;font-size:13px;line-height:1.6;color:var(--el-text-color-secondary)}
.dictionary-sample :deep(.tree-list-template__navigation){padding:0}
.dictionary-sample :deep(.tree-list-template__aside){background:var(--el-fill-color-lighter)}
.dictionary-sample :deep(.tree-list-template__tree){padding:8px}
.dictionary-sample :deep(.el-menu){background:transparent}
.dictionary-sample :deep(.el-menu-item){height:64px;margin-bottom:6px;padding:0 12px;gap:8px}
.dictionary-sample :deep(.el-menu-item.is-active){background:var(--el-color-primary-light-9)}
.sample-type{display:flex;flex:1;min-width:0;flex-direction:column;line-height:1.5}
.sample-type span{overflow:hidden;text-overflow:ellipsis}
.sample-type small{font-size:12px;color:var(--el-text-color-secondary)}
.sample-description{display:block;margin:4px 0 16px;font-size:12px;line-height:1.6;color:var(--el-text-color-secondary)}
.dictionary-sample .management-filter-header{margin-bottom:16px}
.dictionary-sample :deep(.management-table-total){padding:3px 8px;background:var(--el-fill-color-light);border-radius:var(--el-border-radius-base);font-size:12px}
.dictionary-sample :deep(.vxe-table){--vxe-ui-table-header-background-color:var(--el-fill-color-light)}
.dictionary-sample :deep(.vxe-body--column){padding-top:14px;padding-bottom:14px}
.sample-name{font-weight:500;color:var(--el-text-color-primary)}
.sample-code{padding:3px 6px;background:var(--el-fill-color-light);border-radius:var(--el-border-radius-small);font-size:12px}
/* 上下文位于弹窗内容中，Teleport 后仍由局部类名约束，不修改全站弹窗。 */
.sample-context{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:24px;padding:14px 16px;background:var(--el-fill-color-light);border-radius:var(--el-border-radius-base)}
.sample-context span,.sample-context code{font-size:12px;color:var(--el-text-color-secondary)}
.dialog-actions{border-top:1px solid var(--el-border-color-lighter);margin-top:24px;padding-top:20px}
@media(max-width:600px){.sample-heading{padding:16px;align-items:flex-start}.sample-heading h1{font-size:20px}.management-filter-header{flex-wrap:wrap}}

/* 同组操作保持单行并统一间距，避免相邻按钮外边距造成换行。 */
.dictionary-item-actions{display:flex;align-items:center;gap:8px;white-space:nowrap}
.dictionary-item-actions :deep(.el-button){flex-shrink:0;margin-left:0}
.tree-heading,.management-filter-header,.type-actions,.dialog-actions{display:flex;align-items:center}.tree-heading,.management-filter-header{justify-content:space-between;gap:8px}.type-actions,.dialog-actions{gap:8px}.dialog-actions{justify-content:flex-end}.form-row,.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 16px}:deep(.el-menu){border-right:0}:deep(.el-menu-item){height:40px;border-radius:var(--el-border-radius-base)}@media(max-width:600px){.form-row,.form-grid{grid-template-columns:1fr}:deep(.el-button){min-height:44px}}
/* 紧凑版覆盖首版装饰性留白：分类作为导航，筛选与数据区各自保持清晰边界。 */
.sample-heading{padding:18px 24px;gap:0}
.sample-heading h1{font-size:20px;line-height:28px}
.sample-heading p{margin-top:4px;font-size:12px}
.dictionary-sample :deep(.tree-list-template__navigation){background:var(--el-bg-color)}
.dictionary-sample :deep(.tree-list-template__aside){background:var(--el-bg-color)}
.dictionary-sample :deep(.tree-list-template__tree){padding:8px}
.dictionary-sample :deep(.el-menu-item){height:42px;margin-bottom:4px;font-size:13px;border-radius:var(--el-border-radius-base)}
.dictionary-sample :deep(.el-menu-item.is-active){font-weight:600;box-shadow:inset 3px 0 var(--el-color-primary)}
.dictionary-sample :deep(.tree-list-template__search){background:var(--el-bg-color);padding:20px}
.dictionary-sample :deep(.tree-list-template__data){padding:20px}
.dictionary-sample .management-filter-header{margin-bottom:8px}
.sample-selection{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.sample-selection code{font-size:12px;color:var(--el-text-color-secondary)}
.sample-description{margin:4px 0 16px}
.dictionary-sample :deep(.el-form--inline .el-form-item){margin-bottom:0}
.dictionary-sample :deep(.management-filter .el-form){display:flex;align-items:center;flex-wrap:wrap;gap:12px}
.dictionary-sample :deep(.management-filter .el-input){width:240px;max-width:100%}
.dictionary-sample :deep(.el-tag){border-color:transparent;font-size:12px}
.sample-code{background:none;padding:0;color:var(--el-text-color-secondary)}
.sample-context{background:transparent;padding:0 0 14px;margin-bottom:var(--app-section-gap,12px);border-bottom:1px solid var(--el-border-color-lighter)}
.dialog-actions{margin-top:16px;padding-top:16px}
@media(max-width:600px){.sample-heading{padding:16px}.dictionary-sample :deep(.tree-list-template__search),.dictionary-sample :deep(.tree-list-template__data){padding:16px}.dictionary-sample :deep(.management-filter .el-form-item){width:100%;margin-right:0}.dictionary-sample :deep(.management-filter .el-input){width:100%}}

/* 参考 Vben 系统字体栈；中文明确回退系统中文字体，无远程字体请求，弹窗同样生效。 */
.dictionary-sample,.dictionary-editor,.sample-context{
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,"PingFang SC","Microsoft YaHei",sans-serif;
  font-size:14px;line-height:1.5;-webkit-font-smoothing:antialiased;
}
/* 中性文字和低饱和表面从现有语义变量混合；主色只用于选中及主要动作，深色自动跟随。 */
.dictionary-sample{--dictionary-ink:color-mix(in srgb,var(--el-text-color-primary) 85%,var(--el-text-color-regular));--dictionary-muted:var(--el-text-color-regular);color:var(--dictionary-ink)}
.sample-heading h1{font-size:20px;font-weight:600;letter-spacing:-.3px;color:var(--dictionary-ink)}
.sample-heading p,.sample-description,.sample-selection code{color:var(--dictionary-muted);font-size:13px}
.dictionary-sample :deep(.management-filter-title){font-size:15px;font-weight:600;color:var(--dictionary-ink)}
.dictionary-sample :deep(.el-menu-item){color:var(--el-text-color-regular);font-weight:400}
.dictionary-sample :deep(.el-menu-item.is-active){color:var(--el-color-primary);background:color-mix(in srgb,var(--el-color-primary) 8%,var(--el-bg-color));box-shadow:none;font-weight:500}
.dictionary-sample :deep(.el-button){font-family:inherit;font-weight:500}
.dictionary-sample :deep(.el-form-item__label),.dictionary-editor :deep(.el-form-item__label){color:var(--el-text-color-regular);font-size:13px;font-weight:500}
.dictionary-sample :deep(.el-tag--success){--el-tag-bg-color:color-mix(in srgb,var(--el-color-success) 9%,var(--el-bg-color));--el-tag-text-color:var(--el-color-success);font-weight:400}
.sample-name{font-weight:500;color:var(--dictionary-ink)}
.dictionary-editor :deep(.el-input__inner),.dictionary-editor :deep(.el-textarea__inner){font-family:inherit}

/* 高度沿应用壳逐层传递，分类和表体分别滚动；窄屏筛选区可滚动，不裁切操作。 */
.dictionary-sample{display:flex;flex-direction:column;overflow:hidden}
.dictionary-sample :deep(.tree-list-template),.dictionary-sample :deep(.tree-list-template__surface),.dictionary-sample :deep(.tree-list-template__surface > .el-card__body){display:flex;flex-direction:column;flex:1;min-height:0;overflow:hidden}
.dictionary-sample :deep(.tree-list-template__navigation){flex-shrink:0}
.dictionary-sample :deep(.tree-list-template__layout){flex:1;min-height:0;overflow:hidden}
.dictionary-sample :deep(.tree-list-template__aside),.dictionary-sample :deep(.tree-list-template__content){display:flex;flex-direction:column;min-height:0;overflow:hidden}
.dictionary-sample :deep(.tree-list-template__tree){flex:1;min-height:0;overflow:auto}
.dictionary-sample :deep(.tree-list-template__search){flex-shrink:0;max-height:45%;overflow:auto}
.dictionary-sample :deep(.tree-list-template__data),.dictionary-sample :deep(.template-table){display:flex;flex-direction:column;flex:1;min-height:0;overflow:hidden}
.dictionary-sample :deep(.dictionary-grid){display:flex;flex-direction:column;flex:1;min-height:0}
.dictionary-sample :deep(.dictionary-grid-toolbar){flex-shrink:0}

/* 用户参考图的局部实现：横幅与内容双表面，装饰仅用主题渐变，无外部资源或模拟统计。 */
.dictionary-sample :deep(.tree-list-template__surface){border:0;background:transparent;box-shadow:none}
.dictionary-sample :deep(.tree-list-template__surface > .el-card__body){gap:var(--app-section-gap, 12px)}
.dictionary-sample :deep(.tree-list-template__navigation){border:1px solid var(--el-border-color-lighter);border-radius:var(--app-card-radius, 8px);background:linear-gradient(115deg,var(--el-bg-color) 35%,var(--el-color-primary-light-9));flex:none}
.sample-heading{position:relative;isolation:isolate;overflow:hidden;gap:18px;padding:24px}
.sample-heading::after{content:"";position:absolute;right:48px;width:64px;height:64px;border:14px solid color-mix(in srgb,var(--el-color-primary) 12%,transparent);transform:rotate(30deg);z-index:-1;box-shadow:40px 18px 0 color-mix(in srgb,var(--el-color-primary) 6%,transparent)}
.sample-banner-icon{display:grid;place-items:center;flex:none;width:64px;height:64px;border-radius:12px;background:color-mix(in srgb,var(--el-color-primary) 10%,var(--el-bg-color));color:var(--el-color-primary)}
.sample-heading h1{font-size:24px;line-height:32px;letter-spacing:0}
.sample-heading p{font-size:14px;margin-top:8px}
.dictionary-sample :deep(.tree-list-template__layout){border:1px solid var(--el-border-color-lighter);border-radius:var(--app-card-radius, 8px);background:var(--el-bg-color);padding:12px}
.dictionary-sample :deep(.tree-list-template__tree-header){padding:8px 12px;min-height:52px}
.dictionary-sample :deep(.el-menu-item){height:48px;margin:4px 0;gap:12px}
.dictionary-sample :deep(.el-menu-item.is-active){box-shadow:inset 2px 0 var(--el-color-primary)}
.dictionary-sample :deep(.tree-list-template__search){padding:4px 12px 16px;border-bottom:0;max-height:50%}
.sample-summary{padding:16px;border:1px solid var(--el-border-color-lighter);border-radius:var(--app-card-radius, 8px);background:var(--el-fill-color-light);margin-bottom:var(--app-section-gap,12px)}
.sample-summary .sample-description{margin:8px 0 0;font-size:13px}
.dictionary-sample :deep(.management-filter .el-form){gap:12px}
.dictionary-sample :deep(.management-filter .el-form-item){flex:1;margin-right:0}
.dictionary-sample :deep(.management-filter .el-input){width: min(360px,100%)}
.management-filter-actions{margin-left:auto;display:flex;gap:8px}
.dictionary-sample :deep(.tree-list-template__data){padding:0 12px 0}
@media(max-width:600px){.sample-heading{padding:16px;gap:12px}.sample-banner-icon{width:44px;height:44px}.sample-heading h1{font-size:20px}.sample-heading::after{display:none}.dictionary-sample :deep(.tree-list-template__layout){padding:4px}.management-filter-actions{margin-left:0}.sample-summary{padding:12px;margin-bottom:12px}}

/* 分类工具采用文件夹新增与侧栏折叠语义，轻边框方形按钮保持一致的键盘和悬浮反馈。 */
.dictionary-tree-add,.dictionary-sample :deep(.tree-list-template__toggle){width:32px;height:32px;min-height:32px;flex:0 0 32px;padding:0;border:1px solid var(--el-border-color-light);border-radius:6px;background:var(--el-bg-color);color:var(--el-text-color-regular)}
.dictionary-tree-add:hover,.dictionary-sample :deep(.tree-list-template__toggle:hover){color:var(--el-color-primary);border-color:var(--el-color-primary-light-5);background:var(--el-color-primary-light-9)}
@media(max-width:600px){.dictionary-tree-add,.dictionary-sample :deep(.tree-list-template__toggle){width:44px;height:44px;flex-basis:44px}}

/* 摘要操作采用图标和文字共同表达语义；分隔线只作装饰，保留原确认及权限契约。 */
.type-actions{gap:16px;flex-shrink:0}
.type-actions :deep(.el-button){gap:6px;margin:0;font-size:14px;font-weight:500}
.type-actions :deep(.el-button + .el-button){padding-left:16px;border-left:1px solid var(--el-border-color-light);border-radius:0}
@media(max-width:600px){.type-actions{gap:12px}.management-filter-header{align-items:flex-start}}

/* 截图反馈：分类栏 220px（模板支持的最小宽度），横幅与摘要减少垂直占用，不固定高度裁切文案。 */
.sample-heading{padding:14px 20px;gap:14px}
.sample-banner-icon{width:44px;height:44px;border-radius:var(--app-card-radius, 8px)}
.sample-banner-icon :deep(svg){width:26px;height:26px}
.sample-heading h1{font-size:20px;line-height:26px}
.sample-heading p{margin-top:4px;font-size:13px;line-height:20px}
.sample-heading::after{width:40px;height:40px;border-width:10px}
.sample-summary{padding:10px 14px;margin-bottom:var(--app-section-gap,12px)}
.sample-summary .management-filter-header{margin-bottom:0;min-height:24px}
.sample-summary .sample-description{margin:4px 0 0;line-height:20px}
.dictionary-sample :deep(.tree-list-template__tree-header){padding:6px 8px;min-height:44px}
.dictionary-sample :deep(.tree-list-template__tree){padding:6px}
.dictionary-sample :deep(.el-menu-item){padding:0 10px;gap:8px;font-size:13px}
.dictionary-sample :deep(.tree-heading){font-size:14px}
@media(max-width:600px){.sample-heading{padding:12px 14px;gap:10px}.sample-heading h1{font-size:18px}.sample-banner-icon{width:40px;height:40px}.sample-summary{padding:10px 12px}.sample-summary .management-filter-header{gap:6px}}

/* 区块留白全部遵循公共间距，覆盖样板早期局部 padding，避免重复叠加。 */
.dictionary-sample :deep(.tree-list-template__layout){padding:var(--app-section-gap,12px)}
.dictionary-sample :deep(.tree-list-template__search){padding:0 0 var(--app-section-gap,12px) var(--app-section-gap,12px)}
.dictionary-sample :deep(.tree-list-template__data){padding:0 0 0 var(--app-section-gap,12px)}
</style>
