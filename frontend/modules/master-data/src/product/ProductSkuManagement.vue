<script setup lang="ts">
import {computed,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,ElMessageBox,type FormInstance} from "element-plus";
import {BusinessWorkspace,TabbedDetailTemplate,ManagementTable,ManagedDialog,AppPagination,FloatingError,InfoHint,RecordAuditDetails,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import ProductStructure from "./ProductStructure.vue";
import type {ProductApi,ProductConfiguration,ProductRecord,ProductRow} from "./product";
/** 商品明细工作台：按商品固定配置投影动态属性，复用既有SKU身份与聚合命令，不遍历商品伪造全局分页。 */
const props=defineProps<{api:ProductApi;errorText:(e:unknown)=>string}>();
const {t}=useI18n();
const products=ref<ProductRow[]>([]),productTotal=ref(0),productPage=ref(1),keyword=ref(""),selected=ref("");
const product=ref<ProductRecord>(),configuration=ref<ProductConfiguration>(),structure=ref<Awaited<ReturnType<ProductApi['structure']>>>();
const loading=ref(false),searching=ref(false),busy=ref(false),error=ref(""),operations=ref<string[]>([]),code=ref(""),specification=ref(""),status=ref(""),attributeId=ref(""),attributeValue=ref("");
const page=ref(1),size=ref(20),createOpen=ref(false),structureOpen=ref(false),viewOpen=ref(false),detail=ref<Record<string,any>>(),form=ref<FormInstance>();
const draft=reactive({code:"",specificationId:""});
const applied=reactive({code:"",specification:"",status:"",attributeId:"",attributeValue:""});
/** 查询按钮提交本地筛选；数据范围始终限定在服务端已授权返回的当前商品。 */
function search(){Object.assign(applied,{code:code.value,specification:specification.value,status:status.value,attributeId:attributeId.value,attributeValue:attributeValue.value});page.value=1;}
let selectionRequest=0,searchRequest=0;
const specFields=computed(()=>configuration.value?.fields.filter(f=>f.purpose==='SPEC')??[]);
const writeAllowed=computed(()=>!!product.value&&product.value.status!=='ENABLED'&&!product.value.approvalPending&&!product.value.archivedAt&&product.value.structureState==='COMPLETE');
const specs=computed(()=>new Map((structure.value?.specifications??[]).map(s=>[s.id,s])));
/** 可分配候选排除已占用SKU及退出/停用规格；服务端仍以原事务复核，客户端列表不是授权依据。 */
const available=computed(()=>structure.value?.specifications.filter(s=>s.enabled&&!s.retiredAt&&!structure.value?.skus.some(k=>k.specificationId===s.id))??[]);
const allRows=computed<Record<string,any>[]>(()=>(structure.value?.skus??[]).map(k=>({...k,specification:specs.value.get(k.specificationId)})));
const filtered=computed(()=>allRows.value.filter(k=>String(k.code).toLowerCase().includes(applied.code.toLowerCase())&&String(k.specification?.name??'').includes(applied.specification)&&(!applied.status||String(k.enabled)===applied.status)&&(!applied.attributeId||!applied.attributeValue||String(attribute(k,applied.attributeId)).includes(applied.attributeValue))));
const rows=computed(()=>filtered.value.slice((page.value-1)*size.value,page.value*size.value));
/** 属性列从实际规格保存的修订生成，退出规格沿用历史标签，不拿新模板重解释历史。 */
const attributes=computed(()=>{
 const fields=new Map<string,{id:string;name:string;unitCode?:string}>();
 for(const f of configuration.value?.fields??[])if(f.purpose!=='SPEC')fields.set('product:'+f.templateItemId,{id:'product:'+f.templateItemId,name:f.name,unitCode:f.unitCode??undefined});
 for(const row of allRows.value)for(const v of row.specification?.values??[])fields.set(v.revisionId??v.templateItemId,{id:v.revisionId??v.templateItemId,name:v.name,unitCode:v.unitCode});
 return [...fields.values()];
});
const columns=computed<ManagementColumn[]>(()=>[
 {key:'code',prop:'code',label:t('productSku.code'),minWidth:170},
 {key:'specification',label:t('productStructure.specification'),minWidth:170},
 ...attributes.value.map(f=>({key:'attr_'+f.id,label:f.name+(f.unitCode?' ('+f.unitCode+')':''),minWidth:140})),
 {key:'status',label:t('masterData.status'),width:110},{key:'effective',label:t('productStructure.effective'),width:110},
 {key:'operations',label:t('masterData.actions'),width:180}
]);
/** false与零不作为空值处理；枚举标签来自规格快照，不根据当前选项重新解释。 */
function attribute(row:Record<string,any>,id:string){
 if(id.startsWith('product:')){
  const item=id.slice(8),v=product.value?.values.find(v=>v.templateItemId===item);
  const labels=product.value?.choices.filter(c=>c.templateItemId===item).map(c=>c.label).join(' / ');
  if(v?.dataType==='BOOLEAN')return t(v.booleanValue?'productV1.yes':'productV1.no');
  return v?.textValue??v?.dateValue??v?.integerValue??v?.decimalValue??(labels||'—');
 }
 const v=row.specification?.values?.find((v:Record<string,any>)=>(v.revisionId??v.templateItemId)===id);if(!v)return '—';if(v.dataType==='BOOLEAN')return t(v.booleanValue?'productV1.yes':'productV1.no');return v.textValue??v.dateValue??v.integerValue??v.decimalValue??(v.choices?.map((c:Record<string,any>)=>c.label).join(' / ')||'—');
}
async function findProducts(query=keyword.value,p=1){const request=++searchRequest;keyword.value=query;productPage.value=p;searching.value=true;try{const r=await props.api.list({name:query,code:'',archived:false,page:p,size:20});if(request!==searchRequest)return;products.value=r.rows;productTotal.value=r.total;}catch(e){if(request!==searchRequest)return;products.value=[];productTotal.value=0;await setErrorFeedback(error,props.errorText(e));}finally{if(request===searchRequest)searching.value=false;}}
/** 选择变化立即清除旧商品数据，并丢弃迟到响应，避免把另一商品的父版本用于本次写入。 */
async function selectProduct(id:string){selected.value=id;const request=++selectionRequest;product.value=undefined;configuration.value=undefined;structure.value=undefined;createOpen.value=false;structureOpen.value=false;detail.value=undefined;viewOpen.value=false;reset();loading.value=false;if(!id)return;loading.value=true;
 try{const [p,s]=await Promise.all([props.api.read(id),props.api.structure(id)]);const config=p.configId?await props.api.configuration(p.configId):undefined;if(request!==selectionRequest)return;product.value=p;structure.value=s;configuration.value=config;}catch(e){if(request===selectionRequest)await setErrorFeedback(error,props.errorText(e));}finally{if(request===selectionRequest)loading.value=false;}}
function reset(){code.value='';specification.value='';status.value='';attributeId.value='';attributeValue.value='';search();}
async function refresh(){if(selected.value)await selectProduct(selected.value);}
function openCreate(){draft.code='';draft.specificationId='';createOpen.value=true;}
/** 一个SKU命令只绑定已有规格；新增规格与分配货号分开提交，失败不制造已成功的组合反馈。 */
async function create(){if(busy.value||!product.value||!await form.value?.validate().catch(()=>false))return;busy.value=true;try{await props.api.createSku(product.value.id,{...draft,version:structure.value!.version});createOpen.value=false;await refresh();ElMessage.success(t('productSku.created'));}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
async function toggle(row:Record<string,any>){try{await ElMessageBox.confirm(t('productStructure.stateHint'),t('productSku.title'),{confirmButtonText:t(row.enabled?'masterData.disable':'masterData.enable'),cancelButtonText:t('masterData.cancel')});}catch{return;}busy.value=true;try{await props.api.structureState(selected.value,'sku',row.id,{enabled:!row.enabled,rowVersion:row.version,version:structure.value!.version});await refresh();ElMessage.success(t('productSku.saved'));}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
async function structureChanged(){const id=selected.value;const request=selectionRequest;try{const [p,s]=await Promise.all([props.api.read(id),props.api.structure(id)]);if(request!==selectionRequest)return;product.value=p;structure.value=s;}catch(e){await setErrorFeedback(error,props.errorText(e));}}
onMounted(async()=>{try{operations.value=await props.api.operations();if(operations.value.includes('Product.list'))await findProducts('');}catch(e){await setErrorFeedback(error,props.errorText(e));}});
</script>
<template>
<!-- 单根宿主承接应用壳的 main-content；边距、工作区边框和区块间距统一由全局模板维护。 -->
<div class="management-page">
<BusinessWorkspace :title="t('productSku.title')">
 <template #feedback><FloatingError :title="error"><el-button :disabled="loading||busy||searching" @click="selected?refresh():findProducts()">{{t('masterData.retry')}}</el-button></FloatingError></template>
<template #search><el-form inline class="management-filter" @submit.prevent="search">
  <el-form-item :label="t('productSku.product')"><el-select v-model="selected" filterable remote clearable :remote-method="q=>findProducts(q)" :loading="searching" :disabled="busy" :placeholder="t('productSku.choose')" class="sku-product-select" @change="selectProduct"><el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.code+' / '+p.name"/><template #footer><AppPagination :current-page="productPage" :page-size="20" :total="productTotal" @current-change="p=>findProducts(keyword,p)"/></template></el-select></el-form-item>
  <el-form-item :label="t('productSku.code')"><el-input v-model="code" clearable @input="page=1"/></el-form-item>
  <el-form-item :label="t('productStructure.specification')"><el-input v-model="specification" clearable @input="page=1"/></el-form-item>
  <el-form-item :label="t('masterData.status')"><el-select v-model="status" clearable class="sku-status-select" @change="page=1"><el-option value="true" :label="t('masterData.enabled')"/><el-option value="false" :label="t('masterData.disabled')"/></el-select></el-form-item>
  <el-form-item :label="t('productSku.attribute')"><el-select v-model="attributeId" clearable class="sku-status-select" @change="attributeValue='';page=1"><el-option v-for="f in attributes" :key="f.id" :value="f.id" :label="f.name"/></el-select></el-form-item><el-form-item :label="t('productSku.attributeValue')"><el-input v-model="attributeValue" :disabled="!attributeId" clearable @input="page=1"/></el-form-item>
  <el-form-item class="management-filter-actions"><el-button v-operation="'Product.readStructure'" type="primary" native-type="submit" :disabled="!product||loading||busy">{{t('masterData.search')}}</el-button><el-button :disabled="loading||busy" @click="reset">{{t('masterData.reset')}}</el-button></el-form-item>
 </el-form></template>
 <template #data>
  <ManagementTable embedded :total="filtered.length" :empty-text="t(!selected?'productSku.choose':structure?.mode==='NONE'?'productSku.none':'productSku.empty')" :title="product?.name??t('productSku.title')" :columns="columns" :rows="rows" :loading="loading||busy" @refresh="refresh">
   <template #titleHelp><InfoHint :content="t('productSku.hint')"/></template>
   <template #actions><el-button v-operation="'Product.createSku'" type="primary" :disabled="!writeAllowed||!available.length||busy||loading" @click="openCreate">{{t('productSku.create')}}</el-button><el-button v-operation="'Product.readStructure'" :disabled="!product||busy||loading" @click="structureOpen=true">{{t('productSku.specifications')}}</el-button></template>
   <template #specification="{row}">{{row.specification?.name??'—'}}</template>
   <template v-for="field in attributes" :key="field.id" #[`attr_${field.id}`]="{row}">{{attribute(row,field.id)}}</template>
   <template #status="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag></template>
   <template #effective="{row}">{{t(row.effective?'productV1.yes':'productV1.no')}}</template>
   <template #operations="{row}"><el-button v-operation="'Product.readStructure'" link type="primary" @click="detail=row;viewOpen=true">{{t('partner.view')}}</el-button><el-button v-operation="'Product.setStructureState'" link :disabled="!writeAllowed||busy||!!row.specification?.retiredAt" @click="toggle(row)">{{t(row.enabled?'masterData.disable':'masterData.enable')}}</el-button></template>
   <template #pagination><AppPagination :current-page="page" :page-size="size" :total="filtered.length" @current-change="p=>page=p" @size-change="s=>{size=s;page=1}"/></template>
  </ManagementTable>
 </template>
</BusinessWorkspace>
<ManagedDialog v-model="createOpen" :title="t('productSku.create')" :busy="busy"><el-form ref="form" :model="draft" label-position="top" class="business-border-label-form" @submit.prevent="create"><el-form-item :label="t('productSku.code')" prop="code" :rules="[{required:true,message:t('masterData.required')},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t('productSku.codeHint')}]"><el-input v-model="draft.code" maxlength="64"/></el-form-item><el-form-item :label="t('productStructure.specification')" prop="specificationId" :rules="[{required:true,message:t('masterData.required')}]"><el-select v-model="draft.specificationId" filterable><el-option v-for="s in available" :key="s.id" :value="s.id" :label="s.name"/></el-select></el-form-item></el-form><template #footer><el-button :disabled="busy" @click="createOpen=false">{{t('masterData.cancel')}}</el-button><el-button v-operation="'Product.createSku'" type="primary" :loading="busy" @click="create">{{t('masterData.save')}}</el-button></template></ManagedDialog>
<ManagedDialog v-model="structureOpen" :title="t('productSku.specifications')"><ProductStructure v-if="structureOpen&&product" :key="product.id+':'+structure?.version" :id="product.id" :version="structure?.version??product.version" :readonly="!writeAllowed" :api="api" :fields="specFields" :error-text="errorText" @changed="structureChanged"/></ManagedDialog>
<ManagedDialog v-model="viewOpen" :title="t('productSku.detail')">
 <TabbedDetailTemplate master-data>
  <template #content><el-card shadow="never" class="detail-section"><div class="detail-field-grid">
   <div v-for="field in [{label:t('productSku.code'),value:detail?.code},{label:t('productSku.product'),value:product?.name},{label:t('productV1.origin'),value:product?.origin},{label:t('productStructure.specification'),value:detail?.specification?.name},{label:t('masterData.status'),value:t(detail?.enabled?'masterData.enabled':'masterData.disabled')},{label:t('productStructure.effective'),value:t(detail?.effective?'productV1.yes':'productV1.no')}]" :key="field.label" class="detail-readonly-field"><span>{{field.label}}</span><strong>{{field.value??'—'}}</strong></div>
   <div v-for="f in attributes" :key="f.id" class="detail-readonly-field"><span>{{f.name+(f.unitCode?' ('+f.unitCode+')':'')}}</span><strong>{{detail?attribute(detail,f.id):'—'}}</strong></div>
   <div class="detail-readonly-field detail-field-wide"><span>{{t('productV1.description')}}</span><strong>{{detail?.specification?.description||'—'}}</strong></div>
  </div></el-card></template>
  <template #system><h2 class="detail-section-title">{{t('partner.system')}}</h2><RecordAuditDetails v-if="detail?.audit" :audit="detail.audit" inline/></template>
 </TabbedDetailTemplate>
</ManagedDialog>
</div>
</template>
<style scoped>
.sku-product-select{width:360px;max-width:100%}.sku-status-select{width:140px}
@media(max-width:600px){.sku-product-select{width:100%}}
</style>
