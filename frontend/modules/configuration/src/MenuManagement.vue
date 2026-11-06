<script setup lang="ts">
import {computed,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage} from "element-plus";
import {AppIcon} from "@appnexus/icons";
import {ManagedDialog,ManagementTable,TreeListTemplate,type ManagementColumn} from "@appnexus/ui";
import type {MenuInput,MenuManagementApi,MenuNode,MenuType} from "./index";

/** 菜单页只组合公共树表模板和 Element Plus；树编辑不需要新增自定义交互组件。 */
const props=defineProps<{api:MenuManagementApi;errorText:(error:unknown)=>string}>();
const {t,locale}=useI18n();
const items=ref<MenuNode[]>([]),loading=ref(false),saving=ref(false),error=ref(""),query=ref(""),submittedQuery=ref(""),selected=ref("");
const treeCollapsed=ref(false),editingId=ref("");
const dialog=ref<"create"|"edit"|"">(""),formRef=ref<{validate:()=>Promise<boolean>;clearValidate:()=>void}>();
const form=reactive<MenuInput>({parentId:null,type:"MENU",code:"",titleZh:"",titleEn:"",path:"",component:"",icon:"",permission:"",sortOrder:0,visible:true,enabled:true});
const editing=computed(()=>items.value.find(item=>item.id===editingId.value));
const title=(item:MenuNode)=>locale.value.startsWith("zh")?item.titleZh:item.titleEn;
const children=(parent:string|null)=>items.value.filter(item=>(item.parentId??null)===parent).sort((a,b)=>a.sortOrder-b.sortOrder||a.code.localeCompare(b.code));
const filtered=computed(()=>{
 const word=submittedQuery.value.trim().toLocaleLowerCase();
 const scope=selected.value?descendants(selected.value).add(selected.value):null;
 return items.value.filter(item=>(!scope||scope.has(item.id))&&(!word||[item.code,item.titleZh,item.titleEn,item.path,item.permission].some(value=>value?.toLocaleLowerCase().includes(word))));
});
const tree=computed(()=>build(null));
const columns=computed<ManagementColumn[]>(()=>[
 {key:"name",label:t("menuManagement.name"),minWidth:180},
 {key:"code",prop:"code",label:t("menuManagement.code"),minWidth:150},
 {key:"type",label:t("menuManagement.type"),width:110},
 {key:"path",prop:"path",label:t("menuManagement.path"),minWidth:150},
 {key:"status",label:t("menuManagement.status"),width:110},
 {key:"operations",label:t("tenant.operation"),width:220,fixed:"right"}
]);
/** Element Plus Tree 需要嵌套数据；服务端使用平面清单以避免深度绑定和递归响应体风险。 */
function build(parent:string|null):Array<MenuNode&{children:Array<MenuNode&{children:unknown[]}>}>{return children(parent).map(item=>({...item,children:build(item.id)}));}
function descendants(id:string):Set<string>{const result=new Set<string>();const visit=(parent:string)=>children(parent).forEach(item=>{result.add(item.id);visit(item.id);});visit(id);return result;}
const parentOptions=computed(()=>{const excluded=editing.value?descendants(editing.value.id):new Set<string>();if(editing.value)excluded.add(editing.value.id);return items.value.filter(item=>item.type!=="BUTTON"&&!excluded.has(item.id));});
async function load(){loading.value=true;error.value="";try{items.value=await props.api.list();if(selected.value&&!items.value.some(item=>item.id===selected.value))selected.value="";}catch(cause){error.value=props.errorText(cause);}finally{loading.value=false;}}
/** 查询条件显式提交；重置同时恢复当前树范围内的完整列表。 */
function search(reset=false){if(reset)query.value="";submittedQuery.value=query.value;}
function reset(parentId:string|null=null){Object.assign(form,{parentId,type:"MENU" as MenuType,code:"",titleZh:"",titleEn:"",path:"",component:"",icon:"",permission:"",sortOrder:0,visible:true,enabled:true});}
function openCreate(parentId:string|null=null){editingId.value="";reset(parentId);dialog.value="create";}
function openEdit(item:MenuNode){editingId.value=item.id;Object.assign(form,{parentId:item.parentId??null,type:item.type,code:item.code,titleZh:item.titleZh,titleEn:item.titleEn,path:item.path??"",component:item.component??"",icon:item.icon??"",permission:item.permission??"",sortOrder:item.sortOrder,visible:item.visible,enabled:item.enabled});dialog.value="edit";}
/** 切换为按钮时清除路由字段，避免隐藏的旧值触发服务端约束。 */
function changeType(type:MenuType){form.type=type;if(type==="BUTTON"){form.path="";form.component="";}else form.permission="";}
async function save(){if(!await formRef.value?.validate().catch(()=>false))return;saving.value=true;error.value="";try{const input={...form,parentId:form.parentId||null};if(dialog.value==="edit"&&editing.value)await props.api.update(editing.value.id,editing.value.version,input);else await props.api.create(input);dialog.value="";ElMessage.success(t("menuManagement.saved"));await load();}catch(cause){error.value=props.errorText(cause);}finally{saving.value=false;}}
async function remove(item:MenuNode){saving.value=true;error.value="";try{await props.api.remove(item.id,item.version);ElMessage.success(t("menuManagement.deleted"));await load();}catch(cause){error.value=props.errorText(cause);}finally{saving.value=false;}}
const rules={code:[{required:true,pattern:/^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$/,message:t("menuManagement.invalidCode"),trigger:"blur"}],titleZh:[{required:true,whitespace:true,max:100,message:t("menuManagement.invalidTitle"),trigger:"blur"}],titleEn:[{required:true,whitespace:true,max:100,message:t("menuManagement.invalidTitle"),trigger:"blur"}]};
onMounted(load);
</script>
<template>
 <div class="management-page menu-management">
  <TreeListTemplate v-model:collapsed="treeCollapsed" :title="t('menuManagement.title')">
  <template #treeHeader><div class="menu-tree-heading"><strong>{{t('menuManagement.tree')}}</strong><el-tooltip :content="t('menuManagement.createRoot')"><el-button text :aria-label="t('menuManagement.createRoot')" :disabled="saving" @click="openCreate()"><AppIcon name="add" :size="18" /></el-button></el-tooltip></div></template>
  <template #tree>
   <el-button text class="menu-tree-all" :class="{'is-active':!selected}" @click="selected=''">{{t('menuManagement.all')}}</el-button>
   <el-tree v-if="tree.length" :data="tree" node-key="id" default-expand-all highlight-current :current-node-key="selected||undefined" :expand-on-click-node="false" @node-click="node=>selected=node.id"><template #default="{data}"><span class="menu-tree-node"><span class="menu-tree-node-label">{{title(data)}}</span><small>{{t('menuManagement.types.'+data.type)}}</small></span></template></el-tree>
   <el-empty v-else :description="t('menuManagement.empty')" :image-size="56" />
  </template>
  <template v-if="error&&!dialog" #feedback><el-alert type="error" :title="error" :closable="false" show-icon><el-button link type="primary" :disabled="loading||saving" @click="load">{{t('account.retry')}}</el-button></el-alert></template>
  <template #search><div class="management-filter"><div class="management-filter-header"><h2 class="management-filter-title">{{t('menuManagement.query')}}</h2></div><el-form inline @submit.prevent="search()"><el-form-item :label="t('menuManagement.name')"><el-input v-model="query" clearable :maxlength="100" :placeholder="t('menuManagement.queryPlaceholder')" /></el-form-item><div class="management-filter-actions"><el-button type="primary" native-type="submit" :disabled="loading||saving"><AppIcon name="search" :size="16" class="mr-1" />{{t('administration.search')}}</el-button><el-button :disabled="loading||saving" @click="search(true)"><AppIcon name="refresh" :size="16" class="mr-1" />{{t('management.reset')}}</el-button></div></el-form></div></template>
  <template #table><ManagementTable embedded :title="t('menuManagement.list')" :total="filtered.length" :columns="columns" :rows="filtered" :loading="loading" :busy="saving" :empty-text="t('menuManagement.empty')" @refresh="load">
   <template #actions><el-button type="primary" :disabled="loading||saving" @click="openCreate()"><AppIcon name="add" :size="16" class="mr-1" />{{t('menuManagement.createRoot')}}</el-button></template>
   <template #name="{row}">{{title(row)}}</template><template #type="{row}"><el-tag type="info">{{t('menuManagement.types.'+row.type)}}</el-tag></template><template #path="{row}">{{row.path||'—'}}</template><template #status="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'administration.enabled':'administration.disabled')}}</el-tag></template><template #operations="{row}"><el-button link type="primary" :disabled="saving" @click="openCreate(row.id)">{{t('menuManagement.addChild')}}</el-button><el-button link type="primary" :disabled="saving" @click="openEdit(row)">{{t('menuManagement.edit')}}</el-button><el-popconfirm :title="t('menuManagement.confirmDelete',{name:title(row)})" :confirm-button-text="t('menuManagement.delete')" :cancel-button-text="t('organization.cancel')" @confirm="remove(row)"><template #reference><el-button link type="danger" :disabled="saving">{{t('menuManagement.delete')}}</el-button></template></el-popconfirm></template>
  </ManagementTable></template>
  </TreeListTemplate>
  <ManagedDialog :model-value="Boolean(dialog)" :title="t(dialog==='edit'?'menuManagement.edit':'menuManagement.create')" width="min(680px, calc(100vw - 32px))" :busy="saving" @update:model-value="value=>{if(!value)dialog=''}">
  <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
  <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
   <div class="menu-form-grid"><el-form-item :label="t('menuManagement.parent')"><el-select v-model="form.parentId" clearable :disabled="saving"><el-option v-for="item in parentOptions" :key="item.id" :label="title(item)+' ('+item.code+')'" :value="item.id" /></el-select></el-form-item><el-form-item :label="t('menuManagement.type')" required><el-select :model-value="form.type" :disabled="saving" @update:model-value="changeType"><el-option v-for="type in ['DIRECTORY','MENU','BUTTON']" :key="type" :label="t('menuManagement.types.'+type)" :value="type" /></el-select></el-form-item><el-form-item prop="code" :label="t('menuManagement.code')"><el-input v-model="form.code" :maxlength="100" :disabled="saving" /></el-form-item><el-form-item prop="sortOrder" :label="t('menuManagement.sortOrder')"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" :disabled="saving" /></el-form-item><el-form-item prop="titleZh" :label="t('menuManagement.titleZh')"><el-input v-model="form.titleZh" :maxlength="100" :disabled="saving" /></el-form-item><el-form-item prop="titleEn" :label="t('menuManagement.titleEn')"><el-input v-model="form.titleEn" :maxlength="100" :disabled="saving" /></el-form-item><el-form-item v-if="form.type!=='BUTTON'" :label="t('menuManagement.path')"><el-input v-model="form.path" :maxlength="200" :disabled="saving" /></el-form-item><el-form-item v-if="form.type==='MENU'" :label="t('menuManagement.component')"><el-input v-model="form.component" :maxlength="200" :disabled="saving" /></el-form-item><el-form-item v-if="form.type==='BUTTON'" :label="t('menuManagement.permission')" required><el-input v-model="form.permission" :maxlength="100" :disabled="saving" /></el-form-item><el-form-item :label="t('menuManagement.icon')"><el-input v-model="form.icon" :maxlength="100" :disabled="saving" placeholder="ri/menu-line" /></el-form-item></div>
   <div class="menu-switches"><el-form-item :label="t('menuManagement.visible')"><el-switch v-model="form.visible" :disabled="saving" /></el-form-item><el-form-item :label="t('menuManagement.enabled')"><el-switch v-model="form.enabled" :disabled="saving" /></el-form-item></div><el-alert :title="t('menuManagement.securityHint')" type="info" :closable="false" show-icon class="mb-4" /><div class="flex justify-end gap-2"><el-button :disabled="saving" @click="dialog=''">{{t('organization.cancel')}}</el-button><el-button type="primary" native-type="submit" :loading="saving">{{t('organization.save')}}</el-button></div>
  </el-form>
  </ManagedDialog>
 </div>
</template>
<style scoped>
.menu-tree-heading,.menu-tree-node,.menu-switches{display:flex;align-items:center}.menu-tree-heading{justify-content:space-between;gap:8px}.menu-tree-heading strong{font-size:var(--el-font-size-medium)}.menu-tree-all{justify-content:flex-start;width:100%;height:36px;margin-bottom:4px;padding:0 12px}.menu-tree-all.is-active{color:var(--el-color-primary);background:var(--el-color-primary-light-9)}.menu-tree-node{justify-content:space-between;gap:8px;width:100%;min-width:0}.menu-tree-node-label{overflow:hidden;text-overflow:ellipsis}.menu-tree-node small{flex:none;color:var(--el-text-color-secondary)}:deep(.el-tree-node__content){height:36px;border-radius:var(--el-border-radius-base)}:deep(.el-tree-node__content:hover){background:var(--el-fill-color-light)}:deep(.el-tree-node.is-current>.el-tree-node__content){color:var(--el-color-primary);background:var(--el-color-primary-light-9)}.menu-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 16px}.menu-switches{gap:32px}@media(max-width:600px){.menu-form-grid{grid-template-columns:minmax(0,1fr)}:deep(.el-button){min-height:44px}}
</style>
