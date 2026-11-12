<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import { BusinessWorkspace, ManagedDialog } from "@appnexus/ui";
import { createBlankTemplate, type DeviceDraft, type PrintApi, type PrintRow, type PrintTemplate, type TemplateDraft } from "./index";
import { starterTemplate, type DesignContract } from "./authoring";
import { selectDesignContract } from "./workflow";
import PrintPreview from "./PrintPreview.vue";
import PrintFlowWorkspace from "./PrintFlowWorkspace.vue";
import TemplateDesigner from "./TemplateDesigner.vue";

/** 打印管理采用单一工作区分区管理模板、设备和任务；设计器画布是物理纸张布局所需的最小自定义区域。 */
const props=defineProps<{api:PrintApi;errorText:(error:unknown)=>string;initialOutputId?:string}>();
const {t,locale}=useI18n();
const previewOutput=ref(props.initialOutputId??"");
const tab=ref("contracts"),caps=ref<string[]>([]),templates=ref<PrintRow[]>([]),devices=ref<PrintRow[]>([]),jobs=ref<PrintRow[]>([]);
const loading=ref(false),busy=ref(false),error=ref(""),query=ref("");
const templateOpen=ref(false),deviceOpen=ref(false),designerOpen=ref(false),reprintOpen=ref(false);
const versionsOpen=ref(false),versions=ref<PrintRow[]>([]),versionTarget=ref<PrintRow>();
const flowWorkspace=ref<InstanceType<typeof PrintFlowWorkspace>>(),sceneTemplateId=ref("");
const flowSection=computed(()=>tab.value as "contracts"|"scenes"|"documents"|"queues");
const contractLoading=ref(false),contractError=ref("");
const contracts=ref<DesignContract[]>([]),startMode=ref("outbound");
const designContract=computed(()=>contracts.value.find(value=>value.code===templateForm.value.dataContract));
const designerComponent=ref<InstanceType<typeof TemplateDesigner>>();
const editing=ref<PrintRow>(),targetJob=ref<PrintRow>(),reason=ref("");
const templateForm=ref<TemplateDraft>({code:"",name:"",category:"A4",dataContract:"",scope:{},template:createBlankTemplate(),revision:-1});
const deviceForm=ref<DeviceDraft>({code:"",name:"",siteCode:"default",systemName:"",capabilities:{},enabled:true});
const can=(value:string)=>caps.value.includes(value);
const display=(row:Record<string,unknown>,key:string)=>String(row[key]??"—");
const filtered=(rows:PrintRow[])=>rows.filter(row=>!query.value||Object.values(row).some(value=>String(value??"").toLowerCase().includes(query.value.toLowerCase())));
const formatTime=(value:unknown)=>value?new Intl.DateTimeFormat(locale.value.startsWith("en")?"en-US":"zh-CN",{dateStyle:"short",timeStyle:"short"}).format(new Date(String(value))):"—";

async function load(){loading.value=true;error.value="";try{caps.value=await props.api.capabilities();const work:Promise<void>[]=[];if(can("design"))work.push(loadContracts());if(can("read"))work.push(props.api.templates().then(v=>{templates.value=v}));if(can("devices")||can("submit"))work.push(props.api.devices().then(v=>{devices.value=v}));if(can("jobs"))work.push(props.api.jobs().then(v=>{jobs.value=v}));await Promise.all(work);}catch(e){error.value=props.errorText(e)}finally{loading.value=false}}
/** 契约单独显示加载与错误；旧后端或网络失败不能伪装成没有可用业务契约。 */
async function loadContracts(){
  contractLoading.value=true;contractError.value="";
  try{contracts.value=await props.api.designContracts();if(!templateForm.value.dataContract)templateForm.value.dataContract=contracts.value[0]?.code??""}
  catch(e){contractError.value=props.errorText(e)}finally{contractLoading.value=false}
}
onMounted(load);
async function run(action:()=>Promise<void>){busy.value=true;error.value="";try{await action()}catch(e){error.value=props.errorText(e)}finally{busy.value=false}}
/** 新建前读取最新契约；从契约行进入时保留所选编码，不依赖另一分区的旧缓存。 */
async function createTemplate(code?:string){
  await loadContracts();if(contractError.value){error.value=contractError.value;return}
  if(!contracts.value.length){tab.value="contracts";error.value=t("printing.flow.needContract");return}
  editing.value=undefined;tab.value="templates";
  const selected=selectDesignContract(contracts.value,code??templateForm.value.dataContract)!;
  startMode.value=selected.code==="warehouse.outbound.v1"?"outbound":"blank";
  templateForm.value={code:"print_"+crypto.randomUUID(),name:"",category:"A4",dataContract:selected.code,scope:{},template:createBlankTemplate(),revision:-1};templateOpen.value=true;
}
/** 契约保存同步父级目录，并让下一步直接使用新建契约，不必刷新整个应用。 */
function contractsChanged(values:DesignContract[],code:string){contracts.value=values;templateForm.value.dataContract=code;}
function configureScene(row:Record<string,unknown>){sceneTemplateId.value=String(row.id);tab.value="scenes"}
async function refreshCurrent(){await load();await flowWorkspace.value?.refresh()}
function editTemplate(row:Record<string,unknown>){editing.value=row as PrintRow;templateForm.value={code:display(row,"code"),name:display(row,"name"),category:display(row,"category"),dataContract:display(row,"data_contract"),scope:(row.scope_json as Record<string,unknown>)||{},template:row.draft_json as PrintTemplate,revision:Number(row.draft_revision)};templateOpen.value=true}
async function saveTemplate(){if(!templateForm.value.code||!templateForm.value.name.trim()||!templateForm.value.dataContract){error.value=t("printing.requiredFields");return}await run(async()=>{if(!editing.value&&startMode.value==="outbound"&&designContract.value)templateForm.value.template=starterTemplate(designContract.value,locale.value.startsWith("en"));const saved=await props.api.saveTemplate(editing.value?.id,templateForm.value);editing.value=saved;templateOpen.value=false;ElMessage.success(t("printing.saved"));await load();openDesigner(saved)})}
function openDesigner(row:Record<string,unknown>){editTemplate(row);templateOpen.value=false;designerOpen.value=true}
/** 原生画布修改通过快照比较；关闭、路由离开和浏览器退出都保护未保存设计。 */
async function confirmLeave():Promise<boolean>{
  if(busy.value)return false;
  if(!designerOpen.value)return true;
  let changed=true;
  try{changed=await designerComponent.value?.hasChanges()??false}catch{/* 快照失败不能断言设计未修改。 */}
  if(!changed)return true;
  try{await ElMessageBox.confirm(t("printing.discardDesign"),t("printing.unsavedDesign"),{type:"warning",confirmButtonText:t("printing.discard"),cancelButtonText:t("organization.cancel")});return true}catch{return false}
}
async function closeDesigner(){if(await confirmLeave())designerOpen.value=false}
function beforeUnload(event:BeforeUnloadEvent){if(designerOpen.value){event.preventDefault();event.returnValue=""}}
onMounted(()=>window.addEventListener("beforeunload",beforeUnload));
onBeforeUnmount(()=>window.removeEventListener("beforeunload",beforeUnload));
defineExpose({confirmLeave});
async function saveDesign(){await run(async()=>{if(!designerComponent.value)throw new Error("PRINT_DESIGNER_NOT_READY");templateForm.value.template=await designerComponent.value.snapshot();const saved=await props.api.saveTemplate(editing.value!.id,templateForm.value);editing.value=saved;templateForm.value.revision=Number(saved.draft_revision);await nextTick();designerComponent.value.markSaved();ElMessage.success(t("printing.saved"));await load()})}
async function publish(row:Record<string,unknown>){await ElMessageBox.confirm(t("printing.publishConfirm"),t("printing.publish"),{type:"warning"});await run(async()=>{await props.api.publish(String(row.id),Number(row.draft_revision));ElMessage.success(t("printing.published"));await load()})}
/** 历史版本从后端读取，恢复只改草稿，不隐式发布。 */
async function showVersions(row:Record<string,unknown>){await run(async()=>{versionTarget.value=row as PrintRow;versions.value=await props.api.versions(String(row.id));versionsOpen.value=true})}
async function restoreVersion(row:Record<string,unknown>){
  try{await ElMessageBox.confirm(t("printing.restoreConfirm"),t("printing.restoreVersion"),{type:"warning"})}catch{return}
  await run(async()=>{await props.api.restoreVersion(versionTarget.value!.id,Number(row.version),Number(versionTarget.value!.draft_revision));versionsOpen.value=false;ElMessage.success(t("printing.saved"));await load()})
}
function createDevice(){editing.value=undefined;deviceForm.value={code:"",name:"",siteCode:"default",systemName:"",capabilities:{},enabled:true};deviceOpen.value=true}
function editDevice(row:Record<string,unknown>){editing.value=row as PrintRow;deviceForm.value={code:display(row,"code"),name:display(row,"name"),siteCode:display(row,"site_code"),systemName:display(row,"system_name"),capabilities:(row.capabilities_json as Record<string,unknown>)||{},enabled:Boolean(row.enabled)};deviceOpen.value=true}
async function saveDevice(){if(!deviceForm.value.code||!deviceForm.value.name||!deviceForm.value.siteCode||!deviceForm.value.systemName)return;await run(async()=>{await props.api.saveDevice(editing.value?.id,deviceForm.value);deviceOpen.value=false;ElMessage.success(t("printing.saved"));await load()})}
/** 产物通过已认证接口取得；对象 URL 在下载触发后释放，不写入本地业务存储。 */
async function downloadPdf(row:Record<string,unknown>){await run(async()=>{
  const blob=await props.api.downloadPdf(String(row.id));
  const url=URL.createObjectURL(blob),link=document.createElement("a");
  link.href=url;link.download=`print-${String(row.id)}.pdf`;link.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
})}
function openReprint(row:Record<string,unknown>){targetJob.value=row as PrintRow;reason.value="";reprintOpen.value=true}
async function reprint(){if(!reason.value.trim())return;await run(async()=>{await props.api.reprint(targetJob.value!.id,reason.value);reprintOpen.value=false;ElMessage.success(t("printing.reprintCreated"));await load()})}
</script>

<template>
  <!-- 单一根容器承接应用壳 main-content，弹窗由 ManagedDialog Teleport，不额外设置页面外边距。 -->
  <div class="print-management">
  <PrintPreview v-if="previewOutput" :api="api" :output-id="previewOutput" :error-text="errorText" @close="previewOutput=''" @confirmed="load"/><BusinessWorkspace :title="t('printing.title')">
    <template #navigation>
      <el-tabs v-model="tab">
        <el-tab-pane v-if="can('design')||can('submit')||can('publish')" name="contracts" :label="t('printing.flow.stepContracts')"/>
        <el-tab-pane v-if="can('read')" name="templates" :label="t('printing.flow.stepTemplates')"/>
        <el-tab-pane v-if="can('publish')||can('submit')" name="scenes" :label="t('printing.flow.stepScenes')"/>
        <el-tab-pane v-if="can('submit')" name="documents" :label="t('printing.flow.stepPrint')"/>
        <el-tab-pane v-if="can('devices')||can('submit')" name="devices" :label="t('printing.devices')"/>
        <el-tab-pane v-if="can('devices')" name="queues" :label="t('printing.flow.clientQueues')"/>
        <el-tab-pane v-if="can('jobs')" name="jobs" :label="t('printing.jobs')"/>
      </el-tabs>
    </template>    <template v-if="error" #feedback><el-alert type="error" :title="error" :closable="false"><el-button link @click="load">{{t('account.retry')}}</el-button></el-alert></template>
    <template #search><div class="print-search"><el-form inline @submit.prevent><el-form-item :label="t('printing.search')"><el-input v-model="query" clearable/></el-form-item><div class="print-search-actions"><el-button @click="query=''">{{t('management.reset')}}</el-button><el-button :loading="loading" @click="refreshCurrent">{{t('printing.refresh')}}</el-button></div></el-form></div></template>
    <template #data>
      <PrintFlowWorkspace v-if="['contracts','scenes','documents','queues'].includes(tab)" :key="tab" ref="flowWorkspace" :section="flowSection" :query="query" :initial-template-id="tab==='scenes'?sceneTemplateId:undefined" :api="api" :capabilities="caps" :error-text="errorText" @preview="value=>previewOutput=value" @contracts-changed="contractsChanged" @create-template="createTemplate" @submitted="tab='jobs';load()"/><section v-else v-loading="loading" class="print-panel">
        <header class="print-toolbar"><h2>{{t('printing.'+tab)}}</h2><el-button v-if="tab==='templates'&&can('design')" type="primary" @click="createTemplate()">{{t('printing.createTemplate')}}</el-button><el-button v-if="tab==='devices'&&can('devices')" type="primary" @click="createDevice">{{t('printing.createDevice')}}</el-button></header>
        <el-table v-if="tab==='templates'" :data="filtered(templates)" row-key="id" table-layout="fixed">
          <el-table-column prop="name" :label="t('printing.name')" min-width="180"/><el-table-column prop="code" :label="t('printing.code')" min-width="140"/><el-table-column prop="category" :label="t('printing.category')" width="110"/><el-table-column prop="data_contract" :label="t('printing.dataContract')" min-width="150"/><el-table-column prop="published_version" :label="t('printing.version')" width="100"><template #default="{row}">{{row.published_version??t('printing.unpublished')}}</template></el-table-column><el-table-column :label="t('printing.updatedAt')" width="170"><template #default="{row}">{{formatTime(row.updated_at)}}</template></el-table-column>
          <el-table-column :label="t('printing.operations')" width="470" fixed="right"><template #default="{row}"><el-button link type="primary" :disabled="busy" @click="showVersions(row)">{{t('printing.versions')}}</el-button><el-button v-if="can('design')" link type="primary" @click="editTemplate(row)">{{t('printing.edit')}}</el-button><el-button v-if="can('design')" link type="primary" @click="openDesigner(row)">{{t('printing.design')}}</el-button><el-button v-if="can('publish')" link type="primary" @click="publish(row)">{{t('printing.publish')}}</el-button><el-button v-if="can('publish')&&row.published_version" link type="primary" @click="configureScene(row)">{{t('printing.flow.configureScene')}}</el-button></template></el-table-column>
        </el-table>
        <el-table v-else-if="tab==='devices'" :data="filtered(devices)" row-key="id" table-layout="fixed"><el-table-column prop="name" :label="t('printing.name')" min-width="180"/><el-table-column prop="code" :label="t('printing.code')" min-width="130"/><el-table-column prop="site_code" :label="t('printing.site')" min-width="130"/><el-table-column prop="system_name" :label="t('printing.queue')" min-width="200"/><el-table-column :label="t('printing.state')" width="110"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'printing.enabled':'printing.disabled')}}</el-tag></template></el-table-column><el-table-column :label="t('printing.lastSeen')" width="170"><template #default="{row}">{{formatTime(row.last_seen_at)}}</template></el-table-column><el-table-column v-if="can('devices')" :label="t('printing.operations')" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" @click="editDevice(row)">{{t('printing.edit')}}</el-button></template></el-table-column></el-table>
        <el-table v-else :data="filtered(jobs)" row-key="id" table-layout="fixed"><el-table-column prop="source_type" :label="t('printing.sourceType')" min-width="130"/><el-table-column prop="source_id" :label="t('printing.sourceId')" min-width="160"/><el-table-column prop="template_name" :label="t('printing.template')" min-width="160"/><el-table-column prop="device_name" :label="t('printing.device')" min-width="150"/><el-table-column prop="copies" :label="t('printing.copies')" width="80"/><el-table-column :label="t('printing.state')" width="120"><template #default="{row}"><el-tag>{{t('printing.states.'+row.state)}}</el-tag></template></el-table-column><el-table-column :label="t('printing.createdAt')" width="170"><template #default="{row}">{{formatTime(row.created_at)}}</template></el-table-column><el-table-column :label="t('printing.operations')" width="220" fixed="right"><template #default="{row}"><el-button v-if="row.output_type==='PDF'&&row.state==='SUCCEEDED'" link type="primary" :disabled="busy" @click="downloadPdf(row)">{{t('printing.downloadPdf')}}</el-button><el-button v-if="can('reprint')" link type="primary" :disabled="busy" @click="openReprint(row)">{{t('printing.reprint')}}</el-button></template></el-table-column></el-table>
        <el-empty v-if="!loading&&!filtered(tab==='templates'?templates:tab==='devices'?devices:jobs).length" :description="t('printing.empty')"/>
      </section>
    </template>
  </BusinessWorkspace>

  <ManagedDialog :model-value="versionsOpen" :title="t('printing.versions')" :busy="busy" @update:model-value="versionsOpen=$event">
    <el-table :data="versions"><el-table-column prop="version" :label="t('printing.version')"/><el-table-column :label="t('printing.publishedAt')"><template #default="{row}">{{formatTime(row.published_at)}}</template></el-table-column><el-table-column v-if="can('design')" :label="t('printing.operations')"><template #default="{row}"><el-button link type="primary" :disabled="busy" @click="restoreVersion(row)">{{t('printing.restoreVersion')}}</el-button></template></el-table-column></el-table>
  </ManagedDialog>
  <ManagedDialog :model-value="templateOpen" :title="t(editing?'printing.editTemplate':'printing.createTemplate')" :busy="busy" width="min(720px, calc(100vw - 32px))" @update:model-value="templateOpen=$event"><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-form :model="templateForm" label-position="top" @submit.prevent="saveTemplate"><div class="print-grid"><el-form-item :label="t('printing.code')" required><el-input v-model="templateForm.code" maxlength="64" :readonly="!editing"/></el-form-item><el-form-item :label="t('printing.name')" required><el-input v-model="templateForm.name" maxlength="128"/></el-form-item><el-form-item :label="t('printing.category')" required><el-select v-model="templateForm.category"><el-option v-for="value in ['A4','LABEL','WAYBILL','RECEIPT']" :key="value" :value="value"/></el-select></el-form-item><el-form-item :label="t('printing.dataContract')" required><el-select v-model="templateForm.dataContract" @change="startMode=templateForm.dataContract==='warehouse.outbound.v1'?'outbound':'blank'" :loading="contractLoading" :disabled="!!editing||contractLoading"><el-option v-for="contract in contracts" :key="contract.code" :value="contract.code" :label="locale.startsWith('en')?contract.nameEn:contract.nameZh"/><el-option v-if="editing&&!designContract" :value="templateForm.dataContract" :label="templateForm.dataContract"/></el-select><el-alert v-if="contractError" :title="contractError" type="error" :closable="false"><el-button link :loading="contractLoading" @click="loadContracts">{{t('account.retry')}}</el-button></el-alert></el-form-item></div><el-form-item v-if="!editing" :label="t('printing.startWith')"><el-radio-group v-model="startMode"><el-radio v-if="templateForm.dataContract==='warehouse.outbound.v1'" value="outbound">{{t('printing.outboundStarter')}}</el-radio><el-radio value="blank">{{t('printing.blankStarter')}}</el-radio></el-radio-group></el-form-item><el-alert type="info" :title="t('printing.scopeHint')" :closable="false"/><div class="dialog-buttons"><el-button @click="templateOpen=false">{{t('organization.cancel')}}</el-button><el-button type="primary" native-type="submit" :loading="busy">{{t('organization.save')}}</el-button></div></el-form></ManagedDialog>
  <ManagedDialog :model-value="designerOpen" :title="t('printing.design')+' - '+templateForm.name" :busy="busy" width="calc(100vw - 48px)" height="calc(100vh - 48px)" @update:model-value="value=>{if(!value)closeDesigner()}"><el-alert v-if="error" :title="error" type="error" :closable="false"/><TemplateDesigner v-if="designerOpen" ref="designerComponent" v-model="templateForm.template" :language="locale.startsWith('en')?'en':'cn'" :contract="designContract"/><template #footer><el-button @click="closeDesigner">{{t('organization.cancel')}}</el-button><el-button type="primary" :loading="busy" @click="saveDesign">{{t('organization.save')}}</el-button></template></ManagedDialog>
  <ManagedDialog :model-value="deviceOpen" :title="t(editing?'printing.editDevice':'printing.createDevice')" :busy="busy" width="min(680px, calc(100vw - 32px))" @update:model-value="deviceOpen=$event"><el-form :model="deviceForm" label-position="top" @submit.prevent="saveDevice"><div class="print-grid"><el-form-item :label="t('printing.code')" required><el-input v-model="deviceForm.code" maxlength="64"/></el-form-item><el-form-item :label="t('printing.name')" required><el-input v-model="deviceForm.name" maxlength="128"/></el-form-item><el-form-item :label="t('printing.site')" required><el-input v-model="deviceForm.siteCode" maxlength="64"/></el-form-item><el-form-item :label="t('printing.queue')" required><el-input v-model="deviceForm.systemName" maxlength="255"/></el-form-item></div><el-form-item :label="t('printing.enabled')"><el-switch v-model="deviceForm.enabled"/></el-form-item><div class="dialog-buttons"><el-button @click="deviceOpen=false">{{t('organization.cancel')}}</el-button><el-button type="primary" native-type="submit" :loading="busy">{{t('organization.save')}}</el-button></div></el-form></ManagedDialog>
  <ManagedDialog :model-value="reprintOpen" :title="t('printing.reprint')" :busy="busy" width="min(520px, calc(100vw - 32px))" @update:model-value="reprintOpen=$event"><el-form label-position="top" @submit.prevent="reprint"><el-alert type="warning" :title="t('printing.reprintHint')" :closable="false"/><el-form-item :label="t('printing.reason')" required><el-input v-model="reason" type="textarea" maxlength="500" show-word-limit/></el-form-item><div class="dialog-buttons"><el-button @click="reprintOpen=false">{{t('organization.cancel')}}</el-button><el-button type="primary" native-type="submit" :loading="busy">{{t('printing.reprint')}}</el-button></div></el-form></ManagedDialog>
  </div>
</template>

<style scoped>
.print-management{min-width:0}.print-search,.print-panel{padding:0}.print-search :deep(.el-form){display:flex;align-items:flex-start;gap:12px}.print-search-actions{display:flex;gap:8px;margin-left:auto}.print-toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.print-toolbar h2{margin:0;font-size:16px}.print-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.dialog-buttons{display:flex;justify-content:flex-end;gap:8px;margin-top:20px}
@media(max-width:760px){.print-grid{grid-template-columns:1fr}.print-search :deep(.el-form){display:block}.print-search-actions{justify-content:flex-end}.print-panel{padding:0}}
</style>
