<script setup lang="ts">
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
async function inspectReadiness(){if(!selected.value)return;await run(async()=>{checks.value=await props.api.readiness(selected.value!.objectType);});}
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
async function open(d:Descriptor){checks.value=[];savedFeedback.value="";selected.value=d;entryQuery.value='';entryPage.value=1;await run(async()=>{
  if(props.platform){[tenants.value,grants.value,pageEditor.value]=await Promise.all([props.api.tenants(),props.api.grants(d.objectType),props.api.pageRelationships(d.objectType)]);tenant.value='';}
  else {const [config,options]=await Promise.all([props.api.configuration(d.objectType),Promise.all(d.operations.filter(a=>a.interaction.capabilities.map(c=>c.service).includes('WORKFLOW')).map(async a=>[a.operationCode,await props.api.workflows(d.objectType,a.operationCode)] as const))]);revision.value=config.revision;published.value=config.publishedVersion;bindings.value=config.bindings;workflows.value=Object.fromEntries(options);}
  if(!props.platform)printScenes.value=Object.fromEntries(await Promise.all(d.operations.filter(a=>a.interaction.capabilities.map(c=>c.service).includes('PRINT')).map(async a=>[a.operationCode,await props.api.scenes(d.objectType,a.operationCode)] as const)));
  dialog.value=true;
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
    <template #feedback><FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false"><el-button text @click="load">{{t('businessObject.refresh')}}</el-button></FloatingError></template>
    <template #search><div class="management-filter"><el-form inline @submit.prevent="applied=query;page=1"><el-form-item :label="t('businessObject.search')"><el-input v-model="query" clearable /></el-form-item><div class="management-filter-actions"><el-button type="primary" native-type="submit"><AppIcon name="search" :size="16" class="mr-1" />{{t('administration.search')}}</el-button><el-button @click="query='';applied='';page=1"><AppIcon name="refresh" :size="16" class="mr-1" />{{t('businessObject.reset')}}</el-button></div></el-form></div></template>
    <template #data><ManagementTable :title="t(platform?'businessObject.platformTitle':'businessObject.title')" :rows="paged" :columns="columns" :loading="loading" :busy="busy" embedded @refresh="load">
      <template v-if="platform" #titleHelp><InfoHint :content="t('businessObject.catalogBoundary')" /></template>
      <template #objectType="{row}">{{row.descriptor.objectType}}</template><template #name="{row}">{{name(row.descriptor)}}</template><template #module="{row}">{{row.descriptor.module}}</template><template #version="{row}">{{row.descriptor.version}}</template>
      <template #state="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'businessObject.enabled':'businessObject.disabled')}}</el-tag></template>
      <template #category="{row}"><el-tag type="info">{{t('businessObject.categories.'+row.descriptor.category)}}</el-tag></template>
      <template #operations="{row}"><el-button link type="primary" :disabled="busy" v-operation="platform ? 'BusinessObject.relationships' : 'BusinessObject.configuration'" @click="open(row.descriptor)">{{t(platform?'businessObject.grants':'businessObject.configure')}}</el-button><el-button v-operation="'BusinessObject.state'" v-if="platform" link :type="row.enabled?'danger':'primary'" :disabled="busy" @click="activate(row)">{{t(row.enabled?'businessObject.disable':'businessObject.enable')}}</el-button></template>
      <template #empty><el-empty :description="t('businessObject.empty')" /></template>
      <template #pagination><AppPagination v-model:current-page="page" v-model:page-size="size" :total="filtered.length" layout="total, sizes, prev, pager, next" @size-change="page=1" /></template>
    </ManagementTable></template>
  </BusinessWorkspace>
  <ManagedDialog v-model="dialog" :title="selected?name(selected):''" width="min(1200px, calc(100vw - 32px))" :close-on-click-modal="false"><template #title><span class="info-hint-title">{{selected?name(selected):''}}<el-tag v-if="selected" size="small" type="info">{{t('businessObject.contractVersion')}} {{selected.version}}</el-tag><InfoHint v-if="!platform" :content="t('businessObject.bindingHint')" /></span></template>
    <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" />
    <template v-if="selected">
      <el-alert v-if="savedFeedback" :title="savedFeedback" type="success" :closable="false" />
      <!-- 契约详情按信息职责分区；复用 Element Plus 卡片，不改变授权、管控及契约字段。 -->
      <div class="object-contract-sections">
      <el-card shadow="never" class="object-contract-section">
        <template #header><h3>{{t('businessObject.basicInfo')}}</h3></template>
      <el-descriptions :column="2" border><el-descriptions-item :label="t('businessObject.code')">{{selected.objectType}}</el-descriptions-item><el-descriptions-item :label="t('businessObject.contractVersion')">{{selected.version}}</el-descriptions-item></el-descriptions>
      </el-card>
      <el-card shadow="never" class="object-contract-section">
        <template #header><h3>{{t('businessObject.field')}}<el-tag size="small" type="info">{{selected.fields.length}}</el-tag></h3></template>
      <vxe-table size="small" :min-height="0" :column-config="{ resizable: true }"  :data="selected.fields" border="inner"><vxe-column field="key" :title="t('businessObject.field')"/><vxe-column :field="locale.startsWith('zh')?'nameZh':'nameEn'" :title="t('businessObject.name')"/><vxe-column field="type" :title="t('businessObject.fieldType')"/><vxe-column field="unit" :title="t('businessObject.unit')"><template #default="{row}">{{row.unit || '—'}}</template></vxe-column></vxe-table>
      </el-card>
      <el-card shadow="never" class="object-contract-section">
        <template #header><h3>{{t('businessObject.interactions')}}<el-tag size="small" type="info">{{selected.operations.length}}</el-tag></h3></template>
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
      </el-card>
      <template v-if="platform">
        <el-card shadow="never" class="object-contract-section">
          <template #header><h3>{{t('businessObject.pageRelations')}}<InfoHint :content="t('businessObject.pageRelationHint')" /><el-tag size="small" type="info">{{entryRows.length}}</el-tag></h3></template>
        
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
        </el-card>
        <el-card shadow="never" class="object-contract-section">
          <template #header><h3>{{t('businessObject.tenantAuthorization')}}<el-tag size="small" type="info">{{grants.length}}</el-tag></h3></template>
        <el-form label-position="top" class="business-border-label-form object-grant-form"><el-form-item :label="t('businessObject.tenant')"><el-select v-model="tenant" filterable><el-option v-for="v in tenants" :key="v.id" :label="v.name" :value="v.id"/></el-select></el-form-item><el-button v-operation="'BusinessObject.grant'" type="primary" :disabled="!tenant" :loading="busy" @click="grant(tenant,true)">{{t('businessObject.authorize')}}</el-button></el-form>
        <vxe-table size="small" :min-height="0" :column-config="{ resizable: true }"  :data="grants" border="inner"><vxe-column :title="t('businessObject.tenant')"><template #default="{row}">{{tenants.find(v=>v.id===row.tenant_id)?.name||row.tenant_id}}</template></vxe-column><vxe-column :title="t('businessObject.state')"><template #default="{row}">{{t(row.enabled?'businessObject.enabled':'businessObject.disabled')}}</template></vxe-column><vxe-column :title="t('businessObject.operations')"><template #default="{row}"><el-button v-operation="'BusinessObject.grant'" link :disabled="busy" @click="grant(row.tenant_id,!row.enabled)">{{t(row.enabled?'businessObject.revoke':'businessObject.authorize')}}</el-button></template></vxe-column></vxe-table>
      </el-card>
      </template>
      <template v-else>
        <el-button v-operation="'BusinessObject.readiness'" :loading="busy" @click="inspectReadiness">{{t('businessObject.checkReadiness')}}</el-button>
        <vxe-table size="small" :min-height="0" :column-config="{ resizable: true }"  v-if="checks.length" :data="checks" border="inner"><vxe-column field="operationCode" :title="t('businessObject.action')"/><vxe-column :title="t('businessObject.checkItem')"><template #default="{row}">{{t('businessObject.checks.'+row.code)}}</template></vxe-column><vxe-column :title="t('businessObject.state')"><template #default="{row}"><el-tag :type="row.ready?'success':'warning'">{{t(row.ready?'businessObject.ready':'businessObject.notReady')}}</el-tag> {{row.category==='LIVE'?t('businessObject.liveHint'):''}}</template></vxe-column><vxe-column field="target" :title="t('businessObject.checkTarget')"/></vxe-table>
        <el-alert v-if="rows.find(r=>r.descriptor.objectType===selected?.objectType)?.enabled===false" :title="t('businessObject.inactiveHint')" type="warning" :closable="false" />
        
        <p>{{t('businessObject.publishedVersion')}}: {{published}}</p>
        <el-button :disabled="busy" @click="add">{{t('businessObject.addBinding')}}</el-button><el-button :disabled="busy" @click="open(selected)">{{t('businessObject.reload')}}</el-button>
        <el-empty v-if="bindings.some(b=>b.operationCode&&b.service==='WORKFLOW'&&!(workflows[b.operationCode]?.length))" :description="t('businessObject.noWorkflow')" />
        <el-form v-for="(b,index) in bindings" :key="b.id" :ref="form=>bindForm(b.id,form)" :model="b" :rules="bindingRules" label-position="top" class="business-border-label-form business-object-binding">
          <el-form-item :label="t('businessObject.bindingName')" prop="name"><el-input v-model="b.name" maxlength="100"/></el-form-item>
          <el-form-item :label="t('businessObject.action')" prop="operationId">
            <el-select :model-value="b.operationId" :placeholder="t('businessObject.selectPageOperation')" filterable @update:model-value="selectPageOperation(b,selected,$event)">
              <el-option v-for="a in pageOperations(selected)" :key="a.operationId" :value="a.operationId" :label="`${locale.startsWith('zh')?a.nameZh:a.nameEn} (${a.operationCode})`" />
            </el-select>
            <el-alert v-if="!pageOperations(selected).length" :title="t('businessObject.noPageOperation')" type="warning" :closable="false" />
          </el-form-item>
          <el-descriptions v-if="hasPageOperation(b,selected)" :column="1" border>
            <el-descriptions-item :label="t('businessObject.action')">{{selected.objectType}}.{{b.operationCode}}</el-descriptions-item>
            <el-descriptions-item :label="t('businessObject.permission')">{{operationAction(selected,b.operationId)?.permissionCode}}</el-descriptions-item>
            <el-descriptions-item :label="t('businessObject.businessEndpoint')">{{operationAction(selected,b.operationId)?.pageOperation?.method}} {{operationAction(selected,b.operationId)?.pageOperation?.apiPath}}</el-descriptions-item>
          </el-descriptions>
          <el-form-item :label="t('businessObject.service')"><template #label>{{t('businessObject.service')}}<InfoHint v-if="b.service!=='WORKFLOW'&&b.service!=='PRINT'" :content="t('businessObject.directHint')" /></template><el-select v-model="b.service" @change="changeService(b)"><el-option v-for="mode in selected.operations.find(a=>a.operationCode===b.operationCode)?.interaction.capabilities.map(c=>c.service)||[]" :key="mode" :value="mode" :label="t('businessObject.services.'+mode)"/></el-select></el-form-item>
          <el-form-item v-if="b.service==='WORKFLOW'" :label="t('businessObject.workflow')" prop="definitionId"><el-select :model-value="`${b.definitionId}:${b.definitionVersion}`" @update:model-value="workflowSelect(b,$event)"><el-option v-for="w in workflows[b.operationCode]||[]" :key="`${w.id}:${w.version}`" :value="`${w.id}:${w.version}`" :label="`${w.name} · ${w.id} · v${w.version}`"/></el-select></el-form-item>
          <el-form-item v-else-if="b.service==='PRINT'" :label="t('businessObject.printScene')" required><el-select v-model="b.sceneCode"><el-option v-for="scene in printScenes[b.operationCode]||[]" :key="scene.code" :value="scene.code" :label="scene.name+' · '+scene.code"/></el-select></el-form-item>
          
          <el-button v-if="b.service==='WORKFLOW'&&b.operationCode" :disabled="busy" v-operation="'WorkflowDefinition.save'" @click="createWorkflow(b.operationCode)">{{t('businessObject.createWorkflow')}}</el-button>
          <div v-for="(c,ci) in b.conditions" :key="ci" class="business-object-condition"><el-select v-model="c.field" :aria-label="t('businessObject.field')" @change="c.operator='EQ'"><el-option v-for="f in selected.fields" :key="f.key" :value="f.key" :label="locale.startsWith('zh')?f.nameZh:f.nameEn"/></el-select><el-select v-model="c.operator" :aria-label="t('businessObject.operator')"><el-option v-for="op in operators(c.field)" :key="op" :value="op" :label="t('businessObject.operators.'+op)"/></el-select><el-input v-model="c.value" :aria-label="t('businessObject.value')" maxlength="200"/><el-button @click="b.conditions.splice(ci,1)">{{t('businessObject.remove')}}</el-button></div>
          <el-button @click="b.conditions.push({field:'',operator:'EQ',value:''})">{{t('businessObject.addCondition')}}</el-button><el-button type="danger" plain @click="bindings.splice(index,1)">{{t('businessObject.removeBinding')}}</el-button>
        </el-form>
      </template>
      </div>
    </template>
    <template #footer><el-button @click="dialog=false">{{t('businessObject.close')}}</el-button><template v-if="!platform"><el-button v-operation="'BusinessObject.save'" :loading="busy" @click="save()">{{t('businessObject.save')}}</el-button><el-button v-operation="'BusinessObject.publish'" type="primary" :loading="busy" @click="save(true)">{{t('businessObject.publish')}}</el-button></template></template>
  </ManagedDialog>
  </div>
</template>
<style scoped>
/* 页面外围留白引用全局内容与区块 Token，局部字段间距和触控高度按原交互保留。 */
.business-object-search{margin-left:auto;display:flex;gap:8px} .el-form--inline{display:flex;flex-wrap:wrap} .el-pagination{justify-content:flex-end;margin-top:16px}
.business-object-binding{margin-top:16px;padding:var(--app-content-padding,12px);border:1px solid var(--el-border-color);border-radius:var(--el-border-radius-base)}
.business-object-condition{display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap}.business-object-condition>*{flex:1;min-width:100px}.el-descriptions{margin-bottom:var(--app-section-gap,8px)}
/* 弹窗内容自然滚动，各分区不套用主列表撑高规则；主题与边框继承 Element Plus。 */
.object-contract-sections{display:flex;flex-direction:column;gap:8px}
.object-contract-section{flex:none}
.object-contract-section :deep(.el-card__header){padding:8px 12px;border-bottom:0}
.object-contract-section :deep(.el-card__body){padding:0 12px 8px}
.object-contract-section h3{display:flex;align-items:center;gap:8px;margin:0;font-size:var(--el-font-size-medium);font-weight:600}
.object-contract-section .el-descriptions{margin:0}
.object-contract-section :deep(.el-input){margin-bottom:8px}
.object-grant-form{display:flex;align-items:flex-end;gap:8px;flex-wrap:wrap;margin-bottom:8px}
.object-grant-form .el-form-item{flex:1;min-width:200px;margin:0}
/* 契约明细只按真实行高布局，避免默认表体最小高度产生大块空白。 */
.object-contract-section :deep(.vxe-table){width:100%;--vxe-ui-table-row-height-small:32px;--vxe-ui-table-cell-padding-small:4px}
.object-contract-section :deep(.el-descriptions__cell){padding:6px 10px!important}
.object-contract-section :deep(.el-pagination){margin-top:8px}
.object-contract-section h3::before{content:"";width:3px;height:16px;background:var(--el-color-primary);border-radius:var(--el-border-radius-small)}
@media(max-width:700px){.object-contract-section :deep(.el-card__header){padding:8px 12px}.object-contract-section :deep(.el-card__body){padding:0 12px 8px}.object-grant-form>.el-button{min-height:44px}}
</style>
