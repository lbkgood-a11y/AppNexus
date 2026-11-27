<script setup lang="ts">
import {computed,nextTick,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,ElMessageBox,type FormInstance,type LoadFunction,type TreeInstance} from "element-plus";
import ProductHistoryButton from "./ProductHistoryButton.vue";
import {TreeListTemplate,PageHeader,TabbedDetailTemplate,ManagementTable,ManagedDialog,AppPagination,FloatingError,InfoHint,RecordAuditDetails,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import ProductStructure from "./ProductStructure.vue";
import SkuAttributeFilter from "./SkuAttributeFilter.vue";
import type {ProductApi,ProductConfiguration,ProductRecord} from "./product";
import type { ProductCatalogApi } from "./catalog";
import type { SkuAttributeConfiguration } from "./product";
import ProductAttributes from "./ProductAttributes.vue";
/** 商品明细工作台：按商品固定配置投影动态属性，复用既有SKU身份与聚合命令，不遍历商品伪造全局分页。 */
const props=defineProps<{api:ProductApi;listCatalog:ProductCatalogApi["list"];catalogVersions:ProductCatalogApi["versions"];errorText:(e:unknown)=>string}>();
const {t}=useI18n();
const selected = ref("");
const tree = ref<TreeInstance>();
const treeCollapsed = ref(false);
const treeError = ref("");
const checkingOperations = ref(true);
const operationsLoaded = ref(false);
const treeGeneration = ref(0);
const rootKey = "root";
const allProductsKey = "all-products";
const canListProducts = computed(() => operations.value.includes("Product.list"));
const canListCategories = computed(() => operations.value.includes("ProductCategory.list"));
const treeTitle = computed(() => t("productSku.tree"));
const activeNode = ref(rootKey);
type SkuTreeNode =
  | { key: string; kind: "root" | "all"; name?: never }
  | { key: string; kind: "category" | "product"; id: string; name: string; leaf?: boolean };

const product=ref<ProductRecord>(),configuration=ref<ProductConfiguration>(),structure=ref<Awaited<ReturnType<ProductApi['structure']>>>();
const loadIssue = ref<{ message: string; retryable: boolean }>();
const loading=ref(false),busy=ref(false),error=ref(""),operations=ref<string[]>([]),code=ref(""),specification=ref(""),status=ref("");
const attributeFilter = ref({ attributeId: "", attributeValue: "" });
const page=ref(1),size=ref(20),createOpen=ref(false),structureOpen=ref(false),detailId=ref(""),form=ref<FormInstance>();
const draft=reactive({code:"",specificationId:""});
const ownMeasurement=ref(false);
const editMeasurementTarget=ref<{id:string;version:number;rowVersion:number;code:string;enabled:boolean}>();
const measurementLoading=ref(false);
const unitOptions=ref<Array<{id:string;code:string;name:string}>>([]);
const measurement=reactive({
 inventoryUnitId:"",
 pricingUnitId:"",
 packaging:[] as Array<{code:string;name:string;unitId:string;quantity:string}>
});
/** 从当前租户授权单位目录读取全部可选单位；失败后允许重新开启计量配置重试。 */
async function loadSkuUnits(){
 measurementLoading.value=true;
 try {
  const rows: typeof unitOptions.value=[];
  for(let page=1;;page++){
   const result=await props.api.options("unit","",page);
   rows.push(...result.rows);
   if(!result.rows.length||rows.length>=result.total)break;
  }
  unitOptions.value=rows;
 } catch(e){
  ownMeasurement.value=false;
  await setErrorFeedback(error,props.errorText(e));
 } finally {
  measurementLoading.value=false;
 }
}
const applied=reactive({code:"",specification:"",status:"",attributeId:"",attributeValue:""});
/** 查询按钮提交本地筛选；数据范围始终限定在服务端已授权返回的当前商品。 */
function search(){Object.assign(applied,{code:code.value,specification:specification.value,status:status.value,...attributeFilter.value});page.value=1;}
let selectionRequest=0;
const specFields=computed(()=>structure.value?.specificationFields??[]);
/** 与服务端父商品状态校验对齐；未完成的草稿可继续维护结构，记录权限仍由服务端判定。 */
const writeAllowed = computed(() => {
 const record = product.value;
 return !!record && record.status !== "ENABLED" && !record.approvalPending &&
   !record.archivedAt && record.structureState !== "LEGACY_PENDING";
});
const specs=computed(()=>new Map((structure.value?.specifications??[]).map(s=>[s.id,s])));
/** 可分配候选排除已占用SKU及退出/停用规格；服务端仍以原事务复核，客户端列表不是授权依据。 */
const available=computed(()=>structure.value?.specifications.filter(s=>s.enabled&&!s.retiredAt&&!structure.value?.skus.some(k=>k.specificationId===s.id))??[]);
/** 区分操作准入与当前商品的业务限制，避免将缺少可分配规格误判为无权。 */
const createNotice = computed(() => {
 const record = product.value;
 if (!record || !operationsLoaded.value || checkingOperations.value || loading.value || busy.value) return "";
 if (!operations.value.includes("Product.createSku")) return t("productSku.createDenied");
 if (record.archivedAt) return t("productSku.createArchived");
 if (record.approvalPending) return t("productSku.createApproval");
 if (record.status === "ENABLED") return t("productSku.createEnabled");
 if (record.structureState === "LEGACY_PENDING") return t("productSku.createLegacy");
 if (structure.value?.mode === "NONE") return t("productSku.none");
 return available.value.length ? "" : t("productSku.createNoSpecification");
});
const allRows=computed<Record<string,any>[]>(()=>(structure.value?.skus??[]).map(k=>({...k,specification:specs.value.get(k.specificationId)})));
const detail = computed(() => allRows.value.find(row => row.id === detailId.value));
const detailHeader = ref<InstanceType<typeof PageHeader>>();
const attributeDialog=ref(false);
const attributeInput=ref<Record<string,string|string[]>>({});
const attributeView=ref<SkuAttributeConfiguration>();
const attributeTarget=ref<{product:string;sku:string}>();
const attributeValid=ref(false);
const canEditAttributes=computed(()=>writeAllowed.value&&operations.value.includes("Product.saveSkuAttributes"));
const filledAttribute=(value:string|string[]|undefined)=>Array.isArray(value)?value.length>0:Boolean(value?.trim());
const requiredAttributesReady=computed(()=>attributeView.value?.fields.every(
 field=>!field.requiredFlag||filledAttribute(attributeInput.value[field.templateItemId]))??false);

/** 打开时重新读取固定模板及行版本，不复用列表里可能过期的数据。 */
async function openSkuAttributes(row:Record<string,any>){
 const parent=product.value;
 if(!row||!parent||busy.value)return;
 busy.value=true;
 try{
  const view=await props.api.skuAttributes(parent.id,row.id);
  if(product.value?.id!==parent.id)return;
  attributeView.value=view;
  attributeTarget.value={product:parent.id,sku:row.id};
  attributeInput.value=Object.fromEntries(view.values.map(value=>[
   value.templateItemId,value.value??[...value.optionIds,...(value.customValues??[]).map(text=>"custom:"+text)]
  ]));
  attributeDialog.value=true;
 }catch(e){
  await setErrorFeedback(error,props.errorText(e));
 }finally{
  busy.value=false;
 }
}

/** 保持整数、小数、日期原文，分别提交标准选项和自定义选项。 */
async function saveSkuAttributes(){
 const target=attributeTarget.value;
 const view=attributeView.value;
 if(!target||!view?.templateVersionId||busy.value||!canEditAttributes.value
    ||!attributeValid.value||!requiredAttributesReady.value||product.value?.id!==target.product)return;
 busy.value=true;
 try{
  const values=view.fields.flatMap(field=>{
   const value=attributeInput.value[field.templateItemId];
   if(!filledAttribute(value))return [];
   return [Array.isArray(value)
    ? {templateItemId:field.templateItemId,value:null,optionIds:value.filter(item=>!item.startsWith("custom:")),
       customValues:value.filter(item=>item.startsWith("custom:")).map(item=>item.slice(7))}
    : {templateItemId:field.templateItemId,value:value as string,optionIds:[]}];
  });
  await props.api.saveSkuAttributes(target.product,target.sku,{
   version:view.version,rowVersion:view.rowVersion,templateVersionId:view.templateVersionId,values
  });
  attributeDialog.value=false;
  await refresh();
  ElMessage.success(t("masterData.saved"));
 }catch(e){
  await setErrorFeedback(error,props.errorText(e));
 }finally{
  busy.value=false;
 }
}
const batchOpen=ref(false);
const batchChoices=ref<Array<{id:string;label:string;disabled?:boolean}>>([]);
const batchSelection=ref<string|null>(null);
const batchTarget=ref<{product:string;sku:string;version:number;rowVersion:number}>();

/** 获取当前租户可采用的批次版本，类目的固定默认项可在确认前调整。 */
async function openBatchTemplate(row: Record<string, any>){
 const parent=product.value;
 const version=structure.value?.version;
 if(!row||!parent||version===undefined||busy.value||!writeAllowed.value)return;
 busy.value=true;
 try {
  const definitions=[];
  for(let page=1;;page++){
   const result=await props.listCatalog("template",{keyword:"",purpose:"BATCH",page,size:100});
   definitions.push(...result.rows.filter(definition=>definition.enabled));
   if(!result.rows.length||page*100>=result.total)break;
  }
  const choices:Array<{id:string;label:string;disabled?:boolean}>=[];
  for(const definition of definitions){
   const versions=await props.catalogVersions("template",definition.id);
   for(const candidate of versions){
    if(candidate.state==="PUBLISHED"){
     choices.push({id:candidate.id,label:definition.code+" / "+definition.name+" · r"+candidate.versionNo});
    }
   }
  }
  if(detail.value?.id!==row.id||product.value?.id!==parent.id)return;
  const current=row.batchTemplateVersionId;
  if(current&&!choices.some(choice=>choice.id===current)){
   choices.push({id:current,label:t("productSku.currentBatchUnavailable"),disabled:true});
  }
  const categoryDefault=configuration.value?.templates.BATCH;
  batchSelection.value=current??(choices.some(choice=>choice.id===categoryDefault)?categoryDefault!:null);
  batchChoices.value=choices;
  batchTarget.value={product:parent.id,sku:row.id,version,rowVersion:row.version};
  batchOpen.value=true;
 } catch(e){
  await setErrorFeedback(error,props.errorText(e));
 } finally {
  busy.value=false;
 }
}

/** 保存采用版本，不依据类目配置变更动态替换已采用的规则。 */
async function saveBatchTemplate(){
 const target=batchTarget.value;
 if(!target||busy.value||!writeAllowed.value||product.value?.id!==target.product)return;
 busy.value=true;
 try{
  await props.api.saveSkuBatchTemplate(target.product,target.sku,{
   version:target.version,rowVersion:target.rowVersion,templateVersionId:batchSelection.value
  });
  batchOpen.value=false;
  await refresh();
  ElMessage.success(t("masterData.saved"));
 }catch(e){
  await setErrorFeedback(error,props.errorText(e));
 }finally{
  busy.value=false;
 }
}
let detailTrigger: HTMLElement | null = null;

/** 详情使用当前商品已授权的结构记录，返回时恢复列表操作的焦点。 */
async function openDetail(id: string, event: MouseEvent) {
 if(allRows.value.find(row=>row.id===id)?.measurement&&!unitOptions.value.length&&!measurementLoading.value)void loadSkuUnits();
 detailTrigger = event.currentTarget as HTMLElement;
 detailId.value = id;
 await nextTick();
 detailHeader.value?.focusBack();
}

async function closeDetail() {
 detailId.value = "";
 await nextTick();
 detailTrigger?.focus();
 detailTrigger = null;
}

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
 {key:'status',label:t('masterData.status'),width:110},
 {key:'code',prop:'code',label:t('productSku.code'),minWidth:170},
 {key:'specification',label:t('productStructure.specification'),minWidth:170},
 ...attributes.value.map(f=>({key:'attr_'+f.id,label:f.name+(f.unitCode?' ('+f.unitCode+')':''),minWidth:140})),{key:'effective',label:t('productStructure.effective'),width:110},
 {key:'operations',label:t('masterData.actions'),minWidth:310}
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
/** 按分支读取完整分页；每页 100 条，分类与商品分别使用原授权接口。 */
async function readBranch<T>(read: (page: number) => Promise<{ rows: T[]; total: number }>) {
  const rows: T[] = [];
  for (let page = 1; ; page++) {
    const result = await read(page);
    rows.push(...result.rows);
    if (!result.rows.length || rows.length >= result.total) return rows;
  }
}

/** 分类只挂直属商品；全部商品入口保留无分类草稿及无分类权限用户的可达性。 */
const loadTree: LoadFunction = async (node, resolve, reject) => {
  if (node.level === 0) {
    resolve([{ key: rootKey, kind: "root" } satisfies SkuTreeNode]);
    return;
  }
  const branch = node.data as SkuTreeNode;
  const categoryId = branch.kind === "category" ? branch.id : undefined;
  try {
    const [categories, products] = await Promise.all([
      canListCategories.value && branch.kind !== "all"
        ? readBranch(page => props.listCatalog("category", { keyword: "", parentId: categoryId, page, size: 100 }))
        : [],
      branch.kind !== "root" || !canListCategories.value
        ? readBranch(page => props.api.list({ name: "", code: "", categoryId, archived: false, page, size: 100 }))
        : []
    ]);
    const children = categories.map((category): SkuTreeNode => ({
      key: "category:" + category.id, kind: "category", id: category.id, name: category.name
    }));
    if (branch.kind === "root" && canListCategories.value) {
      children.push({ key: allProductsKey, kind: "all" });
    }
    resolve([
      ...children,
      ...products.map((item): SkuTreeNode => ({
        key: "product:" + branch.key + ":" + item.id,
        kind: "product", id: item.id, name: item.name, leaf: true
      }))
    ]);
  } catch (cause) {
    reject();
    treeError.value = props.errorText(cause);
  }
};

/** 分类只用于定位商品，切换范围时清理旧明细及筛选条件。 */
function selectNode(node: SkuTreeNode) {
  if (busy.value) {
    tree.value?.setCurrentKey(activeNode.value, false);
    return;
  }
  if (activeNode.value === node.key) return;
  activeNode.value = node.key;
  selected.value = node.kind === "product" ? node.id : "";
  reset();
  void refresh();
}

function retryTree() {
  treeError.value = "";
  selectNode({ key: rootKey, kind: "root" });
  treeGeneration.value++;
}

/** 丢弃迟到响应，避免把另一商品的父版本用于当前商品的写入。 */
async function refresh() {
  const id = selected.value;
  const request = ++selectionRequest;
  loadIssue.value = undefined;
  product.value = undefined;
  configuration.value = undefined;
  structure.value = undefined;
  createOpen.value = false;
  structureOpen.value = false;
  detailId.value = "";
  loading.value = Boolean(id);
  if (!id) return;
  try {
    const [record, currentStructure] = await Promise.all([props.api.read(id), props.api.structure(id)]);
    if (request !== selectionRequest) return;
    const config = record.configId ? await props.api.configuration(record.configId) : undefined;
    if (request !== selectionRequest) return;
    product.value = record;
    structure.value = currentStructure;
    configuration.value = config;
    page.value = 1;
  } catch (cause) {
    if (request !== selectionRequest) return;
    const message = props.errorText(cause);
    if (!message) return;
    const response = (cause as { response?: { status: number } }).response;
    loadIssue.value = { message, retryable: response?.status !== 403 };
  } finally {
    if (request === selectionRequest) loading.value = false;
  }
}
function reset(){code.value='';specification.value='';status.value='';attributeFilter.value={attributeId:"",attributeValue:""};search();}
function openCreate(){if(!writeAllowed.value||!available.value.length||busy.value||loading.value)return;editMeasurementTarget.value=undefined;draft.code='';draft.specificationId='';ownMeasurement.value=false;measurement.inventoryUnitId='';measurement.pricingUnitId='';measurement.packaging=[];createOpen.value=true;}
/** 一个SKU命令只绑定已有规格；新增规格与分配货号分开提交，失败不制造已成功的组合反馈。 */
/** 同一套字段维护新增与已有货号，编辑使用打开时的父子版本。 */
async function create(){
 if(busy.value||measurementLoading.value||!product.value||!(editMeasurementTarget.value?writeAllowed.value:available.value.length))return;
 if(ownMeasurement.value&&(!measurement.inventoryUnitId||!measurement.pricingUnitId))return;
 busy.value=true;
 try {
  const edit=editMeasurementTarget.value;
  if(edit){
   await props.api.saveSkuMeasurement(product.value.id,edit.id,{
    version:edit.version,rowVersion:edit.rowVersion,measurement
   });
  } else {
   if(!await form.value?.validate().catch(()=>false))return;
   await props.api.createSku(product.value.id,{...draft,version:structure.value!.version,measurement:ownMeasurement.value?measurement:undefined});
  }
  createOpen.value=false;
  await refresh();
  ElMessage.success(t(edit?'masterData.saved':'productSku.created'));
 } catch(e){
  await setErrorFeedback(error,props.errorText(e));
 } finally {
  busy.value=false;
 }
}

/** 首次配置沿用生效商品单位及包装，避免切换为专属计量时丢失既有档位。 */
async function openMeasurementEdit(row: Record<string, any>){
 const parent=product.value;
 const structureVersion=structure.value?.version;
 if(!row||!parent||structureVersion===undefined||!writeAllowed.value||busy.value)return;
 busy.value=true;
 try {
  const inherited=row.measurement?undefined:await props.api.measurement(parent.id);
  const stock=row.measurement?.inventoryUnitId??inherited?.baseUnitId;
  if(!stock||product.value?.id!==parent.id)return;
  measurement.inventoryUnitId=stock;
  measurement.pricingUnitId=row.measurement?.pricingUnitId??stock;
  measurement.packaging=(row.measurement?.packaging??inherited?.configuration.packaging??[])
   .map((pack:typeof measurement.packaging[number])=>({...pack}));
  editMeasurementTarget.value={id:row.id,version:structureVersion,rowVersion:row.version,code:row.code,enabled:row.enabled};
  ownMeasurement.value=true;
  createOpen.value=true;
  await loadSkuUnits();
 } catch(e){
  await setErrorFeedback(error,props.errorText(e));
 } finally {
  busy.value=false;
 }
}
async function toggle(row:Record<string,any>){if(busy.value)return;busy.value=true;try{const confirmed=await ElMessageBox.confirm(t('productStructure.stateHint'),t('productSku.title'),{confirmButtonText:t(row.enabled?'masterData.disable':'masterData.enable'),cancelButtonText:t('masterData.cancel')}).catch(()=>false);if(!confirmed)return;await props.api.structureState(selected.value,'sku',row.id,{enabled:!row.enabled,rowVersion:row.version,version:structure.value!.version});await refresh();ElMessage.success(t('productSku.saved'));}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
async function structureChanged(){const id=selected.value;const request=selectionRequest;try{const [p,s]=await Promise.all([props.api.read(id),props.api.structure(id)]);if(request!==selectionRequest)return;product.value=p;structure.value=s;}catch(e){await setErrorFeedback(error,props.errorText(e));}}
async function loadOperations() {
  checkingOperations.value = true;
  operationsLoaded.value = false;
  error.value = "";
  try {
    operations.value = await props.api.operations();
    operationsLoaded.value = true;
  } catch (cause) {
    await setErrorFeedback(error, props.errorText(cause));
  } finally {
    checkingOperations.value = false;
  }
}

onMounted(loadOperations);
</script>
<template>
<!-- 单根宿主承接应用壳的 main-content；边距、工作区边框和区块间距统一由全局模板维护。 -->
<div class="management-page">
<TreeListTemplate v-show="!detail" v-model:collapsed="treeCollapsed" :title="t('productSku.title')" :tree-label="treeTitle">
 <template #treeHeader>{{ treeTitle }}</template>
 <template #tree>
  <el-tree
    v-if="canListProducts"
    ref="tree"
    :key="treeGeneration"
    :aria-label="treeTitle"
    node-key="key"
    :current-node-key="activeNode"
    :default-expanded-keys="[rootKey]"
    :props="{ label: 'name', isLeaf: 'leaf', disabled: () => busy }"
    highlight-current
    lazy
    :load="loadTree"
    @node-click="selectNode"
  >
    <template #default="{ data }">
      <span class="el-tree-node__label" :title="data.name">
        {{ data.kind === 'root' ? treeTitle : data.kind === 'all' ? t('productV1.allProducts') : data.name }}
      </span>
    </template>
  </el-tree>
  <el-alert v-if="treeError" :title="treeError" type="error" :closable="false">
    <el-button v-operation="'Product.list'" text :disabled="busy" @click="retryTree">{{ t('masterData.retry') }}</el-button>
  </el-alert>
 </template>
  <template #feedback>
  <FloatingError :title="error">
    <el-button v-if="!canListProducts" :disabled="checkingOperations" @click="loadOperations">{{ t('masterData.retry') }}</el-button>
  </FloatingError>
  <el-alert v-if="createNotice" :title="createNotice" type="info" :closable="false" />
 </template>
<template #search><el-form inline class="sku-search" @submit.prevent="search">
  <div class="management-filter">
  <el-form-item :label="t('productSku.code')"><el-input v-model="code" clearable/></el-form-item>
  <el-form-item :label="t('productStructure.specification')"><el-input v-model="specification" clearable/></el-form-item>
  <el-form-item :label="t('masterData.status')"><el-select v-model="status" clearable class="sku-status-select"><el-option value="true" :label="t('masterData.enabled')"/><el-option value="false" :label="t('masterData.disabled')"/></el-select></el-form-item>
  <el-form-item :label="t('productSku.attribute')">
    <SkuAttributeFilter v-model="attributeFilter" :options="attributes" />
  </el-form-item>
  </div>
  <div class="management-filter-actions"><el-button v-operation="'Product.readStructure'" type="primary" native-type="submit" :disabled="!product||loading||busy">{{t('masterData.search')}}</el-button><el-button :disabled="loading||busy" @click="reset">{{t('masterData.reset')}}</el-button></div>
 </el-form></template>
 <template #table>
  <el-result v-if="loadIssue" :icon="loadIssue.retryable ? 'error' : 'warning'" :title="loadIssue.message">
    <template v-if="loadIssue.retryable" #extra>
      <el-button v-operation="'Product.readStructure'" type="primary" :disabled="busy" @click="refresh">{{t('masterData.retry')}}</el-button>
    </template>
  </el-result>
  <el-empty v-else-if="!selected && !checkingOperations" :description="t(canListProducts ? 'productSku.choose' : 'masterData.noPermission')" />
  <ManagementTable v-else embedded :total="filtered.length" :empty-text="t(structure?.mode==='NONE'?'productSku.none':'productSku.empty')" :title="product?.name??t('productSku.title')" :columns="columns" :rows="rows" :loading="checkingOperations||loading||busy" @refresh="refresh">
   <template #titleHelp><InfoHint :content="t('productSku.hint')"/></template>
   <template #actions><el-button v-operation="'Product.createSku'" type="primary" :disabled="!writeAllowed||!available.length||busy||loading" @click="openCreate">{{t('productSku.create')}}</el-button><el-button v-operation="'Product.readStructure'" :disabled="!product||busy||loading" @click="structureOpen=true">{{t('productSku.specifications')}}</el-button></template>
   <template #specification="{row}">{{row.specification?.name??'—'}}</template>
   <template v-for="field in attributes" :key="field.id" #[`attr_${field.id}`]="{row}">{{attribute(row,field.id)}}</template>
   <template #status="{row}"><el-tag class="app-status-tag" :type="row.enabled?'success':'info'">{{t(row.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag></template>
   <template #effective="{row}">{{t(row.effective?'productV1.yes':'productV1.no')}}</template>
   <template #operations="{row}"><el-button v-operation="'Product.readSkuAttributes'" link :disabled="busy||loading" @click="openSkuAttributes(row)">{{t('productCatalog.SKU')}}</el-button><el-button v-operation="'Product.readStructure'" link type="primary" :disabled="busy||loading" @click="openDetail(row.id, $event)">{{t('partner.view')}}</el-button><el-button v-operation="'Product.setStructureState'" link :disabled="!writeAllowed||busy||!!row.specification?.retiredAt" @click="toggle(row)">{{t(row.enabled?'masterData.disable':'masterData.enable')}}</el-button><el-button v-if="writeAllowed" v-operation="'Product.saveSkuBatchTemplate'" link :disabled="busy" @click="openBatchTemplate(row)">{{t('productSku.batchTemplate')}}</el-button><el-button v-if="writeAllowed" v-operation="'Product.saveSkuMeasurement'" link :disabled="busy || measurementLoading" @click="openMeasurementEdit(row)">{{t('productSku.editMeasurement')}}</el-button></template>
   <template #pagination><AppPagination :current-page="page" :page-size="size" :total="filtered.length" @current-change="p=>page=p" @size-change="s=>{size=s;page=1}"/></template>
  </ManagementTable>
 </template>
</TreeListTemplate>
<ManagedDialog v-model="createOpen" :title="t(editMeasurementTarget ? 'productSku.editMeasurement' : 'productSku.create')" :busy="busy"><el-form ref="form" :model="draft" label-position="top" class="business-border-label-form" @submit.prevent="create"><el-form-item v-if="!editMeasurementTarget" :label="t('productSku.code')" prop="code" :rules="[{required:true,message:t('masterData.required')},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t('productSku.codeHint')}]"><el-input v-model="draft.code" maxlength="64"/></el-form-item><el-form-item v-else :label="t('productSku.code')"><span>{{editMeasurementTarget.code}}</span></el-form-item><el-form-item v-if="editMeasurementTarget" :label="t('masterData.status')"><el-tag class="app-status-tag" :type="editMeasurementTarget.enabled?'success':'info'">{{t(editMeasurementTarget.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag></el-form-item><el-form-item v-if="!editMeasurementTarget" :label="t('productStructure.specification')" prop="specificationId" :rules="[{required:true,message:t('masterData.required')}]"><el-select v-model="draft.specificationId" filterable><el-option v-for="s in available" :key="s.id" :value="s.id" :label="s.name"/></el-select></el-form-item>
 <el-form-item v-if="!editMeasurementTarget"><el-checkbox v-model="ownMeasurement" :disabled="measurementLoading" @change="value=>value&&loadSkuUnits()">{{t('productSku.ownMeasurement')}}</el-checkbox></el-form-item>
 <template v-if="ownMeasurement || editMeasurementTarget">
  <el-form-item v-for="role in (['inventoryUnitId','pricingUnitId'] as const)" :key="role" :label="t('productSku.'+role)" required>
   <el-select v-model="measurement[role]" filterable :loading="measurementLoading" :disabled="!!editMeasurementTarget && role === 'inventoryUnitId'">
    <el-option v-for="unit in unitOptions" :key="unit.id" :label="unit.code+' / '+unit.name" :value="unit.id"/>
   </el-select>
  </el-form-item>
  <el-button :disabled="200 <= measurement.packaging.length" @click="measurement.packaging.push({code:'',name:'',unitId:'',quantity:''})">{{t('productSku.addPackaging')}}</el-button>
  <el-card v-for="(pack,index) in measurement.packaging" :key="index" shadow="never">
   <el-form-item :label="t('masterData.code')" required><el-input v-model.trim="pack.code" maxlength="64"/></el-form-item>
   <el-form-item :label="t('masterData.name')" required><el-input v-model.trim="pack.name" maxlength="200"/></el-form-item>
   <el-form-item :label="t('productSku.packagingUnit')" required>
    <el-select v-model="pack.unitId" filterable>
     <el-option v-for="unit in unitOptions" :key="unit.id" :label="unit.code+' / '+unit.name" :value="unit.id"/>
    </el-select>
   </el-form-item>
   <el-form-item :label="t('productSku.packagingQuantity')" required><el-input v-model.trim="pack.quantity" maxlength="31"/></el-form-item>
   <el-button @click="measurement.packaging.splice(index,1)">{{t('productSku.removePackaging')}}</el-button>
  </el-card>
 </template>
 </el-form><template #footer><el-button :disabled="busy" @click="createOpen=false">{{t('masterData.cancel')}}</el-button><el-button v-operation="editMeasurementTarget ? 'Product.saveSkuMeasurement' : 'Product.createSku'" type="primary" :loading="busy" :disabled="measurementLoading || (ownMeasurement && (!measurement.inventoryUnitId || !measurement.pricingUnitId))" @click="create">{{t('masterData.save')}}</el-button></template></ManagedDialog>
<ManagedDialog v-model="attributeDialog" :title="t('productCatalog.SKU')" :busy="busy">
 <el-form class="business-border-label-form" label-position="top" @submit.prevent="saveSkuAttributes">
  <ProductAttributes v-if="attributeView?.fields.length" :fields="attributeView.fields" :values="attributeInput"
    :readonly="!canEditAttributes" :disabled="busy" required-to-save
    @change="(id,value)=>attributeInput[id]=value" @valid="value=>attributeValid=value"/>
  <el-empty v-else :description="t('productSku.noAttributeTemplate')"/>
 </el-form>
 <template #footer>
  <el-button :disabled="busy" @click="attributeDialog=false">{{t('masterData.cancel')}}</el-button>
  <el-button v-if="canEditAttributes && attributeView?.fields.length" v-operation="'Product.saveSkuAttributes'"
    type="primary" :loading="busy" :disabled="!attributeValid || !requiredAttributesReady" @click="saveSkuAttributes">
    {{t('masterData.save')}}
  </el-button>
 </template>
</ManagedDialog>
<ManagedDialog v-model="batchOpen" :title="t('productSku.batchTemplate')" :busy="busy">
 <el-form class="business-border-label-form" label-position="top" @submit.prevent="saveBatchTemplate">
  <el-form-item :label="t('productSku.batchTemplate')">
   <el-select v-model="batchSelection" filterable clearable @clear="batchSelection=null">
    <el-option v-for="choice in batchChoices" :key="choice.id" :label="choice.label" :value="choice.id" :disabled="choice.disabled"/>
   </el-select>
  </el-form-item>
 </el-form>
 <template #footer>
  <el-button :disabled="busy" @click="batchOpen=false">{{t('masterData.cancel')}}</el-button>
  <el-button v-operation="'Product.saveSkuBatchTemplate'" type="primary" :loading="busy" @click="saveBatchTemplate">{{t('masterData.save')}}</el-button>
 </template>
</ManagedDialog>
<ManagedDialog v-model="structureOpen" :title="t('productSku.specifications')"><ProductStructure v-if="structureOpen&&product" :key="product.id+':'+structure?.version" :id="product.id" :version="structure?.version??product.version" :readonly="!writeAllowed" :api="api" :fields="specFields" :error-text="errorText" @changed="structureChanged"/></ManagedDialog>
<TabbedDetailTemplate v-if="detail" master-data>
 <template #header>
  <PageHeader ref="detailHeader" :back-label="t('productV1.back')" @back="closeDetail" :title="t('productSku.detail')">
   <template #meta>
    <el-tag effect="plain">{{detail.code}}</el-tag>
    <el-tag class="app-status-tag" :type="detail.enabled?'success':'info'">{{t(detail.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag>
   </template>
   <template v-if="product" #actions>
    <ProductHistoryButton :id="product.id" :api="api" :error-text="errorText" :subject="{collection:'skus',id:detail.id}" />
   </template>
  </PageHeader>
 </template>
 <template #content>
  <el-card shadow="never" class="detail-section">
   <template #header><div class="detail-section-heading"><h2>{{t('productV1.basic')}}</h2></div></template>
   <div class="detail-field-grid">
    <div class="detail-readonly-field"><span>{{t('productSku.code')}}</span><strong>{{detail.code}}</strong></div>
    <div class="detail-readonly-field"><span>{{t('masterData.status')}}</span><el-tag class="app-status-tag" :type="detail.enabled?'success':'info'">{{t(detail.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag></div>
    <div class="detail-readonly-field"><span>{{t('productSku.product')}}</span><strong>{{product?.name??'—'}}</strong></div>
    <div class="detail-readonly-field"><span>{{t('productV1.origin')}}</span><strong>{{product?.origin||'—'}}</strong></div>
    <div class="detail-readonly-field"><span>{{t('productStructure.specification')}}</span><strong>{{detail.specification?.name??'—'}}</strong></div>
    <div class="detail-readonly-field"><span>{{t('productStructure.effective')}}</span><strong>{{t(detail.effective?'productV1.yes':'productV1.no')}}</strong></div>
    <div class="detail-readonly-field">
     <span>{{t('productSku.batchTemplate')}}</span>
     <strong>{{t(detail.batchTemplateVersionId?'productSku.batchConfigured':'productSku.batchUnconfigured')}}</strong>
    </div>
    <div class="detail-readonly-field detail-field-wide"><span>{{t('productV1.description')}}</span><strong>{{detail.specification?.description||'—'}}</strong></div>
   </div>
  </el-card>
  <el-card v-if="detail.measurement" shadow="never" class="detail-section">
   <template #header><div class="detail-section-heading"><h2>{{t('productSku.ownMeasurement')}}</h2></div></template>
   <div class="detail-field-grid" v-loading="measurementLoading">
    <div v-for="role in (['inventoryUnitId','pricingUnitId'] as const)" :key="role" class="detail-readonly-field">
     <span>{{t('productSku.'+role)}}</span>
     <strong>{{unitOptions.find(unit=>unit.id===detail.measurement[role])?.name??'—'}}</strong>
    </div>
    <div v-for="pack in detail.measurement.packaging" :key="pack.code" class="detail-readonly-field">
     <span>{{pack.code}} · {{pack.name}}</span>
     <strong>{{pack.quantity}} {{unitOptions.find(unit=>unit.id===detail.measurement.inventoryUnitId)?.name??'—'}} / {{unitOptions.find(unit=>unit.id===pack.unitId)?.name??'—'}}</strong>
    </div>
   </div>
  </el-card>
  <el-card v-if="attributes.length" shadow="never" class="detail-section">
   <template #header><div class="detail-section-heading"><h2>{{t('productSku.attribute')}}</h2></div></template>
   <div class="detail-field-grid">
    <div v-for="field in attributes" :key="field.id" class="detail-readonly-field">
     <span>{{field.name+(field.unitCode?' ('+field.unitCode+')':'')}}</span>
     <strong>{{attribute(detail,field.id)}}</strong>
    </div>
   </div>
  </el-card>
 </template>
 <template #system>
  <h2 class="detail-section-title">{{t('partner.system')}}</h2>
  <RecordAuditDetails v-if="detail.audit" :audit="detail.audit" inline/>
 </template>
</TabbedDetailTemplate>
</div>
</template>
<style scoped>
.sku-search {
 display: grid;
 gap: var(--app-section-gap, 8px);
}
.sku-status-select { width: 140px; max-width: 100%; }
@media (max-width: 600px) {
 .sku-status-select { width: 100%; }
}
</style>
