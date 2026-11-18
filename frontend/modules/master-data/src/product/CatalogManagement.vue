<script setup lang="ts">
import {VxeTable,VxeColumn} from "@appnexus/ui";
import { computed,onMounted,onUnmounted,reactive,ref } from "vue";
import {useI18n} from "vue-i18n";
import {ElMessage,ElMessageBox,ElTree,type LoadFunction,type FormInstance} from "element-plus";
import {BusinessWorkspace,TreeListTemplate,ManagementTable,ManagedDialog,AppPagination,FloatingError,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import UnitConversions from "./UnitConversions.vue";
import type {CatalogKind,CatalogRow,CatalogVersion,ProductCatalogApi} from "./catalog";
/** 商品配置领域组合：模板容器不负责固定修订/四槽发布，因此在领域模块组合现有表单与表格。 */
const props=defineProps<{kind:CatalogKind;api:ProductCatalogApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();
/** 窄屏取消操作列固定，保留表内横向滚动；宽操作组不能遮住全部业务列。 */
const narrowQuery=window.matchMedia("(max-width: 768px)"),narrow=ref(narrowQuery.matches);
function updateNarrow(event:MediaQueryListEvent){narrow.value=event.matches;}
onMounted(()=>narrowQuery.addEventListener("change",updateNarrow));
onUnmounted(()=>narrowQuery.removeEventListener("change",updateNarrow));
const prefix=computed(()=>({unit:"ProductUnit",category:"ProductCategory",attribute:"ProductAttribute",template:"ProductTemplate"})[props.kind]);
const rows=ref<CatalogRow[]>([]),total=ref(0),page=ref(1),size=ref(20),keyword=ref(""),parent=ref<string>(),ancestors=ref<CatalogRow[]>([]);
const categoryTree=ref<InstanceType<typeof ElTree>>(),treeKeyword=ref(""),treeCollapsed=ref(false),treeGeneration=ref(0);
/** 分类树只按展开层级加载；同层分页全部读完，不将分页行冒充完整树，不递归预取后代。 */
const loadTree:LoadFunction=async(node,resolve)=>{try{if(node.level===0){resolve([{id:"ROOT",name:t("productCatalog.rootCategories"),root:true,leaf:false}]);return;}const result:CatalogRow[]=[];let current=1,totalRows=1;while(result.length<totalRows){const part=await props.api.list("category",{keyword:"",parentId:node.data.root?undefined:String(node.data.id),page:current++,size:100});totalRows=part.total;if(!part.rows.length)break;result.push(...part.rows);}resolve(result.map(row=>({...row,leaf:row.depth===8})));}catch(e){resolve([]);await setErrorFeedback(error,props.errorText(e));}};
/** 选中节点限定右侧直属下级；根节点显示根分类，不改变分类归属。 */
function selectTree(row:CatalogRow&{root?:boolean}){parent.value=row.root?undefined:row.id;const nodes:CatalogRow[]=[];let node=categoryTree.value?.getNode(row.id);while(node&&!node.data.root){nodes.unshift(node.data as CatalogRow);node=node.parent;}ancestors.value=nodes;search();}
/** 树内搜索仅过滤已加载节点，文案明确范围；不把本地过滤伪装成全库搜索。 */
function filterTree(value:string,row:CatalogRow&{root?:boolean}){return !!row.root||!value||[row.code,row.name].some(text=>text?.toLowerCase().includes(value.toLowerCase()));}
const operations=ref<string[]>([]),loading=ref(false),busy=ref(false),failed=ref(false),error=ref("");
const conversionsOpen=ref(false);
const dialog=ref(false),historyOpen=ref(false),mode=ref<"create"|"edit"|"revision"|"version"|"config">("create"),selected=ref<CatalogRow>();
const history=ref<CatalogVersion[]>([]),form=ref<FormInstance>();
const groupEditorOpen=ref(false),groupSaving=ref(false),groupForm=ref<FormInstance>(),editingGroup=ref<CatalogRow>();
const groupDraft=reactive({code:"",name:""});
const groups=ref<CatalogRow[]>([]),units=ref<CatalogRow[]>([]),groupOpen=ref(false);
const templateItems=ref<Array<{attributeId:string;revisionId:string;groupCode:string;groupName:string;required:boolean;searchable:boolean;allowCustom:boolean;sortOrder:number;label:string}>>([]);
const editingVersion=ref<string>();
const initialOptions=ref([{code:"",label:""}]);
const candidates=ref<CatalogRow[]>([]),revisionOptions=ref<CatalogVersion[]>([]),templateOptions=ref<Array<CatalogVersion&{purpose:string;label:string}>>([]);
const draft=reactive({code:"",name:"",dimensionCode:"",precisionScale:0,parentId:null as string|null,purpose:"BASIC",dataType:"TEXT",unitId:null as string|null,numericScale:0,revisionId:"",attributeId:"",required:false,allowCustom:false,searchable:false,min:"",max:"",minLength:null as number|null,maxLength:null as number|null,minItems:null as number|null,maxItems:null as number|null,minDate:"",maxDate:"",groupId:null as string|null,serviceUnitRequired:false,BASIC:null as string|null,QUALITY:null as string|null,EXTENSION:null as string|null,SPEC:null as string|null});
/** 固定操作语义映射；只选择实际Controller声明，不拼权限码或不完整操作名。 */
const operationMap={
 unit:{list:"ProductUnit.list",create:"ProductUnit.create",update:"ProductUnit.update",status:"ProductUnit.setStatus",readVersions:"",newVersion:"",publish:""},
 category:{list:"ProductCategory.list",create:"ProductCategory.create",update:"ProductCategory.update",status:"ProductCategory.setStatus",readVersions:"",newVersion:"",publish:""},
 attribute:{list:"ProductAttribute.list",create:"ProductAttribute.create",update:"ProductAttribute.update",status:"ProductAttribute.setStatus",readVersions:"ProductAttribute.readRevisions",newVersion:"ProductAttribute.createRevision",publish:"ProductAttribute.publishRevision"},
 template:{list:"ProductTemplate.list",create:"ProductTemplate.create",update:"ProductTemplate.update",status:"ProductTemplate.setStatus",readVersions:"ProductTemplate.readVersions",newVersion:"ProductTemplate.createVersion",publish:"ProductTemplate.publish"}
};
const operation=computed(()=>operationMap[props.kind]);
const dataSlot=computed(()=>props.kind==="category"?"table":"data");
const title=computed(()=>t("productCatalog."+props.kind));
/** 操作列按当前获准操作计宽；只读用户不预留写入按钮的大片空白。 */
const operationWidth=computed(()=>{const map=operation.value;const allowed=operations.value;let count=[map.update,map.status,map.readVersions,map.newVersion].filter(id=>id&&allowed.includes(id)).length;if(props.kind==="category"){count++;if(allowed.includes("ProductCategory.publishConfiguration"))count++;}return Math.max(110,Math.min(560,count*110));});
const columns=computed<ManagementColumn[]>(()=>[{key:"code",prop:"code",label:t("masterData.code"),minWidth:150},...(props.kind!=="attribute"?[{key:"name",prop:"name",label:t("masterData.name"),minWidth:180}]:[]),...(props.kind==="unit"?[{key:"dimensionCode",prop:"dimensionCode",label:t("productCatalog.dimension"),minWidth:120},{key:"precisionScale",prop:"precisionScale",label:t("productCatalog.precision"),width:90}]:[]),...(props.kind==="template"?[{key:"purpose",prop:"purpose",label:t("productCatalog.purpose"),width:120}]:[]),{key:"state",label:t("masterData.status"),width:100},{key:"operations",label:t("masterData.actions"),width:operationWidth.value,fixed:narrow.value?undefined:"right"}]);
const canRead=computed(()=>operations.value.includes(prefix.value+".list"));
const saveOperation=computed(()=>mode.value==="config"?"ProductCategory.publishConfiguration":mode.value==="revision"?(editingVersion.value?"ProductAttribute.updateRevision":"ProductAttribute.createRevision"):mode.value==="version"?(editingVersion.value?"ProductTemplate.updateVersion":"ProductTemplate.createVersion"):mode.value==="edit"?operation.value.update:operation.value.create);
/** 读取失败保留非敏感输入，撤权则清除正文，不把拒绝当空成功。 */
async function load(){if(busy.value)return;loading.value=true;failed.value=false;try{operations.value=await props.api.operations();if(!canRead.value){rows.value=[];total.value=0;return;}const data=await props.api.list(props.kind,{keyword:keyword.value,page:page.value,size:size.value,parentId:parent.value});rows.value=data.rows;total.value=data.total;}catch(e){failed.value=true;if((e as {response?:{status:number}}).response?.status===403){rows.value=[];total.value=0;operations.value=[];}await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
/** 重置关键词并保留当前分类层级，与系统左树右表的查询范围约定一致。 */
function reset(){keyword.value="";search();}
function search(){page.value=1;void load();}
function descend(row:CatalogRow){ancestors.value.push(row);parent.value=row.id;search();}
function up(){ancestors.value.pop();parent.value=ancestors.value.at(-1)?.id;search();}
/** 打开领域表单时只请求实际需要的授权目录；没有权限时失败而不造候选。 */
async function edit(which:typeof mode.value,row?:CatalogRow){

 editingVersion.value=undefined;templateItems.value=[];initialOptions.value=[{code:"",label:""}];draft.groupId=row?.groupId||null;draft.min="";draft.max="";draft.minLength=null;draft.maxLength=null;draft.minItems=null;draft.maxItems=null;draft.minDate="";draft.maxDate="";draft.searchable=false;draft.allowCustom=false;draft.required=false;draft.unitId=null;selected.value=row;mode.value=which;draft.code=row?.code??"";draft.name=row?.name??"";draft.dimensionCode=row?.dimensionCode??"";draft.precisionScale=row?.precisionScale??0;draft.parentId=row?.parentId??parent.value??null;draft.purpose=row?.purpose??"BASIC";draft.BASIC=null;draft.QUALITY=null;draft.EXTENSION=null;draft.SPEC=null;draft.serviceUnitRequired=false;draft.attributeId="";draft.revisionId="";
 try{
 if(props.kind==="attribute"){groups.value=await props.api.groups();if(which==="revision")units.value=(await props.api.list("unit",{keyword:"",page:1,size:100})).rows;}
 if(which==="version"){groups.value=await props.api.groups();candidates.value=(await props.api.list("attribute",{keyword:"",page:1,size:100})).rows;}
 if(which==="config"){
 if(row?.currentConfigId){const old=await props.api.configuration(row.id,row.currentConfigId);draft.BASIC=old.templates.BASIC;draft.QUALITY=old.templates.QUALITY;draft.EXTENSION=old.templates.EXTENSION;draft.SPEC=old.templates.SPEC;draft.serviceUnitRequired=old.serviceUnitRequired;}
 templateOptions.value=[];
 const templates=(await props.api.list("template",{keyword:"",page:1,size:100})).rows;
 for(const item of templates){for(const v of await props.api.versions("template",item.id))if(v.state==="PUBLISHED")templateOptions.value.push({...v,purpose:item.purpose??"",label:item.code+" / "+v.versionNo});}
 }
dialog.value=true;
 }catch(e){await setErrorFeedback(error,props.errorText(e));}
}
/** 模板项先在本地完整编排，保存时一个聚合命令，不拆成半成功发布。 */
function addTemplateItem(){const attribute=candidates.value.find(r=>r.id===draft.attributeId),revision=revisionOptions.value.find(r=>r.id===draft.revisionId);if(!attribute||!revision||templateItems.value.some(r=>r.attributeId===attribute.id))return;const group=groups.value.find(g=>g.id===attribute.groupId);templateItems.value.push({attributeId:attribute.id,revisionId:revision.id,groupCode:group?.code??"",groupName:group?.name??"",required:draft.required,searchable:draft.searchable,allowCustom:draft.allowCustom,sortOrder:templateItems.value.length,label:attribute.code+" / "+revision.name});draft.attributeId="";draft.revisionId="";}
async function chooseAttribute(){draft.revisionId="";draft.searchable=false;draft.allowCustom=false;try{revisionOptions.value=await props.api.versions("attribute",draft.attributeId);}catch(e){await setErrorFeedback(error,props.errorText(e));}}
/** 复用Element Plus懒加载TreeSelect，父选择仅展示候选，服务端最终校验整棵子树无环与深度。 */
const loadParents:LoadFunction=async(node,resolve)=>{try{const rows:CatalogRow[]=[];let page=1,total=1;while(rows.length<total){const result=await props.api.list("category",{keyword:"",parentId:node.level===0?undefined:String(node.data.id),page:page++,size:100});total=result.total;if(!result.rows.length)break;rows.push(...result.rows);}resolve(rows.map(r=>({...r,disabled:r.id===selected.value?.id,leaf:r.depth===8})));}catch(e){resolve([]);await setErrorFeedback(error,props.errorText(e));}}
/** 每次保存只执行一个已授权命令；失败保留草稿，成功后刷新父CAS，绝不自动重放写入。 */
async function save(){
 if(busy.value||!await form.value?.validate().catch(()=>false))return;
 if(mode.value==="version"&&!templateItems.value.length){await setErrorFeedback(error,t("productCatalog.addItemFirst"));return;}
 if(mode.value==="config"){try{await ElMessageBox.confirm(t("productCatalog.publishWarning"),title.value,{confirmButtonText:t("productCatalog.publish"),cancelButtonText:t("masterData.cancel")});}catch{return;}}
 busy.value=true;
 try{
 const version=selected.value?.version??0;
 if(mode.value==="config")await props.api.publishConfiguration(selected.value!.id,{templates:{BASIC:draft.BASIC,SPEC:draft.SPEC,QUALITY:draft.QUALITY,EXTENSION:draft.EXTENSION},serviceUnitRequired:draft.serviceUnitRequired,version});
 else if(mode.value==="revision")await (editingVersion.value?props.api.updateRevision(editingVersion.value,revisionPayload(version)):props.api.createRevision(selected.value!.id,revisionPayload(version)));
 else if(mode.value==="version")await (editingVersion.value?props.api.updateVersion(editingVersion.value,{description:draft.name,items:templateItems.value.map(({label,...item})=>item),version}):props.api.createVersion(selected.value!.id,{description:draft.name,items:templateItems.value.map(({label,...item})=>item),version}));
 else{
 const data=props.kind==="unit"?{code:draft.code,name:draft.name,dimensionCode:draft.dimensionCode,precisionScale:draft.precisionScale,version}:props.kind==="category"?{code:draft.code,name:draft.name,parentId:draft.parentId,version}:props.kind==="attribute"?{code:draft.code,groupId:draft.groupId,version}:{code:draft.code,name:draft.name,purpose:draft.purpose,version};
 if(mode.value==="edit")await props.api.update(props.kind,selected.value!.id,data);else await props.api.create(props.kind,data);
 }
 dialog.value=false;if(props.kind==="category")treeGeneration.value++;ElMessage.success(t("masterData.saved"));busy.value=false;await load();
 }catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
function revisionPayload(version:number){return {name:draft.name,dataType:draft.dataType,unitId:["INTEGER","DECIMAL"].includes(draft.dataType)?draft.unitId:null,numericScale:["INTEGER","DECIMAL"].includes(draft.dataType)?draft.dataType==="INTEGER"?0:draft.numericScale:null,validationJson:JSON.stringify(ruleDraft()),options:["SINGLE_SELECT","MULTI_SELECT"].includes(draft.dataType)?initialOptions.value:[],version};}
async function editVersion(row:CatalogVersion){
 const parent=selected.value;if(!parent)return;historyOpen.value=false;await edit(props.kind==="attribute"?"revision":"version",parent);editingVersion.value=row.id;
 if(props.kind==="attribute"){draft.name=row.name??"";draft.dataType=row.dataType??"TEXT";draft.unitId=row.unitId??null;draft.numericScale=row.numericScale??0;initialOptions.value=await props.api.options(row.id);const rules=JSON.parse(row.validationJson||"{}");Object.assign(draft,rules);}
 else{draft.name=row.description??"";templateItems.value=(await props.api.items(row.id)).map((item,index)=>({...item,required:item.requiredFlag,sortOrder:index,label:candidates.value.find(a=>a.id===item.attributeId)?.code??item.attributeId}));}
}
function ruleDraft(){const rules:Record<string,string|number>={};const numeric=["INTEGER","DECIMAL"].includes(draft.dataType);if(numeric){if(draft.min!=="")rules.min=draft.min;if(draft.max!=="")rules.max=draft.max;}if(draft.dataType==="TEXT"){if(draft.minLength!==null)rules.minLength=draft.minLength;if(draft.maxLength!==null)rules.maxLength=draft.maxLength;}if(draft.dataType==="DATE"){if(draft.minDate)rules.minDate=draft.minDate;if(draft.maxDate)rules.maxDate=draft.maxDate;}if(draft.dataType.endsWith("SELECT")){if(draft.minItems!==null)rules.minItems=draft.minItems;if(draft.maxItems!==null)rules.maxItems=draft.maxItems;}return rules;}
async function retire(row:CatalogVersion){try{await ElMessageBox.confirm(t("productCatalog.retireWarning"),title.value,{confirmButtonText:t("productCatalog.retire"),cancelButtonText:t("masterData.cancel")});}catch{return;}busy.value=true;try{await props.api.retireVersion(props.kind as "attribute"|"template",row.id,selected.value!.version);historyOpen.value=false;busy.value=false;await load();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
async function showGroups(){try{groups.value=await props.api.groups();groupOpen.value=true;}catch(e){await setErrorFeedback(error,props.errorText(e));}}
/** 分组新增/编辑复用统一弹窗与字段校验，不用连续prompt拆分一个原子业务表单。 */
function newGroup(){editingGroup.value=undefined;groupDraft.code="";groupDraft.name="";groupEditorOpen.value=true;}
function editGroup(row:CatalogRow){editingGroup.value=row;groupDraft.code=row.code;groupDraft.name=row.name??"";groupEditorOpen.value=true;}
/** 保存期间阻止重复提交，错误保留本地草稿，服务端仍校验租户与父版本。 */
async function saveGroup(){if(groupSaving.value||!await groupForm.value?.validate().catch(()=>false))return;groupSaving.value=true;try{const row=editingGroup.value;if(row)await props.api.updateGroup(row.id,{code:row.code,name:groupDraft.name,version:row.version});else await props.api.createGroup({...groupDraft,version:0});groupEditorOpen.value=false;ElMessage.success(t("masterData.saved"));await showGroups();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{groupSaving.value=false;}}
async function toggleGroup(row:CatalogRow){try{await ElMessageBox.confirm(t("productCatalog.groupStatusWarning"),title.value,{confirmButtonText:t(row.enabled?"masterData.disable":"masterData.enable"),cancelButtonText:t("masterData.cancel")});await props.api.groupStatus(row.id,{enabled:!row.enabled,version:row.version});await showGroups();}catch(e){if(e!=="cancel"&&e!=="close")await setErrorFeedback(error,props.errorText(e));}}
async function showVersions(row:CatalogRow){selected.value=row;loading.value=true;try{history.value=await props.api.versions(props.kind as "attribute"|"template",row.id);historyOpen.value=true;}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{loading.value=false;}}
async function publish(row:CatalogVersion){try{await ElMessageBox.confirm(t("productCatalog.publishWarning"),title.value,{confirmButtonText:t("productCatalog.publish"),cancelButtonText:t("masterData.cancel")});}catch{return;}busy.value=true;try{await props.api.publishVersion(props.kind as "attribute"|"template",row.id,selected.value!.version);historyOpen.value=false;ElMessage.success(t("masterData.saved"));busy.value=false;await load();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
async function toggle(row:CatalogRow){try{await ElMessageBox.confirm(t("masterData."+(row.enabled?"confirmDisable":"confirmEnable"),{name:row.name??row.code}),title.value,{confirmButtonText:t(row.enabled?"masterData.disable":"masterData.enable"),cancelButtonText:t("masterData.cancel")});}catch{return;}busy.value=true;try{await props.api.status(props.kind as "unit"|"category",row.id,{enabled:!row.enabled,version:row.version});busy.value=false;await load();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
onMounted(load);
</script>
<template>
<div class="product-page"><component :is="kind==='category'?TreeListTemplate:BusinessWorkspace" :title="title" :description="t('productCatalog.description')" :tree-label="title" v-model:collapsed="treeCollapsed">
<template v-if="kind==='category'" #treeHeader><el-input v-model="treeKeyword" clearable :aria-label="t('productCatalog.searchLoadedTree')" :placeholder="t('productCatalog.searchLoadedTree')" @input="categoryTree?.filter(treeKeyword)"/></template>
<template v-if="kind==='category'" #tree><el-tree :key="treeGeneration" ref="categoryTree" node-key="id" :current-node-key="parent||'ROOT'" :default-expanded-keys="['ROOT']" highlight-current lazy :load="loadTree" :props="{label:'name',isLeaf:'leaf'}" :filter-node-method="filterTree" @node-click="selectTree"/></template>
 <template #search><el-form inline class="management-filter" @submit.prevent="search"><el-form-item :label="t('masterData.keyword')"><el-input v-model="keyword" clearable maxlength="200"/></el-form-item><el-button v-if="kind==='category'&&parent" v-operation="'ProductCategory.list'" :disabled="loading||busy" @click="up">{{t('productCatalog.parent')}}</el-button><span v-if="kind==='category'">{{ancestors.map(r=>r.name).join(' / ')}}</span><el-form-item class="management-filter-actions"><el-button v-operation="operation.list" native-type="submit" type="primary" :loading="loading" :disabled="busy">{{t('masterData.search')}}</el-button><el-button v-operation="operation.list" :disabled="loading||busy" @click="reset">{{t('masterData.reset')}}</el-button></el-form-item></el-form></template>
 <template #[dataSlot]>
 <FloatingError :title="error"><el-button v-operation="operation.list" :disabled="busy" @click="load">{{t('masterData.retry')}}</el-button></FloatingError>
 <el-result v-if="failed&&!rows.length" icon="error" :title="t('masterData.unavailable')"/>
 <el-result v-else-if="!loading&&!canRead" icon="warning" :title="t('masterData.noPermission')"/>
 <ManagementTable v-else :title="title" :rows="rows" :columns="columns" :total="total" :loading="loading" :busy="busy" @refresh="load">
 <template #actions><el-button v-if="kind==='unit'" v-operation="'ProductUnit.listConversions'" @click="conversionsOpen=true">{{t('productConversion.title')}}</el-button><el-button v-operation="operation.create" type="primary" :disabled="loading||busy" @click="edit('create')">{{t('masterData.create')}}</el-button><el-button v-if="kind==='attribute'" v-operation="'ProductAttribute.listGroups'" @click="showGroups">{{t('productCatalog.group')}}</el-button></template>
 <template #purpose="{row}">{{t("productCatalog."+row.purpose)}}</template><template #state="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag></template>
 <template #operations="{row}">
 <el-button v-if="kind==='category'" v-operation="'ProductCategory.list'" link @click="descend(row)">{{t('productCatalog.children')}}</el-button>
 <el-button v-operation="operation.update" link type="primary" :disabled="busy" @click="edit('edit',row)">{{t('masterData.edit')}}</el-button>
 <el-button v-operation="operation.status" link :type="row.enabled?'danger':'primary'" :disabled="busy" @click="toggle(row)">{{t(row.enabled?'masterData.disable':'masterData.enable')}}</el-button>
 <el-button v-if="kind==='category'" v-operation="'ProductCategory.publishConfiguration'" link type="primary" @click="edit('config',row)">{{t('productCatalog.configuration')}}</el-button>
 <el-button v-if="kind==='attribute'||kind==='template'" v-operation="operation.readVersions" link @click="showVersions(row)">{{t('productCatalog.versions')}}</el-button>
 <el-button v-if="kind==='attribute'||kind==='template'" v-operation="operation.newVersion" link type="primary" @click="edit(kind==='attribute'?'revision':'version',row)">{{t('productCatalog.newVersion')}}</el-button>
 </template>
 <template #pagination><AppPagination v-model:current-page="page" v-model:page-size="size" :total="total" :disabled="loading||busy" @current-change="load" @size-change="search"/></template>
 </ManagementTable>
 </template>
</component>
<UnitConversions v-if="conversionsOpen" :api="api" :error-text="errorText" @close="conversionsOpen=false"/>
<ManagedDialog v-model="dialog" :title="title" :busy="busy">
<el-form scroll-to-error ref="form" class="business-border-label-form" :model="draft" label-position="top" @submit.prevent="save">
 <template v-if="mode==='create'||mode==='edit'">
 <el-form-item :label="t('masterData.code')" prop="code" :rules="[{required:true,message:t('masterData.required')},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t('masterData.codeRule')}]"><span v-if="mode==='edit'">{{draft.code}}</span><el-input v-else v-model="draft.code" maxlength="64"/></el-form-item>
 <el-form-item v-if="kind!=='attribute'" :label="t('masterData.name')" prop="name" :rules="[{required:true,whitespace:true,message:t('masterData.required')}]"><el-input v-model="draft.name" maxlength="200"/></el-form-item>
 <template v-if="kind==='unit'"><el-form-item :label="t('productCatalog.dimension')" prop="dimensionCode" :rules="[{required:true,message:t('masterData.required')}]"><el-input v-model="draft.dimensionCode" :disabled="mode==='edit'" maxlength="64"/></el-form-item><el-form-item :label="t('productCatalog.precision')"><el-input-number v-model="draft.precisionScale" :min="0" :max="12" :disabled="mode==='edit'"/></el-form-item></template>
 <el-form-item v-if="kind==='category'" :label="t('productCatalog.parentCategory')"><el-tree-select v-model="draft.parentId" clearable check-strictly lazy :load="loadParents" :props="{label:'name',children:'children',isLeaf:'leaf'}" :cache-data="ancestors" node-key="id" :render-after-expand="false" @clear="draft.parentId=null"/></el-form-item>
 <el-form-item v-if="kind==='attribute'" :label="t('productCatalog.group')"><el-select v-model="draft.groupId" clearable @clear="draft.groupId=null"><el-option v-for="g in groups" :key="g.id" :value="g.id" :label="g.name" :disabled="!g.enabled"/></el-select></el-form-item>
 <el-form-item v-if="kind==='template'" :label="t('productCatalog.purpose')"><el-select v-model="draft.purpose" :disabled="mode==='edit'"><el-option v-for="v in ['BASIC','SPEC','QUALITY','EXTENSION']" :key="v" :value="v" :label="t('productCatalog.'+v)"/></el-select></el-form-item>
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
 <template v-if="['INTEGER','DECIMAL'].includes(draft.dataType)"><el-form-item :label="t('productV1.baseUnitId')"><el-select v-model="draft.unitId" clearable @clear="draft.unitId=null"><el-option v-for="u in units" :key="u.id" :value="u.id" :label="u.name" :disabled="!u.enabled"/></el-select></el-form-item><el-form-item :label="t('productCatalog.min')"><el-input v-model="draft.min"/></el-form-item><el-form-item :label="t('productCatalog.max')"><el-input v-model="draft.max"/></el-form-item></template>
 <template v-if="draft.dataType==='TEXT'"><el-form-item :label="t('productCatalog.minLength')"><el-input-number v-model="draft.minLength" :min="0" :max="2000"/></el-form-item><el-form-item :label="t('productCatalog.maxLength')"><el-input-number v-model="draft.maxLength" :min="0" :max="2000"/></el-form-item></template>
 <template v-if="draft.dataType==='DATE'"><el-form-item :label="t('productCatalog.minDate')"><el-date-picker v-model="draft.minDate" value-format="YYYY-MM-DD"/></el-form-item><el-form-item :label="t('productCatalog.maxDate')"><el-date-picker v-model="draft.maxDate" value-format="YYYY-MM-DD"/></el-form-item></template>
 <template v-if="draft.dataType.endsWith('SELECT')"><el-form-item :label="t('productCatalog.minItems')"><el-input-number v-model="draft.minItems" :min="0" :max="50"/></el-form-item><el-form-item :label="t('productCatalog.maxItems')"><el-input-number v-model="draft.maxItems" :min="1" :max="50"/></el-form-item></template>
 <el-form-item v-if="draft.dataType==='DECIMAL'" :label="t('productCatalog.precision')"><el-input-number v-model="draft.numericScale" :min="0" :max="12"/></el-form-item>
 </template>
 <template v-if="mode==='version'">
 <el-form-item :label="t('productCatalog.versionNote')"><el-input v-model="draft.name" maxlength="1000"/></el-form-item>
 <el-form-item :label="t('productCatalog.attribute')" prop="attributeId"><el-select v-model="draft.attributeId" @change="chooseAttribute"><el-option v-for="r in candidates" :key="r.id" :value="r.id" :label="r.code"/></el-select></el-form-item>
 <el-form-item :label="t('productCatalog.versions')" prop="revisionId"><el-select v-model="draft.revisionId" @change="draft.searchable=false;draft.allowCustom=false"><el-option v-for="r in revisionOptions.filter(r=>r.state==='PUBLISHED')" :key="r.id" :value="r.id" :label="r.name+' / '+r.revision"/></el-select></el-form-item><el-checkbox v-model="draft.required">{{t('productCatalog.required')}}</el-checkbox><el-checkbox v-model="draft.searchable" :disabled="revisionOptions.find(r=>r.id===draft.revisionId)?.dataType==='TEXT'">{{t('productCatalog.searchable')}}</el-checkbox><el-checkbox v-model="draft.allowCustom" :disabled="!['SINGLE_SELECT','MULTI_SELECT'].includes(revisionOptions.find(r=>r.id===draft.revisionId)?.dataType??'')">{{t('productCatalog.allowCustom')}}</el-checkbox><el-button v-operation="'ProductTemplate.createVersion'" :disabled="!draft.revisionId||templateItems.length>=200" @click="addTemplateItem">{{t('productCatalog.addItem')}}</el-button><VxeTable border show-overflow :column-config="{resizable:true}" :data="templateItems"><VxeColumn field="label" :title="t('productCatalog.attribute')"/><VxeColumn :title="t('masterData.actions')"><template #default="{ rowIndex }"><el-button v-operation="'ProductTemplate.createVersion'" link @click="templateItems.splice(rowIndex,1)">{{t('productV1.removeCondition')}}</el-button></template></VxeColumn></VxeTable>
 </template>
 <template v-if="mode==='config'"><el-form-item v-for="purpose in (['BASIC','SPEC','QUALITY','EXTENSION'] as const)" :key="purpose" :label="t('productCatalog.'+purpose)"><el-select v-model="draft[purpose]" clearable @clear="draft[purpose]=null"><el-option v-for="v in templateOptions.filter(r=>r.purpose===purpose)" :key="v.id" :value="v.id" :label="v.label"/></el-select></el-form-item><el-checkbox v-model="draft.serviceUnitRequired">{{t('productCatalog.serviceUnitRequired')}}</el-checkbox></template>
</el-form>
<template #footer><el-button :disabled="busy" @click="dialog=false">{{t('masterData.cancel')}}</el-button><el-button v-operation="saveOperation" type="primary" :loading="busy" @click="save">{{t(mode==='config'?'productCatalog.publish':'masterData.save')}}</el-button></template>
</ManagedDialog>
<ManagedDialog v-model="historyOpen" :title="t('productCatalog.versions')" :busy="busy"><VxeTable border show-overflow :column-config="{resizable:true}" :data="history"><VxeColumn :title="t('productV1.version')" width="100"><template #default="{row}">{{row.revision??row.versionNo}}</template></VxeColumn><VxeColumn :title="t(kind==='template'?'productCatalog.versionNote':'masterData.name')" min-width="160"><template #default="{row}">{{row.name||row.description||'—'}}</template></VxeColumn><VxeColumn :title="t('masterData.status')"><template #default="{row}"><el-tag :type="row.state==='PUBLISHED'?'success':'info'">{{t("productCatalog.state_"+row.state)}}</el-tag></template></VxeColumn><VxeColumn :title="t('masterData.actions')"><template #default="{row}"><el-button v-if="row.state==='DRAFT'" v-operation="operation.publish" link :disabled="busy" @click="publish(row as unknown as CatalogVersion)">{{t('productCatalog.publish')}}</el-button><el-button v-if="row.state==='DRAFT'&&kind==='attribute'" v-operation="'ProductAttribute.updateRevision'" link @click="editVersion(row as unknown as CatalogVersion)">{{t('masterData.edit')}}</el-button><el-button v-if="row.state==='DRAFT'&&kind==='template'" v-operation="'ProductTemplate.updateVersion'" link @click="editVersion(row as unknown as CatalogVersion)">{{t('masterData.edit')}}</el-button><el-button v-if="row.state==='PUBLISHED'&&kind==='attribute'" v-operation="'ProductAttribute.retireRevision'" link @click="retire(row as unknown as CatalogVersion)">{{t('productCatalog.retire')}}</el-button><el-button v-if="row.state==='PUBLISHED'&&kind==='template'" v-operation="'ProductTemplate.retire'" link @click="retire(row as unknown as CatalogVersion)">{{t('productCatalog.retire')}}</el-button></template></VxeColumn></VxeTable></ManagedDialog>
<ManagedDialog v-model="groupOpen" :title="t('productCatalog.group')"><el-button v-operation="'ProductAttribute.createGroup'" @click="newGroup">{{t('masterData.create')}}</el-button><VxeTable border show-overflow :column-config="{resizable:true}" :data="groups"><VxeColumn field="code" :title="t('masterData.code')"/><VxeColumn field="name" :title="t('masterData.name')"/><VxeColumn :title="t('masterData.actions')"><template #default="{row}"><el-button v-operation="'ProductAttribute.updateGroup'" link @click="editGroup(row as CatalogRow)">{{t('masterData.edit')}}</el-button><el-button v-operation="'ProductAttribute.setGroupStatus'" link @click="toggleGroup(row as CatalogRow)">{{t(row.enabled?'masterData.disable':'masterData.enable')}}</el-button></template></VxeColumn></VxeTable></ManagedDialog>
<ManagedDialog v-model="groupEditorOpen" :title="t('productCatalog.group')" :busy="groupSaving"><el-form scroll-to-error ref="groupForm" class="business-border-label-form" label-position="top" :model="groupDraft" @submit.prevent="saveGroup"><el-form-item :label="t('masterData.code')" prop="code" :rules="[{required:true,message:t('masterData.required')},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t('masterData.codeRule')}]"><span v-if="editingGroup">{{groupDraft.code}}</span><el-input v-else v-model="groupDraft.code" maxlength="64" :disabled="groupSaving"/></el-form-item><el-form-item :label="t('masterData.name')" prop="name" :rules="[{required:true,whitespace:true,message:t('masterData.required')}]"><el-input v-model="groupDraft.name" maxlength="200" :disabled="groupSaving"/></el-form-item></el-form><template #footer><el-button :disabled="groupSaving" @click="groupEditorOpen=false">{{t('masterData.cancel')}}</el-button><el-button v-if="editingGroup" v-operation="'ProductAttribute.updateGroup'" type="primary" :loading="groupSaving" @click="saveGroup">{{t('masterData.save')}}</el-button><el-button v-else v-operation="'ProductAttribute.createGroup'" type="primary" :loading="groupSaving" @click="saveGroup">{{t('masterData.save')}}</el-button></template></ManagedDialog></div>
</template>

<style scoped>
/* 领域根容器传递模板剩余高度，禁止让Vxe表格撑开正文；弹窗由统一组件管理。 */
.product-page{display:flex;flex-direction:column;flex:1;min-height:0;min-width:0}
</style>
