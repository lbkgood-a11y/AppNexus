<script setup lang="ts">
import {VxeTable,VxeColumn} from "@appnexus/ui";
import {computed,onMounted,reactive,ref,watch} from "vue";
import {ElMessageBox, type LoadFunction} from "element-plus";
import {useRoute} from "vue-router";
import {useI18n} from "vue-i18n";
import {TreeListTemplate,ManagementTable,AppPagination,ManagedDialog,FloatingError,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import ProductEditor from "./ProductEditor.vue";
import type {ProductApi,ProductRow,ProductQuery,ProductConfiguration,ProductOption} from "./product";
import type {CatalogRow, ProductCatalogApi} from "./catalog";
/** 商品档案正式工作区；唯一Product操作与固定版本数据，部署启动前由迁移强制对账。 */
const props=defineProps<{
  api: ProductApi;
  /** 按父级读取分类，服务端独立校验 ProductCategory.list。 */
  listCatalog: ProductCatalogApi["list"];
  errorText: (error: unknown) => string;
}>();
const {t}=useI18n();const route=useRoute();const approvalView=ref<{state:string;productVersion:number;snapshot:Record<string,any>}>();
const rows=ref<ProductRow[]>([]),total=ref(0),loading=ref(false),error=ref(""),operations=ref<string[]>([]);
const filterConfiguration=ref<ProductConfiguration>(),filterCategories=ref<ProductOption[]>([]),filterCategory=ref<string>(),filterConditions=ref<Array<{key:number;templateItemId:string;operator:string;values:string[]}>>([]);
const invalidFilterKeys = ref(new Set<number>());
const editor=ref(false),id=ref(""),editorMode=ref<"view"|"edit">("view");
const legacyOpen=ref(false),legacyRows=ref<Array<{id:string;code:string;result:string}>>([]),legacyPage=ref(1),legacyTotal=ref(0),staging=ref(false);
const query=reactive<ProductQuery>({name:"",code:"",form:undefined,status:undefined,archived:false,page:1,size:20});
const listIssue = ref<{ kind: "denied" | "request"; message: string; retry: boolean }>();
const allProducts = "all-products";
const treeCollapsed = ref(false);
const treeError = ref("");
const treeGeneration = ref(0);
const canListCategories = computed(() => operations.value.includes("ProductCategory.list"));
const categoryTitle = computed(() => t("productCatalog.category"));
const listTitle = computed(() => t("productV1.title"));
let listRequest = 0;
let configurationRequest = 0;
let nextConditionKey = 0;

/** 展开时读取完整的直属分类分页，失败时保留重试入口。 */
const loadCategoryTree: LoadFunction = async (node, resolve, reject) => {
  if (node.level === 0) {
    resolve([{ id: allProducts, root: true }]);
    return;
  }
  try {
    const children: CatalogRow[] = [];
    let page = 1;
    let count: number;
    do {
      const result = await props.listCatalog("category", {
        keyword: "",
        parentId: node.data.root ? undefined : node.data.id,
        page: page++,
        size: 100
      });
      count = result.total;
      if (!result.rows.length) break;
      children.push(...result.rows);
    } while (children.length < count);
    resolve(children.map(category => ({ ...category, leaf: category.depth === 8 })));
  } catch (cause) {
    reject();
    treeError.value = props.errorText(cause);
  }
};

/** 切换分类时清除旧结果和属性条件；服务端查询所选分类及下级，动态属性仍限定固定版本。 */
function selectCategory(categoryId?: string, configId?: string | null) {
  if (categoryId === query.categoryId) return;
  query.categoryId = categoryId;
  rows.value = [];
  total.value = 0;
  void loadFilterConfiguration(configId);
  search();
}

async function loadFilterConfiguration(configId?: string | null) {
  const request = ++configurationRequest;
  filterConfiguration.value = undefined;
  filterConditions.value = [];
  invalidFilterKeys.value.clear();
  query.configId = undefined;
  query.attributeFilters = undefined;
  if (!configId || !operations.value.includes("Product.readConfiguration")) return;
  try {
    const configuration = await props.api.configuration(configId);
    if (request === configurationRequest) filterConfiguration.value = configuration;
  } catch (cause) {
    if (request === configurationRequest) await setErrorFeedback(error, props.errorText(cause));
  }
}
const columns=computed<ManagementColumn[]>(()=>[{key:"code",prop:"code",label:t("masterData.code"),minWidth:140},{key:"name",prop:"name",label:t("masterData.name"),minWidth:180},{key:"form",label:t("productV1.productForm"),width:120},{key:"status",label:t("masterData.status"),width:140},{key:"structure",label:t("productV1.structure"),width:160},{key:"operations",label:t("masterData.actions"),width:200}]);
/** 快速切换分类时只提交最近一次查询的结果。 */
async function load() {
  const request = ++listRequest;
  loading.value = true;
  listIssue.value = undefined;
  try {
    const allowed = await props.api.operations();
    if (request !== listRequest) return;
    operations.value = allowed;
    if (!allowed.includes("Product.list")) {
      rows.value = [];
      total.value = 0;
      listIssue.value = { kind: "denied", message: t("productV1.listDenied"), retry: false };
      return;
    }
    const result = await props.api.list({ ...query, includeDescendants: canListCategories.value && query.categoryId ? true : undefined });
    if (request !== listRequest) return;
    rows.value = result.rows;
    total.value = result.total;
  } catch (cause) {
    if (request !== listRequest) return;
    const message = props.errorText(cause);
    // 会话失效及取消已由请求层处理；陈旧响应不能清空新的列表。
    if (!message) return;
    const response = (cause as { response?: { status: number; data?: { code?: string } } }).response;
    const denied = response?.status === 403 && !response.data?.code?.startsWith("MFA_");
    rows.value = [];
    total.value = 0;
    listIssue.value = denied
      ? { kind: "denied", message: t("productV1.listDenied"), retry: false }
      : { kind: "request", message, retry: response?.status !== 403 };
  } finally {
    if (request === listRequest) loading.value = false;
  }
}
/** 重置查询条件并保留左树范围；无分类树权限时清除查询区的分类选择。 */
function reset() {
  Object.assign(query, { name: "", code: "", form: undefined, status: undefined, archived: false });
  if (!canListCategories.value) {
    query.categoryId = undefined;
    filterCategory.value = undefined;
    void loadFilterConfiguration();
  }
  filterConditions.value = [];
  search();
}
function missingFilterValue(values: string[]) {
  return !values.length || values.some(value => !value.trim());
}

/** 未完成的属性条件在原控件旁提示，保留输入并阻止提交查询。 */
function search() {
  invalidFilterKeys.value = new Set(
    filterConditions.value
      .filter(condition => !condition.templateItemId || missingFilterValue(condition.values))
      .map(condition => condition.key)
  );
  if (invalidFilterKeys.value.size) return;
  query.page = 1;
  query.configId = filterConditions.value.length ? filterConfiguration.value?.id : undefined;
  query.attributeFilters = filterConditions.value.length ? JSON.stringify(filterConditions.value.map(({ templateItemId, operator, values }) => ({ templateItemId, operator, values }))) : undefined;
  void load();
}
async function findFilterCategory(keyword:string){try{filterCategories.value=(await props.api.options("category",keyword,1)).rows;}catch(e){await setErrorFeedback(error,props.errorText(e));}}
function selectFilterCategory(id: string | undefined) {
  const category = filterCategories.value.find(row => row.id === id);
  if (id && !category) {
    void setErrorFeedback(error, t("productV1.optionChanged"));
    return;
  }
  selectCategory(category?.id, category?.configId);
}
function searchableFields(){return (filterConfiguration.value?.fields??[]).filter(f=>f.searchable&&f.dataType!=="TEXT");}
function selectedField(item:string){return searchableFields().find(f=>f.templateItemId===item);}
/** 每次新增使用独立键，删除中间条件时保留其他控件的焦点与状态。 */
function addCondition() {
  if (filterConditions.value.length < 3) {
    filterConditions.value.push({ key: ++nextConditionKey, templateItemId: "", operator: "eq", values: [""] });
  }
}

/** 多选条件从空数组开始，避免渲染空标签或把空值提交给查询接口。 */
function changeOperator(condition: (typeof filterConditions.value)[number], operator: string) {
  condition.operator = operator;
  condition.values = operator === "anyOf" ? [] : operator === "between" ? ["", ""] : [""];
}
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
<div class="product-page"><section v-if="approvalView"><h1>{{t('productApproval.title')}} · {{approvalView.snapshot.name}}</h1><el-descriptions :column="1" border><el-descriptions-item v-for="key in ['code','name','productForm','specificationMode','brand','origin','description']" :key="key" :label="t(['code','name'].includes(key)?'masterData.'+key:key==='specificationMode'?'productStructure.mode':'productV1.'+key)">{{key==='productForm'?t('productV1.'+approvalView.snapshot[key]):key==='specificationMode'?t('productStructure.'+approvalView.snapshot[key]):approvalView.snapshot[key]||'—'}}</el-descriptions-item></el-descriptions><el-descriptions :column="1" border><el-descriptions-item v-for="field in approvalView.snapshot.values??[]" :key="field.templateItemId" :label="field.attributeName">{{field.textValue??field.integerValue??field.decimalValue??field.booleanValue??field.dateValue??((approvalView.snapshot.choices??[]).filter((v:any)=>v.templateItemId===field.templateItemId).map((v:any)=>v.label).join(' / ')||'—')}}</el-descriptions-item></el-descriptions><VxeTable :data="approvalView.snapshot.specifications??[]" border><VxeColumn field="name" :title="t('productStructure.specification')"/><VxeColumn :title="t('productStructure.mode')"><template #default="{row}">{{t('productStructure.'+row.mode)}}</template></VxeColumn></VxeTable><el-descriptions :column="1" border><el-descriptions-item v-for="field in approvalView.snapshot.specificationValues??[]" :key="field.specificationId+field.templateItemId" :label="field.attributeName">{{field.textValue??field.dateValue??field.integerValue??field.decimalValue??field.booleanValue??field.optionLabel??((approvalView.snapshot.specificationChoices??[]).filter((c:any)=>c.specificationId===field.specificationId&&c.templateItemId===field.templateItemId).map((c:any)=>c.label).join(' / ')||'—')}}</el-descriptions-item></el-descriptions><VxeTable :data="approvalView.snapshot.components??[]" border><VxeColumn field="componentCode" :title="t('masterData.code')"/><VxeColumn field="componentName" :title="t('masterData.name')"/><VxeColumn field="quantity" :title="t('productConversion.quantity')"/></VxeTable></section><ProductEditor v-else-if="editor" :id="id" :mode="editorMode" :api="api" :error-text="errorText" @close="close"/>
<TreeListTemplate v-else :title="listTitle" :tree-label="categoryTitle" v-model:collapsed="treeCollapsed">
<template #treeHeader>{{ categoryTitle }}</template>
<template #tree>
  <el-tree
    v-if="canListCategories"
    :key="treeGeneration"
    :aria-label="categoryTitle"
    node-key="id"
    :current-node-key="query.categoryId ?? allProducts"
    :default-expanded-keys="[allProducts]"
    :props="{ label: 'name', isLeaf: 'leaf' }"
    highlight-current
    lazy
    :load="loadCategoryTree"
    @node-click="category => selectCategory(category.root ? undefined : category.id, category.currentConfigId)"
  >
    <template #default="{ data }">
      <span class="el-tree-node__label">{{ data.root ? t("productV1.allProducts") : data.name }}</span>
    </template>
  </el-tree>
  <el-text v-else-if="!loading && !listIssue && operations.includes('Product.list')" type="info">{{ t("masterData.noPermission") }}</el-text>
  <el-alert v-if="canListCategories && treeError" :title="treeError" type="error" :closable="false">
    <el-button v-operation="'ProductCategory.list'" text @click="treeError = ''; treeGeneration++">
      {{ t("masterData.retry") }}
    </el-button>
  </el-alert>
</template>
<template #search>
<el-form inline class="product-search" @submit.prevent="search">
<div class="management-filter product-search__basic"><el-form-item :label="t('masterData.name')"><el-input v-model="query.name" maxlength="200" clearable/></el-form-item><el-form-item :label="t('masterData.code')"><el-input v-model="query.code" maxlength="64" clearable/></el-form-item><el-form-item :label="t('productV1.productForm')"><el-select v-model="query.form" clearable style="min-width:140px"><el-option v-for="form in ['PHYSICAL','SERVICE','COMPOSITE']" :key="form" :value="form" :label="t('productV1.'+form)"/></el-select></el-form-item><el-form-item :label="t('masterData.status')"><el-select v-model="query.status" clearable style="min-width:140px"><el-option v-for="status in ['DRAFT','ENABLED','DISABLED']" :key="status" :value="status" :label="t('productV1.'+status)"/></el-select></el-form-item><el-form-item :label="t('productV1.archived')"><el-switch v-model="query.archived" @change="search"/></el-form-item><el-form-item v-if="!canListCategories && operations.includes('Product.options')" :label="t('productV1.filterConfiguration')"><el-select v-model="filterCategory" clearable remote filterable :remote-method="findFilterCategory" @visible-change="v=>v&&findFilterCategory('')" @change="selectFilterCategory"><el-option v-for="r in filterCategories" :key="r.id" :value="r.id" :label="r.name" :disabled="!r.configId"/></el-select></el-form-item>
</div>
<section v-if="filterConfiguration && filterConditions.length" class="attribute-filters" :aria-label="t('productV1.attributeFilters')">
  <el-text size="small" type="info">
    {{ t("productV1.fixedVersion", { version: filterConfiguration.revision }) }} · {{ t("productV1.filterScope") }}
  </el-text>
  <div v-for="(condition, index) in filterConditions" :key="condition.key" class="attribute-filter-row">
    <el-form-item :error="invalidFilterKeys.has(condition.key) && !condition.templateItemId ? t('productV1.filterAttribute') : ''">
      <el-select v-model="condition.templateItemId" :aria-label="t('productV1.filterAttribute')" :placeholder="t('productV1.filterAttribute')" @change="changeOperator(condition, 'eq')">
        <el-option v-for="field in searchableFields()" :key="field.templateItemId" :value="field.templateItemId" :label="field.name"/>
      </el-select>
    </el-form-item>
    <el-select v-model="condition.operator" :aria-label="t('productV1.filterOperator')" :disabled="!condition.templateItemId" @change="operator => changeOperator(condition, operator)">
      <el-option v-for="operator in operators(condition.templateItemId)" :key="operator" :value="operator" :label="t('productV1.op_' + operator)"/>
    </el-select>
    <el-form-item :error="invalidFilterKeys.has(condition.key) && condition.templateItemId && missingFilterValue(condition.values) ? t('masterData.required') : ''">
    <div class="attribute-filter-row__values">
      <el-select v-if="['SINGLE_SELECT', 'MULTI_SELECT'].includes(selectedField(condition.templateItemId)?.dataType ?? '')"
        :model-value="condition.operator === 'anyOf' ? condition.values : condition.values[0]"
        :multiple="condition.operator === 'anyOf'" :aria-label="t('productV1.filterValue')" :placeholder="t('productV1.filterValue')"
        @update:model-value="value => condition.values = Array.isArray(value) ? value : [String(value ?? '')]">
        <el-option v-for="option in selectedField(condition.templateItemId)?.options ?? []" :key="option.id" :value="option.id" :label="option.label"/>
      </el-select>
      <el-select v-else-if="selectedField(condition.templateItemId)?.dataType === 'BOOLEAN'" v-model="condition.values[0]" :aria-label="t('productV1.filterValue')">
        <el-option value="true" :label="t('productV1.yes')"/>
        <el-option value="false" :label="t('productV1.no')"/>
      </el-select>
      <template v-else>
        <el-input v-model="condition.values[0]" :disabled="!condition.templateItemId" :aria-label="t('productV1.filterValue')" :placeholder="selectedField(condition.templateItemId)?.dataType === 'DATE' ? 'YYYY-MM-DD' : t('productV1.filterValue')"/>
        <el-input v-if="condition.operator === 'between'" v-model="condition.values[1]" :aria-label="t('productV1.filterUpper')" :placeholder="t('productV1.filterUpper')"/>
      </template>
    </div>
    </el-form-item>
    <el-button v-operation="'Product.list'" text type="primary" @click="filterConditions.splice(index, 1)">{{ t("productV1.removeCondition") }}</el-button>
  </div>
</section>
<div class="product-search__actions">
  <div class="management-filter-actions">
    <el-button v-if="filterConfiguration && searchableFields().length" v-operation="'Product.list'" :disabled="filterConditions.length >= 3" @click="addCondition">{{ t("productV1.addCondition") }}</el-button>
    <el-button v-operation="'Product.list'" type="primary" native-type="submit" :loading="loading">{{ t("masterData.search") }}</el-button>
    <el-button v-operation="'Product.list'" :disabled="loading" @click="reset">{{ t("masterData.reset") }}</el-button>
  </div>
</div>
</el-form>
</template>
<template #table><FloatingError :title="error"><el-button v-operation="'Product.list'" @click="load">{{t('masterData.retry')}}</el-button></FloatingError><el-result v-if="listIssue" :icon="listIssue.kind === 'denied' ? 'warning' : 'error'" :title="listIssue.message">
  <template v-if="listIssue.retry" #extra>
    <el-button v-operation="'Product.list'" type="primary" @click="load">{{ t("masterData.retry") }}</el-button>
  </template>
</el-result>
<ManagementTable v-else :title="t('productV1.title')" :rows="rows" :columns="columns" :total="total" :loading="loading" @refresh="load">
<template #actions><el-button v-operation="'Product.create'" type="primary" :disabled="loading" @click="open()">{{t('productV1.create')}}</el-button><el-button v-operation="'Product.reconcileLegacy'" :disabled="loading||staging" @click="reconcile">{{t('productV1.legacyReconciliation')}}</el-button></template>
<template #form="{row}">{{row.productForm?t('productV1.'+row.productForm):'—'}}</template><template #status="{row}">{{t('productV1.'+row.status)}}</template><template #structure="{row}">{{t('productV1.'+row.structureState)}}</template><template #operations="{row}"><el-button v-operation="'Product.read'" link type="primary" :disabled="loading" @click="open(row,'view')">{{t('partner.view')}}</el-button><el-button v-operation="'Product.update'" link type="primary" :disabled="loading||!!row.archivedAt||row.structureState==='LEGACY_PENDING'" @click="open(row,'edit')">{{t('masterData.edit')}}</el-button></template>
<template #pagination><AppPagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :disabled="loading" @current-change="load" @size-change="search"/></template>
</ManagementTable></template>
</TreeListTemplate>
<ManagedDialog v-model="legacyOpen" :title="t('productV1.legacyReconciliation')" :busy="staging"><p>{{t('productV1.reconcileWarning')}}</p><VxeTable border show-overflow :column-config="{resizable:true}" :data="legacyRows"><VxeColumn field="code" :title="t('masterData.code')"/><VxeColumn :title="t('masterData.status')"><template #default="{row}">{{t('productV1.reconcile_'+row.result)}}</template></VxeColumn><VxeColumn :title="t('masterData.actions')"><template #default="{row}"><el-button v-if="row.result==='MISSING'" v-operation="'Product.stageLegacy'" link :disabled="staging" @click="stage({id:row.id,code:row.code})">{{t('productV1.stageLegacy')}}</el-button></template></VxeColumn></VxeTable><AppPagination v-model:current-page="legacyPage" :page-size="20" :total="legacyTotal"  :disabled="staging" @current-change="reconcile"/></ManagedDialog></div>
</template>
<style scoped>
/* 保留公共工作区的剩余高度与内部表格滚动。 */
.product-page{display:flex;flex-direction:column;flex:1;min-height:0;min-width:0}
.product-search,
.attribute-filters {
  display: grid;
  gap: var(--app-section-gap, 8px);
}
.product-search__basic {
  display: flex;
  flex-wrap: wrap;
  column-gap: 20px;
  row-gap: var(--app-section-gap, 8px);
}
.attribute-filter-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 2fr) auto;
  align-items: start;
  gap: var(--app-section-gap, 8px);
}
.attribute-filter-row :deep(.el-form-item) {
  min-width: 0;
  margin: 0;
}
.attribute-filter-row :deep(.el-form-item__error) {
  position: static;
  width: 100%;
  line-height: 1.4;
}
.attribute-filter-row__values { width: 100%; }
.attribute-filter-row__values,
.product-search__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-section-gap, 8px);
}
.attribute-filter-row__values > * { flex: 1 1 120px; min-width: 0; }
.product-search__actions .management-filter-actions { width: auto; }
@media (max-width: 760px) {
  .attribute-filter-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
