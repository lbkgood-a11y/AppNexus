<script setup lang="ts">
import {AppIcon} from "@appnexus/icons";
import {computed,onBeforeUnmount,onMounted,reactive,ref} from "vue";
import {useI18n} from "vue-i18n";
import {onBeforeRouteLeave} from "vue-router";
import {ElMessage,ElMessageBox,type FormInstance,type FormRules} from "element-plus";
import {PageHeader,TabbedDetailTemplate,ManagementTable,ManagedDialog,InfoHint,FloatingError,setErrorFeedback,type ManagementColumn} from "@appnexus/ui";
import {emptyPersonnel,personnelFields,personnelChoices,personnelSections,type PersonnelApi,type PersonnelProfile,type PersonnelCatalog,type PersonnelSection,type PersonnelDetail,type PersonnelAssignment} from "./personnel";
/** 人员专用完整详情；复用现有详情模板、表格和弹窗，按参考图分区并适配窄屏。 */
const props=defineProps<{id:string;api:PersonnelApi;errorText:(error:unknown)=>string}>();
const emit=defineEmits<{close:[]}>();
const {t,locale}=useI18n();
const id=ref(props.id),profile=ref<PersonnelProfile>(),catalog=ref<PersonnelCatalog>({organizations:[],positions:[],users:[]});
const draft=reactive(emptyPersonnel()),baseline=ref(""),loading=ref(false),busy=ref(false),failed=ref(false),error=ref("");
const allowed=ref<string[]>([]),form=ref<FormInstance>(),detailForm=ref<FormInstance>(),dialog=ref(false),detailId=ref("");
const tab=ref<PersonnelSection|"assignments">("organizations");
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
const dialogFields=computed(()=>isAssignment.value?["memberId"]:[...(section.value?.fields??[])]);
const dirty=computed(()=>baseline.value!==JSON.stringify(draft));
const saveOperation=computed(()=>id.value?"Person.update":"Person.create");
const detailOperation=computed(()=>isAssignment.value?(detailId.value?"Person.updateAssignment":"Person.assign"):(detailId.value?"Person.updateDetail":"Person.createDetail"));
const canEdit=computed(()=>allowed.value.includes(saveOperation.value));
const editable=computed(()=>allowed.value.includes(saveOperation.value)&&!loading.value&&!failed.value);
const detailsLocked=computed(()=>busy.value||loading.value||dirty.value||!id.value||failed.value);
const currentRows=computed<Array<PersonnelDetail|PersonnelAssignment>>(()=>isAssignment.value?(profile.value?.assignments??[]):(profile.value?.details??[]).filter(d=>d.section===tab.value));
const detailColumns=computed<ManagementColumn[]>(()=>[
 ...(isAssignment.value?[{key:"userName",prop:"userName",label:t("personnel.userName"),minWidth:160},{key:"active",label:t("personnel.status"),width:130}]:dialogFields.value.map(key=>({key,label:label(key),minWidth:key==="remarks"?220:150}))),
 {key:"operations",label:t("masterData.actions"),width:190,fixed:"right"}]);
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
function options(field:string){
 if(field==="organizationId")return catalog.value.organizations.filter(o=>o.enabled).map(o=>({value:o.id,label:o.name}));
 if(field==="positionId")return catalog.value.positions.filter(p=>p.enabled&&p.organizationId===values.organizationId).map(p=>({value:p.id,label:p.name}));
 if(field==="memberId")return catalog.value.users.map(u=>({value:u.memberId,label:u.name}));
 return (personnelChoices[field]??[]).map(value=>({value,label:t("personnel.choices."+value)}));
}
/** 切换组织清空依赖用户或岗位，防止把旧组织的选择隐式提交到新组织。 */
function changeOrganization(){values.memberId="";values.positionId="";}
/** 审计时间使用当前语言格式化；原值是后端 UTC 时间点，未知历史显示缺省说明。 */
function time(value:string|null|undefined){return value?new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"short"}).format(new Date(value)):t("personnel.unknownHistory");}
/** 明细值只读展示，未解析到已失效引用时保留标识用于排查，不伪装有效关系。 */
function display(row:PersonnelDetail,field:string){
 const value=row.values[field];if(!value)return "—";
 if(field==="organizationId")return catalog.value.organizations.find(o=>o.id===value)?.name??value;
 if(field==="positionId")return catalog.value.positions.find(p=>p.id===value)?.name??value;
 return personnelChoices[field]?t("personnel.choices."+value):value;
}
/** 重建基础草稿只在主动加载或写入成功后执行；不会在后台刷新时覆盖用户输入。 */
function accept(value:PersonnelProfile){profile.value=value;Object.assign(draft,{code:value.person.code,name:value.person.name,version:value.person.version,attributes:{...value.person.attributes}});baseline.value=JSON.stringify(draft);}
/** 读取最新档案及实时准入；失败停止继续写入，保留草稿供核实，403 清空敏感正文。 */
async function load(){
 loading.value=true;failed.value=false;
 try{allowed.value=await props.api.operations();catalog.value=await props.api.options();if(id.value)accept(await props.api.read(id.value));else baseline.value=JSON.stringify(draft);}
 catch(e){failed.value=true;if((e as {response?:{status:number}})?.response?.status===403){profile.value=undefined;Object.assign(draft,emptyPersonnel());allowed.value=[];}await setErrorFeedback(error,props.errorText(e));}
 finally{loading.value=false;}
}
/** 主动重新读取前确认丢弃未保存草稿，409 不自动重放或强行覆盖。 */
async function reload(){if(dirty.value||dialog.value){try{await ElMessageBox.confirm(t("personnel.reloadWarning"),t("personnel.title"),{confirmButtonText:t("personnel.retry"),cancelButtonText:t("masterData.cancel")});}catch{return;}}dialog.value=false;await load();}
/** 返回列表只影响本地草稿；未保存时确认，不调用删除接口。 */
async function back(){if(busy.value)return;if(dirty.value){try{await ElMessageBox.confirm(t("personnel.discard"),t("personnel.title"),{confirmButtonText:t("personnel.discardAction"),cancelButtonText:t("masterData.cancel")});}catch{return;}}emit("close");}
/** 保存失败保留基础表单；成功后先记录确定结果，再刷新，防止读取失败导致重复创建。 */
async function save(){
 if(busy.value||!editable.value||!await form.value?.validate().catch(()=>false))return;
 if(draft.attributes.birthDate&&draft.attributes.birthDate>new Date().toISOString().slice(0,10)){await setErrorFeedback(error,t("personnel.birthFuture"));return;}
 busy.value=true;let saved=false;
 try{const input={...draft,attributes:{...draft.attributes}};if(id.value)await props.api.update("person",id.value,input);else id.value=(await props.api.create("person",input)).id;
 saved=true;baseline.value=JSON.stringify(draft);ElMessage.success(t("personnel.saved"));accept(await props.api.read(id.value));}
 catch(e){if(saved)failed.value=true;await setErrorFeedback(error,saved?t("personnel.latestLoadFailed"):props.errorText(e));}finally{busy.value=false;}
}
/** 明细弹窗使用行快照；未知键不传后端，创建/修改操作分别绑定。 */
function openDetail(row?:PersonnelDetail|PersonnelAssignment){
 if(detailsLocked.value)return;
 detailId.value=row?.id??"";for(const key of Object.keys(values))delete values[key];
 for(const field of dialogFields.value)values[field]=isAssignment.value?String((row as unknown as Record<string,unknown>)?.[field]??""):(row as PersonnelDetail)?.values[field]??"";
 for(const field of ["enabled","isPrimary"])if(dialogFields.value.includes(field)&&!values[field])values[field]=field==="enabled"?"true":"false";
 dialog.value=true;
}
/** 明细与关联保存共用父档案版本；成功后关闭弹窗再加载最新版本，失败保留输入。 */
async function saveDetail(){
 if(busy.value||!await detailForm.value?.validate().catch(()=>false))return;
 if(values.startsOn&&values.endsOn&&values.endsOn<values.startsOn){await setErrorFeedback(error,t("personnel.dateRange"));return;}
 busy.value=true;let saved=false;
 try{if(isAssignment.value)await props.api.assign(id.value,detailId.value||undefined,{memberId:values.memberId,version:draft.version});
 else await props.api.saveDetail(id.value,tab.value,detailId.value||undefined,{version:draft.version,values:Object.fromEntries(dialogFields.value.map(field=>[field,values[field]??""]))});
 saved=true;dialog.value=false;ElMessage.success(t("personnel.saved"));accept(await props.api.read(id.value));catalog.value=await props.api.options();}
 catch(e){if(saved)failed.value=true;await setErrorFeedback(error,saved?t("personnel.latestLoadFailed"):props.errorText(e));}finally{busy.value=false;}
}
/** 删除仅删除所选明细或解除单条关联，确认后以父版本提交，不删除人员或账号。 */
async function remove(row:PersonnelDetail|PersonnelAssignment){
 if(detailsLocked.value)return;
 try{await ElMessageBox.confirm(isAssignment.value?t("personnel.confirmUnassign",{name:(row as PersonnelAssignment).userName||"—"}):t("personnel.confirmDelete",{section:t("personnel.sections."+tab.value)}),t("personnel.title"),{type:"warning",confirmButtonText:t(isAssignment.value?"personnel.unassign":"personnel.deleteDetail"),cancelButtonText:t("masterData.cancel")});}catch{return;}
 busy.value=true;let saved=false;
 try{if(isAssignment.value)await props.api.unassign(id.value,row.id,draft.version);else await props.api.deleteDetail(id.value,tab.value,row.id,draft.version);
 saved=true;ElMessage.success(t("personnel.saved"));accept(await props.api.read(id.value));}
 catch(e){if(saved)failed.value=true;await setErrorFeedback(error,saved?t("personnel.latestLoadFailed"):props.errorText(e));}finally{busy.value=false;}
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
  <FloatingError :title="error"><el-button :disabled="busy||loading" @click="reload">{{t('personnel.retry')}}</el-button></FloatingError>
  <TabbedDetailTemplate master-data>
   <template #header><PageHeader :title="id?(draft.name||t('personnel.title')):t('personnel.new')">
    <template #meta><el-tag v-if="draft.code" type="info" effect="plain">{{draft.code}}</el-tag><el-tag v-if="profile" :type="profile.person.enabled?'success':'info'">{{t('masterData.'+(profile.person.enabled?'enabled':'disabled'))}}</el-tag></template>
    <template #actions><el-button :disabled="busy" @click="back">{{t('personnel.back')}}</el-button><el-button v-operation="saveOperation" type="primary" :loading="busy" :disabled="!editable||!dirty" @click="save">{{t('masterData.save')}}</el-button></template>
   </PageHeader></template>
   <template #content>
    <el-result v-if="failed" icon="error" :title="t('masterData.unavailable')"><template #extra><el-button :disabled="busy" @click="reload">{{t('personnel.retry')}}</el-button></template></el-result>
    <el-form v-else ref="form" :model="draft" :rules="rules" label-position="top" :disabled="!editable||busy" class="detail-profile-form detail-border-label-form" @submit.prevent="save">
     <el-card v-for="group in profileGroups" :key="group.key" shadow="never" class="detail-section" :body-style="group.key==='details'&&!detailsExpanded?{padding:'0'}:undefined">
      <template #header><div class="detail-section-heading"><h2>{{group.title}}</h2><el-button v-if="group.key==='details'" text :aria-label="t(detailsExpanded?'personnel.collapseDetails':'personnel.expandDetails')" :aria-expanded="detailsExpanded" aria-controls="personnel-profile-details" @click="detailsExpanded=!detailsExpanded"><AppIcon :name="detailsExpanded?'arrowUp':'arrowDown'" :size="18" /></el-button></div></template>
      <div v-show="group.key==='basic'||detailsExpanded" :id="group.key==='details'?'personnel-profile-details':undefined" :class="{'detail-identity-layout':group.key==='basic'}">
       <div v-if="group.key==='basic'" class="detail-identity-mark" aria-hidden="true"><el-avatar :size="64">{{initials}}<AppIcon v-if="!initials" name="user" :size="30" /></el-avatar></div>
       <div v-if="canEdit" class="detail-field-grid">
        <template v-if="group.key==='basic'">
         <el-form-item :label="t('personnel.code')" prop="code"><span v-if="id" class="detail-code-value">{{draft.code}}</span><el-input v-else v-model="draft.code" maxlength="64" /></el-form-item>
         <el-form-item :label="t('personnel.name')" prop="name"><el-input v-model="draft.name" maxlength="200" /></el-form-item>
        </template>
        <el-form-item v-for="field in group.fields" :key="field" :label="label(field)" :prop="'attributes.'+field" :class="{'detail-field-wide':field==='remarks'}">
         <el-date-picker v-if="isDate(field)" v-model="draft.attributes[field]" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :disabled-date="date=>field==='birthDate'&&date.getTime()>Date.now()" />
         <el-select v-else-if="personnelChoices[field]" v-model="draft.attributes[field]" clearable><el-option v-for="option in options(field)" :key="option.value" :value="option.value" :label="option.label" /></el-select>
         <el-input v-else v-model="draft.attributes[field]" :maxlength="field==='email'?200:500" :type="field==='remarks'?'textarea':'text'" :rows="1" />
        </el-form-item>
       </div>
       <div v-else class="detail-field-grid detail-readonly-grid">
        <template v-if="group.key==='basic'"><div class="detail-readonly-field"><span>{{t('personnel.code')}}</span><strong>{{draft.code||'—'}}</strong></div><div class="detail-readonly-field"><span>{{t('personnel.name')}}</span><strong>{{draft.name||'—'}}</strong></div></template>
        <div v-for="field in group.fields" :key="field" class="detail-readonly-field" :class="{'detail-field-wide':field==='remarks'}"><span>{{label(field)}}</span><strong>{{draft.attributes[field]?(personnelChoices[field]?t('personnel.choices.'+draft.attributes[field]):draft.attributes[field]):'—'}}</strong></div>
       </div>
      </div>
     </el-card>
    </el-form>
   </template>
   <template #system>
    <h2 class="detail-section-title">{{t('personnel.system')}}</h2>
    <div class="detail-audit-grid">
     <div class="detail-audit-item"><AppIcon name="user" :size="20" /><div><span>{{t('personnel.createdName')}}</span><strong>{{profile?.audit.createdByName||t('personnel.unknownHistory')}}</strong></div></div>
     <div class="detail-audit-item"><AppIcon name="history" :size="20" /><div><span>{{t('personnel.createdAt')}}</span><strong>{{time(profile?.audit.createdAt)}}</strong></div></div>
     <div class="detail-audit-item"><AppIcon name="edit" :size="20" /><div><span>{{t('personnel.updatedName')}}</span><strong>{{profile?.audit.updatedByName||t('personnel.unknownHistory')}}</strong></div></div>
     <div class="detail-audit-item"><AppIcon name="history" :size="20" /><div><span>{{t('personnel.updatedAt')}}</span><strong>{{time(profile?.audit.updatedAt)}}</strong></div></div>
    </div>
   </template>
   <template #tabs>
    <el-empty v-if="!id" :description="t('personnel.saveFirst')" />
    <template v-else>
     <el-tabs v-model="tab"><el-tab-pane v-for="(_,key) in personnelSections" :key="key" :name="key" :label="t('personnel.sections.'+key)" /><el-tab-pane name="assignments" :label="t('personnel.assignments')" /></el-tabs>
     <p v-if="dirty" class="personnel-inline-hint">{{t('personnel.dirty')}}</p>
     <ManagementTable :title="t(isAssignment?'personnel.assignments':'personnel.sections.'+tab)" :rows="currentRows" :columns="detailColumns" :busy="busy" :loading="loading" heading-level="h2" embedded @refresh="reload">
      <template #titleHelp><InfoHint :content="t(isAssignment?'personnel.assignmentHint':'personnel.organizationHint')" /></template>
      <template #actions><el-button v-operation="isAssignment?'Person.assign':'Person.createDetail'" type="primary" :disabled="detailsLocked" @click="openDetail()">{{t(isAssignment?'personnel.addAssignment':'personnel.addDetail')}}</el-button></template>
      <template v-for="field in (section?.fields??[])" :key="field" #[field]="{row}">{{display(row as PersonnelDetail,field)}}</template>
      <template #active="{row}"><el-tag :type="('active' in row&&row.active)?'success':'warning'">{{t(('active' in row&&row.active)?'personnel.active':'personnel.inactive')}}</el-tag></template>
      <template #operations="{row}"><el-button v-operation="isAssignment?'Person.updateAssignment':'Person.updateDetail'" link type="primary" :disabled="detailsLocked" @click="openDetail(row)">{{t('masterData.edit')}}</el-button><el-button v-operation="isAssignment?'Person.unassign':'Person.deleteDetail'" link type="danger" :disabled="detailsLocked" @click="remove(row)">{{t(isAssignment?'personnel.unassign':'personnel.deleteDetail')}}</el-button></template>
     </ManagementTable>
    </template>
   </template>
  </TabbedDetailTemplate>
  <ManagedDialog v-model="dialog" :title="t(isAssignment?(detailId?'personnel.editAssignment':'personnel.addAssignment'):(detailId?'personnel.editDetail':'personnel.addDetail'))" :busy="busy">
   <el-form class="business-border-label-form" ref="detailForm" :model="values" :rules="detailRules" label-position="top" :disabled="busy" @submit.prevent="saveDetail">
    <el-form-item v-for="field in dialogFields" :key="field" :label="label(field)" :prop="field">
     <el-date-picker v-if="isDate(field)" v-model="values[field]" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
     <el-select v-else-if="['organizationId','positionId','memberId'].includes(field)||personnelChoices[field]" v-model="values[field]" filterable clearable @change="field==='organizationId'&&changeOrganization()"><el-option v-for="option in options(field)" :key="option.value" :value="option.value" :label="option.label" /></el-select>
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
