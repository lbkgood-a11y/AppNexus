<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox, type FormInstance } from "element-plus";
import { BusinessWorkspace, ManagementTable, ManagedDialog, type ManagementColumn } from "@appnexus/ui";
import type { Binding, Descriptor, DocumentApi } from "./index";

/** 平台目录与租户绑定共用列表；模式由构建入口固定，所有接口仍在服务端鉴权。 */
const props = defineProps<{ api: DocumentApi; platform?: boolean; errorText: (e: unknown) => string }>();
const { t, locale } = useI18n(); const router = useRouter();
const rows = ref<Awaited<ReturnType<DocumentApi['catalog']>>>([]);
const loading=ref(false), busy=ref(false), error=ref(""), query=ref(""), applied=ref(""), page=ref(1), size=ref(20);
const selected=ref<Descriptor>(), dialog=ref(false), revision=ref(-1), published=ref(0), bindings=ref<Binding[]>([]);
const workflows=ref<Awaited<ReturnType<DocumentApi['workflows']>>>([]), tenants=ref<Awaited<ReturnType<DocumentApi['tenants']>>>([]), grants=ref<Awaited<ReturnType<DocumentApi['grants']>>>([]);
const tenant=ref("");
const bindingForms=ref<FormInstance[]>([]);
const bindingRules=computed(()=>({name:[{required:true,whitespace:true,max:100,message:t('document.required'),trigger:'blur'}],definitionId:[{required:true,message:t('document.required'),trigger:'change'}]}));
const name=(d:Descriptor)=>locale.value.startsWith('zh')?d.nameZh:d.nameEn;
const filtered=computed(()=>rows.value.filter(r=>[r.descriptor.typeCode,name(r.descriptor)].some(x=>x.toLowerCase().includes(applied.value.toLowerCase()))));
const paged=computed(()=>filtered.value.slice((page.value-1)*size.value,page.value*size.value));
const columns=computed<ManagementColumn[]>(()=>[
  {key:'typeCode',label:t('document.code'),minWidth:180}, {key:'name',label:t('document.name'),minWidth:160},
  {key:'module',label:t('document.module'),minWidth:100},{key:'version',label:t('document.contractVersion'),width:140},
  {key:'state',label:t('document.state'),width:110},
  {key:'operations',label:t('document.operations'),minWidth:240,fixed:'right'}
]);
async function run(action:()=>Promise<void>) {busy.value=true;error.value='';try{await action();}catch(e){error.value=props.errorText(e);}finally{busy.value=false;}}
async function load(){loading.value=true;error.value='';try{rows.value=await props.api.catalog();}catch(e){error.value=props.errorText(e);}finally{loading.value=false;}}
async function open(d:Descriptor){selected.value=d;await run(async()=>{
  if(props.platform){[tenants.value,grants.value]=await Promise.all([props.api.tenants(),props.api.grants(d.typeCode)]);tenant.value='';}
  else {const [config,options]=await Promise.all([props.api.configuration(d.typeCode),props.api.workflows(d.typeCode)]);revision.value=config.revision;published.value=config.publishedVersion;bindings.value=config.bindings.map(b=>({...b,actionCode:b.actionCode||'submit',mode:b.mode||'APPROVAL'}));workflows.value=options;}
  dialog.value=true;
});}
async function activate(row:(typeof rows.value)[number]){
  try{await ElMessageBox.confirm(t(row.enabled?'document.disableConfirm':'document.enableConfirm'),t('document.state'),{type:'warning'});}catch{return;}
  await run(async()=>{await props.api.activate(row.descriptor.typeCode,row.revision,!row.enabled);await load();});
}
async function grant(id:string,enabled:boolean){if(!selected.value)return;
  if(!enabled){try{await ElMessageBox.confirm(t('document.revokeConfirm'),t('document.grants'),{type:'warning'});}catch{return;}}
  await run(async()=>{await props.api.grant(selected.value!.typeCode,id,enabled);grants.value=await props.api.grants(selected.value!.typeCode);ElMessage.success(t('document.saved'));});}
function add(){bindings.value.push({id:crypto.randomUUID(),name:'',definitionId:'',definitionVersion:0,conditions:[],actionCode:'submit',mode:'APPROVAL'});}
/** 策略切换清空旧流程引用，直接提交不得携带隐藏目标。 */
function changeMode(b:Binding){b.definitionId=b.mode==='DIRECT'?null:'';b.definitionVersion=0;}
async function save(publish=false){if(!selected.value)return;
  const valid=await Promise.all(bindingForms.value.map(f=>f.validate().catch(()=>false)));if(valid.some(v=>!v))return;
  if(!bindings.value.length||bindings.value.some(b=>!b.name.trim()||(b.mode==='APPROVAL'&&!b.definitionId)||b.conditions.some(c=>!c.field||!c.value.trim()))){error.value=t('document.required');return;}
  await run(async()=>{const type=selected.value!.typeCode;await props.api.save(type,revision.value,bindings.value);
    let config=await props.api.configuration(type);revision.value=config.revision;
    if(publish){await props.api.publish(type,revision.value);config=await props.api.configuration(type);revision.value=config.revision;published.value=config.publishedVersion;}
    ElMessage.success(t(publish?'document.published':'document.saved'));
  });}
function workflowSelect(b:Binding,value:string){const w=workflows.value.find(w=>`${w.id}:${w.version}`===value);b.definitionId=w?.id||'';b.definitionVersion=w?.version||0;}
function operators(field:string){return selected.value?.fields.find(f=>f.key===field)?.type==='NUMBER'?['EQ','NE','GT','GE','LT','LE']:['EQ','NE'];}
/** 创建入口带回跳信息；绑定草稿保存后再离开，流程仍需人工校验和发布。 */
async function createWorkflow(){if(bindings.value.length){try{await ElMessageBox.confirm(t('document.leaveConfiguration'),t('document.createWorkflow'),{type:'warning'});}catch{return;}}dialog.value=false;await router.push({path:'/workflow/designer',query:{documentType:selected.value?.typeCode,returnTo:'/documents/workbench',create:'1'}});}
onMounted(load);
</script>
<template>
  <div class="management-page">
  <BusinessWorkspace :title="t(platform?'document.platformTitle':'document.title')">
    <template #feedback><el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="load">{{t('document.refresh')}}</el-button></el-alert></template>
    <template #search><el-form inline @submit.prevent="applied=query;page=1"><el-form-item :label="t('document.search')"><el-input v-model="query" clearable /></el-form-item><div class="document-search"><el-button type="primary" native-type="submit">{{t('document.search')}}</el-button><el-button @click="query='';applied='';page=1">{{t('document.reset')}}</el-button></div></el-form></template>
    <template #data><ManagementTable :title="t(platform?'document.platformTitle':'document.title')" :rows="paged" :columns="columns" :loading="loading" :busy="busy" embedded @refresh="load">
      <template #typeCode="{row}">{{row.descriptor.typeCode}}</template><template #name="{row}">{{name(row.descriptor)}}</template><template #module="{row}">{{row.descriptor.module}}</template><template #version="{row}">{{row.descriptor.version}}</template>
      <template #state="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'document.enabled':'document.disabled')}}</el-tag></template>
      <template #operations="{row}"><el-button link type="primary" :disabled="busy" @click="open(row.descriptor)">{{t(platform?'document.grants':'document.configure')}}</el-button><el-button v-if="platform" link :type="row.enabled?'danger':'primary'" :disabled="busy" @click="activate(row)">{{t(row.enabled?'document.disable':'document.enable')}}</el-button></template>
      <template #empty><el-empty :description="t('document.empty')" /></template>
      <template #pagination><el-pagination v-model:current-page="page" v-model:page-size="size" :total="filtered.length" layout="total, sizes, prev, pager, next" @size-change="page=1" /></template>
    </ManagementTable></template>
  </BusinessWorkspace>
  <ManagedDialog v-model="dialog" :title="selected?name(selected):''" width="1000px" :close-on-click-modal="false">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="selected">
      <el-descriptions :column="2" border><el-descriptions-item :label="t('document.code')">{{selected.typeCode}}</el-descriptions-item><el-descriptions-item :label="t('document.contractVersion')">{{selected.version}}</el-descriptions-item></el-descriptions>
      <el-table :data="selected.fields"><el-table-column prop="key" :label="t('document.field')"/><el-table-column :prop="locale.startsWith('zh')?'nameZh':'nameEn'" :label="t('document.name')"/><el-table-column prop="type" :label="t('document.fieldType')"/><el-table-column prop="unit" :label="t('document.unit')"/></el-table>
      <el-table :data="selected.actions"><el-table-column prop="actionCode" :label="t('document.action')"/><el-table-column prop="operationKey" :label="t('document.operationKey')"/><el-table-column prop="permissionCode" :label="t('document.permission')"/></el-table>
      <template v-if="platform">
        <el-form label-position="top"><el-form-item :label="t('document.tenant')"><el-select v-model="tenant" filterable><el-option v-for="v in tenants" :key="v.id" :label="v.name" :value="v.id"/></el-select></el-form-item><el-button type="primary" :disabled="!tenant" :loading="busy" @click="grant(tenant,true)">{{t('document.authorize')}}</el-button></el-form>
        <el-table :data="grants"><el-table-column :label="t('document.tenant')"><template #default="{row}">{{tenants.find(v=>v.id===row.tenant_id)?.name||row.tenant_id}}</template></el-table-column><el-table-column :label="t('document.state')"><template #default="{row}">{{t(row.enabled?'document.enabled':'document.disabled')}}</template></el-table-column><el-table-column :label="t('document.operations')"><template #default="{row}"><el-button link :disabled="busy" @click="grant(row.tenant_id,!row.enabled)">{{t(row.enabled?'document.revoke':'document.authorize')}}</el-button></template></el-table-column></el-table>
      </template>
      <template v-else>
        <el-alert v-if="rows.find(r=>r.descriptor.typeCode===selected?.typeCode)?.enabled===false" :title="t('document.inactiveHint')" type="warning" :closable="false" />
        <el-alert :title="t('document.bindingHint')" type="info" :closable="false" />
        <p>{{t('document.publishedVersion')}}: {{published}}</p>
        <el-button :disabled="busy" @click="add">{{t('document.addBinding')}}</el-button><el-button :disabled="busy" @click="createWorkflow">{{t('document.createWorkflow')}}</el-button><el-button :disabled="busy" @click="open(selected)">{{t('document.reload')}}</el-button>
        <el-empty v-if="!workflows.length&&bindings.some(b=>b.mode==='APPROVAL')" :description="t('document.noWorkflow')" />
        <el-form v-for="(b,index) in bindings" :key="b.id" ref="bindingForms" :model="b" :rules="bindingRules" label-position="top" class="document-binding">
          <el-form-item :label="t('document.bindingName')" prop="name"><el-input v-model="b.name" maxlength="100"/></el-form-item>
          <el-form-item :label="t('document.action')"><el-tag>{{selected.typeCode}}.{{b.actionCode}}</el-tag></el-form-item>
          <el-form-item :label="t('document.submissionMode')"><el-select v-model="b.mode" @change="changeMode(b)"><el-option v-for="mode in selected.actions.find(a=>a.actionCode===b.actionCode)?.supportedModes||[]" :key="mode" :value="mode" :label="t('document.modes.'+mode)"/></el-select></el-form-item>
          <el-form-item v-if="b.mode==='APPROVAL'" :label="t('document.workflow')" prop="definitionId"><el-select :model-value="`${b.definitionId}:${b.definitionVersion}`" @update:model-value="workflowSelect(b,$event)"><el-option v-for="w in workflows" :key="`${w.id}:${w.version}`" :value="`${w.id}:${w.version}`" :label="`${w.name} · ${w.id} · v${w.version}`"/></el-select></el-form-item>
          <el-alert v-else :title="t('document.directHint')" type="info" :closable="false"/>
          <div v-for="(c,ci) in b.conditions" :key="ci" class="document-condition"><el-select v-model="c.field" :aria-label="t('document.field')" @change="c.operator='EQ'"><el-option v-for="f in selected.fields" :key="f.key" :value="f.key" :label="locale.startsWith('zh')?f.nameZh:f.nameEn"/></el-select><el-select v-model="c.operator" :aria-label="t('document.operator')"><el-option v-for="op in operators(c.field)" :key="op" :value="op" :label="t('document.operators.'+op)"/></el-select><el-input v-model="c.value" :aria-label="t('document.value')" maxlength="200"/><el-button @click="b.conditions.splice(ci,1)">{{t('document.remove')}}</el-button></div>
          <el-button @click="b.conditions.push({field:'',operator:'EQ',value:''})">{{t('document.addCondition')}}</el-button><el-button type="danger" plain @click="bindings.splice(index,1)">{{t('document.removeBinding')}}</el-button>
        </el-form>
      </template>
    </template>
    <template #footer><el-button @click="dialog=false">{{t('document.close')}}</el-button><template v-if="!platform"><el-button :loading="busy" @click="save()">{{t('document.save')}}</el-button><el-button type="primary" :loading="busy" @click="save(true)">{{t('document.publish')}}</el-button></template></template>
  </ManagedDialog>
  </div>
</template>
<style scoped>
.document-search{margin-left:auto;display:flex;gap:8px} .el-form--inline{display:flex;flex-wrap:wrap} .el-pagination{justify-content:flex-end;margin-top:16px}
.document-binding{margin-top:16px;padding:16px;border:1px solid var(--el-border-color);border-radius:var(--el-border-radius-base)}
.document-condition{display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap}.document-condition>*{flex:1;min-width:100px}.el-descriptions{margin-bottom:16px}
</style>
