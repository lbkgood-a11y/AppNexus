<script setup lang="ts">
import {computed,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {BusinessWorkspace,ManagementTable,AppPagination,FloatingError,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import ProductEditor from "./ProductEditor.vue";
import type {ProductApi,ProductRow,ProductQuery} from "./product";
/** V1主档工作区；独立验证入口不替换旧商品写菜单，正式切换仍需存量对账。 */
const props=defineProps<{api:ProductApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();
const rows=ref<ProductRow[]>([]),total=ref(0),loading=ref(false),failed=ref(false),error=ref(""),operations=ref<string[]>([]);
const editor=ref(false),id=ref("");
const query=reactive<ProductQuery>({name:"",code:"",form:undefined,status:undefined,archived:false,page:1,size:20});
const columns=computed<ManagementColumn[]>(()=>[{key:"code",prop:"code",label:t("masterData.code"),minWidth:140},{key:"name",prop:"name",label:t("masterData.name"),minWidth:180},{key:"form",label:t("productV1.productForm"),width:120},{key:"status",label:t("masterData.status"),width:140},{key:"structure",label:t("productV1.structure"),width:160},{key:"operations",label:t("masterData.actions"),width:140}]);
async function load(){loading.value=true;failed.value=false;try{operations.value=await props.api.operations();if(!operations.value.includes("ProductV1.list")){rows.value=[];total.value=0;return;}const result=await props.api.list(query);rows.value=result.rows;total.value=result.total;}catch(e){failed.value=true;if((e as {response?:{status:number}}).response?.status===403){rows.value=[];operations.value=[];}await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
function search(){query.page=1;void load();}
function open(row?:ProductRow){id.value=row?.id??"";editor.value=true;}
function close(){editor.value=false;void load();}
onMounted(load);
</script>
<template>
<ProductEditor v-if="editor" :id="id" :api="api" :error-text="errorText" @close="close"/>
<BusinessWorkspace v-else :title="t('productV1.title')" :description="t('productV1.descriptionHint')">
<template #search><el-form inline @submit.prevent="search"><el-form-item :label="t('masterData.name')"><el-input v-model="query.name" maxlength="200" clearable/></el-form-item><el-form-item :label="t('masterData.code')"><el-input v-model="query.code" maxlength="64" clearable/></el-form-item><el-form-item :label="t('productV1.productForm')"><el-select v-model="query.form" clearable style="min-width:140px"><el-option v-for="form in ['PHYSICAL','SERVICE']" :key="form" :value="form" :label="t('productV1.'+form)"/></el-select></el-form-item><el-form-item :label="t('masterData.status')"><el-select v-model="query.status" clearable style="min-width:140px"><el-option v-for="status in ['DRAFT','ENABLED','DISABLED']" :key="status" :value="status" :label="t('productV1.'+status)"/></el-select></el-form-item><el-form-item :label="t('productV1.archived')"><el-switch v-model="query.archived" @change="search"/></el-form-item><el-button v-operation="'ProductV1.list'" type="primary" native-type="submit" :loading="loading">{{t('masterData.search')}}</el-button></el-form></template>
<template #data><FloatingError :title="error"><el-button v-operation="'ProductV1.list'" @click="load">{{t('masterData.retry')}}</el-button></FloatingError><el-result v-if="failed&&!rows.length" icon="error" :title="t('masterData.unavailable')"/><el-result v-else-if="!loading&&!operations.includes('ProductV1.list')" icon="warning" :title="t('masterData.noPermission')"/>
<ManagementTable v-else :title="t('productV1.title')" :rows="rows" :columns="columns" :total="total" :loading="loading" @refresh="load">
<template #actions><el-button v-operation="'ProductV1.create'" type="primary" :disabled="loading" @click="open()">{{t('productV1.create')}}</el-button></template>
<template #form="{row}">{{row.productForm?t('productV1.'+row.productForm):'—'}}</template><template #status="{row}">{{t('productV1.'+row.status)}}</template><template #structure="{row}">{{t('productV1.'+row.structureState)}}</template><template #operations="{row}"><el-button v-operation="'ProductV1.read'" link type="primary" @click="open(row)">{{t('productV1.details')}}</el-button></template>
<template #pagination><AppPagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :disabled="loading" @current-change="load" @size-change="search"/></template>
</ManagementTable></template>
</BusinessWorkspace>
</template>
