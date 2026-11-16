<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { BusinessWorkspace, ManagementTable, ManagedDialog, RecordAuditDetails, AppPagination, InfoHint, FloatingError, setErrorFeedback, type ManagementColumn } from "@appnexus/ui";
import { masterDataFields, masterDataOperations, type MasterDataApi, type MasterDataKind, type MasterDataRow, type MasterDataInput } from "./index";

/** 五类档案复用现有工作区、表格和弹窗；表单差异来自固定字段契约，不构建平行 UI 体系。 */
const props=defineProps<{kind:MasterDataKind;api:MasterDataApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();
const operation=computed(()=>masterDataOperations[props.kind]);
const fields=computed(()=>masterDataFields[props.kind]);
const title=computed(()=>t("masterData."+props.kind));
const rows=ref<MasterDataRow[]>([]),total=ref(0),page=ref(1),size=ref(20);
const keyword=ref(""),status=ref("all"),loading=ref(false),busy=ref(false),failed=ref(false),ready=ref(false);
const operations=ref<string[]>([]),error=ref(""),open=ref(false),editingId=ref("");
const form=ref<FormInstance>();
/** 审计只取原已授权记录，独立于编辑草稿，不进入写入参数。 */
const editingAudit=computed(()=>rows.value.find(row=>row.id===editingId.value)?.audit);
const draft=reactive<MasterDataInput>({code:"",name:"",attributes:{},version:0});
const saveOperation=computed(()=>editingId.value?operation.value.update:operation.value.create);
const canRead=computed(()=>operations.value.includes(operation.value.list));
const columns=computed<ManagementColumn[]>(()=>[
  {key:"code",prop:"code",label:t("masterData.code"),minWidth:140},
  {key:"name",prop:"name",label:t("masterData.name"),minWidth:180},
  ...fields.value.map(key=>({key,label:t("masterData.fields."+key),minWidth:150})),
  {key:"status",label:t("masterData.status"),width:110},
  {key:"operations",label:t("masterData.actions"),width:200,fixed:"right"}
]);
/** 前后端字段长度、必填和标准代码约束一致；只改善输入体验，不能替代服务端校验。 */
const rules=computed<FormRules>(()=>{
  const required={required:true,message:t("masterData.required"),trigger:"blur"};
  const result:FormRules={code:[required,{pattern:props.kind==="country-region"?/^[A-Z]{2}$/:/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/,message:t("masterData."+(props.kind==="country-region"?"countryRule":"codeRule")),trigger:"blur"}],name:[required]};
  if(props.kind==="partner")result["attributes.partnerType"]=[required];
  if(props.kind==="product")result["attributes.unit"]=[required];
  if(props.kind==="country-region"){
    result["attributes.alpha3Code"]=[required,{pattern:/^[A-Z]{3}$/,message:t("masterData.alpha3Rule"),trigger:"blur"}];
    result["attributes.numericCode"]=[required,{pattern:/^[0-9]{3}$/,message:t("masterData.numericRule"),trigger:"blur"}];
  }
  return result;
});
/** 查询失败保留上次行数据并显示原区域故障状态；重新获取实时准入，避免撤权后继续展示旧资料。 */
async function load(){
  if(loading.value||busy.value)return;
  loading.value=true;failed.value=false;
  try{
    operations.value=await props.api.operations();ready.value=true;
    if(!canRead.value){rows.value=[];total.value=0;return;}
    const result=await props.api.list(props.kind,{keyword:keyword.value,enabled:status.value==="all"?undefined:status.value==="enabled",page:page.value,size:size.value});
    rows.value=result.rows;total.value=result.total;
  }catch(e){
    failed.value=true;
    // 撤权拒绝时清除已有正文，避免仍展示之前获准读取的敏感档案；网络故障保留原列表以便核实结果。
    if((e as {response?:{status?:number}})?.response?.status===403){rows.value=[];total.value=0;operations.value=[];}
    await setErrorFeedback(error,props.errorText(e));
  }
  finally{loading.value=false;}
}
/** 筛选变化回到第一页；只读取，不写业务数据。 */
function search(){page.value=1;void load();}
/** 重置查询条件后显式重新读取。 */
function reset(){keyword.value="";status.value="all";search();}
/** 编辑使用行快照及原版本；复制属性避免草稿修改直接影响表格。 */
function edit(row?:MasterDataRow){
  editingId.value=row?.id??"";draft.code=row?.code??"";draft.name=row?.name??"";draft.version=row?.version??0;
  draft.attributes=Object.fromEntries(fields.value.map(key=>[key,row?.attributes[key]??(key==="partnerType"?"CUSTOMER":"")]));
  error.value="";open.value=true;
}
/** 保存成功才关闭；失败保留草稿，409 通过公共浮层提供重新读取入口，不自动覆盖输入。 */
async function save(){
  if(busy.value||!(await form.value?.validate().catch(()=>false)))return;
  busy.value=true;
  try{
    const input={...draft,attributes:{...draft.attributes}};
    if(editingId.value)await props.api.update(props.kind,editingId.value,input);else await props.api.create(props.kind,input);
    open.value=false;ElMessage.success(t("masterData.saved"));busy.value=false;await load();
  }catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
/** 启停先确认对象及后果；使用所选行版本，失败不乐观改变表格状态。 */
async function toggle(row:MasterDataRow){
  if(busy.value)return;
  try{await ElMessageBox.confirm(t("masterData."+(row.enabled?"confirmDisable":"confirmEnable"),{name:row.name}),t("masterData.confirmTitle"),{type:row.enabled?"warning":"info",confirmButtonText:t("masterData."+(row.enabled?"disable":"enable")),cancelButtonText:t("masterData.cancel")});}catch{return;}
  busy.value=true;
  try{await props.api.setStatus(props.kind,row.id,{enabled:!row.enabled,version:row.version});ElMessage.success(t("masterData.statusSaved"));busy.value=false;await load();}
  catch(e){await setErrorFeedback(error,props.errorText(e));}finally{busy.value=false;}
}
/** 发生冲突后重新读取列表，保留当前编辑草稿，用户可取消后重新打开最新档案。 */
function retry(){void load();}
onMounted(load);
</script>

<template>
  <BusinessWorkspace :title="title" :description="t('masterData.description')">
    <template #search>
      <el-form inline @submit.prevent="search">
        <el-form-item :label="t('masterData.keyword')"><el-input v-model="keyword" maxlength="200" clearable /></el-form-item>
        <el-form-item :label="t('masterData.status')"><el-select v-model="status" style="width: 140px"><el-option value="all" :label="t('masterData.all')" /><el-option value="enabled" :label="t('masterData.enabled')" /><el-option value="disabled" :label="t('masterData.disabled')" /></el-select></el-form-item>
        <el-form-item class="management-filter-actions">
          <el-button v-operation="operation.list" type="primary" native-type="submit" :loading="loading" :disabled="busy">{{t('masterData.search')}}</el-button>
          <el-button :disabled="loading||busy" @click="reset">{{t('masterData.reset')}}</el-button>
        </el-form-item>
      </el-form>
    </template>
    <template #data>
      <FloatingError :title="error"><el-button :disabled="busy||loading" @click="retry">{{t('masterData.retry')}}</el-button></FloatingError>
      <el-result v-if="failed&&!rows.length" icon="error" :title="t('masterData.unavailable')"><template #extra><el-button :loading="loading" @click="retry">{{t('masterData.retry')}}</el-button></template></el-result>
      <el-result v-else-if="ready&&!canRead" icon="warning" :title="t('masterData.noPermission')" />
      <ManagementTable v-else :title="title" :rows="rows" :columns="columns" :total="total" :loading="loading" :busy="busy" :empty-text="t('masterData.empty')" embedded @refresh="load">
        <template #titleHelp>
          <InfoHint :content="t('masterData.scopeHint')" />
          <InfoHint v-if="kind==='country-region'" :content="t('masterData.countryHint')" />
        </template>
        <template #actions><el-button v-operation="operation.create" type="primary" :disabled="loading||busy" @click="edit()">{{t('masterData.create')}}</el-button></template>
        <template v-for="field in fields" :key="field" #[field]="{row}">{{field==='partnerType'?t('masterData.partnerTypes.'+row.attributes[field]):row.attributes[field]||'—'}}</template>
        <template #status="{row}"><el-tag :type="row.enabled?'success':'info'">{{t('masterData.'+(row.enabled?'enabled':'disabled'))}}</el-tag></template>
        <template #operations="{row}">
          <el-button v-operation="operation.update" link type="primary" :disabled="busy||loading" @click="edit(row)">{{t('masterData.edit')}}</el-button>
          <el-button v-operation="operation.setStatus" link :type="row.enabled?'danger':'primary'" :disabled="busy||loading" @click="toggle(row)">{{t('masterData.'+(row.enabled?'disable':'enable'))}}</el-button>
        </template>
        <template #pagination><AppPagination v-model:current-page="page" v-model:page-size="size" :total="total" :disabled="loading||busy" @current-change="load" @size-change="search" /></template>
      </ManagementTable>
      <ManagedDialog v-model="open" :title="t('masterData.'+(editingId?'edit':'create'))+' · '+title" :busy="busy" @closed="form?.clearValidate()">
        <el-form class="business-border-label-form" ref="form" :model="draft" :rules="rules" label-position="top" @submit.prevent="save">
          <el-form-item :label="t('masterData.code')" prop="code"><template #label>{{t('masterData.code')}}<InfoHint :content="t('masterData.codeHint')" /></template><el-input v-model="draft.code" maxlength="64" :disabled="!!editingId||busy" /></el-form-item>
          <el-form-item :label="t('masterData.name')" prop="name"><el-input v-model="draft.name" maxlength="200" :disabled="busy" /></el-form-item>
          <el-form-item v-for="field in fields" :key="field" :label="t('masterData.fields.'+field)" :prop="'attributes.'+field">
            <el-select v-if="field==='partnerType'" v-model="draft.attributes[field]" :disabled="busy"><el-option v-for="value in ['CUSTOMER','SUPPLIER','BOTH']" :key="value" :value="value" :label="t('masterData.partnerTypes.'+value)" /></el-select>
            <el-input v-else v-model="draft.attributes[field]" :maxlength="field==='unit'?32:field==='email'?200:500" :disabled="busy" />
          </el-form-item>
        </el-form>
        <RecordAuditDetails v-if="editingId && editingAudit" :audit="editingAudit" />
        <template #footer><el-button :disabled="busy" @click="open=false">{{t('masterData.cancel')}}</el-button><el-button v-operation="saveOperation" type="primary" :loading="busy" @click="save">{{t('masterData.save')}}</el-button></template>
      </ManagedDialog>
    </template>
  </BusinessWorkspace>
</template>
