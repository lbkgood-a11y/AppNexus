<script setup lang="ts">
import {VxeTable,VxeColumn} from "@appnexus/ui";
import {computed,onMounted,reactive,ref,watch} from "vue";
import {ElMessageBox} from "element-plus";
import {useRoute} from "vue-router";
import {useI18n} from "vue-i18n";
import {BusinessWorkspace,ManagementTable,AppPagination,ManagedDialog,FloatingError,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import ProductEditor from "./ProductEditor.vue";
import type {ProductApi,ProductRow,ProductQuery,ProductConfiguration,ProductOption} from "./product";
/** 商品档案正式工作区；唯一Product操作与固定版本数据，部署启动前由迁移强制对账。 */
const props=defineProps<{api:ProductApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();const route=useRoute();const approvalView=ref<{state:string;productVersion:number;snapshot:Record<string,any>}>();
const rows=ref<ProductRow[]>([]),total=ref(0),loading=ref(false),failed=ref(false),error=ref(""),operations=ref<string[]>([]);
const filterConfiguration=ref<ProductConfiguration>(),filterCategories=ref<ProductOption[]>([]),filterCategory=ref<string>(),filterConditions=ref<Array<{templateItemId:string;operator:string;values:string[]}>>([]);
const editor=ref(false),id=ref(""),editorMode=ref<"view"|"edit">("view");
const legacyOpen=ref(false),legacyRows=ref<Array<{id:string;code:string;result:string}>>([]),legacyPage=ref(1),legacyTotal=ref(0),staging=ref(false);
const query=reactive<ProductQuery>({name:"",code:"",form:undefined,status:undefined,archived:false,page:1,size:20});
const columns=computed<ManagementColumn[]>(()=>[{key:"code",prop:"code",label:t("masterData.code"),minWidth:140},{key:"name",prop:"name",label:t("masterData.name"),minWidth:180},{key:"form",label:t("productV1.productForm"),width:120},{key:"status",label:t("masterData.status"),width:140},{key:"structure",label:t("productV1.structure"),width:160},{key:"operations",label:t("masterData.actions"),width:200}]);
async function load(){loading.value=true;failed.value=false;try{operations.value=await props.api.operations();if(!operations.value.includes("Product.list")){rows.value=[];total.value=0;return;}const result=await props.api.list(query);rows.value=result.rows;total.value=result.total;}catch(e){failed.value=true;if((e as {response?:{status:number}}).response?.status===403){rows.value=[];operations.value=[];}await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
/** 重置全部筛选与动态配置范围，回到第一页，只发起读取。 */
function reset(){Object.assign(query,{name:"",code:"",form:undefined,status:undefined,archived:false,categoryId:undefined,configId:undefined,attributeFilters:undefined});filterCategory.value=undefined;filterConfiguration.value=undefined;filterConditions.value=[];search();}
function search(){if(filterConditions.value.some(c=>!c.templateItemId||c.values.some(v=>!v))){void setErrorFeedback(error,t("productV1.completeFilter"));return;}query.page=1;query.attributeFilters=filterConditions.value.length?JSON.stringify(filterConditions.value):undefined;void load();}
async function findFilterCategory(keyword:string){try{filterCategories.value=(await props.api.options("category",keyword,1)).rows;}catch(e){await setErrorFeedback(error,props.errorText(e));}}
async function selectFilterCategory(id:string|undefined){
 try{const category=filterCategories.value.find(r=>r.id===id);if(id&&!category){await setErrorFeedback(error,t("productV1.optionChanged"));return;}filterConfiguration.value=category?.configId?await props.api.configuration(category.configId):undefined;query.configId=filterConfiguration.value?.id;query.categoryId=id;filterConditions.value=[];query.attributeFilters=undefined;search();}catch(e){await setErrorFeedback(error,props.errorText(e));}
}
function searchableFields(){return (filterConfiguration.value?.fields??[]).filter(f=>f.searchable&&f.dataType!=="TEXT");}
function selectedField(item:string){return searchableFields().find(f=>f.templateItemId===item);}
function addCondition(){if(filterConditions.value.length<3)filterConditions.value.push({templateItemId:"",operator:"eq",values:[""]});}
function operators(item:string){const type=selectedField(item)?.dataType;if(type==="SINGLE_SELECT"||type==="MULTI_SELECT")return ["eq","anyOf"];if(type==="BOOLEAN")return ["eq"];if(type==="DATE")return ["eq","between"];return ["eq","gte","lte","between"];}
/** 查看和编辑使用不同准入模式；查看即使具有更新权限也不展示写入控件。 */
function open(row?:ProductRow,mode:"view"|"edit"="edit"){id.value=row?.id??"";editorMode.value=mode;editor.value=true;}
function close(){editor.value=false;void load();}
/** 对账只读；暂存一条仍保留源正式入口，变化项禁止覆盖。 */
async function reconcile(){loading.value=true;try{const data=await props.api.reconcileLegacy(legacyPage.value,20);legacyRows.value=data.rows;legacyTotal.value=data.total;legacyOpen.value=true;}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
async function stage(row:{id:string;code:string}){
 try{await ElMessageBox.confirm(t("productV1.stageWarning",{code:row.code}),t("productV1.legacyReconciliation"));}catch{return;}
 staging.value=true;try{await props.api.stageLegacy(row.id);await reconcile();await load();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{staging.value=false;}
}
/** 工作流跳转可能复用已打开商品页；按路由参数重读任务快照，不保留上一申请正文。 */
async function loadRoute(){approvalView.value=undefined;editor.value=false;if(typeof route.query.objectId==='string'){try{approvalView.value=await props.api.approvalSnapshot(route.query.objectId,typeof route.query.taskId==='string'?route.query.taskId:undefined);}catch(e){await setErrorFeedback(error,props.errorText(e));}}else await load();}
watch(()=>[route.query.objectId,route.query.taskId],loadRoute);
onMounted(loadRoute);
</script>
<template>
<div class="product-page"><section v-if="approvalView"><h1>{{t('productApproval.title')}} · {{approvalView.snapshot.name}}</h1><el-descriptions :column="1" border><el-descriptions-item v-for="key in ['code','name','productForm','specificationMode','brand','origin','description']" :key="key" :label="t(['code','name'].includes(key)?'masterData.'+key:key==='specificationMode'?'productStructure.mode':'productV1.'+key)">{{key==='productForm'?t('productV1.'+approvalView.snapshot[key]):key==='specificationMode'?t('productStructure.'+approvalView.snapshot[key]):approvalView.snapshot[key]||'—'}}</el-descriptions-item></el-descriptions><el-descriptions :column="1" border><el-descriptions-item v-for="field in approvalView.snapshot.values??[]" :key="field.templateItemId" :label="field.attributeName">{{field.textValue??field.integerValue??field.decimalValue??field.booleanValue??field.dateValue??((approvalView.snapshot.choices??[]).filter((v:any)=>v.templateItemId===field.templateItemId).map((v:any)=>v.label).join(' / ')||'—')}}</el-descriptions-item></el-descriptions><VxeTable :data="approvalView.snapshot.specifications??[]" border><VxeColumn field="name" :title="t('productStructure.specification')"/><VxeColumn :title="t('productStructure.mode')"><template #default="{row}">{{t('productStructure.'+row.mode)}}</template></VxeColumn></VxeTable><VxeTable :data="approvalView.snapshot.components??[]" border><VxeColumn field="componentCode" :title="t('masterData.code')"/><VxeColumn field="componentName" :title="t('masterData.name')"/><VxeColumn field="quantity" :title="t('productConversion.quantity')"/></VxeTable></section><ProductEditor v-else-if="editor" :id="id" :mode="editorMode" :api="api" :error-text="errorText" @close="close"/>
<BusinessWorkspace v-else :title="t('productV1.title')" :description="t('productV1.descriptionHint')">
<template #search><el-form inline class="management-filter" @submit.prevent="search"><el-form-item :label="t('masterData.name')"><el-input v-model="query.name" maxlength="200" clearable/></el-form-item><el-form-item :label="t('masterData.code')"><el-input v-model="query.code" maxlength="64" clearable/></el-form-item><el-form-item :label="t('productV1.productForm')"><el-select v-model="query.form" clearable style="min-width:140px"><el-option v-for="form in ['PHYSICAL','SERVICE','COMPOSITE']" :key="form" :value="form" :label="t('productV1.'+form)"/></el-select></el-form-item><el-form-item :label="t('masterData.status')"><el-select v-model="query.status" clearable style="min-width:140px"><el-option v-for="status in ['DRAFT','ENABLED','DISABLED']" :key="status" :value="status" :label="t('productV1.'+status)"/></el-select></el-form-item><el-form-item :label="t('productV1.archived')"><el-switch v-model="query.archived" @change="search"/></el-form-item><el-form-item :label="t('productV1.filterConfiguration')"><el-select v-model="filterCategory" clearable remote filterable :remote-method="findFilterCategory" @visible-change="v=>v&&findFilterCategory('')" @change="selectFilterCategory"><el-option v-for="r in filterCategories" :key="r.id" :value="r.id" :label="r.name" :disabled="!r.configId"/></el-select></el-form-item>
<p v-if="filterConfiguration">{{t('productV1.fixedVersion',{version:filterConfiguration.revision})}} · {{t('productV1.filterScope')}}</p>
<div v-for="(condition,index) in filterConditions" :key="index" class="attribute-filter-row">
<el-select v-model="condition.templateItemId" @change="condition.operator='eq';condition.values=['']"><el-option v-for="f in searchableFields()" :key="f.templateItemId" :value="f.templateItemId" :label="f.name"/></el-select>
<el-select v-model="condition.operator" @change="condition.values=condition.operator==='between'?['','']:['']"><el-option v-for="op in operators(condition.templateItemId)" :key="op" :value="op" :label="t('productV1.op_'+op)"/></el-select>
<el-select v-if="['SINGLE_SELECT','MULTI_SELECT'].includes(selectedField(condition.templateItemId)?.dataType??'')" :model-value="condition.operator==='anyOf'?condition.values:condition.values[0]" :multiple="condition.operator==='anyOf'" @update:model-value="v=>condition.values=Array.isArray(v)?v:[String(v)]"><el-option v-for="o in selectedField(condition.templateItemId)?.options??[]" :key="o.id" :value="o.id" :label="o.label"/></el-select>
<el-select v-else-if="selectedField(condition.templateItemId)?.dataType==='BOOLEAN'" v-model="condition.values[0]"><el-option value="true" :label="t('productV1.yes')"/><el-option value="false" :label="t('productV1.no')"/></el-select>
<template v-else><el-input v-model="condition.values[0]" :placeholder="selectedField(condition.templateItemId)?.dataType==='DATE'?'YYYY-MM-DD':t('productV1.filterValue')"/><el-input v-if="condition.operator==='between'" v-model="condition.values[1]" :placeholder="t('productV1.filterUpper')"/></template>
<el-button v-operation="'Product.list'" text @click="filterConditions.splice(index,1)">{{t('productV1.removeCondition')}}</el-button>
</div>
<el-button v-if="filterConfiguration" v-operation="'Product.list'" :disabled="filterConditions.length>=3" @click="addCondition">{{t('productV1.addCondition')}}</el-button>
<el-form-item class="management-filter-actions"><el-button v-operation="'Product.list'" type="primary" native-type="submit" :loading="loading">{{t('masterData.search')}}</el-button><el-button v-operation="'Product.list'" :disabled="loading" @click="reset">{{t('masterData.reset')}}</el-button></el-form-item></el-form></template>
<template #data><FloatingError :title="error"><el-button v-operation="'Product.list'" @click="load">{{t('masterData.retry')}}</el-button></FloatingError><el-result v-if="failed&&!rows.length" icon="error" :title="t('masterData.unavailable')"/><el-result v-else-if="!loading&&!operations.includes('Product.list')" icon="warning" :title="t('masterData.noPermission')"/>
<ManagementTable v-else :title="t('productV1.title')" :rows="rows" :columns="columns" :total="total" :loading="loading" @refresh="load">
<template #actions><el-button v-operation="'Product.create'" type="primary" :disabled="loading" @click="open()">{{t('productV1.create')}}</el-button><el-button v-operation="'Product.reconcileLegacy'" :disabled="loading||staging" @click="reconcile">{{t('productV1.legacyReconciliation')}}</el-button></template>
<template #form="{row}">{{row.productForm?t('productV1.'+row.productForm):'—'}}</template><template #status="{row}">{{t('productV1.'+row.status)}}</template><template #structure="{row}">{{t('productV1.'+row.structureState)}}</template><template #operations="{row}"><el-button v-operation="'Product.read'" link type="primary" :disabled="loading" @click="open(row,'view')">{{t('partner.view')}}</el-button><el-button v-operation="'Product.update'" link type="primary" :disabled="loading||!!row.archivedAt||row.structureState==='LEGACY_PENDING'" @click="open(row,'edit')">{{t('masterData.edit')}}</el-button></template>
<template #pagination><AppPagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :disabled="loading" @current-change="load" @size-change="search"/></template>
</ManagementTable></template>
</BusinessWorkspace>
<ManagedDialog v-model="legacyOpen" :title="t('productV1.legacyReconciliation')" :busy="staging"><p>{{t('productV1.reconcileWarning')}}</p><VxeTable border show-overflow :column-config="{resizable:true}" :data="legacyRows"><VxeColumn field="code" :title="t('masterData.code')"/><VxeColumn :title="t('masterData.status')"><template #default="{row}">{{t('productV1.reconcile_'+row.result)}}</template></VxeColumn><VxeColumn :title="t('masterData.actions')"><template #default="{row}"><el-button v-if="row.result==='MISSING'" v-operation="'Product.stageLegacy'" link :disabled="staging" @click="stage({id:row.id,code:row.code})">{{t('productV1.stageLegacy')}}</el-button></template></VxeColumn></VxeTable><AppPagination v-model:current-page="legacyPage" :page-size="20" :total="legacyTotal"  :disabled="staging" @current-change="reconcile"/></ManagedDialog></div>
</template>
<style scoped>
/* 保留公共工作区的剩余高度与内部表格滚动。 */
.product-page{display:flex;flex-direction:column;flex:1;min-height:0;min-width:0}
.attribute-filter-row{display:flex;flex-wrap:wrap;gap:8px;margin-block:8px}.attribute-filter-row>*{flex:1;min-width:140px}
</style>
