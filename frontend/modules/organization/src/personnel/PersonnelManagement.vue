<script setup lang="ts">
import {computed,onMounted,ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,ElMessageBox} from "element-plus";
import {BusinessWorkspace,ManagementTable,AppPagination,FloatingError,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import PersonnelEditor from "./PersonnelEditor.vue";
import type {PersonnelRow} from "./types";
import type {PersonnelApi} from "./personnel";
/** 人员列表仅展示摘要，编辑时从真实详情接口读取；不在列表预加载证件或履历正文。 */
const props=defineProps<{api:PersonnelApi;errorText:(e:unknown)=>string}>();
const {t}=useI18n();
const rows=ref<PersonnelRow[]>([]),total=ref(0),page=ref(1),size=ref(20),keyword=ref(""),status=ref("all");
const loading=ref(false),busy=ref(false),failed=ref(false),denied=ref(false),error=ref(""),editor=ref(false),selected=ref("");
const columns=computed<ManagementColumn[]>(()=>[
 {key:"code",prop:"code",label:t("personnel.code"),minWidth:150},{key:"name",prop:"name",label:t("personnel.name"),minWidth:140},
 {key:"phone",label:t("personnel.fields.phone"),minWidth:150},{key:"email",label:t("personnel.fields.email"),minWidth:180},
 {key:"employment",label:t("personnel.employmentStatus"),minWidth:130},{key:"status",label:t("personnel.status"),width:100},
 {key:"operations",label:t("masterData.actions"),width:210,fixed:"right"}]);
/** 每次加载重新验证准入，403 清除原档案，网络故障保留原结果供核实。 */
async function load(){if(loading.value||busy.value)return;loading.value=true;failed.value=false;
 try{const allowed=await props.api.operations();denied.value=!allowed.includes("Person.list");if(denied.value){rows.value=[];total.value=0;return;}
 const result=await props.api.list("person",{keyword:keyword.value,enabled:status.value==="all"?undefined:status.value==="enabled",page:page.value,size:size.value});rows.value=result.rows;total.value=result.total;
 }catch(e){failed.value=true;if((e as {response?:{status:number}})?.response?.status===403){rows.value=[];denied.value=true;}await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
/** 查询条件变化回到第一页。 */
function search(){page.value=1;void load();}
/** 仅重置查询条件并读取，无业务写入。 */
function reset(){keyword.value="";status.value="all";search();}
/** 打开人员详情，不把列表摘要当成完整档案。 */
function show(id=""){selected.value=id;editor.value=true;error.value="";}
/** 关闭详情后重新读取，保持列表当前筛选及页码。 */
function close(){editor.value=false;void load();}
/** 启停确认说明业务影响，使用当前行版本；失败不修改原行状态。 */
async function toggle(row:PersonnelRow){if(busy.value)return;
 try{await ElMessageBox.confirm(t("masterData."+(row.enabled?"confirmDisable":"confirmEnable"),{name:row.name}),t("masterData.confirmTitle"),{type:"warning",confirmButtonText:t("masterData."+(row.enabled?"disable":"enable")),cancelButtonText:t("masterData.cancel")});}catch{return;}
 busy.value=true;try{await props.api.setStatus("person",row.id,{enabled:!row.enabled,version:row.version});ElMessage.success(t("masterData.statusSaved"));busy.value=false;await load();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
onMounted(load);
</script>
<template>
 <PersonnelEditor v-if="editor" :id="selected" :api="api" :error-text="errorText" @close="close" />
 <BusinessWorkspace v-else :title="t('masterData.person')" :description="t('masterData.description')">
  <template #search><el-form inline @submit.prevent="search">
   <el-form-item :label="t('masterData.keyword')"><el-input v-model="keyword" maxlength="200" clearable /></el-form-item>
   <el-form-item :label="t('masterData.status')"><el-select v-model="status" style="width:140px"><el-option v-for="value in ['all','enabled','disabled']" :key="value" :value="value" :label="t('masterData.'+value)" /></el-select></el-form-item>
   <el-form-item class="management-filter-actions"><el-button v-operation="'Person.list'" type="primary" native-type="submit" :loading="loading">{{t('masterData.search')}}</el-button><el-button :disabled="loading||busy" @click="reset">{{t('masterData.reset')}}</el-button></el-form-item>
  </el-form></template>
  <template #data>
   <FloatingError :title="error"><el-button :disabled="loading||busy" @click="load">{{t('personnel.retry')}}</el-button></FloatingError>
   <el-result v-if="denied" icon="warning" :title="t('masterData.noPermission')" />
   <el-result v-else-if="failed&&!rows.length" icon="error" :title="t('masterData.unavailable')"><template #extra><el-button @click="load">{{t('personnel.retry')}}</el-button></template></el-result>
   <ManagementTable v-else :title="t('masterData.person')" :rows="rows" :columns="columns" :loading="loading" :busy="busy" :total="total" embedded @refresh="load">
    <template #actions><el-button v-operation="'Person.create'" type="primary" :disabled="loading||busy" @click="show()">{{t('personnel.new')}}</el-button></template>
    <template #phone="{row}">{{row.attributes.phone||'—'}}</template><template #email="{row}">{{row.attributes.email||'—'}}</template>
    <template #employment="{row}">{{row.attributes.employmentStatus?t('personnel.choices.'+row.attributes.employmentStatus):'—'}}</template>
    <template #status="{row}"><el-tag :type="row.enabled?'success':'info'">{{t('masterData.'+(row.enabled?'enabled':'disabled'))}}</el-tag></template>
    <template #operations="{row}"><el-button v-operation="'Person.read'" link type="primary" :disabled="loading||busy" @click="show(row.id)">{{t('personnel.details')}}</el-button><el-button v-operation="'Person.setStatus'" link :type="row.enabled?'danger':'primary'" :disabled="loading||busy" @click="toggle(row)">{{t('masterData.'+(row.enabled?'disable':'enable'))}}</el-button></template>
    <template #pagination><AppPagination v-model:current-page="page" v-model:page-size="size" :total="total" :disabled="loading||busy" @current-change="load" @size-change="search" /></template>
   </ManagementTable>
  </template>
 </BusinessWorkspace>
</template>
