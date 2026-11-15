<script setup lang="ts">
import { setErrorFeedback } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import { InfoHint } from "@appnexus/ui";
/** 只读功能目录：复用业务工作区与管理表格，不提供代码登记信息的编辑入口。 */
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { AppIcon } from "@appnexus/icons";
import { BusinessWorkspace, ManagementTable } from "@appnexus/ui";
import type { FeatureRegistration, MenuScope, OperationDefinition, ObjectDefinition } from "./index";
import { buildFeatureTree } from "./featureTree";
const props = defineProps<{
  /** 读取当前构建功能快照；服务端负责平台身份及权限检查。 */
  load: () => Promise<FeatureRegistration[]>;
  /** 从实际处理方法读取的操作目录，不与页面权限列表混淆。 */
  loadOperations: () => Promise<OperationDefinition[]>;
  /** 全模块对象目录，不以是否支持策略配置过滤。 */
  loadObjects: () => Promise<ObjectDefinition[]>;
  /** 将真实请求错误转换为用户文案，不暴露内部异常。 */
  errorText: (cause: unknown) => string;
}>();
const { t, locale } = useI18n();
const registrations = ref<FeatureRegistration[]>([]);
const operations = ref<OperationDefinition[]>([]);
const objects=ref<ObjectDefinition[]>([]),view=ref("pages");
const loading = ref(false), error = ref("");
const keyword = ref(""), scope = ref<MenuScope | "">("");
const query = ref(""), selectedScope = ref<MenuScope | "">("");
const expandedKeys = ref<string[]>([]);
const rows = computed(() => {
 if(view.value==='pages')return buildFeatureTree(registrations.value,query.value,selectedScope.value,locale.value.startsWith('zh'),operations.value);
 const needle=query.value.trim().toLowerCase();
 const allowedPages=new Set(registrations.value.filter(page=>!selectedScope.value||page.page.scope===selectedScope.value).map(page=>page.page.key));
 return objects.value.flatMap(object=>{
  const match=[object.code,object.nameZh,object.nameEn,object.module].some(value=>value.toLowerCase().includes(needle));
  const children=object.operations.filter(operation=>operation.pages.some(page=>allowedPages.has(page))&&(match||[operation.id,operation.nameZh,operation.nameEn,...operation.permissions].some(value=>value.toLowerCase().includes(needle)))).map(operation=>({id:operation.id,type:'operation' as const,title:locale.value.startsWith('zh')?operation.nameZh:operation.nameEn,objectType:object.code,operationCode:operation.operationCode,operationId:operation.id,permission:operation.permissions.join(' OR '),endpoints:operation.endpoints.join('；'),policy:operation.policy}));
  return children.length?[{id:object.code,type:'object' as const,title:locale.value.startsWith('zh')?object.nameZh:object.nameEn,objectType:object.code,category:object.category,module:object.module,children}]:[];
 });
});
/** 每次操作生成新的展开快照，用户手动折叠后仍可再次使用全部展开。 */
function expandAll(value: boolean) {
  expandedKeys.value = value ? rows.value.flatMap(row => [row.id, ...(row.children ?? []).map(page => page.id)]) : [];
}
/** 首次取得非空数据时初始化；后续查询、刷新及语言切换保留用户展开状态。 */
let treeInitialized = false;
watch(rows, value => {
  if (treeInitialized || !value.length) return;
  treeInitialized = true;
  expandedKeys.value = value.slice(0, 1).map(row => row.id);
}, { immediate: true });
/** 同步手动展开、收起；保留暂时被筛选隐藏的节点状态。 */
function updateExpansion(row: { id: string }, expanded: boolean) {
  if (expandedKeys.value.includes(row.id) === expanded) return;
  expandedKeys.value = expanded
    ? [...expandedKeys.value, row.id]
    : expandedKeys.value.filter(key => key !== row.id);
}
const columns = computed(() => [
  { key: "title", label: t("featureCatalog.name"), minWidth: 240 },
  ...(view.value==='objects'?[{key:"category",label:t("featureCatalog.category"),minWidth:140},{key:"module",prop:"module",label:t("featureCatalog.module"),minWidth:140}]:[]),
  { key: "type", label: t("featureCatalog.type"), width: 110 },
  { key: "permission", prop: "permission", label: t("featureCatalog.permission"), minWidth: 280 },
  { key: "objectType", prop: "objectType", label: t("featureCatalog.objectType"), minWidth: 180 },
  { key: "operationCode", prop: "operationCode", label: t("featureCatalog.operationCode"), minWidth: 160 },
  { key: "operationId", prop: "operationId", label: t("featureCatalog.operationId"), minWidth: 240 },
  { key: "endpoints", prop: "endpoints", label: t("featureCatalog.endpoints"), minWidth: 350 },
  { key: "policy", prop: "policy", label: t("featureCatalog.policy"), minWidth: 300 },
  { key: "path", prop: "path", label: t("featureCatalog.path"), minWidth: 220 },
  { key: "component", prop: "component", label: t("featureCatalog.component"), minWidth: 220 },
  { key: "gate", label: t("featureCatalog.gate"), minWidth: 160 }
]);
/** 刷新失败清除旧数据，避免将失效的权限快照继续展示为当前结果。 */
async function refresh() {
  if (loading.value) return;
  loading.value = true; error.value = "";
  try { const result = await Promise.all([props.load(),props.loadOperations(),props.loadObjects()]); registrations.value = result[0]; operations.value = result[1]; objects.value=result[2]; }
  catch (cause) { registrations.value = []; operations.value = []; objects.value=[]; await setErrorFeedback(error, props.errorText(cause)); }
  finally { loading.value = false; }
}
/** 显式提交筛选；重置仅清理查询条件，保留当前树展开状态。 */
function search(reset = false) {
  if (reset) { keyword.value = ""; scope.value = ""; }
  query.value = keyword.value; selectedScope.value = scope.value;
}
onMounted(refresh);
</script>
<template>
  <BusinessWorkspace :title="t('featureCatalog.title')">
    <template #navigation><div class="feature-navigation"><el-tabs v-model="view"><el-tab-pane :label="t('featureCatalog.pageView')" name="pages"/><el-tab-pane :label="t('featureCatalog.objectView')" name="objects"/></el-tabs>
      <!-- 说明按需展开以节省垂直空间；错误保留在反馈区，不能折叠成不可发现状态。 -->
      
    </div></template>
    <template v-if="error" #feedback>
      <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" show-icon />
    </template>
    <template #search>
      <el-form class="management-filter" inline @submit.prevent="search()">
        <el-form-item :label="t('featureCatalog.keyword')"><el-input v-model="keyword" clearable :placeholder="t('featureCatalog.placeholder')" /></el-form-item>
        <el-form-item :label="t('featureCatalog.scope')"><el-select v-model="scope" clearable class="feature-scope"><el-option v-for="value in ['PLATFORM','TENANT','AUTHENTICATED']" :key="value" :value="value" :label="t('menuManagement.scopes.' + value)" /></el-select></el-form-item>
        <el-form-item class="management-filter-actions"><el-button type="primary" native-type="submit"><AppIcon name="search" :size="16" />{{ t('featureCatalog.search') }}</el-button><el-button @click="search(true)"><AppIcon name="refresh" :size="16" />{{ t('featureCatalog.reset') }}</el-button></el-form-item>
      </el-form>
    </template>
    <template #data>
      <ManagementTable embedded :title="t('featureCatalog.title')" :rows="rows" :columns="columns" :loading="loading" row-key="id" :expanded-row-keys="expandedKeys" :current-row-key="rows[0]?.id" @refresh="refresh" @expand-change="updateExpansion"><template #titleHelp><InfoHint :content="t('featureCatalog.hint')" /></template>
        <template #actions><el-button @click="expandAll(true)">{{ t('featureCatalog.expand') }}</el-button><el-button @click="expandAll(false)">{{ t('featureCatalog.collapse') }}</el-button></template>
        <template #title="{row}">{{ row.type === 'scope' ? t('menuManagement.scopes.' + row.title) : row.title }}</template>
        <template #category="{row}">{{ row.category ? t('businessObject.categories.'+row.category) : '—' }}</template>
        <template #type="{row}"><el-tag :type="row.type === 'operation' ? 'warning' : row.type === 'scope' ? 'primary' : 'info'">{{ t('featureCatalog.types.' + row.type) }}</el-tag></template>
        <template #gate="{row}">{{ row.gate ? t('featureCatalog.gates.' + row.gate) : '—' }}</template>
      </ManagementTable>
    </template>
  </BusinessWorkspace>
</template>
<style>
/* 限制说明浮层在窄屏内，长文案换行且不撑开页面。 */
.feature-help-popover{max-width:calc(100vw - 32px);box-sizing:border-box}
</style>
<style scoped>
.feature-navigation{display:flex;align-items:center;gap:12px}
.feature-navigation :deep(.el-tabs){flex:1;min-width:0}
.feature-help-button{flex:none;width:32px;height:32px;padding:0;color:var(--el-text-color-secondary)}
.feature-help-content{margin:0;line-height:1.7;color:var(--el-text-color-regular);font-size:13px}
@media(pointer:coarse){.feature-help-button{width:44px;height:44px}}

.feature-scope { width: 180px; }
</style>
