<script setup lang="ts">
import { computed,onMounted,reactive,ref } from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,ElMessageBox,type FormInstance} from "element-plus";
import {BusinessWorkspace,ManagementTable,ManagedDialog,AppPagination,FloatingError,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import type {CatalogKind,CatalogRow,CatalogVersion,ProductCatalogApi} from "./catalog";
/** 商品配置领域组合：模板容器不负责固定修订/四槽发布，因此在领域模块组合现有表单与表格。 */
const props=defineProps<{kind:CatalogKind;api:ProductCatalogApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();
const prefix=computed(()=>({unit:"ProductUnit",category:"ProductCategory",attribute:"ProductAttribute",template:"ProductTemplate"})[props.kind]);
const rows=ref<CatalogRow[]>([]),total=ref(0),page=ref(1),size=ref(20),keyword=ref(""),parent=ref<string>(),ancestors=ref<CatalogRow[]>([]);
const operations=ref<string[]>([]),loading=ref(false),busy=ref(false),failed=ref(false),error=ref("");
const dialog=ref(false),historyOpen=ref(false),mode=ref<"create"|"edit"|"revision"|"version"|"config">("create"),selected=ref<CatalogRow>();
const history=ref<CatalogVersion[]>([]),form=ref<FormInstance>();
const initialOptions=ref([{code:"",label:""}]);
const candidates=ref<CatalogRow[]>([]),revisionOptions=ref<CatalogVersion[]>([]),templateOptions=ref<Array<CatalogVersion&{purpose:string;label:string}>>([]);
const draft=reactive({code:"",name:"",dimensionCode:"",precisionScale:0,parentId:null as string|null,purpose:"BASIC",dataType:"TEXT",unitId:null as string|null,numericScale:0,revisionId:"",attributeId:"",required:false,serviceUnitRequired:false,BASIC:null as string|null,QUALITY:null as string|null,EXTENSION:null as string|null});
/** 固定操作语义映射；只选择实际Controller声明，不拼权限码或不完整操作名。 */
const operationMap={
 unit:{list:"ProductUnit.list",create:"ProductUnit.create",update:"ProductUnit.update",status:"ProductUnit.setStatus",readVersions:"",newVersion:"",publish:""},
 category:{list:"ProductCategory.list",create:"ProductCategory.create",update:"ProductCategory.update",status:"ProductCategory.setStatus",readVersions:"",newVersion:"",publish:""},
 attribute:{list:"ProductAttribute.list",create:"ProductAttribute.create",update:"",status:"",readVersions:"ProductAttribute.readRevisions",newVersion:"ProductAttribute.createRevision",publish:"ProductAttribute.publishRevision"},
 template:{list:"ProductTemplate.list",create:"ProductTemplate.create",update:"",status:"",readVersions:"ProductTemplate.readVersions",newVersion:"ProductTemplate.createVersion",publish:"ProductTemplate.publish"}
};
const operation=computed(()=>operationMap[props.kind]);
const title=computed(()=>t("productCatalog."+props.kind));
const columns=computed<ManagementColumn[]>(()=>[{key:"code",prop:"code",label:t("masterData.code"),minWidth:150},{key:"name",prop:"name",label:t("masterData.name"),minWidth:180},...(props.kind==="unit"?[{key:"dimensionCode",prop:"dimensionCode",label:t("productCatalog.dimension"),minWidth:120},{key:"precisionScale",prop:"precisionScale",label:t("productCatalog.precision"),width:90}]:[]),...(props.kind==="template"?[{key:"purpose",prop:"purpose",label:t("productCatalog.purpose"),width:120}]:[]),{key:"state",label:t("masterData.status"),width:100},{key:"operations",label:t("masterData.actions"),width:400}]);
const canRead=computed(()=>operations.value.includes(prefix.value+".list"));
const saveOperation=computed(()=>mode.value==="config"?"ProductCategory.publishConfiguration":mode.value==="revision"?"ProductAttribute.createRevision":mode.value==="version"?"ProductTemplate.createVersion":mode.value==="edit"?operation.value.update:operation.value.create);
/** 读取失败保留非敏感输入，撤权则清除正文，不把拒绝当空成功。 */
async function load(){if(busy.value)return;loading.value=true;failed.value=false;try{operations.value=await props.api.operations();if(!canRead.value){rows.value=[];total.value=0;return;}const data=await props.api.list(props.kind,{keyword:keyword.value,page:page.value,size:size.value,parentId:parent.value});rows.value=data.rows;total.value=data.total;}catch(e){failed.value=true;if((e as {response?:{status:number}}).response?.status===403){rows.value=[];total.value=0;operations.value=[];}await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
function search(){page.value=1;void load();}
function descend(row:CatalogRow){ancestors.value.push(row);parent.value=row.id;search();}
function up(){ancestors.value.pop();parent.value=ancestors.value.at(-1)?.id;search();}
/** 打开领域表单时只请求实际需要的授权目录；没有权限时失败而不造候选。 */
async function edit(which:typeof mode.value,row?:CatalogRow){
 initialOptions.value=[{code:"",label:""}];selected.value=row;mode.value=which;draft.code=row?.code??"";draft.name=row?.name??"";draft.dimensionCode=row?.dimensionCode??"";draft.precisionScale=row?.precisionScale??0;draft.parentId=row?.parentId??parent.value??null;draft.purpose=row?.purpose??"BASIC";draft.BASIC=null;draft.QUALITY=null;draft.EXTENSION=null;draft.serviceUnitRequired=false;draft.attributeId="";draft.revisionId="";
 try{
 if(which==="version"){candidates.value=(await props.api.list("attribute",{keyword:"",page:1,size:100})).rows;}
 if(which==="config"){
 templateOptions.value=[];
 const templates=(await props.api.list("template",{keyword:"",page:1,size:100})).rows;
 for(const item of templates){for(const v of await props.api.versions("template",item.id))if(v.state==="PUBLISHED")templateOptions.value.push({...v,purpose:item.purpose??"",label:item.code+" / "+v.versionNo});}
 }
 dialog.value=true;
 }catch(e){await setErrorFeedback(error,props.errorText(e));}
}
async function chooseAttribute(){draft.revisionId="";revisionOptions.value=await props.api.versions("attribute",draft.attributeId);}
/** 每次保存只执行一个已授权命令；失败保留草稿，成功后刷新父CAS，绝不自动重放写入。 */
async function save(){
 if(busy.value||!await form.value?.validate().catch(()=>false))return;
 if(mode.value==="config"){try{await ElMessageBox.confirm(t("productCatalog.publishWarning"),title.value,{confirmButtonText:t("productCatalog.publish"),cancelButtonText:t("masterData.cancel")});}catch{return;}}
 busy.value=true;
 try{
 const version=selected.value?.version??0;
 if(mode.value==="config")await props.api.publishConfiguration(selected.value!.id,{templates:{BASIC:draft.BASIC,SPEC:null,QUALITY:draft.QUALITY,EXTENSION:draft.EXTENSION},serviceUnitRequired:draft.serviceUnitRequired,version});
 else if(mode.value==="revision")await props.api.createRevision(selected.value!.id,{name:draft.name,dataType:draft.dataType,unitId:null,numericScale:["INTEGER","DECIMAL"].includes(draft.dataType)?draft.dataType==="INTEGER"?0:draft.numericScale:null,validationJson:"{}",options:["SINGLE_SELECT","MULTI_SELECT"].includes(draft.dataType)?initialOptions.value:[],version});
 else if(mode.value==="version")await props.api.createVersion(selected.value!.id,{description:draft.name,items:[{attributeId:draft.attributeId,revisionId:draft.revisionId,groupCode:"",groupName:"",required:draft.required,searchable:false,allowCustom:false,sortOrder:0}],version});
 else{
 const data=props.kind==="unit"?{code:draft.code,name:draft.name,dimensionCode:draft.dimensionCode,precisionScale:draft.precisionScale,version}:props.kind==="category"?{code:draft.code,name:draft.name,parentId:draft.parentId,version}:props.kind==="attribute"?{code:draft.code,groupId:null,version}:{code:draft.code,name:draft.name,purpose:draft.purpose,version};
 if(mode.value==="edit")await props.api.update(props.kind as "unit"|"category",selected.value!.id,data);else await props.api.create(props.kind,data);
 }
 dialog.value=false;ElMessage.success(t("masterData.saved"));busy.value=false;await load();
 }catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
async function showVersions(row:CatalogRow){selected.value=row;loading.value=true;try{history.value=await props.api.versions(props.kind as "attribute"|"template",row.id);historyOpen.value=true;}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
async function publish(row:CatalogVersion){try{await ElMessageBox.confirm(t("productCatalog.publishWarning"),title.value,{confirmButtonText:t("productCatalog.publish"),cancelButtonText:t("masterData.cancel")});}catch{return;}busy.value=true;try{await props.api.publishVersion(props.kind as "attribute"|"template",row.id,selected.value!.version);historyOpen.value=false;ElMessage.success(t("masterData.saved"));busy.value=false;await load();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
async function toggle(row:CatalogRow){try{await ElMessageBox.confirm(t("masterData."+(row.enabled?"confirmDisable":"confirmEnable"),{name:row.name??row.code}),title.value);}catch{return;}busy.value=true;try{await props.api.status(props.kind as "unit"|"category",row.id,{enabled:!row.enabled,version:row.version});busy.value=false;await load();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
onMounted(load);
</script>
<template>
<BusinessWorkspace :title="title" :description="t('productCatalog.description')">
 <template #search><el-form inline @submit.prevent="search"><el-form-item :label="t('masterData.keyword')"><el-input v-model="keyword" clearable maxlength="200"/></el-form-item><el-button v-operation="operation.list" native-type="submit" type="primary" :loading="loading" :disabled="busy">{{t('masterData.search')}}</el-button><el-button v-if="kind==='category'&&parent" v-operation="'ProductCategory.list'" :disabled="loading||busy" @click="up">{{t('productCatalog.parent')}}</el-button><span v-if="kind==='category'">{{ancestors.map(r=>r.name).join(' / ')}}</span></el-form></template>
 <template #data>
 <FloatingError :title="error"><el-button v-operation="operation.list" :disabled="busy" @click="load">{{t('masterData.retry')}}</el-button></FloatingError>
 <el-result v-if="failed&&!rows.length" icon="error" :title="t('masterData.unavailable')"/>
 <el-result v-else-if="!loading&&!canRead" icon="warning" :title="t('masterData.noPermission')"/>
 <ManagementTable v-else :title="title" :rows="rows" :columns="columns" :total="total" :loading="loading" :busy="busy" @refresh="load">
 <template #actions><el-button v-operation="operation.create" type="primary" :disabled="loading||busy" @click="edit('create')">{{t('masterData.create')}}</el-button></template>
 <template #state="{row}">{{t(row.enabled?'masterData.enabled':'masterData.disabled')}}</template>
 <template #operations="{row}">
 <el-button v-if="kind==='category'" v-operation="'ProductCategory.list'" link @click="descend(row)">{{t('productCatalog.children')}}</el-button>
 <el-button v-if="kind==='unit'||kind==='category'" v-operation="operation.update" link type="primary" :disabled="busy" @click="edit('edit',row)">{{t('masterData.edit')}}</el-button>
 <el-button v-if="kind==='unit'||kind==='category'" v-operation="operation.status" link :type="row.enabled?'danger':'primary'" :disabled="busy" @click="toggle(row)">{{t(row.enabled?'masterData.disable':'masterData.enable')}}</el-button>
 <el-button v-if="kind==='category'" v-operation="'ProductCategory.publishConfiguration'" link type="primary" @click="edit('config',row)">{{t('productCatalog.configuration')}}</el-button>
 <el-button v-if="kind==='attribute'||kind==='template'" v-operation="operation.readVersions" link @click="showVersions(row)">{{t('productCatalog.versions')}}</el-button>
 <el-button v-if="kind==='attribute'||kind==='template'" v-operation="operation.newVersion" link type="primary" @click="edit(kind==='attribute'?'revision':'version',row)">{{t('productCatalog.newVersion')}}</el-button>
 </template>
 <template #pagination><AppPagination v-model:current-page="page" v-model:page-size="size" :total="total" :disabled="loading||busy" @current-change="load" @size-change="search"/></template>
 </ManagementTable>
 </template>
</BusinessWorkspace>
<ManagedDialog v-model="dialog" :title="title" :busy="busy">
<el-form ref="form" class="business-border-label-form" :model="draft" label-position="top" @submit.prevent="save">
 <template v-if="mode==='create'||mode==='edit'">
 <el-form-item :label="t('masterData.code')" prop="code" :rules="[{required:true,message:t('masterData.required')},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t('masterData.codeRule')}]"><span v-if="mode==='edit'">{{draft.code}}</span><el-input v-else v-model="draft.code" maxlength="64"/></el-form-item>
 <el-form-item v-if="kind!=='attribute'" :label="t('masterData.name')" prop="name" :rules="[{required:true,whitespace:true,message:t('masterData.required')}]"><el-input v-model="draft.name" maxlength="200"/></el-form-item>
 <template v-if="kind==='unit'"><el-form-item :label="t('productCatalog.dimension')" prop="dimensionCode" :rules="[{required:true,message:t('masterData.required')}]"><el-input v-model="draft.dimensionCode" :disabled="mode==='edit'" maxlength="64"/></el-form-item><el-form-item :label="t('productCatalog.precision')"><el-input-number v-model="draft.precisionScale" :min="0" :max="12" :disabled="mode==='edit'"/></el-form-item></template>
 <el-form-item v-if="kind==='template'" :label="t('productCatalog.purpose')"><el-select v-model="draft.purpose"><el-option v-for="v in ['BASIC','QUALITY','EXTENSION']" :key="v" :value="v" :label="t('productCatalog.'+v)"/></el-select></el-form-item>
 </template>
 <template v-if="mode==='revision'">
 <el-form-item :label="t('masterData.name')" prop="name" :rules="[{required:true,whitespace:true,message:t('masterData.required')}]"><el-input v-model="draft.name" maxlength="200"/></el-form-item>
 <el-form-item :label="t('productCatalog.dataType')"><el-select v-model="draft.dataType"><el-option v-for="v in ['TEXT','INTEGER','DECIMAL','BOOLEAN','SINGLE_SELECT','MULTI_SELECT','DATE']" :key="v" :value="v" :label="t('productCatalog.'+v)"/></el-select></el-form-item>
 <template v-if="['SINGLE_SELECT','MULTI_SELECT'].includes(draft.dataType)">
 <div v-for="(option,index) in initialOptions" :key="index">
 <el-form-item :label="t('productCatalog.optionCode')"><el-input v-model="option.code" maxlength="64"/></el-form-item>
 <el-form-item :label="t('productCatalog.optionLabel')"><el-input v-model="option.label" maxlength="200"/></el-form-item>
 </div>
 <el-button v-operation="'ProductAttribute.createRevision'" :disabled="initialOptions.length>=50||busy" @click="initialOptions.push({code:'',label:''})">{{t('productCatalog.addOption')}}</el-button>
 </template>
 <el-form-item v-if="draft.dataType==='DECIMAL'" :label="t('productCatalog.precision')"><el-input-number v-model="draft.numericScale" :min="0" :max="12"/></el-form-item>
 </template>
 <template v-if="mode==='version'">
 <el-form-item :label="t('productCatalog.versionNote')"><el-input v-model="draft.name" maxlength="1000"/></el-form-item>
 <el-form-item :label="t('productCatalog.attribute')" prop="attributeId" :rules="[{required:true,message:t('masterData.required')}]"><el-select v-model="draft.attributeId" @change="chooseAttribute"><el-option v-for="r in candidates" :key="r.id" :value="r.id" :label="r.code"/></el-select></el-form-item>
 <el-form-item :label="t('productCatalog.versions')" prop="revisionId" :rules="[{required:true,message:t('masterData.required')}]"><el-select v-model="draft.revisionId"><el-option v-for="r in revisionOptions.filter(r=>r.state==='PUBLISHED')" :key="r.id" :value="r.id" :label="r.name+' / '+r.revision"/></el-select></el-form-item><el-checkbox v-model="draft.required">{{t('productCatalog.required')}}</el-checkbox>
 </template>
 <template v-if="mode==='config'"><el-form-item v-for="purpose in (['BASIC','QUALITY','EXTENSION'] as const)" :key="purpose" :label="t('productCatalog.'+purpose)"><el-select v-model="draft[purpose]" clearable @clear="draft[purpose]=null"><el-option v-for="v in templateOptions.filter(r=>r.purpose===purpose)" :key="v.id" :value="v.id" :label="v.label"/></el-select></el-form-item><el-checkbox v-model="draft.serviceUnitRequired">{{t('productCatalog.serviceUnitRequired')}}</el-checkbox></template>
</el-form>
<template #footer><el-button :disabled="busy" @click="dialog=false">{{t('masterData.cancel')}}</el-button><el-button v-operation="saveOperation" type="primary" :loading="busy" @click="save">{{t(mode==='config'?'productCatalog.publish':'masterData.save')}}</el-button></template>
</ManagedDialog>
<ManagedDialog v-model="historyOpen" :title="t('productCatalog.versions')" :busy="busy"><el-table :data="history"><el-table-column prop="name" :label="t('masterData.name')"/><el-table-column prop="state" :label="t('masterData.status')"/><el-table-column :label="t('masterData.actions')"><template #default="{row}"><el-button v-if="row.state==='DRAFT'" v-operation="operation.publish" link :disabled="busy" @click="publish(row as unknown as CatalogVersion)">{{t('productCatalog.publish')}}</el-button></template></el-table-column></el-table></ManagedDialog>
</template>
