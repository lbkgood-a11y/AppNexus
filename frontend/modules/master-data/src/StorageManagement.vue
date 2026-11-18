<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { FloatingError, setErrorFeedback } from "@appnexus/ui";
import Management from "./Management.vue";
import type { MasterDataApi, MasterDataRow } from "./index";

/** 库区与库位同页维护；复用公共管理工作区，父级选择不授予任何写权限。 */
const props=defineProps<{api:MasterDataApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();
const tab=ref<"storage-area"|"storage-location">("storage-area");
const warehouses=ref<MasterDataRow[]>([]),areas=ref<MasterDataRow[]>([]);
const warehouse=ref<MasterDataRow>(),area=ref<MasterDataRow>();
const warehousePage=ref(1),areaPage=ref(1),warehouseTotal=ref(0),areaTotal=ref(0);
const warehouseQuery=ref(""),areaQuery=ref("");
const loading=ref(false),error=ref(""),revision=ref(0);
const parent=computed(()=>tab.value==="storage-area"?warehouse.value:area.value);
let request=0;

/** 父级目录按20条分页，不截断大型仓库；请求序号阻止旧租户范围或旧检索响应覆盖当前选择。 */
async function loadParents(){
  const current=++request;loading.value=true;
  try{
    error.value="";
    const result=await props.api.list("warehouse",{keyword:warehouseQuery.value,page:warehousePage.value,size:20});
    if(current!==request)return;
    warehouses.value=result.rows;warehouseTotal.value=result.total;
    if(warehouse.value){
      const result=await props.api.list("storage-area",{keyword:areaQuery.value,page:areaPage.value,size:20,scope:warehouse.value.code});
      if(current!==request)return;
      areas.value=result.rows;areaTotal.value=result.total;
    }else{areas.value=[];areaTotal.value=0;}
  }catch(cause){if(current===request){warehouses.value=[];areas.value=[];if((cause as {response?:{status?:number}})?.response?.status===403){warehouse.value=undefined;area.value=undefined;}await setErrorFeedback(error,props.errorText(cause));}}
  finally{if(current===request)loading.value=false;}
}
/** 切换仓库清空库区及分页，避免将上一仓库下的库位显示或新增到当前范围。 */
function selectWarehouse(code:string){
  warehouse.value=warehouses.value.find(row=>row.code===code)??(warehouse.value?.code===code?warehouse.value:undefined);area.value=undefined;areas.value=[];areaPage.value=1;areaQuery.value="";revision.value++;void loadParents();
}
/** 库区写入后重新读取父级快照，保留当前编码；新建库区可在选择器中立即检索。 */
async function refreshParents(){
  await loadParents();
  if(area.value){
    const selectedId=area.value.id,warehouseCode=warehouse.value?.code;
    try{
      const result=await props.api.list("storage-area",{keyword:"",page:1,size:1,id:selectedId,scope:warehouseCode});
      const selected=result.rows[0];
      if(!selected)throw new Error("MASTER_DATA_CHANGED");
      if(area.value?.id===selectedId&&warehouse.value?.code===warehouseCode)area.value=selected;
    }catch(cause){if(area.value?.id===selectedId)area.value=undefined;await setErrorFeedback(error,props.errorText(cause));}
  }
}
onMounted(loadParents);
</script>

<template>
  <Management :key="tab+':'+(parent?.code??'')+':'+revision" :kind="tab" :api="api" :error-text="errorText" :storage-parent="parent" @changed="refreshParents">
    <template #navigation>
      <FloatingError :title="error"><el-button v-operation="'Warehouse.list'" :loading="loading" @click="loadParents">{{t('masterData.retry')}}</el-button></FloatingError>
      <el-form inline class="storage-context" @submit.prevent>
        <el-form-item :label="t('storageHierarchy.warehouse')">
          <el-select :model-value="warehouse?.code" filterable remote clearable :loading="loading" :remote-method="query=>{warehouseQuery=query;warehousePage=1;loadParents()}" @change="selectWarehouse">
            <el-option v-if="warehouse&&!warehouses.some(row=>row.id===warehouse?.id)" :value="warehouse.code" :label="warehouse.code+' · '+warehouse.name" />
            <el-option v-for="row in warehouses" :key="row.id" :label="row.code+' · '+row.name" :value="row.code" />
            <template #footer><el-pagination v-model:current-page="warehousePage" small layout="prev,pager,next" :pager-count="5" :page-size="20" :total="warehouseTotal" :disabled="loading" @current-change="loadParents" /></template>
          </el-select>
        </el-form-item>
        <el-form-item :label="t('storageHierarchy.area')">
          <el-select :model-value="area?.code" filterable remote clearable :disabled="!warehouse||loading" :remote-method="query=>{areaQuery=query;areaPage=1;loadParents()}" @change="code=>{area=areas.find(row=>row.code===code)??(area?.code===code?area:undefined)}">
            <el-option v-if="area&&!areas.some(row=>row.id===area?.id)" :value="area.code" :label="area.code+' · '+area.name" />
            <el-option v-for="row in areas" :key="row.id" :label="row.code+' · '+row.name" :value="row.code" />
            <template #footer><el-pagination v-model:current-page="areaPage" small layout="prev,pager,next" :pager-count="5" :page-size="20" :total="areaTotal" :disabled="loading" @current-change="loadParents" /></template>
          </el-select>
        </el-form-item>
      </el-form>
      <el-tabs v-model="tab"><el-tab-pane :label="t('masterData.storage-area')" name="storage-area" /><el-tab-pane :label="t('masterData.storage-location')" name="storage-location" /></el-tabs>
      <el-text v-if="!parent" type="info">{{t(tab==='storage-area'?'storageHierarchy.selectWarehouse':'storageHierarchy.selectArea')}}</el-text>
    </template>
  </Management>
</template>

<style scoped>
/* 选择器遵循模板宽度和主题，窄屏自动换行，避免正文横向溢出。 */
.storage-context :deep(.el-select){width:260px;max-width:100%;}
.storage-context :deep(.el-form-item){max-width:100%;}
</style>
