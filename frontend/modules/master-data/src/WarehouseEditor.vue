<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,reactive,ref} from "vue";
import {onBeforeRouteLeave} from "vue-router";
import {useI18n} from "vue-i18n";
import {ElMessage,ElMessageBox,type FormInstance,type FormRules} from "element-plus";
import {AppIcon} from "@appnexus/icons";
import {GeographyPicker,geographyPath,type GeographyNode,PageHeader,TabbedDetailTemplate,RecordAuditDetails,FloatingError,LoadErrorResult,presentLoadError,setErrorFeedback,type LoadIssue} from "@appnexus/ui";
import type {MasterDataApi,MasterDataInput,MasterDataRow,DictionaryOption} from "./index";
import WarehouseMapPicker from "./WarehouseMapPicker.vue";
import {warehouseGroups,warehouseDictionaryFields,warehouseNumericFields} from "./warehouse";
/** 仓库详情复用主数据样板；主档与产权资料同事务保存，启停始终使用独立操作权限。 */
const loadIssue = ref<LoadIssue>();
const props=defineProps<{id:string;mode?:"view"|"edit";api:MasterDataApi;errorText:(error:unknown)=>string}>();
const emit=defineEmits<{close:[]}>();
const {t}=useI18n();
const id=ref(props.id),record=ref<MasterDataRow>(),allowed=ref<string[]>([]),loading=ref(false),busy=ref(false),failed=ref(false),error=ref("");
const geographyNodes=ref<GeographyNode[]>([]);
const dictionary=ref<Record<string,DictionaryOption[]>>({}),form=ref<FormInstance>();
const fieldRestrictions=ref<Record<string,string>>({});
const expanded=reactive<Record<string,boolean>>({basic:true,property:true});
const allFields=Object.values(warehouseGroups).flat();
const draft=reactive<MasterDataInput>({code:"",name:"",attributes:Object.fromEntries(allFields.map(key=>[key,""])),version:0});
const baseline=ref("");
const mapOpen=ref(false);
/** 地图确认仅成对回填可编辑草稿；实际持久化仍由原保存操作及服务端权限校验完成。 */
function applyMapPoint(longitude:string,latitude:string){
 if(!canEdit.value||busy.value||loading.value)return;
 draft.attributes.longitude=longitude;draft.attributes.latitude=latitude;mapOpen.value=false;
 form.value?.clearValidate(["attributes.longitude","attributes.latitude"]);
}
const operation=computed(()=>id.value?"Warehouse.update":"Warehouse.create");
const canRead=computed(()=>allowed.value.includes("Warehouse.list"));
const canEdit=computed(()=>props.mode!=="view"&&canRead.value&&allowed.value.includes(operation.value)&&!failed.value);
const booleanFields=["personalGuarantee","wholeLease"];
/** 区域路径和地址说明跨两列；手机沿用公共网格恢复单列，不改变字段提交契约。 */
const wideFields=["region","address","billAddress","introduction","structureDescription","remarks","leaseChainDescription","subleaseDescription","otherStorageDescription"];
/** 比对仅含可写字段，审计和启停不混入保存契约。 */
function snapshot(){return JSON.stringify({code:draft.code,name:draft.name,attributes:draft.attributes});}
const dirty=computed(()=>snapshot()!==baseline.value);
/** 新增时仍展示四个系统字段；真实审计仅取已授权响应，不填造账号或当前时间。 */
const audit=computed(()=>record.value?.audit??{createdBy:null,createdAt:null,updatedBy:null,updatedAt:null});
/** 仓库字段限制来自服务端投影，受限字段不进入保存请求。 */
function fieldMode(field:string){return fieldRestrictions.value[field];}
function label(field:string){return t("warehouseProfile.fields."+field);}
/** 停用或未知历史选项可以原样保留；不得作为其他记录的新选项。 */
function options(field:string){const current=draft.attributes[field];const rows=dictionary.value[field]??[];return [...rows.filter(row=>row.enabled||row.value===current),...(current&&!rows.some(row=>row.value===current)?[{value:current,name:current,enabled:false}]:[])];}
function display(field:string){const value=draft.attributes[field];if(!value)return "—";if(booleanFields.includes(field)&&fieldMode(field)!=="MASKED")return t(value==="true"?"partner.yes":"partner.no");return field==="region"?(geographyPath(geographyNodes.value,value).map(n=>n.name).join("-")||value):dictionary.value[field]?.find(row=>row.value===value)?.name??value;}
/** 前端数量与坐标规则与后端一致，不将空值或0互换，也不转换十进制精度。 */
function validNumber(field:string,value:string){if(!value)return !["longitude","latitude"].includes(field)||!draft.attributes[field==="longitude"?"latitude":"longitude"];if(["longitude","latitude"].includes(field))return /^-?[0-9]{1,3}(\.[0-9]{1,6})?$/.test(value)&&Math.abs(Number(value))<=(field==="longitude"?180:90)&&!!draft.attributes[field==="longitude"?"latitude":"longitude"];return /^[0-9]{1,18}(\.[0-9]{1,6})?$/.test(value);}
const rules=computed<FormRules>(()=>{
 const required={required:true,whitespace:true,message:t("masterData.required"),trigger:"blur"};
 const result:FormRules={code:[required,{pattern:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t("masterData.codeRule"),trigger:"blur"}],name:[required],"attributes.email":[{type:"email",message:t("masterData.emailRule"),trigger:"blur"}]};
 for(const field of warehouseNumericFields)result["attributes."+field]=[{validator:(_rule,value,done)=>done(validNumber(field,String(value??""))?undefined:new Error(t("warehouseProfile.numberRule"))),trigger:"blur"}];
 return result;
});
/** 读取失败保留输入并禁写；403清除原正文，防止撤权后仍展示旧档案。 */
async function load(){loading.value=true;failed.value=false;loadIssue.value=undefined;try{
 allowed.value=await props.api.operations();if(!canRead.value){record.value=undefined;draft.code="";draft.name="";draft.attributes={};return;}
 fieldRestrictions.value=await props.api.warehouseFieldRestrictions();
 dictionary.value=await props.api.warehouseOptions();geographyNodes.value=await props.api.geographyDirectory("warehouse");
 if(id.value){const row=await props.api.readWarehouse(id.value);record.value=row;fieldRestrictions.value=row.fieldRestrictions?.["masterdata.warehouse"]??fieldRestrictions.value;draft.code=row.code;draft.name=row.name;draft.version=row.version;draft.attributes=Object.fromEntries(allFields.map(key=>[key,row.attributes[key]??""]));}
 baseline.value=snapshot();
 }catch(e){failed.value=true;if((e as {response?:{status:number}})?.response?.status===403){allowed.value=[];record.value=undefined;draft.code="";draft.name="";draft.attributes={};}await presentLoadError(error,loadIssue,props.errorText(e),true,(e as {response?:{status?:number}})?.response?.status===403);}finally{loading.value=false;}}
/** 取消、刷新及离开路由均保护未保存内容，冲突不会自动覆盖草稿。 */
async function discard(){if(!dirty.value)return true;try{await ElMessageBox.confirm(t("partner.discard"),t("masterData.warehouse"),{confirmButtonText:t("masterData.cancel"),cancelButtonText:t("masterData.edit")});return true;}catch{return false;}}
async function back(){if(!busy.value&&await discard())emit("close");}
async function reload(){if(!busy.value&&!loading.value&&await discard())await load();}
/** 保存采用服务端读取版本，不自动重放写请求；失败保留用户输入。 */
async function save(){if(!canEdit.value||busy.value||loading.value||!await form.value?.validate().catch(()=>false))return;busy.value=true;try{const input={...draft,attributes:Object.fromEntries(Object.entries(draft.attributes).filter(([field,value])=>!fieldMode(field)&&(Boolean(id.value)||value!=="")))};if(id.value)await props.api.update("warehouse",id.value,input);else id.value=(await props.api.create("warehouse",input)).id;baseline.value=snapshot();ElMessage.success(t("masterData.saved"));await load();}catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}}
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value||busy.value)event.preventDefault();}
onBeforeRouteLeave(async()=>busy.value?false:await discard());
onMounted(()=>{baseline.value=snapshot();window.addEventListener("beforeunload",beforeUnload);void load();});
onBeforeUnmount(()=>window.removeEventListener("beforeunload",beforeUnload));
</script>
<template>
 <div class="warehouse-editor" v-loading="loading">
  <WarehouseMapPicker v-if="mapOpen&&canEdit&&!busy&&!loading" :configuration="api.warehouseMapConfig" :longitude="draft.attributes.longitude??''" :latitude="draft.attributes.latitude??''" @select="applyMapPoint" @close="mapOpen=false" />
  <FloatingError :title="error"><el-button :disabled="busy||loading" @click="reload">{{t('masterData.retry')}}</el-button></FloatingError>
  <TabbedDetailTemplate master-data>
   <template #header><PageHeader :title="id?(draft.name||t('masterData.warehouse')):t('warehouseProfile.new')"><template #meta><el-tag v-if="props.mode==='view'" type="info">{{t('partner.view')}}</el-tag><el-tag v-if="record" :type="record.enabled?'success':'info'">{{t('masterData.'+(record.enabled?'enabled':'disabled'))}}</el-tag></template><template #actions><el-button :disabled="busy||loading" @click="back">{{t('partner.back')}}</el-button><el-button v-if="props.mode!=='view'" v-operation="operation" type="primary" :loading="busy" :disabled="!canEdit||!dirty||loading" @click="save">{{t('masterData.save')}}</el-button></template></PageHeader></template>
   <template #content>
    <LoadErrorResult v-if="loadIssue" :issue="loadIssue"><template #retry><el-button :disabled="busy||loading" @click="reload">{{t('masterData.retry')}}</el-button></template></LoadErrorResult>
    <el-result v-else-if="!loading&&!canRead" icon="warning" :title="t('masterData.noPermission')" />
    <el-form v-else-if="!loading&&!failed" ref="form" :model="draft" :rules="rules" label-position="top" :disabled="busy" class="detail-profile-form detail-border-label-form" @submit.prevent="save">
     <el-card v-for="(fields,group) in warehouseGroups" :key="group" shadow="never" class="detail-section" :body-style="!expanded[group]?{padding:'0'}:undefined">
      <template #header><div class="detail-section-heading"><h2>{{t('warehouseProfile.'+group)}}</h2><el-button text :aria-label="t(expanded[group]?'partner.collapse':'partner.expand')" :title="t(expanded[group]?'partner.collapse':'partner.expand')" :aria-expanded="expanded[group]" :aria-controls="'warehouse-'+group" @click="expanded[group]=!expanded[group]"><AppIcon :name="expanded[group]?'arrowUp':'arrowDown'" :size="18" /></el-button></div></template>
      <div v-show="expanded[group]" :id="'warehouse-'+group" class="detail-field-grid">
       <template v-if="group==='basic'">
        <el-form-item v-if="canEdit&&!id" :label="t('masterData.code')" prop="code"><el-input v-model="draft.code" maxlength="64" /></el-form-item><div v-else class="detail-readonly-field"><span>{{t('masterData.code')}}</span><el-tooltip :content="draft.code"><strong class="warehouse-code" tabindex="0">{{draft.code||'—'}}</strong></el-tooltip></div>
        <el-form-item v-if="canEdit" :label="t('masterData.name')" prop="name"><el-input v-model="draft.name" maxlength="200" /></el-form-item><div v-else class="detail-readonly-field"><span>{{t('masterData.name')}}</span><strong>{{draft.name||'—'}}</strong></div>
        <div class="detail-readonly-field"><span>{{t('masterData.status')}}</span><strong>{{record?t('masterData.'+(record.enabled?'enabled':'disabled')):t('masterData.enabled')}}</strong></div>
       </template>
       <template v-for="field in fields" :key="field">
        <el-form-item v-if="canEdit&&!fieldMode(field)" :label="label(field)" :prop="'attributes.'+field" :class="{'detail-field-wide':wideFields.includes(field)}">
         <GeographyPicker v-if="field==='region'" v-model="draft.attributes[field]" :nodes="geographyNodes" />
         <el-select v-else-if="warehouseDictionaryFields.includes(field)" v-model="draft.attributes[field]" clearable filterable><el-option v-for="option in options(field)" :key="option.value" :value="option.value" :label="option.name" :disabled="!option.enabled" /></el-select>
         <el-select v-else-if="booleanFields.includes(field)" v-model="draft.attributes[field]" clearable><el-option value="true" :label="t('partner.yes')" /><el-option value="false" :label="t('partner.no')" /></el-select>
         <el-date-picker v-else-if="field==='builtOn'||field==='openedOn'" v-model="draft.attributes[field]" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
         <el-input v-else-if="field==='longitude'" v-model="draft.attributes[field]" maxlength="12"><template #append><el-button :disabled="busy||loading" @click="mapOpen=true">{{t('warehouseMap.pick')}}</el-button></template></el-input>
         <el-input v-else v-model="draft.attributes[field]" :maxlength="field==='email'?200:500" :type="wideFields.includes(field)?'textarea':'text'" :autosize="{minRows:1,maxRows:4}" />
        </el-form-item>
        <div v-else-if="fieldMode(field)!=='HIDDEN'" class="detail-readonly-field" :class="{'detail-field-wide':wideFields.includes(field)}"><span>{{label(field)}}</span><strong>{{display(field)}}</strong></div>
       </template>
      </div>
     </el-card>
    </el-form>
   </template>
   <template #system><h2 class="detail-section-title">{{t('partner.system')}}</h2><RecordAuditDetails v-if="canRead&&!failed" :audit="audit" inline /></template>
  </TabbedDetailTemplate>
 </div>
</template>
<style scoped>
/* 单一外层滚动及六列响应式网格沿用主数据模板，不压缩表单或另建主题。 */
.warehouse-editor{display:flex;flex-direction:column;flex:1;min-width:0;overflow:auto;}
.warehouse-editor > :deep(.master-data-detail){flex:1;}
.warehouse-code{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0;}
</style>
