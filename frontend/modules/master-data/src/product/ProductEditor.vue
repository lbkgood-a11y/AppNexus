<script setup lang="ts">
import ProductMeasurementPanel from "./ProductMeasurementPanel.vue";
import {VxeTable,VxeColumn} from "@appnexus/ui";
import {computed,onMounted,reactive,ref,watch} from "vue";
import {useI18n} from "vue-i18n";
import {onBeforeRouteLeave} from "vue-router";
import {ElMessage,ElMessageBox,type FormInstance} from "element-plus";
import {TabbedDetailTemplate,AppPagination,PageHeader,RecordAuditDetails,ManagedDialog,FloatingError,LoadErrorResult,presentLoadError,setErrorFeedback,type LoadIssue} from "@appnexus/ui";
import ProductApprovals from "./ProductApprovals.vue";
import ProductStructure from "./ProductStructure.vue";
import ProductImages from "./ProductImages.vue";
import ProductAttributes from "./ProductAttributes.vue";
import type {ProductApi,ProductRecord,ProductConfiguration,ProductDraft,ProductValue,ProductMigration,ProductPreview,ProductOption,ProductChange} from "./product";
/** 商品主档领域编辑器；普通修改不提交结构，迁移独立预览/确认，全量草稿只保存在内存。 */
const loadIssue = ref<LoadIssue>();
const props=defineProps<{id:string;mode?:"view"|"edit";api:ProductApi;errorText:(error:unknown)=>string}>();
const emit=defineEmits<{close:[]}>();const {t,locale}=useI18n();
/** 与公共审计详情保持相同的语言和浏览器时区；非法或未知时间不猜测。 */
function displayTime(value:string|null|undefined){if(!value)return t("recordAudit.unknown");const date=new Date(value);return Number.isNaN(date.getTime())?t("recordAudit.unknown"):new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"medium"}).format(date);}
const id=ref(props.id),record=ref<ProductRecord>(),config=ref<ProductConfiguration>(),operations=ref<string[]>([]),loading=ref(false),busy=ref(false),failed=ref(false),error=ref(""),form=ref<FormInstance>();
const targetMode=ref("NONE"),retireSpecifications=ref(false);
const migrationMode=ref(false),preview=ref<ProductPreview>(),previewOpen=ref(false),dropConfirmed=ref(false),baseline=ref("");
const recovery=ref<{change:ProductMigration;token:string;key:string}>(),ambiguous=ref(false);
const categoryOptions=ref<ProductOption[]>([]),unitOptions=ref<ProductOption[]>([]),categoryPage=ref(1),unitPage=ref(1),categoryTotal=ref(0),unitTotal=ref(0),categoryKeyword=ref(""),unitKeyword=ref("");
const configurations=ref<Array<{id:string;revision:number;path:string}>>([]),configurationPage=ref(1),configurationTotal=ref(0);
const categoryLoading=ref(false),unitLoading=ref(false),reason=ref("");
const attributesValid=ref(true);
const values=reactive<Record<string,string|string[]>>({});
const organizationOptions=ref<Array<{id:string;name:string}>>([]);
const draft=reactive<ProductDraft>({organizationId:null,code:"",name:"",barcode:"",shortName:null,categoryId:null,configId:null,productForm:null,unitId:null,description:null,brand:null,origin:null,values:[]});
const completion=ref<string[]>([]),changes=ref<ProductChange[]>([]),historyPage=ref(1),historyTotal=ref(0);
const historyOpen=ref(false),historyLoading=ref(false),historyError=ref(""),historyDenied=ref(false);
let historyRequest=0;
const detailTabs=computed(()=>[
 {name:"measurement",label:"productMeasurement.title",operation:"Product.readMeasurement"},
 {name:"structure",label:"productStructure.title",operation:"Product.readStructure"},
 {name:"approval",label:"productApproval.title",operation:"Product.readApprovals"},
 {name:"images",label:"productImages.title",operation:"Product.listImages"}
].filter(item=>operations.value.includes(item.operation)));
const detailTab=ref("");
const canReadHistory=computed(()=>!!record.value&&operations.value.includes("Product.readChanges"));
const organizationLabel=computed(()=>(record.value?.organizationId===draft.organizationId ? record.value?.organizationName : undefined)
 ?? organizationOptions.value.find(item=>item.id===draft.organizationId)?.name ?? t("productV1.organizationUnknown"));
watch(detailTabs,tabs=>{if(!tabs.some(item=>item.name===detailTab.value))detailTab.value=tabs[0]?.name??"";});
const canUpdate=computed(()=>props.mode!=="view"&&!record.value?.approvalPending&&operations.value.includes(id.value?"Product.update":"Product.create")&&!failed.value&&!record.value?.archivedAt&&record.value?.structureState!=="LEGACY_PENDING");
const canStructure=computed(()=>props.mode!=="view"&&!record.value?.approvalPending&&operations.value.includes("Product.previewStructure")&&operations.value.includes("Product.migrateStructure")&&!failed.value&&!record.value?.archivedAt&&record.value?.status!=="ENABLED"&&(record.value?.structureState!=="LEGACY_PENDING"||record.value?.legacyDisposition==="CONFIRMED_NONE"));
const editing=computed(()=>migrationMode.value?canStructure.value:canUpdate.value);
const structureEditable=computed(()=>!id.value||migrationMode.value);
const saveOperation=computed(()=>id.value?"Product.update":"Product.create");
const dirty=computed(()=>state()!==baseline.value);
const groupFields=computed(()=>(config.value?.fields??[]).filter(field=>field.purpose!=="SPEC"));
/** 比较业务输入，不包含签名、审计和UI状态；切换配置前不能静默丢失已有值。 */
function state(){return JSON.stringify({draft,values,reason:reason.value});}
function invalidate(){preview.value=undefined;recovery.value=undefined;ambiguous.value=false;}
function setValue(itemId:string,value:string|string[]){values[itemId]=value;invalidate();}
function collect():ProductValue[]{return groupFields.value.flatMap(field=>{const value=values[field.templateItemId];if(Array.isArray(value))return value.length?[{templateItemId:field.templateItemId,value:null,optionIds:value.filter(v=>!v.startsWith("custom:")),customValues:value.filter(v=>v.startsWith("custom:")).map(v=>v.slice(7))}]:[];return value!==undefined&&value!==""?[{templateItemId:field.templateItemId,value,optionIds:[],customValues:[]}]:[];});}
/** 数据结构完整性字段稳定码转本页字段/属性名称，不向用户暴露内部表结构。 */
function issueName(issue:string){if(issue.startsWith("attribute:"))return groupFields.value.find(f=>f.templateItemId===issue.slice(10))?.name??t("productV1.attribute");return t("productV1."+issue);}
async function confirmDiscard(){if(!dirty.value)return true;try{await ElMessageBox.confirm(t("productV1.discard"),t("productV1.title"),{confirmButtonText:t("productV1.discardAction"),cancelButtonText:t("masterData.cancel")});return true;}catch{return false;}}
async function close(){if(busy.value||!await confirmDiscard())return;emit("close");}
/** 按记录固定配置解释，不追分类当前指针；0与false规范为字符串保留。 */
async function accept(row:ProductRecord){
 record.value=row;Object.assign(draft,{organizationId:row.organizationId??null,code:row.code,name:row.name,barcode:row.barcode??"",shortName:row.shortName,categoryId:row.categoryId,configId:row.configId,productForm:row.productForm,unitId:row.unitId,description:row.description,brand:row.brand,origin:row.origin,values:[]});
 config.value=row.configId?await props.api.configuration(row.configId):undefined;
 for(const key of Object.keys(values))delete values[key];
 for(const value of row.values){const field=groupFields.value.find(f=>f.templateItemId===value.templateItemId);if(!field)continue;if(["SINGLE_SELECT","MULTI_SELECT"].includes(field.dataType))values[field.templateItemId]=row.choices.filter(c=>c.templateItemId===field.templateItemId).map(c=>c.customText?"custom:"+c.customText:c.optionId);else values[field.templateItemId]=value.textValue??value.integerValue??value.decimalValue??(value.booleanValue===null?null:String(value.booleanValue))??value.dateValue??"";}
 completion.value=row.completionIssues;reason.value="";migrationMode.value=false;invalidate();baseline.value=state();
}
async function load(){loading.value=true;failed.value=false;loadIssue.value=undefined;try{operations.value=await props.api.operations();if(operations.value.includes(id.value?"Product.update":"Product.create"))organizationOptions.value=await props.api.organizations(id.value?"update":"create");if(id.value)await accept(await props.api.read(id.value));else baseline.value=state();}catch(e){failed.value=true;await fail(e, true);}finally{loading.value=false;}}
async function fail(e:unknown, loadingRecord=false){if((e as {response?:{status:number}})?.response?.status===403){record.value=undefined;config.value=undefined;operations.value=[];draft.name="";draft.code="";for(const key of Object.keys(values))delete values[key];}if(loadingRecord)await presentLoadError(error,loadIssue,props.errorText(e),true,(e as {response?:{status?:number}})?.response?.status===403);else await setErrorFeedback(error,props.errorText(e));}
async function options(kind:"category"|"unit",keyword:string,page=1){
 const isCategory=kind==="category";if(isCategory){categoryLoading.value=true;categoryKeyword.value=keyword;categoryPage.value=page;}else{unitLoading.value=true;unitKeyword.value=keyword;unitPage.value=page;}
 try{const data=await props.api.options(kind,keyword,page);if(isCategory){categoryOptions.value=data.rows;categoryTotal.value=data.total;}else{unitOptions.value=data.rows;unitTotal.value=data.total;}}catch(e){await fail(e);}finally{if(isCategory)categoryLoading.value=false;else unitLoading.value=false;}
}
async function loadConfigurations(page=1){if(!draft.categoryId)return;try{const response=await props.api.configurations(draft.categoryId,page);configurations.value=response.rows;configurationPage.value=page;configurationTotal.value=response.total;}catch(e){await fail(e);}}
async function chooseConfiguration(value:string|null){
 if(value===draft.configId)return;
 if(Object.values(values).some(v=>Array.isArray(v)?v.length:v!=="")){try{await ElMessageBox.confirm(t("productV1.categoryChange"),t("productV1.title"),{confirmButtonText:t("productV1.changeCategory"),cancelButtonText:t("masterData.cancel")});}catch{return;}}
 try{const target=value?await props.api.configuration(value):undefined;draft.configId=value||null;config.value=target;for(const key of Object.keys(values))delete values[key];invalidate();}catch(e){await fail(e);}
}
async function chooseCategory(value:string|null){
 if(value===draft.categoryId)return;
 if(Object.values(values).some(v=>Array.isArray(v)?v.length:v!=="")){try{await ElMessageBox.confirm(t("productV1.categoryChange"),t("productV1.title"),{confirmButtonText:t("productV1.changeCategory"),cancelButtonText:t("masterData.cancel")});}catch{return;}}
 const option=categoryOptions.value.find(o=>o.id===value);if(value&&!option){await setErrorFeedback(error,t("productV1.optionChanged"));return;}
 try{const next=option?.configId?await props.api.configuration(option.configId):undefined;draft.categoryId=value||null;configurations.value=[];configurationTotal.value=0;draft.configId=option?.configId??null;config.value=next;for(const key of Object.keys(values))delete values[key];invalidate();}catch(e){await fail(e);}
}
async function save(){if(busy.value||!attributesValid.value||!editing.value||!await form.value?.validate().catch(()=>false))return;busy.value=true;try{let row:ProductRecord;if(id.value)row=await props.api.update(id.value,{organizationId:draft.organizationId,name:draft.name,barcode:draft.barcode,shortName:draft.shortName,description:draft.description,brand:draft.brand,origin:draft.origin,values:collect(),version:record.value!.version});else row=await props.api.create({...draft,values:collect()});id.value=row.id;await accept(row);ElMessage.success(t("masterData.saved"));}catch(e){await fail(e);}finally{busy.value=false;}}
async function startMigration(){if(!await confirmDiscard())return;try{if(record.value)await accept(await props.api.read(id.value));targetMode.value=record.value?.specificationMode??"NONE";retireSpecifications.value=false;migrationMode.value=true;reason.value="";baseline.value=state();}catch(e){await fail(e);}}
async function cancelMigration(){if(!await confirmDiscard())return;try{if(record.value)await accept(await props.api.read(id.value));}catch(e){await fail(e);}}
/** 退出确认覆盖所有源模板项，不将隐藏字段视为自动删除。 */
async function previewMigration(){
 if(!reason.value.trim()){await setErrorFeedback(error,t("masterData.required"));return;}
 if(!canStructure.value||busy.value||!attributesValid.value||!await form.value?.validate().catch(()=>false))return;
 const proposed=collect(),supplied=new Set(proposed.map(v=>v.templateItemId));
 const dropped=(record.value?.values??[]).filter(v=>!supplied.has(v.templateItemId)).map(v=>v.templateItemId);
 const structure=await props.api.structure(id.value);
 const change:ProductMigration={specificationMode:targetMode.value as "NONE"|"STANDARD"|"CUSTOM",retiredSpecificationIds:retireSpecifications.value?structure.specifications.filter(s=>!s.retiredAt).map(s=>String(s.id)):[],sourceVersion:record.value!.version,sourceStructureRevision:record.value!.structureRevision,sourceCategoryConfigId:record.value!.configId,categoryId:draft.categoryId,configId:draft.configId,productForm:draft.productForm,unitId:draft.unitId,values:proposed,droppedItemIds:dropped,reason:reason.value,legacyDisposition:record.value?.legacyDisposition??null};
 busy.value=true;try{preview.value=await props.api.preview(id.value,change);dropConfirmed.value=dropped.length===0;previewOpen.value=true;recovery.value=undefined;ambiguous.value=false;}catch(e){await fail(e);}finally{busy.value=false;}
}
/** 成功结果重放后读取最新对象；写结果未知时保留相同key供用户显式恢复，不自动重发。 */
async function executeMigration(retry=false){
 if(busy.value)return;if(!retry){if(!preview.value||!dropConfirmed.value)return;recovery.value={change:preview.value.target,token:preview.value.previewToken,key:crypto.randomUUID()};}
 const command=recovery.value;if(!command)return;busy.value=true;
 try{await props.api.migrate(id.value,command.change,command.token,command.key);previewOpen.value=false;ambiguous.value=false;await accept(await props.api.read(id.value));ElMessage.success(t("masterData.saved"));}
 catch(e){ambiguous.value=true;await fail(e);}finally{busy.value=false;}
}
async function verifyResult(){if(!recovery.value||busy.value)return;busy.value=true;try{const latest=await props.api.read(id.value);if(latest.version>recovery.value.change.sourceVersion){previewOpen.value=false;await accept(latest);ElMessage.info(t("productV1.resultReloaded"));}else ElMessage.info(t("productV1.resultUnconfirmed"));}catch(e){await fail(e);}finally{busy.value=false;}}
/** 原文确认使用独立CAS命令，不以选择NONE冒充确认，不覆盖原文。 */
async function reviewLegacy(disposition:"CONFIRMED_NONE"|"DEFERRED_SPEC"){
 if(!record.value||busy.value)return;
 let reason:string;
 try{const answer=await ElMessageBox.prompt(t("productV1.legacyReviewReason"),t(disposition==="CONFIRMED_NONE"?"productV1.confirmNone":"productV1.deferSpecification"),{inputType:"textarea",inputValidator:v=>!!v?.trim()&&v.length<=1000,confirmButtonText:t("productV1.confirmReview"),cancelButtonText:t("masterData.cancel")});reason=answer.value;}
 catch{return;}
 busy.value=true;try{await accept(await props.api.reviewLegacy(id.value,disposition,reason,record.value.version));ElMessage.success(t("masterData.saved"));}catch(e){await fail(e);}finally{busy.value=false;}
}
async function status(next:"ENABLED"|"DISABLED"){
 try{await ElMessageBox.confirm(t(next==="ENABLED"?"productV1.confirmEnable":"productV1.confirmDisable"),t("productV1.title"),{confirmButtonText:t(next==="ENABLED"?"masterData.enable":"masterData.disable"),cancelButtonText:t("masterData.cancel")});}catch{return;}
 busy.value=true;try{await accept(await props.api.status(id.value,next,record.value!.version));ElMessage.success(t("masterData.statusSaved"));}catch(e){await fail(e);if(record.value){try{completion.value=(await props.api.read(id.value)).completionIssues;}catch{}}}finally{busy.value=false;}
}
async function archive(restore:boolean){try{await ElMessageBox.confirm(t(restore?"productV1.restoreHint":"productV1.archiveHint"),t("productV1.title"),{confirmButtonText:t(restore?"productV1.restore":"productV1.archive"),cancelButtonText:t("masterData.cancel")});}catch{return;}busy.value=true;try{await accept(await props.api.archive(id.value,record.value!.version,restore));}catch(e){await fail(e);}finally{busy.value=false;}}
/** 每次打开按原读取授权请求第一页；历史异常仅影响弹窗，不清理详情或草稿。 */
function openHistory(){
 if(!canReadHistory.value)return;
 historyPage.value=1;
 historyOpen.value=true;
 void readHistory();
}
async function readHistory(){
 if(!historyOpen.value||!canReadHistory.value)return;
 const request=++historyRequest;
 historyLoading.value=true;
 historyError.value="";
 historyDenied.value=false;
 try{
  const data=await props.api.changes(id.value,historyPage.value,20);
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
watch(canReadHistory,allowed=>{if(!allowed)historyOpen.value=false;});
function snapshotText(value:string|null){if(!value)return t("recordAudit.unknown");try{const row=JSON.parse(value) as ProductRecord&{changeReason?:string};return [row.name,...(row.changeReason?[t("productV1.reason")+": "+row.changeReason]:[]),t("productV1."+row.status),t("productV1.version")+": "+row.version,t("productV1.structureVersion")+": "+row.structureRevision,...(row.values??[]).map(v=>v.attributeName+": "+(v.textValue??v.integerValue??v.decimalValue??v.booleanValue??v.dateValue??row.choices?.filter(c=>c.templateItemId===v.templateItemId).map(c=>c.label).join(" / ")??"—"))].join("\n");}catch{return t("productV1.snapshotUnsupported");}}
onBeforeRouteLeave(async()=>!busy.value&&await confirmDiscard());
onMounted(load);
</script>
<template>
<div class="product-editor"><TabbedDetailTemplate master-data>
<template #header><PageHeader :title="record?.name||t('productV1.create')">
<template #meta>
 <el-tag v-if="props.mode==='view'" type="info">{{t('partner.view')}}</el-tag>
 <el-tag v-if="id&&draft.code" effect="plain">{{draft.code}}</el-tag>
 <el-tag v-if="record" :type="record.status==='ENABLED'?'success':'info'">{{t('productV1.'+record.status)}}</el-tag>
 <el-tag v-if="record" effect="plain">{{t('productV1.'+record.structureState)}}</el-tag>
 <el-tag v-if="record?.approvalPending" type="warning">{{t('productApproval.SUBMITTED')}}</el-tag>
 <el-tag v-if="record?.archivedAt" type="info">{{t('productV1.archived')}}</el-tag>
</template>
<template #actions>
<el-button :disabled="busy" @click="close">{{t('productV1.back')}}</el-button>
<el-button v-if="!migrationMode&&editing" v-operation="saveOperation" type="primary" :loading="busy" @click="save">{{t('masterData.save')}}</el-button>
<el-button v-if="id&&!migrationMode&&canStructure" v-operation="'Product.previewStructure'" :disabled="busy" @click="startMigration">{{t('productV1.migrate')}}</el-button>
<el-button v-if="migrationMode" :disabled="busy" @click="cancelMigration">{{t('masterData.cancel')}}</el-button>
<el-button v-if="migrationMode" v-operation="'Product.previewStructure'" type="primary" :loading="busy" @click="previewMigration">{{t('productV1.preview')}}</el-button>
<template v-if="record&&!record.approvalPending&&props.mode!=='view'&&!migrationMode&&!dirty">
<el-button v-if="record.status!=='ENABLED'&&!record.archivedAt" v-operation="'Product.setStatus'" :disabled="busy" @click="status('ENABLED')">{{t('masterData.enable')}}</el-button>
<el-button v-if="record.status==='ENABLED'" v-operation="'Product.setStatus'" type="danger" :disabled="busy" @click="status('DISABLED')">{{t('masterData.disable')}}</el-button>
<el-button v-if="record.status==='DRAFT'&&!record.archivedAt" v-operation="'Product.archiveDraft'" :disabled="busy" @click="archive(false)">{{t('productV1.archive')}}</el-button>
<el-button v-if="record.status==='DRAFT'&&record.archivedAt" v-operation="'Product.restoreDraft'" :disabled="busy" @click="archive(true)">{{t('productV1.restore')}}</el-button>
</template>
</template>
<template v-if="canReadHistory" #trailing>
<el-button v-operation="'Product.readChanges'" :disabled="loading||busy" @click="openHistory">{{t('productV1.history')}}</el-button>
</template></PageHeader></template>
<template #content>
<FloatingError :title="error"><el-button v-operation="'Product.read'" :disabled="busy" @click="load">{{t('masterData.retry')}}</el-button></FloatingError>
<el-skeleton v-if="loading" :rows="6" animated/>
<LoadErrorResult v-else-if="loadIssue" :issue="loadIssue"><template #retry><el-button v-operation="'Product.read'" :disabled="busy||loading" @click="load">{{t('masterData.retry')}}</el-button></template></LoadErrorResult>
<el-form v-else-if="!failed" ref="form" class="detail-profile-form detail-border-label-form" :model="draft" label-position="top" @submit.prevent="migrationMode?previewMigration():save()">
<el-card shadow="never" class="detail-section">
<template #header><div class="detail-section-heading"><h2>{{t('productV1.basic')}}</h2></div></template>
<div v-if="completion.length" class="completion-note"><strong>{{t('productV1.completion')}}</strong><ul><li v-for="issue in completion" :key="issue">{{issueName(issue)}}</li></ul></div>
<div class="detail-field-grid">
<el-form-item v-if="editing&&!migrationMode" :label="t('productV1.organizationId')" prop="organizationId" :rules="!id?[{required:true,message:t('productV1.organizationRequired'),trigger:'change'}]:[]">
 <el-select v-model="draft.organizationId" :disabled="busy" filterable :placeholder="t('productV1.organizationRequired')">
  <el-option v-for="organization in organizationOptions" :key="organization.id" :value="organization.id" :label="organization.name"/>
 </el-select>
 <span v-if="id&&!draft.organizationId" class="text-[var(--el-text-color-secondary)]">{{t('productV1.organizationUnknown')}}</span>
</el-form-item>
<div v-else class="detail-readonly-field"><span>{{t('productV1.organizationId')}}</span><strong>{{organizationLabel}}</strong></div>
<el-form-item v-if="editing&&!id" :label="t('masterData.code')" prop="code" :rules="[{required:true,message:t('masterData.required')},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t('masterData.codeRule')}]"><el-input v-model="draft.code" maxlength="64" :disabled="busy"/></el-form-item><div v-else class="detail-readonly-field"><span>{{t('masterData.code')}}</span><strong>{{draft.code||'—'}}</strong></div>
<el-form-item v-if="editing&&!migrationMode" :label="t('masterData.name')" prop="name" :rules="[{required:true,whitespace:true,message:t('masterData.required')}]"><el-input v-model="draft.name" maxlength="200" :disabled="busy"/></el-form-item><div v-else class="detail-readonly-field"><span>{{t('masterData.name')}}</span><strong>{{draft.name||'—'}}</strong></div>
<el-form-item v-if="editing&&!migrationMode" :label="t('productV1.shortName')"><el-input v-model="draft.shortName" maxlength="100" :disabled="busy"/></el-form-item><div v-else class="detail-readonly-field"><span>{{t('productV1.shortName')}}</span><strong>{{draft.shortName||'—'}}</strong></div>
<el-form-item v-if="editing&&structureEditable" :label="t('productV1.categoryId')">
<el-select :model-value="draft.categoryId" clearable filterable remote :remote-method="q=>options('category',q)" :loading="categoryLoading" :disabled="busy" @visible-change="v=>v&&options('category','')" @update:model-value="chooseCategory">
<el-option v-for="r in categoryOptions" :key="r.id" :value="r.id" :label="r.name+' / '+r.code"/>
<template #footer><el-pagination :current-page="categoryPage" :page-size="20" :total="categoryTotal" small layout="prev,next" @current-change="p=>options('category',categoryKeyword,p)"/></template>
</el-select>
</el-form-item><div v-else class="detail-readonly-field"><span>{{t('productV1.categoryId')}}</span><strong>{{record?.categoryConfiguration?.path||config?.path||'—'}}</strong></div>
<el-form-item v-if="editing&&structureEditable" :label="t('productV1.categoryConfigId')">
<el-select :model-value="draft.configId" clearable :disabled="busy||!draft.categoryId" @visible-change="v=>v&&loadConfigurations()" @update:model-value="chooseConfiguration">
<el-option v-for="row in configurations" :key="row.id" :value="row.id" :label="'r'+row.revision+' / '+row.path"/>
<template #footer><el-pagination :current-page="configurationPage" :page-size="20" :total="configurationTotal" small layout="prev,next" @current-change="loadConfigurations"/></template>
</el-select>
</el-form-item><div v-else class="detail-readonly-field"><span>{{t('productV1.categoryConfigId')}}</span><strong>{{config?t('productV1.fixedVersion',{version:config.revision}):'—'}}</strong></div>
<el-form-item v-if="editing&&structureEditable" :label="t('productV1.productForm')"><el-select v-model="draft.productForm" clearable :disabled="busy" @change="draft.productForm=draft.productForm||null;invalidate()"><el-option value="PHYSICAL" :label="t('productV1.PHYSICAL')"/><el-option value="SERVICE" :label="t('productV1.SERVICE')"/><el-option value="COMPOSITE" :label="t('productStructure.COMPOSITE')"/></el-select></el-form-item><div v-else class="detail-readonly-field"><span>{{t('productV1.productForm')}}</span><strong>{{draft.productForm?t('productV1.'+draft.productForm):'—'}}</strong></div>
<el-form-item v-if="editing&&structureEditable" :label="t('productV1.baseUnitId')">
<el-select v-model="draft.unitId" clearable filterable remote :remote-method="q=>options('unit',q)" :loading="unitLoading" :disabled="busy" @visible-change="v=>v&&options('unit','')" @change="draft.unitId=draft.unitId||null;invalidate()">
<el-option v-for="r in unitOptions" :key="r.id" :value="r.id" :label="r.name+' / '+r.code"/><template #footer><el-pagination :current-page="unitPage" :page-size="20" :total="unitTotal" small layout="prev,next" @current-change="p=>options('unit',unitKeyword,p)"/></template>
</el-select>
</el-form-item><div v-else class="detail-readonly-field"><span>{{t('productV1.baseUnitId')}}</span><strong>{{record?.baseUnit?.name||'—'}}</strong></div>
<el-form-item v-if="editing&&!migrationMode" :label="t('productV1.barcode')"><el-input v-model="draft.barcode" maxlength="500" :disabled="busy"/></el-form-item><div v-else class="detail-readonly-field"><span>{{t('productV1.barcode')}}</span><strong>{{draft.barcode||'—'}}</strong></div>
<el-form-item v-if="editing&&!migrationMode" :label="t('productV1.brand')"><el-input v-model="draft.brand" maxlength="200" :disabled="busy"/></el-form-item><div v-else class="detail-readonly-field"><span>{{t('productV1.brand')}}</span><strong>{{draft.brand||'—'}}</strong></div>
<el-form-item v-if="editing&&!migrationMode" :label="t('productV1.origin')"><el-input v-model="draft.origin" maxlength="200" :disabled="busy"/></el-form-item><div v-else class="detail-readonly-field"><span>{{t('productV1.origin')}}</span><strong>{{draft.origin||'—'}}</strong></div>
<el-form-item v-if="editing&&!migrationMode" class="detail-field-wide" :label="t('productV1.description')"><el-input v-model="draft.description" type="textarea" maxlength="20000" :disabled="busy"/></el-form-item><div v-else class="detail-readonly-field detail-field-wide"><span>{{t('productV1.description')}}</span><strong>{{draft.description||'—'}}</strong></div>
<el-form-item v-if="migrationMode" :label="t('productStructure.mode')"><el-select v-model="targetMode" @change="invalidate"><el-option v-for="mode in ['NONE','STANDARD','CUSTOM']" :key="mode" :value="mode" :label="t('productStructure.'+mode)"/></el-select><el-checkbox v-model="retireSpecifications" @change="invalidate">{{t('productStructure.retireAll')}}</el-checkbox></el-form-item>
<el-form-item v-if="migrationMode" :label="t('productV1.reason')" :rules="[{required:true,message:t('masterData.required')}]" ><el-input v-model="reason" type="textarea" maxlength="1000" :disabled="busy" @input="invalidate"/></el-form-item>
</div>
<p>{{t('productStructure.'+(record?.specificationMode||'NONE'))}} · {{config?t('productV1.fixedVersion',{version:config.revision}):t('productV1.noConfiguration')}}</p>
</el-card>
<el-card v-if="groupFields.length" shadow="never" class="detail-section"><ProductAttributes detail-profile :fields="groupFields" :values="values" :readonly="!editing" :disabled="busy" @change="setValue" @valid="value=>attributesValid=value"/></el-card>
<el-card v-if="record?.legacySpecificationOriginal!==null&&record?.legacySpecificationOriginal!==undefined" shadow="never" class="detail-section"><template #header><div class="detail-section-heading"><h2>{{t('productV1.legacySpecification')}}</h2></div></template><pre>{{record.legacySpecificationOriginal}}</pre><p>{{t("productV1.legacyReview_"+(record.legacyDisposition??"UNREVIEWED"))}}</p><template v-if="props.mode!=='view'&&record.structureState==='LEGACY_PENDING'"><el-button v-operation="'Product.reviewLegacySpecification'" :disabled="busy" @click="reviewLegacy('CONFIRMED_NONE')">{{t("productV1.confirmNone")}}</el-button><el-button v-operation="'Product.reviewLegacySpecification'" :disabled="busy" @click="reviewLegacy('DEFERRED_SPEC')">{{t("productV1.deferSpecification")}}</el-button></template></el-card>
</el-form>
</template>
<template v-if="record&&!failed" #system>
 <h2 class="detail-section-title">{{t('partner.system')}}</h2>
 <RecordAuditDetails :audit="record.audit" inline/>
</template>
<template v-if="record&&!loading&&!failed&&detailTabs.length" #tabs>
 <el-tabs v-model="detailTab">
  <el-tab-pane v-for="item in detailTabs" :key="item.name" :name="item.name" :label="t(item.label)" lazy>
   <template v-if="item.name==='measurement'"><ProductMeasurementPanel :key="record.version" :id="id" :readonly="!editing||record.approvalPending||record.status==='ENABLED'||dirty||!operations.includes('Product.saveMeasurement')" :api="api" :base-unit-name="record.baseUnit?.name" :error-text="errorText" @changed="load"/></template>
   <template v-else-if="item.name==='structure'"><ProductStructure :key="record.version" :id="id" :version="record.version" :readonly="!editing||record.status==='ENABLED'||dirty" :api="api" :fields="config?.fields.filter(f=>f.purpose==='SPEC')??[]" :error-text="errorText" @changed="load"/></template>
   <template v-else-if="item.name==='approval'"><ProductApprovals :key="record.version" :id="id" :version="record.version" :readonly="props.mode==='view'||record.status==='ENABLED'||dirty||!!record.archivedAt" :can-submit="operations.includes('ProductApproval.submit')" :api="api" :error-text="errorText" @changed="load"/></template>
   <template v-else><ProductImages :key="record.version" :id="id" :version="record.version" :readonly="!editing||record.status==='ENABLED'||dirty" :api="api" :error-text="errorText" @changed="load"/></template>
  </el-tab-pane>
 </el-tabs>
</template>
</TabbedDetailTemplate>
<ManagedDialog v-model="historyOpen" :title="t('productV1.history')" width="min(1080px, calc(100vw - 32px))">
 <el-result v-if="historyError" :icon="historyDenied?'warning':'error'" :title="historyError">
  <template v-if="!historyDenied" #extra><el-button v-operation="'Product.readChanges'" @click="readHistory">{{t('masterData.retry')}}</el-button></template>
 </el-result>
 <template v-else><VxeTable border :loading="historyLoading" :column-config="{resizable:true}" :data="changes"><VxeColumn field="version" :title="t('productV1.version')" width="90"/><VxeColumn :title="t('productV1.event')"><template #default="{row}">{{t('productV1.event_'+row.eventType)}}</template></VxeColumn><VxeColumn :title="t('recordAudit.createdAt')" min-width="180"><template #default="{row}">{{displayTime(row.createdAt)}}</template></VxeColumn><VxeColumn :title="t('productV1.before')"><template #default="{row}"><pre>{{row.schemaVersion===1?snapshotText(row.beforeSnapshot):t('productV1.snapshotUnsupported')}}</pre></template></VxeColumn><VxeColumn :title="t('productV1.after')"><template #default="{row}"><pre>{{row.schemaVersion===1?snapshotText(row.afterSnapshot):t('productV1.snapshotUnsupported')}}</pre></template></VxeColumn></VxeTable><AppPagination v-model:current-page="historyPage" :page-size="20" :total="historyTotal" :disabled="historyLoading" @current-change="readHistory"/></template>
</ManagedDialog>
<ManagedDialog v-model="previewOpen" :title="t('productV1.preview')" :busy="busy">
<template v-if="preview"><p>{{t('productV1.previewNotice')}}</p><el-descriptions :column="1" border><el-descriptions-item :label="t('productV1.version')">{{preview.sourceVersion}}</el-descriptions-item><el-descriptions-item :label="t('productV1.target')">{{config?.path||'—'}} / {{config?.revision??'—'}}</el-descriptions-item><el-descriptions-item :label="t('productV1.expires')">{{displayTime(preview.expiresAt)}}</el-descriptions-item></el-descriptions><p v-if="preview.completionIssues.length">{{t('productV1.completion')}}: {{preview.completionIssues.map(issueName).join(' / ')}}</p><el-checkbox v-if="preview.droppedItemIds.length" v-model="dropConfirmed">{{t('productV1.confirmDropped',{count:preview.droppedItemIds.length})}}</el-checkbox><p v-if="ambiguous">{{t('productV1.resultUnknown')}}</p></template>
<template #footer><el-button :disabled="busy" @click="previewOpen=false">{{t('masterData.cancel')}}</el-button><el-button v-if="ambiguous" v-operation="'Product.read'" :disabled="busy" @click="verifyResult">{{t('productV1.verify')}}</el-button><el-button v-if="ambiguous" v-operation="'Product.migrateStructure'" :loading="busy" @click="executeMigration(true)">{{t('productV1.retrySame')}}</el-button><el-button v-else v-operation="'Product.migrateStructure'" type="primary" :loading="busy" :disabled="!preview?.executable||!dropConfirmed" @click="executeMigration()">{{t('productV1.execute')}}</el-button></template>
</ManagedDialog></div>
</template>
<style scoped>
/* 详情密度与列数由客商同款公共模板管理，长内容由应用壳统一滚动。 */
.product-editor { display:flex; flex:1; flex-direction:column; min-width:0; }
.product-editor > :deep(.master-data-detail) { flex:1; }
.completion-note { padding-block:var(--app-section-gap,8px); color:var(--el-text-color-secondary); }
pre { white-space:pre-wrap; overflow-wrap:anywhere; margin:0; font:inherit; }
</style>
