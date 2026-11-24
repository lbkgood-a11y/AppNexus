<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { AppPagination, ManagedDialog } from "@appnexus/ui";
import type { ProductApi, ProductChange, ProductRecord } from "./product";

/** 商品及规格、SKU、组件共用授权的商品聚合历史；分页按父商品事件进行。 */
const props = defineProps<{
  id: string;
  api: ProductApi;
  errorText: (error: unknown) => string;
  disabled?: boolean;
  link?: boolean;
  subject?: { collection: "specifications" | "skus" | "components"; id: string };
}>();
const { t, locale } = useI18n();
const changes=ref<ProductChange[]>([]),historyPage=ref(1),historyTotal=ref(0);
const historyOpen=ref(false),historyLoading=ref(false),historyError=ref(""),historyDenied=ref(false);
let historyRequest=0;
function displayTime(value:string|null|undefined){if(!value)return t("recordAudit.unknown");const date=new Date(value);return Number.isNaN(date.getTime())?t("recordAudit.unknown"):new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"medium"}).format(date);}
/** 每次打开按原读取授权请求第一页；历史异常仅影响弹窗，不清理详情或草稿。 */
function openHistory(){
 historyPage.value=1;
 historyOpen.value=true;
 void readHistory();
}
async function readHistory(){
 if(!historyOpen.value)return;
 const request=++historyRequest;
 historyLoading.value=true;
 historyError.value="";
 historyDenied.value=false;
 try{
  const data=await props.api.changes(props.id,historyPage.value,20);
  if(request!==historyRequest)return;
  changes.value=data.rows;
  historyTotal.value=data.total;
 }catch(cause){
  if(request===historyRequest){
   changes.value=[];
   historyTotal.value=0;
   historyDenied.value=(cause as {response?:{status:number}})?.response?.status===403;
   historyError.value=props.errorText(cause);
  }
 }finally{
  if(request===historyRequest)historyLoading.value=false;
 }
}
watch(historyOpen,open=>{if(!open){++historyRequest;changes.value=[];historyError.value="";historyLoading.value=false;historyTotal.value=0;}},{flush:"sync"});
watch(() => props.id, () => { historyOpen.value = false; });
function snapshotText(value:string|null){if(!value)return t("recordAudit.unknown");try{const row=JSON.parse(value) as ProductRecord&{changeReason?:string};if (props.subject) {
 const item=JSON.parse(value)[props.subject.collection]?.find((entry: {id?:string;componentId?:string})=>(entry.id??entry.componentId)===props.subject?.id);
 return item?.enabled === true || item?.enabled === 1 ? t("masterData.enabled") :
  item?.enabled === false || item?.enabled === 0 ? t("masterData.disabled") : "—";
}return [row.name,...(row.changeReason?[t("productV1.reason")+": "+row.changeReason]:[]),t("productV1."+row.status),t("productV1.version")+": "+row.version,t("productV1.structureVersion")+": "+row.structureRevision,...(row.values??[]).map(v=>v.attributeName+": "+(v.textValue??v.integerValue??v.decimalValue??v.booleanValue??v.dateValue??row.choices?.filter(c=>c.templateItemId===v.templateItemId).map(c=>c.label).join(" / ")??"—"))].join("\n");}catch{return t("productV1.snapshotUnsupported");}}
</script>
<template>
  <el-button v-operation="'Product.readChanges'" :link="link" :disabled="disabled" @click="openHistory">{{ t("productV1.history") }}</el-button>
<ManagedDialog v-model="historyOpen" :title="t('productV1.history')" width="min(1080px, calc(100vw - 32px))">
 <el-result v-if="historyError" :icon="historyDenied?'warning':'error'" :title="historyError">
  <template v-if="!historyDenied" #extra><el-button v-operation="'Product.readChanges'" @click="readHistory">{{t('masterData.retry')}}</el-button></template>
 </el-result>
 <template v-else><VxeTable border :loading="historyLoading" :column-config="{resizable:true}" :data="changes"><VxeColumn field="version" :title="t('productV1.version')" width="90"/><VxeColumn :title="t('productV1.event')"><template #default="{row}">{{t('productV1.event_'+row.eventType)}}</template></VxeColumn><VxeColumn :title="t('recordAudit.createdBy')" min-width="160"><template #default="{row}">{{row.createdBy || t('recordAudit.unknownActor')}}</template></VxeColumn><VxeColumn :title="t('recordAudit.createdAt')" min-width="180"><template #default="{row}">{{displayTime(row.createdAt)}}</template></VxeColumn><VxeColumn :title="t('productV1.before')"><template #default="{row}"><pre>{{row.schemaVersion===1?snapshotText(row.beforeSnapshot):t('productV1.snapshotUnsupported')}}</pre></template></VxeColumn><VxeColumn :title="t('productV1.after')"><template #default="{row}"><pre>{{row.schemaVersion===1?snapshotText(row.afterSnapshot):t('productV1.snapshotUnsupported')}}</pre></template></VxeColumn></VxeTable><AppPagination v-model:current-page="historyPage" :page-size="20" :total="historyTotal" :disabled="historyLoading" @current-change="readHistory"/></template>
</ManagedDialog>
</template>
