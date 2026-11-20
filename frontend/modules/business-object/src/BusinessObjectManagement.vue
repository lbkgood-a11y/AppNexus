<script setup lang="ts">
import CodingConfiguration from "./CodingConfiguration.vue";
import { VxeTable, VxeColumn } from "@appnexus/ui";
import { setErrorFeedback } from "@appnexus/ui";
import { AppPagination } from "@appnexus/ui";
import { AppIcon } from "@appnexus/icons";
import { FloatingError } from "@appnexus/ui";
import { InfoHint } from "@appnexus/ui";
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox, type FormInstance } from "element-plus";
import { BusinessWorkspace, ManagementTable, ManagedDialog, type ManagementColumn } from "@appnexus/ui";
import type { Binding, Descriptor, BusinessObjectApi,PageRelationshipEditor,PageRelationship } from "./index";
import {pageOperations,selectPageOperation,hasPageOperation} from './pageOperation';

/** 平台目录与租户绑定共用列表；模式由构建入口固定，所有接口仍在服务端鉴权。 */
const props = defineProps<{ api: BusinessObjectApi; platform?: boolean; errorText: (e: unknown) => string }>();
const { t, locale } = useI18n(); const router = useRouter();
const rows = ref<Awaited<ReturnType<BusinessObjectApi['catalog']>>>([]);
const loading=ref(false), busy=ref(false), error=ref(""), query=ref(""), applied=ref(""), page=ref(1), size=ref(20);
const selected=ref<Descriptor>(), dialog=ref(false), revision=ref(-1), published=ref(0), bindings=ref<Binding[]>([]);
const workflows=ref<Record<string,Awaited<ReturnType<BusinessObjectApi['workflows']>>>>({}), tenants=ref<Awaited<ReturnType<BusinessObjectApi['tenants']>>>([]), grants=ref<Awaited<ReturnType<BusinessObjectApi['grants']>>>([]);
const tenant=ref("");
const checks=ref<Awaited<ReturnType<BusinessObjectApi['readiness']>>>([]);
/** 按服务端操作分组，仅聚合三项基础检查；缺失项显示未检查，不将缺失或未知状态当成功。 */
const readinessRows=computed(()=>[...new Set(checks.value.map(check=>check.operationCode))].map(operationCode=>{
 const operation=selected.value?.operations.find(item=>item.operationCode===operationCode);
 const items=checks.value.filter(item=>item.operationCode===operationCode);
 const state=(code:string)=>{const matches=items.filter(item=>item.code===code);return matches.length?matches.every(item=>item.ready):undefined;};
 return {operationCode,name:selected.value?.objectType==='BusinessPartner'&&['list','create','update','setStatus'].includes(operationCode)?t('businessObject.partnerOperations.'+operationCode):operation?(locale.value.startsWith('zh')?operation.nameZh:operation.nameEn):operationCode,
  enabled:state('TYPE_ENABLED'),connected:state('PAGE_RELATION'),published:state('PUBLISHED_BINDING')};
}));
/** 流程、打印及实时依赖保留在补充检查中，避免汇总时丢失失败信息。 */
const dependencyChecks=computed(()=>checks.value.filter(item=>!['TYPE_ENABLED','PAGE_RELATION','PUBLISHED_BINDING'].includes(item.code)));
const readinessText=(value:boolean|undefined)=>t(value===undefined?'businessObject.unchecked':value?'businessObject.checkYes':'businessObject.checkNo');
async function inspectReadiness(){if(!selected.value)return;await run(async()=>{checks.value=await props.api.readiness(selected.value!.objectType);});}
/** 页签只管理展示，切换使用既有实例保留草稿；保存/发布仅作用于业务绑定。 */
const detailTab=ref('declaration');
const savedFeedback=ref('');
const pageEditor=ref<PageRelationshipEditor>({revision:-1,relationships:[],candidates:[]});
/** 已接入关系只读，搜索限定当前业务对象；管控使用独立接口并要求原因。 */
const entryQuery=ref(''),entryPage=ref(1);
const entryRows=computed(()=>pageEditor.value.candidates.filter(c=>[c.operationId,c.compatibleOperationCode,c.pageOperation.nameZh,c.pageOperation.nameEn].some(v=>v.toLowerCase().includes(entryQuery.value.toLowerCase()))));
const entryEnabled=(key:string)=>pageEditor.value.relationships.some(r=>r.operationId===key);
async function controlEntry(operation:string){
  if(!selected.value)return;
  const enabled=!entryEnabled(operation);
  let reason:string;
  try{reason=(await ElMessageBox.prompt(t('businessObject.controlReason'),t(enabled?'businessObject.enableEntry':'businessObject.disableEntry'),{inputValidator:value=>!!value?.trim()&&value.length<=200, inputErrorMessage:t('businessObject.controlReason'),type:'warning'})).value;}catch{return;}
  await run(async()=>{pageEditor.value=await props.api.controlPage(selected.value!.objectType,pageEditor.value.revision,operation,enabled,reason);ElMessage.success(t('businessObject.saved'));});
}
/** 能力概览直接读取同一对象声明及关联，不维护另一套可编辑目录。 */
const capabilityRows=computed(()=>{
 if(!selected.value)return [];
 const entries=selected.value.operations.flatMap(operation=>operation.interaction.capabilities.map(capability=>({
  key:operation.operationId+":"+capability.service,operation:locale.value.startsWith("zh")?operation.nameZh:operation.nameEn,
  service:capability.service,contract:capability.reference,
  bindings:bindings.value.filter(binding=>binding.operationCode===operation.operationCode&&binding.service===capability.service).length
 })));
 for(const field of selected.value.codingFields??[])entries.push({key:"CODING:"+field.field,operation:field.field,service:"CODING",contract:field.operationCode,bindings:0});
 return entries;
});
const printScenes=ref<Record<string,Awaited<ReturnType<BusinessObjectApi['scenes']>>>>({});
const operationAction=(d:Descriptor,key:string)=>pageOperations(d).find(a=>a.operationId===key);
/** 按绑定 ID 管理在场表单，避免弹窗重开/重载后数组残留旧表单造成静默校验失败。 */
const bindingForms=new Map<string,FormInstance>();
function bindForm(id:string,form:unknown){if(form)bindingForms.set(id,form as FormInstance);else bindingForms.delete(id);}
const bindingRules=computed(()=>({name:[{required:true,whitespace:true,max:100,message:t('businessObject.required'),trigger:'blur'}],operationId:[{required:true,message:t('businessObject.selectPageOperation'),trigger:'change'}],definitionId:[{required:true,message:t('businessObject.required'),trigger:'change'}]}));
const name=(d:Descriptor)=>locale.value.startsWith('zh')?d.nameZh:d.nameEn;
const filtered=computed(()=>rows.value.filter(r=>[r.descriptor.objectType,r.descriptor.module,name(r.descriptor)].some(x=>x.toLowerCase().includes(applied.value.toLowerCase()))));
const paged=computed(()=>filtered.value.slice((page.value-1)*size.value,page.value*size.value));
const columns=computed<ManagementColumn[]>(()=>[
  {key:'objectType',label:t('businessObject.code'),minWidth:180}, {key:'name',label:t('businessObject.name'),minWidth:160},
  {key:'category',label:t('businessObject.category'),minWidth:140},
  {key:'module',label:t('businessObject.module'),minWidth:100},{key:'version',label:t('businessObject.contractVersion'),width:140},
  {key:'state',label:t('businessObject.state'),width:110},
  {key:'operations',label:t('businessObject.operations'),minWidth:240,fixed:'right'}
]);
async function run(action:()=>Promise<void>) {busy.value=true;error.value='';try{await action();}catch(e){await setErrorFeedback(error, props.errorText(e));}finally{busy.value=false;}}
async function load(){loading.value=true;error.value='';try{rows.value=await props.api.catalog();}catch(e){await setErrorFeedback(error, props.errorText(e));}finally{loading.value=false;}}
async function open(d:Descriptor){detailTab.value=props.platform?'declaration':d.codingFields?.length?'coding':'configuration';checks.value=[];savedFeedback.value="";selected.value=d;entryQuery.value='';entryPage.value=1;await run(async()=>{
  if(props.platform){[tenants.value,grants.value,pageEditor.value]=await Promise.all([props.api.tenants(),props.api.grants(d.objectType),props.api.pageRelationships(d.objectType)]);tenant.value='';}
  else {const [config,options]=await Promise.all([props.api.configuration(d.objectType),Promise.all(d.operations.filter(a=>a.interaction.capabilities.map(c=>c.service).includes('WORKFLOW')).map(async a=>[a.operationCode,await props.api.workflows(d.objectType,a.operationCode)] as const))]);revision.value=config.revision;published.value=config.publishedVersion;bindings.value=config.bindings;workflows.value=Object.fromEntries(options);}
  if(!props.platform)printScenes.value=Object.fromEntries(await Promise.all(d.operations.filter(a=>a.interaction.capabilities.map(c=>c.service).includes('PRINT')).map(async a=>[a.operationCode,await props.api.scenes(d.objectType,a.operationCode)] as const)));
  dialog.value=true;
  // 默认展示当前对象真实检查结果；检查失败不阻止配置弹窗打开，也不伪装为已就绪。
  if(!props.platform){try{checks.value=await props.api.readiness(d.objectType);}catch(e){await setErrorFeedback(error,props.errorText(e));}}
});}
async function activate(row:(typeof rows.value)[number]){
  try{await ElMessageBox.confirm(t(row.enabled?'businessObject.disableConfirm':'businessObject.enableConfirm'),t('businessObject.state'),{type:'warning'});}catch{return;}
  await run(async()=>{await props.api.activate(row.descriptor.objectType,row.revision,!row.enabled);await load();});
}
async function grant(id:string,enabled:boolean){if(!selected.value)return;
  if(!enabled){try{await ElMessageBox.confirm(t('businessObject.revokeConfirm'),t('businessObject.grants'),{type:'warning'});}catch{return;}}
  await run(async()=>{await props.api.grant(selected.value!.objectType,id,enabled);grants.value=await props.api.grants(selected.value!.objectType);ElMessage.success(t('businessObject.saved'));});}
function add(){bindings.value.push({id:crypto.randomUUID(),name:'',definitionId:'',definitionVersion:0,conditions:[],operationCode:'',service:'WORKFLOW',operationId:''});}
/** 策略切换清空旧流程引用，直接提交不得携带隐藏目标。 */
function changeService(b:Binding){b.definitionId=b.service==='WORKFLOW'?'':null;b.definitionVersion=0;b.sceneCode=null;}
async function save(publish=false){if(!selected.value)return;
  const valid=await Promise.all(bindings.value.map(b=>new Promise<boolean>(resolve=>{
    const form=bindingForms.get(b.id);if(!form){resolve(false);return;}
    void form.validate(ok=>{resolve(ok);}).catch(()=>{resolve(false);});
  })));if(valid.some(v=>!v)){error.value=t('businessObject.required');return;}
  if(bindings.value.some(b=>!hasPageOperation(b,selected.value!))){error.value=t('businessObject.selectPageOperation');return;}
  if(!bindings.value.length||bindings.value.some(b=>!b.name.trim()||(b.service==='WORKFLOW'&&!b.definitionId)||(b.service==='PRINT'&&!b.sceneCode)||b.conditions.some(c=>!c.field||!c.value.trim()))){error.value=t('businessObject.required');return;}
  await run(async()=>{const type=selected.value!.objectType;await props.api.save(type,revision.value,bindings.value);
    let config=await props.api.configuration(type);revision.value=config.revision;
    if(publish){await props.api.publish(type,revision.value);config=await props.api.configuration(type);revision.value=config.revision;published.value=config.publishedVersion;}
    checks.value=[];savedFeedback.value=t(publish?'businessObject.published':'businessObject.saved');ElMessage.success(savedFeedback.value);
  });}
/** 下拉展开时刷新兼容发布版本，保留绑定草稿，避免流程发布后仍显示旧的空候选。 */
async function refreshWorkflowOptions(operationCode:string){
 if(!selected.value||!operationCode)return;
 try{workflows.value={...workflows.value,[operationCode]:await props.api.workflows(selected.value.objectType,operationCode)};}
 catch(e){await setErrorFeedback(error,props.errorText(e));}
}
function workflowSelect(b:Binding,value:string){const w=(workflows.value[b.operationCode]||[]).find(w=>`${w.id}:${w.version}`===value);b.definitionId=w?.id||'';b.definitionVersion=w?.version||0;}
function operators(field:string){return selected.value?.fields.find(f=>f.key===field)?.type==='NUMBER'?['EQ','NE','GT','GE','LT','LE']:['EQ','NE'];}
/** 创建入口带回跳信息；绑定草稿保存后再离开，流程仍需人工校验和发布。 */
async function createWorkflow(operationCode:string){
  const capability=selected.value?.operations.find(a=>a.operationCode===operationCode)?.interaction.capabilities.find(c=>c.service==='WORKFLOW');if(!capability)return;if(bindings.value.length){try{await ElMessageBox.confirm(t('businessObject.leaveConfiguration'),t('businessObject.createWorkflow'),{type:'warning'});}catch{return;}}dialog.value=false;await router.push({path:'/workflow/designer',query:{objectType:selected.value?.objectType,entryCapability:capability.reference,returnTo:'/business-objects/workbench',create:'1'}});}
onMounted(async()=>{await load();const type=router.currentRoute.value.query.type;if(!props.platform&&typeof type==='string'){const item=rows.value.find(row=>row.descriptor.objectType===type);if(item)await open(item.descriptor);}});
</script>
<template>
  <div class="management-page">
  <BusinessWorkspace :title="t(platform?'businessObject.platformTitle':'businessObject.title')">
    <template #feedback><FloatingError v-if="error && !dialog" :title="error" scope="error" type="error" :closable="false"><el-button v-operation="platform ? 'BusinessObject.catalog' : 'BusinessObject.tenantCatalog'" text @click="load">{{t('businessObject.refresh')}}</el-button></FloatingError></template>
    <template #search><div class="management-filter"><el-form inline @submit.prevent="applied=query;page=1"><el-form-item :label="t('businessObject.search')"><el-input v-model="query" clearable /></el-form-item><div class="management-filter-actions"><el-button type="primary" native-type="submit"><AppIcon name="search" :size="16" class="mr-1" />{{t('administration.search')}}</el-button><el-button @click="query='';applied='';page=1"><AppIcon name="refresh" :size="16" class="mr-1" />{{t('businessObject.reset')}}</el-button></div></el-form></div></template>
    <template #data><ManagementTable :title="t(platform?'businessObject.platformTitle':'businessObject.title')" :rows="paged" :columns="columns" :loading="loading" :busy="busy" embedded @refresh="load">
      <template v-if="platform" #titleHelp><InfoHint :content="t('businessObject.catalogBoundary')" /></template>
      <template #objectType="{row}">{{row.descriptor.objectType}}</template><template #name="{row}">{{name(row.descriptor)}}</template><template #module="{row}">{{row.descriptor.module}}</template><template #version="{row}">{{row.descriptor.version}}</template>
      <template #state="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'businessObject.enabled':'businessObject.disabled')}}</el-tag></template>
      <template #category="{row}"><el-tag type="info">{{t('businessObject.categories.'+row.descriptor.category)}}</el-tag></template>
      <template #operations="{row}"><el-button link type="primary" :disabled="busy" v-operation="platform ? 'BusinessObject.relationships' : 'BusinessObject.configuration'" @click="open(row.descriptor)">{{t(platform?'businessObject.viewDetails':'businessObject.configure')}}</el-button><el-button v-operation="'BusinessObject.state'" v-if="platform" link :type="row.enabled?'danger':'primary'" :disabled="busy" @click="activate(row)">{{t(row.enabled?'businessObject.disable':'businessObject.enable')}}</el-button></template>
      <template #empty><el-empty :description="t('businessObject.empty')" /></template>
      <template #pagination><AppPagination v-model:current-page="page" v-model:page-size="size" :total="filtered.length" layout="total, sizes, prev, pager, next" @size-change="page=1" /></template>
    </ManagementTable></template>
  </BusinessWorkspace>
  <ManagedDialog v-model="dialog" :title="selected?name(selected):''" width="min(1200px, calc(100vw - 32px))" :close-on-click-modal="false" :busy="busy"><template #title><span class="info-hint-title">{{selected?name(selected):''}}<el-tag v-if="selected" size="small" type="info">{{t('businessObject.contractVersion')}} {{selected.version}}</el-tag><InfoHint v-if="!platform" :content="t('businessObject.bindingHint')" /></span></template>
    <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" />
    <template v-if="selected">
      <el-alert v-if="savedFeedback" :title="savedFeedback" type="success" :closable="false" />
      <!-- 契约详情按信息职责分区；复用 Element Plus 卡片，不改变授权、管控及契约字段。 -->
      <el-tabs v-model="detailTab" class="object-detail-tabs">
      <el-tab-pane :label="t('businessObject.declarationTab')" name="declaration">
      <div class="object-contract-sections">
      <el-descriptions :column="2" border><el-descriptions-item :label="t('businessObject.code')">{{selected.objectType}}</el-descriptions-item><el-descriptions-item :label="t('businessObject.contractVersion')">{{selected.version}}</el-descriptions-item></el-descriptions>
      <section class="object-contract-section">
       <header><h3>{{t('businessObject.codingDeclaration')}}<InfoHint :content="t('businessObject.codingDeclarationHint')" /></h3></header>
       <VxeTable v-if="selected.codingFields?.length" size="small" :min-height="0" :data="selected.codingFields || []" :column-config="{resizable:true}" border="inner">
        <VxeColumn field="field" :title="t('coding.field')" min-width="180" />
        <VxeColumn :title="t('businessObject.action')" min-width="220"><template #default="{row}">{{selected.objectType}}.{{row.operationCode}}</template></VxeColumn>
        <VxeColumn field="maxLength" :title="t('businessObject.codingMaxLength')" min-width="160" align="right" />
       </VxeTable>
       <p v-else class="object-empty-note">{{t('businessObject.noCodingDeclaration')}}</p>
      </section>
      <section class="object-contract-section">
        <header><h3>{{t('businessObject.field')}}<el-tag size="small" type="info">{{selected.fields.length}}</el-tag></h3></header>
      <vxe-table v-if="selected.fields.length" size="small" :min-height="0" :column-config="{ resizable: true }"  :data="selected.fields" border="inner"><vxe-column field="key" :title="t('businessObject.field')"/><vxe-column :field="locale.startsWith('zh')?'nameZh':'nameEn'" :title="t('businessObject.name')"/><vxe-column field="type" :title="t('businessObject.fieldType')"/><vxe-column field="unit" :title="t('businessObject.unit')"><template #default="{row}">{{row.unit || '—'}}</template></vxe-column></vxe-table><p v-else class="object-empty-note">{{t('businessObject.noConditionFields')}}</p>
      </section>
      <section class="object-contract-section">
        <header><h3>{{t('businessObject.interactions')}}<el-tag size="small" type="info">{{selected.operations.length}}</el-tag></h3></header>
      <vxe-table size="small" :min-height="0" :column-config="{ resizable: true }"  :data="selected.operations" border="inner">
        <vxe-column type="expand"><template #content="{row}">
          <vxe-table size="small" :min-height="0" :column-config="{ resizable: true }"  :data="row.interaction.capabilities" border="inner">
            <vxe-column min-width="140" :title="t('businessObject.service')"><template #default="{row:capability}">{{t('businessObject.services.'+capability.service)}}</template></vxe-column>
            <vxe-column min-width="120" :title="t('businessObject.completion')"><template #default="{row:capability}">{{t('businessObject.completions.'+capability.completion)}}</template></vxe-column>
            <vxe-column field="dataContract" min-width="180" :title="t('workflow.tabs.contracts')"/><vxe-column field="dataVersion" width="110" :title="t('businessObject.contractVersion')"/><vxe-column field="reference" min-width="180" :title="t('businessObject.reference')"/>
            <vxe-column field="purpose" min-width="130" :title="t('businessObject.purpose')"/>
            <vxe-column min-width="240" :title="t('businessObject.inputs')"><template #default="{row:capability}">{{capability.fields.map((field: {key:string})=>field.key).join(', ')||'—'}}</template></vxe-column>
            <vxe-column min-width="240" :title="t('businessObject.outcomes')"><template #default="{row:capability}">{{capability.outcomes.join(', ')||'—'}}</template></vxe-column>
          </vxe-table>
        </template></vxe-column>
        <vxe-column field="operationCode" :title="t('businessObject.action')"/>
        <vxe-column field="interaction.code" min-width="200" :title="t('businessObject.interoperationCode')"/>
        <vxe-column field="interaction.version" width="110" :title="t('businessObject.contractVersion')"/>
        <vxe-column :title="t('businessObject.trigger')"><template #default="{row}">{{t('businessObject.triggers.'+row.interaction.trigger)}}</template></vxe-column>
        <vxe-column field="permissionCode" min-width="180" :title="t('businessObject.permission')"/>
      </vxe-table>
      </section>
      </div>
      </el-tab-pane>
      <el-tab-pane :label="t('businessObject.capabilityOverview')" name="capabilities">
       <header><h3>{{t('businessObject.capabilityOverview')}}<InfoHint :content="t('businessObject.capabilityOverviewHint')" /></h3></header>
       <vxe-table :data="capabilityRows" border="inner" :column-config="{resizable:true}">
        <vxe-column field="operation" :title="t('businessObject.action')" min-width="150" />
        <vxe-column field="service" :title="t('businessObject.service')" min-width="120" />
        <vxe-column field="contract" :title="t('businessObject.contractReference')" min-width="200" />
        <vxe-column :title="t('businessObject.operations')" min-width="170"><template #default="{row}">
         <el-button v-if="!platform" link type="primary" @click="detailTab=row.service==='CODING'?'coding':'configuration'">{{t('businessObject.configureCapability')}}</el-button>
        </template></vxe-column>
       </vxe-table>
      </el-tab-pane>
      <el-tab-pane v-if="!platform && selected.codingFields?.length" :label="t('coding.bindingsTitle')" name="coding" lazy>
       <CodingConfiguration :key="selected.objectType" :descriptor="selected" :api="api" :error-text="errorText" />
      </el-tab-pane>
      <el-tab-pane v-if="platform" :label="t('businessObject.governanceTab')" name="governance">
       <div class="object-contract-sections">
        <section class="object-contract-section">
          <header><h3>{{t('businessObject.pageRelations')}}<InfoHint :content="t('businessObject.pageRelationHint')" /><el-tag size="small" type="info">{{entryRows.length}}</el-tag></h3></header>

        <el-input v-model="entryQuery" clearable :aria-label="t('businessObject.search')" :placeholder="t('businessObject.search')" @input="entryPage=1" />
        <vxe-table size="small" :min-height="0" :column-config="{ resizable: true }"  :data="entryRows.slice((entryPage-1)*10,entryPage*10)" border="inner">
          <vxe-column field="compatibleOperationCode" :title="t('businessObject.action')" min-width="110" />
          <vxe-column :title="t('businessObject.pageOperation')" min-width="230"><template #default="{row}">{{locale.startsWith('zh')?row.pageOperation.nameZh:row.pageOperation.nameEn}}<div>{{row.operationId}}</div></template></vxe-column>
          <vxe-column :title="t('businessObject.businessEndpoint')" min-width="260"><template #default="{row}">{{row.pageOperation.method}} {{row.pageOperation.apiPath}}</template></vxe-column>
          <vxe-column field="permissionCode" :title="t('businessObject.permission')" min-width="170" />
          <vxe-column :title="t('businessObject.state')" width="100"><template #default="{row}"><el-tag :type="entryEnabled(row.operationId)?'success':'warning'">{{t(entryEnabled(row.operationId)?'businessObject.enabled':'businessObject.disabled')}}</el-tag></template></vxe-column>
          <vxe-column :title="t('businessObject.operations')" width="120"><template #default="{row}"><el-button v-operation="'BusinessObject.control'" link :type="entryEnabled(row.operationId)?'danger':'primary'" :disabled="busy" @click="controlEntry(row.operationId)">{{t(entryEnabled(row.operationId)?'businessObject.disableEntry':'businessObject.enableEntry')}}</el-button></template></vxe-column>
        </vxe-table>
        <AppPagination v-model:current-page="entryPage" :page-size="10" :total="entryRows.length" layout="prev, pager, next" />
        </section>
        <section class="object-contract-section">
          <header><h3>{{t('businessObject.tenantAuthorization')}}<el-tag size="small" type="info">{{grants.length}}</el-tag></h3></header>
        <el-form label-position="top" class="business-border-label-form object-grant-form"><el-form-item :label="t('businessObject.tenant')"><el-select v-model="tenant" filterable><el-option v-for="v in tenants" :key="v.id" :label="v.name" :value="v.id"/></el-select></el-form-item><el-button v-operation="'BusinessObject.grant'" type="primary" :disabled="!tenant" :loading="busy" @click="grant(tenant,true)">{{t('businessObject.authorize')}}</el-button></el-form>
        <vxe-table size="small" :min-height="0" :column-config="{ resizable: true }"  :data="grants" border="inner"><vxe-column :title="t('businessObject.tenant')"><template #default="{row}">{{tenants.find(v=>v.id===row.tenant_id)?.name||row.tenant_id}}</template></vxe-column><vxe-column :title="t('businessObject.state')"><template #default="{row}">{{t(row.enabled?'businessObject.enabled':'businessObject.disabled')}}</template></vxe-column><vxe-column :title="t('businessObject.operations')"><template #default="{row}"><el-button v-operation="'BusinessObject.grant'" link :disabled="busy" @click="grant(row.tenant_id,!row.enabled)">{{t(row.enabled?'businessObject.revoke':'businessObject.authorize')}}</el-button></template></vxe-column></vxe-table>
      </section>
       </div>
      </el-tab-pane>
      <el-tab-pane v-else :label="t('businessObject.configurationTab')" name="configuration">
       <div class="object-contract-sections">
        <div class="object-config-toolbar">
        <el-button v-operation="'BusinessObject.readiness'" :loading="busy" @click="inspectReadiness">{{t('businessObject.checkReadiness')}}</el-button><el-tag type="info">{{t('businessObject.publishedVersion')}}: {{published}}</el-tag>
        </div>
        <VxeTable v-if="checks.length" size="small" :min-height="0" :column-config="{resizable:true}" :data="readinessRows" border="inner">
         <VxeColumn field="name" :title="t('businessObject.businessOperation')" min-width="200" />
         <VxeColumn v-for="column in [{key:'enabled',label:'platformEnabled'},{key:'connected',label:'pageConnected'},{key:'published',label:'executionPublished'}]" :key="column.key" :title="t('businessObject.'+column.label)" min-width="160">
          <template #default="{row}"><el-tag :type="row[column.key]===undefined?'info':row[column.key]?'success':'warning'">{{readinessText(row[column.key])}}</el-tag></template>
         </VxeColumn>
        </VxeTable>
        <el-collapse v-if="dependencyChecks.length">
         <el-collapse-item :title="t('businessObject.dependencyChecks')" name="dependencies">
          <VxeTable size="small" :min-height="0" :data="dependencyChecks" border="inner">
           <VxeColumn field="operationCode" :title="t('businessObject.action')" />
           <VxeColumn :title="t('businessObject.checkItem')"><template #default="{row}">{{t('businessObject.checks.'+row.code)}}</template></VxeColumn>
           <VxeColumn :title="t('businessObject.state')"><template #default="{row}">{{readinessText(row.ready)}} {{row.category==='LIVE'?t('businessObject.liveHint'):''}}</template></VxeColumn>
           <VxeColumn field="target" :title="t('businessObject.checkTarget')" />
          </VxeTable>
         </el-collapse-item>
        </el-collapse>
        <el-alert v-if="rows.find(r=>r.descriptor.objectType===selected?.objectType)?.enabled===false" :title="t('businessObject.inactiveHint')" type="warning" :closable="false" />

        <div class="object-config-toolbar"><el-button :disabled="busy" @click="add">{{t('businessObject.addBinding')}}</el-button><el-button :disabled="busy" @click="open(selected)">{{t('businessObject.reload')}}</el-button></div>
        <p v-if="!bindings.length" class="object-empty-note">{{t('businessObject.noBusinessBindings')}}</p>
        <p v-if="bindings.some(b=>b.operationCode&&b.service==='WORKFLOW'&&!(workflows[b.operationCode]?.length))" class="object-empty-note">{{t('businessObject.noWorkflow')}}</p>
        <el-form v-for="(b,index) in bindings" :key="b.id" :ref="form=>bindForm(b.id,form)" :model="b" :rules="bindingRules" label-position="top" class="business-border-label-form business-object-binding" :disabled="busy">
          <el-form-item :label="t('businessObject.bindingName')" prop="name"><el-input v-model="b.name" maxlength="100"/></el-form-item>
          <el-form-item :label="t('businessObject.action')" prop="operationId">
            <el-select :model-value="b.operationId" :placeholder="t('businessObject.selectPageOperation')" filterable @update:model-value="selectPageOperation(b,selected,$event)">
              <el-option v-for="a in pageOperations(selected)" :key="a.operationId" :value="a.operationId" :label="`${locale.startsWith('zh')?a.nameZh:a.nameEn} (${a.operationCode})`" />
            </el-select>
            <el-alert v-if="!pageOperations(selected).length" :title="t('businessObject.noPageOperation')" type="warning" :closable="false" />
          </el-form-item>

          <el-form-item :label="t('businessObject.service')"><template #label>{{t('businessObject.service')}}<InfoHint v-if="b.service!=='WORKFLOW'&&b.service!=='PRINT'" :content="t('businessObject.directHint')" /></template><el-select v-model="b.service" @change="changeService(b)"><el-option v-for="mode in selected.operations.find(a=>a.operationCode===b.operationCode)?.interaction.capabilities.map(c=>c.service)||[]" :key="mode" :value="mode" :label="t('businessObject.services.'+mode)"/></el-select></el-form-item>
          <el-form-item v-if="b.service==='WORKFLOW'" :label="t('businessObject.workflow')" prop="definitionId"><el-select :model-value="b.definitionId?`${b.definitionId}:${b.definitionVersion}`:undefined" @visible-change="visible=>{if(visible)refreshWorkflowOptions(b.operationCode)}" @update:model-value="workflowSelect(b,$event)"><el-option v-for="w in workflows[b.operationCode]||[]" :key="`${w.id}:${w.version}`" :value="`${w.id}:${w.version}`" :label="`${w.name} · ${w.id} · v${w.version}`"/></el-select></el-form-item>
          <el-form-item v-else-if="b.service==='PRINT'" :label="t('businessObject.printScene')" required><el-select v-model="b.sceneCode"><el-option v-for="scene in printScenes[b.operationCode]||[]" :key="scene.code" :value="scene.code" :label="scene.name+' · '+scene.code"/></el-select></el-form-item>

          <!-- 只读操作信息横向平铺，长接口在本列内换行，不撑开弹窗。 -->
          <dl v-if="hasPageOperation(b,selected)" class="binding-operation-info">
           <div><dt>{{t('businessObject.action')}}</dt><dd>{{selected.objectType}}.{{b.operationCode}}</dd></div>
           <div><dt>{{t('businessObject.permission')}}</dt><dd>{{operationAction(selected,b.operationId)?.permissionCode}}</dd></div>
           <div><dt>{{t('businessObject.businessEndpoint')}}</dt><dd>{{operationAction(selected,b.operationId)?.pageOperation?.method}} {{operationAction(selected,b.operationId)?.pageOperation?.apiPath}}</dd></div>
          </dl>

          <div v-for="(c,ci) in b.conditions" :key="ci" class="business-object-condition"><el-select v-model="c.field" :aria-label="t('businessObject.field')" @change="c.operator='EQ'"><el-option v-for="f in selected.fields" :key="f.key" :value="f.key" :label="locale.startsWith('zh')?f.nameZh:f.nameEn"/></el-select><el-select v-model="c.operator" :aria-label="t('businessObject.operator')"><el-option v-for="op in operators(c.field)" :key="op" :value="op" :label="t('businessObject.operators.'+op)"/></el-select><el-input v-model="c.value" :aria-label="t('businessObject.value')" maxlength="200"/><el-button @click="b.conditions.splice(ci,1)">{{t('businessObject.remove')}}</el-button></div>
          <div class="binding-actions">
          <el-button v-if="b.service==='WORKFLOW'&&b.operationCode" :disabled="busy" v-operation="'WorkflowDefinition.save'" @click="createWorkflow(b.operationCode)">{{t('businessObject.createWorkflow')}}</el-button>
          <el-button @click="b.conditions.push({field:'',operator:'EQ',value:''})">{{t('businessObject.addCondition')}}</el-button><el-button type="danger" plain @click="bindings.splice(index,1)">{{t('businessObject.removeBinding')}}</el-button>
          </div>
        </el-form>
       </div>
      </el-tab-pane>
      </el-tabs>
    </template>
    <template #footer><el-button @click="dialog=false">{{t('businessObject.close')}}</el-button><template v-if="!platform && detailTab==='configuration'"><el-button v-operation="'BusinessObject.save'" :loading="busy" @click="save()">{{t('businessObject.save')}}</el-button><el-button v-operation="'BusinessObject.publish'" type="primary" :loading="busy" @click="save(true)">{{t('businessObject.publish')}}</el-button></template></template>
  </ManagedDialog>
  </div>
</template>
<style scoped>
/* 展示分区继承公共Token，不覆盖公共卡片、描述列表及表格内部密度。 */
.object-contract-sections{container-type:inline-size;display:flex;flex-direction:column;gap:var(--app-section-gap);min-width:0}
.object-contract-section{flex:none;min-width:0;display:grid;gap:var(--app-section-gap);padding:var(--app-content-padding);border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base)}
.object-empty-note{margin:0;color:var(--el-text-color-secondary);font-size:var(--el-font-size-small)}
.object-contract-section h3{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin:0;font-size:var(--el-font-size-medium);font-weight:600}
.object-config-toolbar,.object-grant-form{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.object-config-toolbar .el-button,.object-grant-form .el-button{margin:0}
.object-grant-form .el-form-item{flex:1;min-width:200px}
.business-object-binding{padding:var(--app-content-padding);border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base);display:grid;grid-template-columns:repeat(4,minmax(0,1fr));column-gap:16px;align-items:start}
.binding-operation-info,.business-object-binding>.business-object-condition,.binding-actions{grid-column:1/-1}
.binding-operation-info{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:0;padding:var(--app-content-padding);background:var(--el-fill-color-light);border-radius:var(--el-border-radius-base)}
.binding-operation-info dt{color:var(--el-text-color-secondary);font-size:var(--el-font-size-small)}
.binding-operation-info dd{margin:4px 0 0;color:var(--el-text-color-primary);overflow-wrap:anywhere}
@container (max-width:700px){.binding-operation-info{grid-template-columns:minmax(0,1fr);gap:8px}}
/* 操作独立成栏，避免按钮作为表单列被拉伸；只读接口信息在字段之后展示。 */
.binding-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding-top:var(--app-content-padding);margin-top:var(--app-section-gap);border-top:1px solid var(--el-border-color-lighter)}
.binding-actions .el-button{margin:0}
@container (max-width:900px){.business-object-binding{grid-template-columns:repeat(2,minmax(0,1fr))}}
@container (max-width:480px){.business-object-binding{grid-template-columns:minmax(0,1fr)}}
.business-object-condition{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.business-object-condition>*{flex:1;min-width:100px}
@media(max-width:700px){.business-object-binding{grid-template-columns:minmax(0,1fr)}.object-grant-form>.el-button,.object-config-toolbar>.el-button{min-height:44px}}
</style>
