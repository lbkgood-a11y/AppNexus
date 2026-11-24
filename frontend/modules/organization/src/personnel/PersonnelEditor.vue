<script setup lang="ts">
import { referenceName } from "@appnexus/ui";
import {personnelAccess,type PersonnelMode} from "./access";
import {AppIcon} from "@appnexus/icons";
import {computed,onBeforeUnmount,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {onBeforeRouteLeave} from "vue-router";
import {ElMessage,ElMessageBox,type FormInstance,type FormRules} from "element-plus";
import {GeographyPicker,geographyPath,type GeographyNode,type GeographySelection,PageHeader,TabbedDetailTemplate,ManagementTable,ManagedDialog,InfoHint,FloatingError,LoadErrorResult,presentLoadError,setErrorFeedback,type LoadIssue,type ManagementColumn} from "@appnexus/ui";
import {emptyPersonnel,personnelFields,personnelChoices,personnelDictionaryFields,personnelSections,type PersonnelApi,type PersonnelProfile,type PersonnelCatalog,type PersonnelSection,type PersonnelDetail,type PersonnelAssignment} from "./personnel";
/** 人员专用完整详情；mode 为 view 时只读，edit 时仍逐项校验平台操作准入，不因入口模式扩大权限。 */
const loadIssue = ref<LoadIssue>();
const props=defineProps<{id:string;mode:PersonnelMode;api:PersonnelApi;errorText:(error:unknown)=>string}>();
const emit=defineEmits<{close:[]}>();
const {t,locale}=useI18n();
const id=ref(props.id),profile=ref<PersonnelProfile>(),catalog=ref<PersonnelCatalog>({organizations:[],positions:[],users:[]});
const draft=reactive(emptyPersonnel()),baseline=ref(""),loading=ref(false),busy=ref(false),failed=ref(false),error=ref("");
const allowed=ref<string[]>([]),form=ref<FormInstance>(),detailForm=ref<FormInstance>(),dialog=ref(false),detailId=ref("");
const tab=ref<PersonnelSection|"assignments">("organizations");
const geographyNodes=ref<GeographyNode[]>([]);
/** 地址上级改变时清除依赖草稿，禁止把旧城市提交到新省份。 */
function selectGeography(field:string,selection:GeographySelection|null){if(field==="countryRegion"){values.province="";values.city="";}if(field==="province"){values.city="";if(selection)values.countryRegion=selection.node.countryCode;}if(field==="city"&&selection){values.countryRegion=selection.node.countryCode;values.province=selection.path[1]?.code??"";}}
const values=reactive<Record<string,string>>({});
/** 基础资料优先展示身份识别字段，敏感及工作属性置于详细资料；顺序固定，不由客户端动态配置。 */
const basicFields=["englishName","gender","birthDate","ethnicity","nativePlace","politicalStatus"] as const;
const profileGroups=computed(()=>[
 {key:"basic",title:t("personnel.basic"),fields:basicFields},
 {key:"details",title:t("personnel.profileDetails"),fields:personnelFields.filter(field=>!basicFields.includes(field as typeof basicFields[number]))}
]);
const detailsExpanded=ref(true);
/** 摘要仅使用真实姓名首字，不新增未接入后端的头像上传入口。 */
const initials=computed(()=>draft.name.trim().slice(0,1));

const isAssignment=computed(()=>tab.value==="assignments");
const section=computed(()=>isAssignment.value?undefined:personnelSections[tab.value as PersonnelSection]);
const profileFieldRestrictions=ref<Record<string,string>>({});
/** 由统一服务端策略限制表单字段；遮盖或隐藏的值不回传。 */
function profileMode(field:string){return profileFieldRestrictions.value[field];}
function detailMode(field:string){return isAssignment.value?undefined:profile.value?.detailFieldRestrictions?.[field];}
const canAddDetail=computed(()=>isAssignment.value||!(section.value?.required??[]).some(field=>detailMode(field)));
const canRemoveDetail=computed(()=>isAssignment.value||!(section.value?.fields??[]).some(field=>detailMode(field)));
const dialogFields=computed(()=>isAssignment.value?["memberId"]:[...(section.value?.fields??[])]);
const dirty=computed(()=>baseline.value!==JSON.stringify(draft));
const saveOperation=computed(()=>id.value?"Person.update":"Person.create");
const detailOperation=computed(()=>isAssignment.value?(detailId.value?"Person.updateAssignment":"Person.assign"):(detailId.value?"Person.updateDetail":"Person.createDetail"));
/** 页面模式和平台真实操作共同决定可写范围；主档、明细、账号关联权限互不代替。 */
const access=computed(()=>personnelAccess(allowed.value,props.mode));
const canRead=computed(()=>access.value.canRead);
const canEdit=computed(()=>access.value.allows(saveOperation.value));
const editable=computed(()=>canEdit.value&&!loading.value&&!failed.value);
const detailsLocked=computed(()=>props.mode!=="edit"||!canRead.value||busy.value||loading.value||dirty.value||!id.value||failed.value);
const currentRows=computed<Array<PersonnelDetail|PersonnelAssignment>>(()=>isAssignment.value?(profile.value?.assignments??[]):(profile.value?.details??[]).filter(d=>d.section===tab.value));
const detailColumns=computed<ManagementColumn[]>(()=>[
 ...(isAssignment.value?[{key:"active",label:t("personnel.status"),width:130},{key:"userName",prop:"userName",label:t("personnel.userName"),minWidth:160}]:dialogFields.value.filter(key=>detailMode(key)!=="HIDDEN").map(key=>({key,label:label(key),minWidth:key==="remarks"?220:150}))),
 ...(props.mode==="edit"?[{key:"operations",label:t("masterData.actions"),width:190,fixed:"right" as const}]:[])]);
const rules=computed<FormRules>(()=>({
 code:[{required:true,message:t("personnel.required"),trigger:"blur"},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t("masterData.codeRule"),trigger:"blur"}],
 name:[{required:true,whitespace:true,message:t("personnel.required"),trigger:"blur"}],
 "attributes.employmentStatus":[{required:true,message:t("personnel.required"),trigger:"change"}],
 "attributes.email":[{type:"email",message:t("masterData.emailRule"),trigger:"blur"}]
}));
const detailRules=computed<FormRules>(()=>Object.fromEntries((isAssignment.value?["memberId"]:[...(section.value?.required??[])]).map(key=>[key,[{required:true,whitespace:true,message:t("personnel.required"),trigger:"change"}]])));
/** 日期或下拉使用固定控件，不把业务日期转换为 UTC 时间点。 */
function isDate(field:string){return field==="birthDate"||field.endsWith("On");}
/** 字段展示名；组织/岗位选择使用已有业务语义文案。 */
function label(field:string){return ["organizationId","positionId","memberId"].includes(field)?t("personnel."+field):t("personnel.fields."+field);}
/** 已保存引用可显示停用对象名称，新建选项只提供启用且属于当前组织的实体。 */
function options(field:string,currentValue?:string){
 if(field==="organizationId")return catalog.value.organizations.filter(o=>o.enabled).map(o=>({value:o.id,label:o.name}));
 if(field==="positionId")return catalog.value.positions.filter(p=>p.enabled&&p.organizationId===values.organizationId).map(p=>({value:p.id,label:p.name}));
 if(field==="memberId")return catalog.value.users.map(u=>({value:u.memberId,label:u.name}));
 if(personnelChoices[field])return personnelChoices[field].map(value=>({value,label:t("personnel.choices."+value),disabled:false}));
 const current=currentValue??values[field]??draft.attributes[field]??"";const rows=catalog.value.dictionaries?.[field]??[];
 return [...rows.filter(row=>row.enabled||row.value===current).map(row=>({value:row.value,label:row.name,disabled:!row.enabled})),...(!current||rows.some(row=>row.value===current)?[]:[{value:current,label:current,disabled:true}])];
}
/** 按真实字典名称展示，旧值未收录时保留稳定值；不从硬编码选项回退。 */
function dictionaryName(field:string,value:string){if(["nativePlace","countryRegion","province","city"].includes(field))return geographyPath(geographyNodes.value,value).map(n=>n.name).join("-")||value;return personnelChoices[field]?t("personnel.choices."+value):(catalog.value.dictionaries?.[field]?.find(row=>row.value===value)?.name??value);}
/** 切换组织清空依赖用户或岗位，防止把旧组织的选择隐式提交到新组织。 */
function changeOrganization(){values.memberId="";values.positionId="";}
/** 审计时间使用当前语言格式化；原值是后端 UTC 时间点，未知历史显示缺省说明。 */
function time(value:string|null|undefined){return value?new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"short"}).format(new Date(value)):t("personnel.unknownHistory");}
/** 明细值只读展示，未解析到已失效引用时保留标识用于排查，不伪装有效关系。 */
function display(row:PersonnelDetail,field:string){
 const value=row.values[field];if(!value)return "—";
 const resolved=row[`${field}Name`];if(resolved)return resolved;
 if(field==="organizationId")return referenceName(value,catalog.value.organizations,item=>item.name,t("referenceName.unavailable"));
 if(field==="positionId")return referenceName(value,catalog.value.positions,item=>item.name,t("referenceName.unavailable"));
 return dictionaryName(field,value);
}
/** 重建基础草稿只在主动加载或写入成功后执行；不会在后台刷新时覆盖用户输入。 */
function accept(value:PersonnelProfile){profile.value=value;profileFieldRestrictions.value=value.person.fieldRestrictions??{};Object.assign(draft,{code:value.person.code,name:value.person.name,version:value.person.version,attributes:{...value.person.attributes}});baseline.value=JSON.stringify(draft);}
/**
 * 人员详情的可重试加载错误使用顶部浮层；无权时由正文承载状态且不提供重试。
 * @param cause 读取失败原因，用于识别 HTTP 403。
 * @param message 已转换的提示文案；空文案交给请求或会话流程处理。
 */
async function showLoadFailure(cause:unknown,message:string){
 const denied=(cause as {response?:{status?:number}})?.response?.status===403;
 if(denied)await presentLoadError(error,loadIssue,message,true,true);
 else await setErrorFeedback(error,message);
}
/** 读取最新档案及实时准入；失败停止继续写入，保留草稿供核实，403 清空敏感正文。 */
async function load(){
 loading.value=true;failed.value=false;loadIssue.value=undefined;error.value="";
 try{allowed.value=await props.api.operations();if(!canRead.value){profile.value=undefined;catalog.value={organizations:[],positions:[],users:[]};geographyNodes.value=[];Object.assign(draft,emptyPersonnel());baseline.value=JSON.stringify(draft);return;}profileFieldRestrictions.value=await props.api.fieldRestrictions();catalog.value=await props.api.options();geographyNodes.value=await props.api.geographyDirectory();if(id.value)accept(await props.api.read(id.value));else baseline.value=JSON.stringify(draft);}
 catch(e){failed.value=true;if((e as {response?:{status:number}})?.response?.status===403){profile.value=undefined;Object.assign(draft,emptyPersonnel());allowed.value=[];catalog.value={organizations:[],positions:[],users:[]};geographyNodes.value=[];dialog.value=false;baseline.value=JSON.stringify(draft);}await showLoadFailure(e,props.errorText(e));}
 finally{loading.value=false;}
}
/** 主动重新读取前确认丢弃未保存草稿，409 不自动重放或强行覆盖。 */
async function reload(){if(busy.value||loading.value)return;if(dirty.value||dialog.value){try{await ElMessageBox.confirm(t("personnel.reloadWarning"),t("personnel.title"),{confirmButtonText:t("personnel.retry"),cancelButtonText:t("masterData.cancel")});}catch{return;}}dialog.value=false;await load();}
/** 返回列表只影响本地草稿；未保存时确认，不调用删除接口。 */
async function back(){if(busy.value)return;if(dirty.value){try{await ElMessageBox.confirm(t("personnel.discard"),t("personnel.title"),{confirmButtonText:t("personnel.discardAction"),cancelButtonText:t("masterData.cancel")});}catch{return;}}emit("close");}
/** 保存失败保留基础表单；成功后先记录确定结果，再刷新，防止读取失败导致重复创建。 */
async function save(){
 if(busy.value||!editable.value||!await form.value?.validate().catch(()=>false))return;
 if(draft.attributes.birthDate&&draft.attributes.birthDate>new Date().toISOString().slice(0,10)){await setErrorFeedback(error,t("personnel.birthFuture"));return;}
 busy.value=true;let saved=false;
 try{const input={...draft,attributes:Object.fromEntries(Object.entries(draft.attributes).filter(([field])=>!profileMode(field)))};if(id.value)await props.api.update("person",id.value,input);else id.value=(await props.api.create("person",input)).id;
 saved=true;baseline.value=JSON.stringify(draft);ElMessage.success(t("personnel.saved"));accept(await props.api.read(id.value));}
 catch(e){if (saved) {
   failed.value = true;
   const message = props.errorText(e);
   await showLoadFailure(e,message ? t("personnel.latestLoadFailed") : "");
 } else {
   await setErrorFeedback(error, props.errorText(e));
 }}finally{busy.value=false;}
}
/** 明细弹窗使用行快照；未知键不传后端，创建/修改操作分别绑定。 */
function openDetail(row?:PersonnelDetail|PersonnelAssignment){
 if(detailsLocked.value||!access.value.allows(isAssignment.value?(row?"Person.updateAssignment":"Person.assign"):(row?"Person.updateDetail":"Person.createDetail")))return;
 detailId.value=row?.id??"";for(const key of Object.keys(values))delete values[key];
 for(const field of dialogFields.value)values[field]=isAssignment.value?String((row as unknown as Record<string,unknown>)?.[field]??""):(row as PersonnelDetail)?.values[field]??"";
 for(const field of ["enabled","isPrimary"])if(dialogFields.value.includes(field)&&!values[field])values[field]=field==="enabled"?"true":"false";
 dialog.value=true;
}
/** 明细与关联保存共用父档案版本；成功后关闭弹窗再加载最新版本，失败保留输入。 */
async function saveDetail(){
 if(detailsLocked.value||!access.value.allows(detailOperation.value)||!await detailForm.value?.validate().catch(()=>false))return;
 if(values.startsOn&&values.endsOn&&values.endsOn<values.startsOn){await setErrorFeedback(error,t("personnel.dateRange"));return;}
 busy.value=true;let saved=false;
 try{if(isAssignment.value)await props.api.assign(id.value,detailId.value||undefined,{memberId:values.memberId,version:draft.version});
 else await props.api.saveDetail(id.value,tab.value,detailId.value||undefined,{version:draft.version,values:Object.fromEntries(dialogFields.value.filter(field=>!detailMode(field)).map(field=>[field,values[field]??""]))});
 saved=true;dialog.value=false;ElMessage.success(t("personnel.saved"));accept(await props.api.read(id.value));catalog.value=await props.api.options();geographyNodes.value=await props.api.geographyDirectory();}
 catch(e){if (saved) {
   failed.value = true;
   const message = props.errorText(e);
   await showLoadFailure(e,message ? t("personnel.latestLoadFailed") : "");
 } else {
   await setErrorFeedback(error, props.errorText(e));
 }}finally{busy.value=false;}
}
/** 删除仅删除所选明细或解除单条关联，确认后以父版本提交，不删除人员或账号。 */
async function remove(row:PersonnelDetail|PersonnelAssignment){
 if(detailsLocked.value||!access.value.allows(isAssignment.value?"Person.unassign":"Person.deleteDetail"))return;
 try{await ElMessageBox.confirm(isAssignment.value?t("personnel.confirmUnassign",{name:(row as PersonnelAssignment).userName||"—"}):t("personnel.confirmDelete",{section:t("personnel.sections."+tab.value)}),t("personnel.title"),{type:"warning",confirmButtonText:t(isAssignment.value?"personnel.unassign":"personnel.deleteDetail"),cancelButtonText:t("masterData.cancel")});}catch{return;}
 busy.value=true;let saved=false;
 try{if(isAssignment.value)await props.api.unassign(id.value,row.id,draft.version);else await props.api.deleteDetail(id.value,tab.value,row.id,draft.version);
 saved=true;ElMessage.success(t("personnel.saved"));accept(await props.api.read(id.value));}
 catch(e){if (saved) {
   failed.value = true;
   const message = props.errorText(e);
   await showLoadFailure(e,message ? t("personnel.latestLoadFailed") : "");
 } else {
   await setErrorFeedback(error, props.errorText(e));
 }}finally{busy.value=false;}
}
/** 页面关闭保护仅在草稿未保存时启用，不阻止正常只读导航。 */
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value||dialog.value)event.preventDefault();}
/** 应用内切换路由时也保护草稿；在途写入结束前不离开，避免用户误判提交结果。 */
onBeforeRouteLeave(async()=>{if(busy.value)return false;if(!dirty.value&&!dialog.value)return true;try{await ElMessageBox.confirm(t("personnel.discard"),t("personnel.title"),{confirmButtonText:t("personnel.discardAction"),cancelButtonText:t("masterData.cancel")});return true;}catch{return false;}});
onMounted(()=>{baseline.value=JSON.stringify(draft);window.addEventListener("beforeunload",beforeUnload);void load();});
onBeforeUnmount(()=>window.removeEventListener("beforeunload",beforeUnload));
</script>
<template>
 <div class="personnel-editor" v-loading="loading">
  <FloatingError :title="error"><el-button v-if="props.mode!=='view'" :disabled="busy||loading" @click="reload">{{t('personnel.retry')}}</el-button></FloatingError>
  <TabbedDetailTemplate master-data>
   <template #header><PageHeader :title="id?(draft.name||t('personnel.title')):t('personnel.new')">
    <template #meta><el-tag v-if="props.mode==='view'" type="info">{{t('personnel.readOnly')}}</el-tag><el-tag v-if="draft.code" type="info" effect="plain">{{draft.code}}</el-tag><el-tag class="app-status-tag" v-if="profile" :type="profile.person.enabled?'success':'info'">{{t('masterData.'+(profile.person.enabled?'enabled':'disabled'))}}</el-tag></template>
    <template #actions><el-button :disabled="busy" @click="back">{{t('personnel.back')}}</el-button><el-button v-if="props.mode==='edit'" v-operation="saveOperation" type="primary" :loading="busy" :disabled="!editable||!dirty" @click="save">{{t('masterData.save')}}</el-button></template>
   </PageHeader></template>
   <template #content>
    <LoadErrorResult v-if="loadIssue" :issue="loadIssue" />
    <el-result v-else-if="!loading&&!failed&&!canRead" icon="warning" :title="t('masterData.noPermission')" />
    <el-form v-else-if="!failed" ref="form" :model="draft" :rules="rules" label-position="top" :disabled="!editable||busy" class="detail-profile-form detail-border-label-form" @submit.prevent="save">
     <el-card v-for="group in profileGroups" :key="group.key" shadow="never" class="detail-section" :body-style="group.key==='details'&&!detailsExpanded?{padding:'0'}:undefined">
      <template #header><div class="detail-section-heading"><h2>{{group.title}}</h2><el-button v-if="props.mode!=='view'&&group.key==='details'" text :title="t(detailsExpanded?'personnel.collapseDetails':'personnel.expandDetails')" :aria-label="t(detailsExpanded?'personnel.collapseDetails':'personnel.expandDetails')" :aria-expanded="detailsExpanded" aria-controls="personnel-profile-details" @click="detailsExpanded=!detailsExpanded"><AppIcon :name="detailsExpanded?'arrowUp':'arrowDown'" :size="18" /></el-button></div></template>
      <div v-show="props.mode==='view'||group.key==='basic'||detailsExpanded" :id="group.key==='details'?'personnel-profile-details':undefined" :class="{'detail-identity-layout':group.key==='basic'}">
       <div v-if="group.key==='basic'" class="detail-identity-mark" aria-hidden="true"><el-avatar :size="64">{{initials}}<AppIcon v-if="!initials" name="user" :size="30" /></el-avatar></div>
       <div v-if="canEdit" class="detail-field-grid">
        <template v-if="group.key==='basic'">
         <!-- 已保存编码保持只读，单行省略仅影响展示；悬停或键盘聚焦通过模板 Tooltip 查看原值。 -->
         <el-form-item :label="t('personnel.code')" prop="code"><el-tooltip v-if="id" :content="draft.code" placement="top" :show-after="200" :popper-style="{maxWidth:'min(480px, calc(100vw - 24px))',overflowWrap:'anywhere'}"><span class="detail-code-value" tabindex="0">{{draft.code}}</span></el-tooltip><el-input v-else v-model="draft.code" maxlength="64" /></el-form-item>
         <div v-if="profile" class="detail-readonly-field"><span>{{t('personnel.status')}}</span><el-tag class="app-status-tag" :type="profile.person.enabled?'success':'info'">{{t(profile.person.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag></div>
         <el-form-item :label="t('personnel.name')" prop="name"><el-input v-model="draft.name" maxlength="200" /></el-form-item>
        </template>
        <div v-for="field in group.fields.filter(field=>profileMode(field)&&profileMode(field)!=='HIDDEN')" :key="'protected-'+field" class="detail-readonly-field"><span>{{label(field)}}</span><strong>{{draft.attributes[field]||'—'}}</strong></div>
        <el-form-item v-for="field in group.fields.filter(field=>!profileMode(field))" :key="field" :label="label(field)" :prop="'attributes.'+field" :class="{'detail-field-wide':field==='remarks'}">
         <el-date-picker v-if="isDate(field)" v-model="draft.attributes[field]" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :disabled-date="date=>field==='birthDate'&&date.getTime()>Date.now()" />
         <el-select v-else-if="personnelChoices[field]||personnelDictionaryFields.includes(field)" v-model="draft.attributes[field]" clearable><el-option v-for="option in options(field,draft.attributes[field])" :key="option.value" :value="option.value" :label="option.label" :disabled="'disabled' in option&&option.disabled===true" /></el-select>
         <GeographyPicker v-else-if="field==='nativePlace'" v-model="draft.attributes[field]" :nodes="geographyNodes" />
         <el-input v-else v-model="draft.attributes[field]" :maxlength="field==='email'?200:500" :type="field==='remarks'?'textarea':'text'" :rows="1" />
        </el-form-item>
       </div>
       <div v-else class="detail-field-grid detail-readonly-grid">
        <template v-if="group.key==='basic'"><div class="detail-readonly-field"><span>{{t('personnel.code')}}</span><strong>{{draft.code||'—'}}</strong></div><div v-if="profile" class="detail-readonly-field"><span>{{t('personnel.status')}}</span><el-tag class="app-status-tag" :type="profile.person.enabled?'success':'info'">{{t(profile.person.enabled?'masterData.enabled':'masterData.disabled')}}</el-tag></div><div class="detail-readonly-field"><span>{{t('personnel.name')}}</span><strong>{{draft.name||'—'}}</strong></div></template>
        <div v-for="field in group.fields.filter(field=>profileMode(field)!=='HIDDEN')" :key="field" class="detail-readonly-field" :class="{'detail-field-wide':field==='remarks'}"><span>{{label(field)}}</span><strong>{{draft.attributes[field]?profile?.person?.[`${field}Name`] || dictionaryName(field,draft.attributes[field]):'—'}}</strong></div>
       </div>
      </div>
     </el-card>
    </el-form>
   </template>
   <template #system>
    <h2 class="detail-section-title">{{t('personnel.system')}}</h2>
    <div v-if="canRead&&!failed" class="detail-audit-grid">
     <div class="detail-audit-item"><AppIcon name="user" :size="20" /><div><span>{{t('personnel.createdName')}}</span><strong>{{profile?.audit.createdByName||t('personnel.unknownHistory')}}</strong></div></div>
     <div class="detail-audit-item"><AppIcon name="history" :size="20" /><div><span>{{t('personnel.createdAt')}}</span><strong>{{time(profile?.audit.createdAt)}}</strong></div></div>
     <div class="detail-audit-item"><AppIcon name="edit" :size="20" /><div><span>{{t('personnel.updatedName')}}</span><strong>{{profile?.audit.updatedByName||t('personnel.unknownHistory')}}</strong></div></div>
     <div class="detail-audit-item"><AppIcon name="history" :size="20" /><div><span>{{t('personnel.updatedAt')}}</span><strong>{{time(profile?.audit.updatedAt)}}</strong></div></div>
    </div>
   </template>
   <template #tabs>
    <el-empty v-if="!id" :description="t('personnel.saveFirst')" />
    <template v-else-if="canRead&&!failed">
     <el-tabs v-model="tab"><el-tab-pane v-for="(_,key) in personnelSections" :key="key" :name="key" :label="t('personnel.sections.'+key)" /><el-tab-pane name="assignments" :label="t('personnel.assignments')" /></el-tabs>
     <p v-if="dirty" class="personnel-inline-hint">{{t('personnel.dirty')}}</p>
     <ManagementTable :title="t(isAssignment?'personnel.assignments':'personnel.sections.'+tab)" :rows="currentRows" :columns="detailColumns" :readonly-view="props.mode==='view'" :busy="busy" :loading="loading" heading-level="h2" embedded @refresh="reload">
      <template v-if="props.mode!=='view'" #titleHelp><InfoHint :content="t(isAssignment?'personnel.assignmentHint':'personnel.organizationHint')" /></template>
      <template v-if="props.mode==='edit'" #actions><el-button v-operation="isAssignment?'Person.assign':'Person.createDetail'" type="primary" :disabled="detailsLocked||!canAddDetail" @click="openDetail()">{{t(isAssignment?'personnel.addAssignment':'personnel.addDetail')}}</el-button></template>
      <template v-for="field in (section?.fields??[])" :key="field" #[field]="{row}">{{display(row as PersonnelDetail,field)}}</template>
      <template #active="{row}"><el-tag class="app-status-tag" :type="('active' in row&&row.active)?'success':'warning'">{{t(('active' in row&&row.active)?'personnel.active':'personnel.inactive')}}</el-tag></template>
      <template v-if="props.mode==='edit'" #operations="{row}"><el-button v-operation="isAssignment?'Person.updateAssignment':'Person.updateDetail'" link type="primary" :disabled="detailsLocked" @click="openDetail(row)">{{t('masterData.edit')}}</el-button><el-button v-operation="isAssignment?'Person.unassign':'Person.deleteDetail'" link type="danger" :disabled="detailsLocked||!canRemoveDetail" @click="remove(row)">{{t(isAssignment?'personnel.unassign':'personnel.deleteDetail')}}</el-button></template>
     </ManagementTable>
    </template>
   </template>
  </TabbedDetailTemplate>
  <ManagedDialog v-if="props.mode==='edit'&&canRead" v-model="dialog" :title="t(isAssignment?(detailId?'personnel.editAssignment':'personnel.addAssignment'):(detailId?'personnel.editDetail':'personnel.addDetail'))" :busy="busy">
   <el-form class="business-border-label-form" ref="detailForm" :model="values" :rules="detailRules" label-position="top" :disabled="busy" @submit.prevent="saveDetail">
    <el-form-item v-for="field in dialogFields.filter(field=>!detailMode(field))" :key="field" :label="label(field)" :prop="field">
     <GeographyPicker v-if="['countryRegion','province','city'].includes(field)" v-model="values[field]" :nodes="geographyNodes" :kind="field==='countryRegion'?'country':'region'" :country-code="field==='countryRegion'?undefined:values.countryRegion" :parent-code="field==='city'?values.province:undefined" :level="field==='province'?'province':field==='city'?'city':'any'" @select="selectGeography(field,$event)" />
     <el-date-picker v-else-if="isDate(field)" v-model="values[field]" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
     <el-select v-else-if="['organizationId','positionId','memberId'].includes(field)||personnelChoices[field]||personnelDictionaryFields.includes(field)" v-model="values[field]" filterable clearable @change="field==='organizationId'&&changeOrganization()"><el-option v-for="option in options(field)" :key="option.value" :value="option.value" :label="option.label" :disabled="'disabled' in option&&option.disabled===true" /></el-select>
     <el-input v-else v-model="values[field]" maxlength="500" :type="field==='remarks'?'textarea':'text'" />
    </el-form-item>
   </el-form>
   <template #footer><el-button :disabled="busy" @click="dialog=false">{{t('masterData.cancel')}}</el-button><el-button v-operation="detailOperation" type="primary" :loading="busy" @click="saveDetail">{{t('masterData.save')}}</el-button></template>
  </ManagedDialog>
 </div>
</template>
<style scoped>
/* 页面只维护业务提示，分区、表单、审计与响应式由公共主数据详情样板负责。 */
.personnel-editor {min-width:0;overflow:auto;}
.personnel-inline-hint {color:var(--el-text-color-secondary);margin:8px 0;font-size:13px;}
</style>
