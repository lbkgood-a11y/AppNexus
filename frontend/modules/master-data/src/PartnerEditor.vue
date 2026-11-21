<script setup lang="ts">
import CodingInput from "@appnexus/business-object/coding-input";
import {AppIcon} from "@appnexus/icons";
import {computed,onMounted,onBeforeUnmount,reactive,ref} from "vue";
import {onBeforeRouteLeave} from "vue-router";
import {useI18n} from "vue-i18n";
import {ElMessage,ElMessageBox,type FormInstance,type FormRules} from "element-plus";
import {GeographyPicker,geographyPath,type GeographyNode,type GeographySelection,PageHeader,TabbedDetailTemplate,ManagementTable,ManagedDialog,InfoHint,FloatingError,LoadErrorResult,presentLoadError,setErrorFeedback,type LoadIssue,type ManagementColumn} from "@appnexus/ui";
import type {MasterDataApi,MasterDataRow,MasterDataInput,PartnerDetail,DictionaryOption} from "./index";
import {partnerBasic,partnerAdditional,partnerDictionaryFields,partnerSections} from "./partner";
/** 客商主数据详情；复用人员样板和公共明细表格，主档及明细以父版本原子保存。 */
const loadIssue = ref<LoadIssue>();
const props=defineProps<{id:string;mode?:"view"|"edit";api:MasterDataApi;errorText:(error:unknown)=>string}>();
const emit=defineEmits<{close:[]}>();
const {t,locale}=useI18n();
const id=ref(props.id),record=ref<MasterDataRow>(),allowed=ref<string[]>([]),loading=ref(false),busy=ref(false),failed=ref(false),error=ref("");
const geographyNodes=ref<GeographyNode[]>([]);
/** 服务端限制只用于页面可编辑提示，所有写入仍由后端复核；未知历史归属不自动选择当前组织。 */
const organizations=ref<{id:string;name:string}[]>([]),fieldRestrictions=ref<Record<string,Record<string,string>>>({});
const resourceFor=(section:string)=>section==='contacts'?'masterdata.partner.contact':'masterdata.partner.bank';
const restricted=(resource:string,field:string)=>Boolean(fieldRestrictions.value[resource]?.[field]);
const hidden=(resource:string,field:string)=>fieldRestrictions.value[resource]?.[field]==='HIDDEN';
const restrictedDetail=computed(()=>Object.keys(fieldRestrictions.value[resourceFor(tab.value)]??{}).length>0);
function writableValues(resource:string,values:Record<string,string>){return Object.fromEntries(Object.entries(values).filter(([key])=>!restricted(resource,key)));}

/** 更改国别时显式清除原注册地区，避免跨国错误关联。 */
function selectCountry(value:GeographySelection|null){draft.attributes.registeredPlace="";}
/** 先选注册地区时回填所属国家，保持两个字段一致。 */
function selectRegisteredPlace(value:GeographySelection|null){if(value)draft.attributes.countryRegion=value.node.countryCode;}
const dictionaryOptions=ref<Record<string,DictionaryOption[]>>({});
const form=ref<FormInstance>(),detailForm=ref<FormInstance>(),expanded=ref(true),baseline=ref("");
const draft=reactive<MasterDataInput>({code:"",name:"",attributes:Object.fromEntries([...partnerBasic,...partnerAdditional].map(key=>[key,""])),version:0,organizationId:"",contacts:[],bankAccounts:[]});
const details=reactive<{contacts:PartnerDetail[];bankAccounts:PartnerDetail[]}>({contacts:[],bankAccounts:[]});
const tab=ref<keyof typeof partnerSections>("contacts"),dialog=ref(false),detailIndex=ref(-1),values=reactive<Record<string,string>>({});
const operation=computed(()=>id.value?"BusinessPartner.update":"BusinessPartner.create");
const canEdit=computed(()=>props.mode!=="view"&&allowed.value.includes(operation.value)&&!failed.value);
const roles=computed({get:()=>draft.attributes.partnerType==="BOTH"?["CUSTOMER","SUPPLIER"]:(draft.attributes.partnerType||"").split(",").filter(Boolean),set:(value:string[])=>{draft.attributes.partnerType=value.join(",");}});
const groups=computed(()=>[{key:"basic",fields:partnerBasic},{key:"additional",fields:partnerAdditional}]);
const booleanFields=["listed","taxAccount","basicAccount","isDefault"];
/** 草稿比对包含明细且排除只读审计，取消和路由离开都保护未提交变更。 */
function snapshot(){return JSON.stringify({code:draft.code,name:draft.name,attributes:draft.attributes,organizationId:draft.organizationId,contacts:details.contacts.map(({id,values})=>({id,values})),bankAccounts:details.bankAccounts.map(({id,values})=>({id,values}))});}
const dirty=computed(()=>snapshot()!==baseline.value);
const columns=computed<ManagementColumn[]>(()=>[...partnerSections[tab.value].filter(key=>!hidden(resourceFor(tab.value),key)).map(key=>({key,label:label(key),minWidth:key==="address"||key==="accountNumber"?200:140})),{key:"operations",label:t("masterData.actions"),width:160,fixed:"right"}]);
/** 标签按固定字段契约翻译，不将截图示例值或未接入选项作为默认业务资料。 */
function label(key:string){return t("partner.fields."+key);}
/** 启用项可新选；历史停用或未知值只显示原值，不能给其他记录新选。 */
function options(field:string,current:string[]=[]){const rows=dictionaryOptions.value[field]??[];return [...rows.filter(row=>row.enabled||current.includes(row.value)),...current.filter(value=>value&&!rows.some(row=>row.value===value)).map(value=>({value,name:value,enabled:false}))];}
function optionName(field:string,value:string){return dictionaryOptions.value[field]?.find(row=>row.value===value)?.name??value;}
const rules=computed<FormRules>(()=>({code:[{required:true,message:t("masterData.required"),trigger:"blur"},{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t("masterData.codeRule"),trigger:"blur"}],name:[{required:true,whitespace:true,message:t("masterData.required"),trigger:"blur"}],"attributes.partnerType":[{required:true,message:t("masterData.required"),trigger:"change"}],"attributes.registeredCapital":restricted("masterdata.partner","registeredCapital")?[]:[{pattern:/^(?:[0-9]{1,18}(\.[0-9]{1,6})?)?$/,message:t("partner.capitalRule"),trigger:"blur"}],"attributes.registeredCurrency":restricted("masterdata.partner","registeredCurrency")?[]:[{pattern:/^(?:[A-Z]{3})?$/,message:t("partner.currencyRule"),trigger:"blur"}]}));
const detailRules=computed<FormRules>(()=>{
 const required=tab.value==="contacts"?["contactType","contactName","phone","address","isDefault"]:["taxAccount","basicAccount","isDefault","bankName","accountNumber","currency"];
 const result:FormRules=Object.fromEntries(required.filter(key=>!restricted(resourceFor(tab.value),key)).map(key=>[key,[{required:true,whitespace:true,message:t("masterData.required"),trigger:"change"}]]));
 if(tab.value==="contacts"&&!restricted(resourceFor(tab.value),"email"))result.email=[{type:"email",message:t("masterData.emailRule"),trigger:"blur"}];
 else if(tab.value!=="contacts"){result.currency=[{required:true,message:t("masterData.required"),trigger:"change"},{pattern:/^[A-Z]{3}$/,message:t("partner.currencyRule"),trigger:"blur"}];result.cnaps=[{pattern:/^(?:[0-9]{12})?$/,message:t("partner.cnapsRule"),trigger:"blur"}];result.swiftCode=[{pattern:/^(?:[A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)?$/,message:t("partner.swiftRule"),trigger:"blur"}];}
 return result;
});
/** 缺省与 0 区别展示；布尔值带文本，日期保持业务日期，不转 UTC 时间。 */
function display(value:string|undefined,key:string){if(!value)return "—";if(booleanFields.includes(key))return t(value==="true"?"partner.yes":"partner.no");if(key==="partnerType")return (value==="BOTH"?["CUSTOMER","SUPPLIER"]:value.split(",")).map(v=>optionName("partnerType",v)).join(" / ");return ["countryRegion","registeredPlace"].includes(key)?(geographyPath(geographyNodes.value,value).map(n=>n.name).join("-")||value):optionName(key,value);}
/** 审计仅格式化服务端 UTC 时间；未知历史不填当前时间，也不额外读取账号目录。 */
function time(value:string|null|undefined){return value?new Intl.DateTimeFormat(locale.value,{dateStyle:"medium",timeStyle:"short"}).format(new Date(value)):t("recordAudit.unknown");}
/** 成功读取后才替换草稿；刷新需用户确认，读取失败保留输入并停止写入。 */
function accept(row:MasterDataRow){record.value=row;draft.organizationId=row.organizationId??"";fieldRestrictions.value=row.fieldRestrictions??fieldRestrictions.value;draft.code=row.code;draft.name=row.name;draft.version=row.version;draft.attributes=Object.fromEntries([...partnerBasic,...partnerAdditional].map(key=>[key,row.attributes[key]??""]));details.contacts=structuredClone(row.contacts??[]);details.bankAccounts=structuredClone(row.bankAccounts??[]);baseline.value=snapshot();}
async function load(){loading.value=true;failed.value=false;loadIssue.value=undefined;try{allowed.value=await props.api.operations();dictionaryOptions.value=await props.api.partnerOptions();const dataAccess=await props.api.partnerAccess();organizations.value=dataAccess.organizations;fieldRestrictions.value=dataAccess.fieldRestrictions;geographyNodes.value=await props.api.geographyDirectory("partner");if(id.value)accept(await props.api.readPartner(id.value));else baseline.value=snapshot();}catch(e){failed.value=true;if((e as {response?:{status:number}})?.response?.status===403){record.value=undefined;allowed.value=[];draft.name="";draft.code="";draft.attributes={};details.contacts=[];details.bankAccounts=[];}await presentLoadError(error,loadIssue,props.errorText(e),true,(e as {response?:{status?:number}})?.response?.status===403);}finally{loading.value=false;}}
/** 冲突后不自动覆盖；用户主动重新读取时确认丢弃本地草稿。 */
async function reload(){if(busy.value||loading.value)return;if((dirty.value||dialog.value)&&!await confirmDiscard())return;dialog.value=false;await load();}
async function confirmDiscard(){try{await ElMessageBox.confirm(t("partner.discard"),t("masterData.partner"),{confirmButtonText:t("partner.discardAction"),cancelButtonText:t("masterData.cancel")});return true;}catch{return false;}}
async function back(){if(busy.value)return;if((dirty.value||dialog.value)&&!await confirmDiscard())return;emit("close");}
/** 主档与明细一次提交；剥离审计，保留字符串金额。成功后读取失败不重复创建，必须主动刷新。 */
async function save(){
 if(busy.value||!canEdit.value||!await form.value?.validate().catch(()=>false))return;
 if(!id.value&&!draft.organizationId){await setErrorFeedback(error,t("partner.organizationRequired"));return;}
 if(!restricted("masterdata.partner","establishedOn")&&draft.attributes.establishedOn&&draft.attributes.establishedOn>new Date().toISOString().slice(0,10)){await setErrorFeedback(error,t("partner.futureDate"));return;}
 busy.value=true;let saved=false;
 try{const input={...draft,attributes:writableValues("masterdata.partner",draft.attributes),organizationId:draft.organizationId||null,contacts:details.contacts.map(({id,values})=>({id,values:writableValues(resourceFor("contacts"),values)})),bankAccounts:details.bankAccounts.map(({id,values})=>({id,values:writableValues(resourceFor("bankAccounts"),values)}))};if(id.value)await props.api.update("partner",id.value,input);else id.value=(await props.api.create("partner",input)).id;saved=true;baseline.value=snapshot();ElMessage.success(t("masterData.saved"));accept(await props.api.readPartner(id.value));}
 catch(e){if (saved) {
   failed.value = true;
   const message = props.errorText(e);
   await presentLoadError(error, loadIssue, message ? t("partner.refreshFailed") : "", true,
     (e as { response?: { status?: number } })?.response?.status === 403);
 } else {
   await setErrorFeedback(error, props.errorText(e));
 }}finally{busy.value=false;}
}
/** 明细弹窗只编辑本地快照；使用页面统一保存才能提交聚合，不显示伪持久化成功。 */
function editDetail(index=-1){if(!canEdit.value||busy.value)return;detailIndex.value=index;for(const key of Object.keys(values))delete values[key];for(const key of partnerSections[tab.value])values[key]=details[tab.value][index]?.values[key]??(booleanFields.includes(key)?"false":"");dialog.value=true;}
async function applyDetail(){
 if(!await detailForm.value?.validate().catch(()=>false))return;
 for(const key of tab.value==="contacts"?["isDefault"]:["isDefault","basicAccount"]){if(values[key]==="true"&&details[tab.value].some((row,index)=>index!==detailIndex.value&&row.values[key]==="true")){await setErrorFeedback(error,t("partner.uniqueDefault"));return;}}
 const row={...details[tab.value][detailIndex.value],values:{...values}};if(detailIndex.value<0)details[tab.value].push(row);else details[tab.value][detailIndex.value]=row;dialog.value=false;
}
/** 删除先明确告知会在页面保存时生效；不即时发出 API 删除请求。 */
async function removeDetail(index:number){try{await ElMessageBox.confirm(t("partner.removeConfirm"),t("masterData.partner"),{type:"warning",confirmButtonText:t("partner.remove"),cancelButtonText:t("masterData.cancel")});details[tab.value].splice(index,1);}catch{/* 取消不改草稿。 */}}
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value||dialog.value)event.preventDefault();}
onBeforeRouteLeave(async()=>busy.value?false:(!(dirty.value||dialog.value)||await confirmDiscard()));
onMounted(()=>{baseline.value=snapshot();window.addEventListener("beforeunload",beforeUnload);void load();});
onBeforeUnmount(()=>window.removeEventListener("beforeunload",beforeUnload));
</script>
<template>
 <div class="partner-editor" v-loading="loading">
  <FloatingError :title="error"><el-button :disabled="loading||busy" @click="reload">{{t('masterData.retry')}}</el-button></FloatingError>
  <TabbedDetailTemplate master-data>
   <template #header><PageHeader :title="id?(draft.name||t('masterData.partner')):t('partner.new')"><template #meta><el-tag v-if="props.mode==='view'" type="info">{{t('partner.view')}}</el-tag><el-tag v-if="draft.code" effect="plain">{{draft.code}}</el-tag><el-tag v-if="record" :type="record.enabled?'success':'info'">{{t('masterData.'+(record.enabled?'enabled':'disabled'))}}</el-tag></template><template #actions><el-button :disabled="busy" @click="back">{{t('partner.back')}}</el-button><el-button v-if="props.mode!=='view'" v-operation="operation" type="primary" :loading="busy" :disabled="!canEdit||!dirty||loading||dialog" @click="save">{{t('masterData.save')}}</el-button></template></PageHeader></template>
   <template #content>
    <LoadErrorResult v-if="loadIssue" :issue="loadIssue"><template #retry><el-button :disabled="busy||loading" @click="reload">{{t('masterData.retry')}}</el-button></template></LoadErrorResult>
    <el-form v-else-if="!failed" ref="form" :model="draft" :rules="rules" label-position="top" :disabled="busy||loading" class="detail-profile-form detail-border-label-form" @submit.prevent="save">
     <el-card v-for="group in groups" :key="group.key" shadow="never" class="detail-section" :body-style="group.key==='additional'&&!expanded?{padding:'0'}:undefined">
      <template #header><div class="detail-section-heading"><h2>{{t('partner.'+group.key)}}</h2><el-button v-if="group.key==='additional'" text :aria-label="t(expanded?'partner.collapse':'partner.expand')" :title="t(expanded?'partner.collapse':'partner.expand')" :aria-expanded="expanded" aria-controls="partner-additional" @click="expanded=!expanded"><AppIcon :name="expanded?'arrowUp':'arrowDown'" :size="18" /></el-button></div></template>
      <div v-show="group.key==='basic'||expanded" :id="group.key==='additional'?'partner-additional':undefined" class="detail-field-grid">
       <template v-if="group.key==='basic'">
        <el-form-item v-if="canEdit&&!id" :label="t('masterData.code')" prop="code"><CodingInput v-if="api.codingOptions && api.issueCode" v-model="draft.code" operation="BusinessPartner.create" :max-length="64" :disabled="busy" :options="()=>api.codingOptions!('BusinessPartner','code')" :issue="(key,request)=>api.issueCode!('BusinessPartner',key,request)" :error-text="errorText" /><el-input v-else v-model="draft.code" maxlength="64" /></el-form-item>
        <div v-else class="detail-readonly-field"><span>{{t('masterData.code')}}</span><strong>{{draft.code||'—'}}</strong></div>
        <el-form-item v-if="canEdit" :label="t('partner.commonName')" prop="name"><el-input v-model="draft.name" maxlength="200" /></el-form-item><div v-else class="detail-readonly-field"><span>{{t('partner.commonName')}}</span><strong>{{draft.name||'—'}}</strong></div>
       </template>
       <el-form-item v-if="group.key==='basic'&&canEdit" :label="t('partner.organization')" :required="!id"><el-select v-model="draft.organizationId" filterable><el-option v-for="item in organizations" :key="item.id" :value="item.id" :label="item.name" /></el-select></el-form-item><div v-else-if="group.key==='basic'" class="detail-readonly-field"><span>{{t('partner.organization')}}</span><strong>{{organizations.find(item=>item.id===draft.organizationId)?.name||t('partner.unknownOrganization')}}</strong></div>
       <template v-for="field in group.fields.filter(key=>!hidden('masterdata.partner',key))" :key="field">
        <el-form-item v-if="canEdit&&!restricted('masterdata.partner',field)" :label="label(field)" :prop="'attributes.'+field" :class="{'detail-field-wide':field==='remarks'||field==='partnerType'}">
         <GeographyPicker v-if="field==='countryRegion'||field==='registeredPlace'" v-model="draft.attributes[field]" :nodes="geographyNodes" :kind="field==='countryRegion'?'country':'region'" :country-code="field==='registeredPlace'?draft.attributes.countryRegion:undefined" @select="field==='countryRegion'?selectCountry($event):selectRegisteredPlace($event)" />
         <el-select v-else-if="field==='partnerType'" v-model="roles" multiple><el-option v-for="option in options(field,roles)" :key="option.value" :value="option.value" :label="option.name" :disabled="!option.enabled" /></el-select>
         <el-select v-else-if="partnerDictionaryFields.includes(field)" v-model="draft.attributes[field]" clearable filterable><el-option v-for="option in options(field,[draft.attributes[field]])" :key="option.value" :value="option.value" :label="option.name" :disabled="!option.enabled" /></el-select>
         <el-select v-else-if="field==='listed'" v-model="draft.attributes[field]" clearable><el-option value="true" :label="t('partner.yes')" /><el-option value="false" :label="t('partner.no')" /></el-select>
         <el-date-picker v-else-if="field==='establishedOn'" v-model="draft.attributes[field]" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :disabled-date="date=>date.getTime()>Date.now()" />
         <el-input v-else v-model="draft.attributes[field]" :maxlength="500" :type="field==='remarks'?'textarea':'text'" :rows="1" />
        </el-form-item>
        <div v-else class="detail-readonly-field" :class="{'detail-field-wide':field==='remarks'||field==='partnerType'}"><span>{{label(field)}}</span><strong>{{display(draft.attributes[field],field)}}</strong></div>
       </template>
      </div>
     </el-card>
    </el-form>
   </template>
   <template #system><h2 class="detail-section-title">{{t('partner.system')}}</h2><div class="detail-audit-grid"><div v-for="key in ['createdBy','createdAt','updatedBy','updatedAt'] as const" :key="key" class="detail-audit-item"><div><span>{{t('recordAudit.'+key)}}</span><strong>{{key.endsWith('At')?time(record?.audit?.[key]):record?.audit?.[key]||t('recordAudit.unknownActor')}}</strong></div></div></div></template>
   <template #tabs>
    <el-tabs v-model="tab"><el-tab-pane v-for="(_,section) in partnerSections" :key="section" :name="section" :label="t('partner.'+section)" /></el-tabs>
    <ManagementTable :key="tab" :title="t('partner.'+tab)" :rows="details[tab]" :columns="columns" :loading="loading" :busy="busy" heading-level="h2" embedded @refresh="reload">
     <template #titleHelp><InfoHint :content="t('partner.atomicHint')" /></template>
     <template #actions><el-button v-operation="operation" type="primary" :disabled="!canEdit||busy||loading||restrictedDetail||details[tab].length>=100" @click="editDetail()">{{t('partner.add')}}</el-button></template>
     <template v-for="field in partnerSections[tab]" :key="field" #[field]="{row}">{{display(row.values[field],field)}}</template>
     <template #operations="{row}"><el-button v-operation="operation" link type="primary" :disabled="!canEdit||busy||loading" @click="editDetail(details[tab].indexOf(row))">{{t('masterData.edit')}}</el-button><el-button v-operation="operation" link type="danger" :disabled="!canEdit||busy||loading||restrictedDetail" @click="removeDetail(details[tab].indexOf(row))">{{t('partner.remove')}}</el-button></template>
    </ManagementTable>
   </template>
  </TabbedDetailTemplate>
  <ManagedDialog v-model="dialog" :title="t('partner.'+tab)" :busy="busy" @closed="detailForm?.clearValidate()">
   <el-form ref="detailForm" :model="values" :rules="detailRules" label-position="top" class="business-border-label-form" @submit.prevent="applyDetail">
    <el-form-item v-for="field in partnerSections[tab].filter(key=>!hidden(resourceFor(tab),key))" :key="field" :label="label(field)" :prop="field"><span v-if="restricted(resourceFor(tab),field)">{{display(values[field],field)}}</span><el-select v-else-if="booleanFields.includes(field)" v-model="values[field]"><el-option value="true" :label="t('partner.yes')" /><el-option value="false" :label="t('partner.no')" /></el-select><el-select v-else-if="partnerDictionaryFields.includes(field)" v-model="values[field]" filterable clearable><el-option v-for="option in options(field,[values[field]])" :key="option.value" :value="option.value" :label="option.name" :disabled="!option.enabled" /></el-select><el-input v-else v-model="values[field]" :maxlength="field==='email'?200:500" :type="field==='remarks'?'textarea':'text'" /></el-form-item>
   </el-form>
   <template #footer><el-button @click="dialog=false">{{t('masterData.cancel')}}</el-button><el-button v-operation="operation" type="primary" @click="applyDetail">{{t('partner.apply')}}</el-button></template>
  </ManagedDialog>
 </div>
</template>
<style scoped>
/* 表单密度、主题与容器宽度断点沿用公共主数据样板，静态说明复用标题提示入口。 */
.partner-editor{display:flex;flex-direction:column;flex:1;min-width:0;overflow:auto;}
.partner-editor > :deep(.master-data-detail){flex:1;}
/* Vxe 当前版本表头有独立文字变量，显式跟随 Element Plus，保证深色对比度。 */
.partner-editor :deep(.vxe-table){--vxe-ui-table-header-font-color:var(--el-text-color-primary);}
</style>
