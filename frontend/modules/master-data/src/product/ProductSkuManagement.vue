<script setup lang="ts">
import {computed,nextTick,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,ElMessageBox,type FormInstance,type LoadFunction,type TreeInstance} from "element-plus";
import {TreeListTemplate,PageHeader,TabbedDetailTemplate,ManagementTable,ManagedDialog,AppPagination,FloatingError,InfoHint,RecordAuditDetails,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import ProductStructure from "./ProductStructure.vue";
import SkuAttributeFilter from "./SkuAttributeFilter.vue";
import type {ProductApi,ProductConfiguration,ProductRecord} from "./product";
import type { ProductCatalogApi } from "./catalog";
/** 商品明细工作台：按商品固定配置投影动态属性，复用既有SKU身份与聚合命令，不遍历商品伪造全局分页。 */
const props=defineProps<{api:ProductApi;listCatalog:ProductCatalogApi["list"];errorText:(e:unknown)=>string}>();
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
const applied=reactive({code:"",specification:"",status:"",attributeId:"",attributeValue:""});
/** 查询按钮提交本地筛选；数据范围始终限定在服务端已授权返回的当前商品。 */
function search(){Object.assign(applied,{code:code.value,specification:specification.value,status:status.value,...attributeFilter.value});page.value=1;}
let selectionRequest=0;
const specFields=computed(()=>configuration.value?.fields.filter(f=>f.purpose==='SPEC')??[]);
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
const detailActions = ref<HTMLElement>();
let detailTrigger: HTMLElement | null = null;

/** 详情使用当前商品已授权的结构记录，返回时恢复列表操作的焦点。 */
async function openDetail(id: string, event: MouseEvent) {
 detailTrigger = event.currentTarget as HTMLElement;
 detailId.value = id;
 await nextTick();
 detailActions.value?.querySelector("button")?.focus();
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
function openCreate(){if(!writeAllowed.value||!available.value.length||busy.value||loading.value)return;draft.code='';draft.specificationId='';createOpen.value=true;}
/** 一个SKU命令只绑定已有规格；新增规格与分配货号分开提交，失败不制造已成功的组合反馈。 */
async function create(){if(busy.value||!product.value)return;busy.value=true;try{if(!await form.value?.validate().catch(()=>false))return;await props.api.createSku(product.value.id,{...draft,version:structure.value!.version});createOpen.value=false;await refresh();ElMessage.success(t('productSku.created'));}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
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
   <template #status="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag></template>
   <template #effective="{row}">{{t(row.effective?'productV1.yes':'productV1.no')}}</template>
   <template #operations="{row}"><el-button v-operation="'Product.readStructure'" link type="primary" :disabled="busy||loading" @click="openDetail(row.id, $event)">{{t('partner.view')}}</el-button><el-button v-operation="'Product.setStructureState'" link :disabled="!writeAllowed||busy||!!row.specification?.retiredAt" @click="toggle(row)">{{t(row.enabled?'masterData.disable':'masterData.enable')}}</el-button></template>
   <template #pagination><AppPagination :current-page="page" :page-size="size" :total="filtered.length" @current-change="p=>page=p" @size-change="s=>{size=s;page=1}"/></template>
  </ManagementTable>
 </template>
</TreeListTemplate>
<ManagedDialog v-model="createOpen" :title="t('productSku.create')" :busy="busy"><el-form ref="form" :model="draft" label-position="top" class="business-border-label-form" @submit.prevent="create"><el-form-item :label="t('productSku.code')" prop="code" :rules="[{required:true,message:t('masterData.required')},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t('productSku.codeHint')}]"><el-input v-model="draft.code" maxlength="64"/></el-form-item><el-form-item :label="t('productStructure.specification')" prop="specificationId" :rules="[{required:true,message:t('masterData.required')}]"><el-select v-model="draft.specificationId" filterable><el-option v-for="s in available" :key="s.id" :value="s.id" :label="s.name"/></el-select></el-form-item></el-form><template #footer><el-button :disabled="busy" @click="createOpen=false">{{t('masterData.cancel')}}</el-button><el-button v-operation="'Product.createSku'" type="primary" :loading="busy" @click="create">{{t('masterData.save')}}</el-button></template></ManagedDialog>
<ManagedDialog v-model="structureOpen" :title="t('productSku.specifications')"><ProductStructure v-if="structureOpen&&product" :key="product.id+':'+structure?.version" :id="product.id" :version="structure?.version??product.version" :readonly="!writeAllowed" :api="api" :fields="specFields" :error-text="errorText" @changed="structureChanged"/></ManagedDialog>
<TabbedDetailTemplate v-if="detail" master-data>
 <template #header>
  <PageHeader :title="t('productSku.detail')">
   <template #meta>
    <el-tag effect="plain">{{detail.code}}</el-tag>
    <el-tag :type="detail.enabled?'success':'info'">{{t(detail.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag>
   </template>
   <template #actions>
    <div ref="detailActions"><el-button @click="closeDetail">{{t('productV1.back')}}</el-button></div>
   </template>
  </PageHeader>
 </template>
 <template #content>
  <el-card shadow="never" class="detail-section">
   <template #header><div class="detail-section-heading"><h2>{{t('productV1.basic')}}</h2></div></template>
   <div class="detail-field-grid">
    <div class="detail-readonly-field"><span>{{t('productSku.product')}}</span><strong>{{product?.name??'—'}}</strong></div>
    <div class="detail-readonly-field"><span>{{t('productV1.origin')}}</span><strong>{{product?.origin||'—'}}</strong></div>
    <div class="detail-readonly-field"><span>{{t('productStructure.specification')}}</span><strong>{{detail.specification?.name??'—'}}</strong></div>
    <div class="detail-readonly-field"><span>{{t('productStructure.effective')}}</span><strong>{{t(detail.effective?'productV1.yes':'productV1.no')}}</strong></div>
    <div class="detail-readonly-field detail-field-wide"><span>{{t('productV1.description')}}</span><strong>{{detail.specification?.description||'—'}}</strong></div>
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
