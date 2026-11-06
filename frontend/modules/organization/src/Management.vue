<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import { AppIcon } from "@appnexus/icons";
import { ManagementTable, type ManagementColumn } from "@appnexus/ui";
import type { OrganizationApi, OrganizationCapability, OrganizationGrant, OrganizationMember, OrganizationUnit } from "./index";

/** 组合 Element Plus 表格、表单与确认组件；请求和错误映射由应用壳公开端口注入。 */
const props = defineProps<{ api: OrganizationApi; errorText: (error: unknown) => string }>();
const { t, locale } = useI18n();
const capabilities = ref<OrganizationCapability[]>([]), units = ref<OrganizationUnit[]>([]);
const members = ref<OrganizationMember[]>([]), grants = ref<OrganizationGrant[]>([]);
const loading = ref(false), saving = ref(false), grantLoading = ref(false);
const error = ref(""), memberId = ref("");
const dialog = ref(false), editingId = ref(""), name = ref("");
const capability = ref<OrganizationCapability>("READ"), expiresAt = ref<Date | null>(null);
const organizationId=ref<string|null>(null);
const operations: OrganizationCapability[] = ["READ", "CREATE", "UPDATE", "DELETE"];
const can = (action: OrganizationCapability) => capabilities.value.includes(action);
const tab=ref("units"),query=ref(""),submittedQuery=ref(""),scope=ref("all"),scopeQuery=ref("");
const memberDrawer=ref(false);
const treeRef=ref<{filter:(value:string)=>void}>();
const selectedMember=computed(()=>members.value.find(member=>member.memberId===memberId.value));
const filteredUnits=computed(()=>units.value.filter(unit=>unit.name.toLocaleLowerCase().includes(submittedQuery.value.toLocaleLowerCase())));
const filteredMembers=computed(()=>members.value.filter(member=>(scope.value==='all'||(scope.value==='unassigned'?!member.organizationId:member.organizationId===scope.value))&&member.displayName.toLocaleLowerCase().includes(submittedQuery.value.toLocaleLowerCase())));
const scopeNodes=computed(()=>[{id:'all',label:t('management.allOrganizations'),children:[...units.value.map(unit=>({id:unit.id,label:unit.name})),{id:'unassigned',label:t('organization.unassigned')}]}]);
const unitColumns=computed<ManagementColumn[]>(()=>[
 {key:'name',prop:'name',label:t('organization.name'),minWidth:200},
 ...(can('UPDATE')||can('DELETE')?[{key:'operations',label:t('tenant.operation'),width:180,fixed:'right' as const}]:[])
]);
const memberColumns=computed<ManagementColumn[]>(()=>[
 {key:'displayName',prop:'displayName',label:t('administration.displayName'),minWidth:160},
 {key:'organization',label:t('organization.primaryOrganization'),minWidth:180},
 {key:'operations',label:t('tenant.operation'),width:160,fixed:'right'}
]);
const grantColumns=computed<ManagementColumn[]>(()=>[
 {key:'capability',label:t('organization.capability'),minWidth:160},
 {key:'expiry',label:t('organization.expiresAt'),minWidth:180},
 {key:'operations',label:t('tenant.operation'),width:160,fixed:'right'}
]);
watch(scopeQuery,value=>treeRef.value?.filter(value));
/** 树只表达服务端返回的平面组织，不制造父子关系；根节点表示查询全部组织范围。 */
function filterScope(value:string,node:{label:string}){return node.label.toLocaleLowerCase().includes(value.toLocaleLowerCase());}
/** 查询只过滤服务端已授权返回的完整集合，不伪造分页或新接口。 */
function search(reset=false){if(reset)query.value='';submittedQuery.value=query.value;}
function changeTab(value:string){if(saving.value)return;tab.value=value;query.value='';submittedQuery.value='';}
/** 授权抽屉固定当前成员；保存期间禁止切换和关闭，防止请求作用对象变化。 */
function manageMember(member:OrganizationMember){memberId.value=member.memberId;capability.value='READ';expiresAt.value=null;memberDrawer.value=true;void loadGrants();}
function clearMember(){memberId.value='';grants.value=[];expiresAt.value=null;grantRequest++;}

/** 每次刷新重验权限；权限移除时清理旧数据，失败保留错误和重试入口。 */
async function load() {
  loading.value = true; error.value = "";
  try {
    capabilities.value = await props.api.capabilities();
    if(!can('READ')&&can('GRANT'))tab.value='members';
    if (!can("READ")) units.value = [];
    if (!can("GRANT")) { members.value = []; grants.value = []; memberId.value = ""; memberDrawer.value=false;tab.value='units';grantRequest++; }
    if (can("READ")) units.value = await props.api.units();
    if(scope.value!=='all'&&scope.value!=='unassigned'&&!units.value.some(unit=>unit.id===scope.value))scope.value='all';
    if (can("GRANT")) {
      members.value = await props.api.members();
      if (!members.value.some(member => member.memberId === memberId.value)) {memberId.value = "";memberDrawer.value=false;}
      await loadGrants();
    }
  } catch (cause) { error.value = props.errorText(cause); }
  finally { loading.value = false; }
}
/** 打开简单组织名称表单，不新建自定义弹层。 */
function edit(unit?: OrganizationUnit) {
  editingId.value = unit?.id ?? ""; name.value = unit?.name ?? ""; dialog.value = true;
}
/** 写入成功才显示反馈；刷新失败仍展示独立错误，避免误报写入失败而重复提交。 */
async function write(action: () => Promise<unknown>, refresh: () => Promise<void>) {
  saving.value = true; error.value = "";
  try { await action(); ElMessage.success(t('organization.saved')); await refresh(); }
  catch (cause) { error.value = props.errorText(cause); }
  finally { saving.value = false; }
}
/** el-form 验证后的名称与后端 1–100 UTF-16 字符契约一致。 */
async function save(form: { validate: () => Promise<boolean> } | undefined) {
  if (!form || !await form.validate().catch(() => false)) return;
  await write(async () => {
    if (editingId.value) await props.api.rename(editingId.value, name.value);
    else await props.api.create(name.value);
    dialog.value = false;
  }, load);
}
const formRef = ref<{ validate: () => Promise<boolean> }>();
let grantRequest = 0;
let displayedGrantMember = "";
/** 请求序号阻止快速切换成员时旧授权列表覆盖新成员，保存期间禁止切换。 */
async function loadGrants() {
  const request = ++grantRequest, target = memberId.value;
  organizationId.value=members.value.find(member=>member.memberId===target)?.organizationId??null;
  if(displayedGrantMember!==target){grants.value=[];displayedGrantMember=target;}
  error.value = "";
  if (!target) { grantLoading.value = false; return; }
  grantLoading.value = true;
  try { const result = await props.api.grants(target); if (request === grantRequest) grants.value = result; }
  catch (cause) { if (request === grantRequest) error.value = props.errorText(cause); }
  finally { if (request === grantRequest) grantLoading.value = false; }
}
/** 到期时间以 UTC 时间点提交；前端禁用过去时间，服务端为最终校验权威。 */
async function grant() {
  if (!memberId.value || (expiresAt.value && expiresAt.value.getTime() <= Date.now())) return;
  await write(() => props.api.grant(memberId.value, capability.value, expiresAt.value?.toISOString() ?? null), load);
}
/** 标准语言格式展示时间点，永久授权使用明确文案。 */
function expiry(value: string | null) { return value ? new Date(value).toLocaleString(locale.value) : t("organization.permanent"); }
onMounted(load);
</script>

<template>
  <div class="management-page organization-management">
    <el-alert v-if="error && !dialog && !memberDrawer" :title="error" type="error" show-icon :closable="false"><el-button link type="primary" :disabled="loading || saving" @click="load">{{t('account.retry')}}</el-button></el-alert>
    <el-card v-if="loading && !capabilities.length" v-loading="loading" shadow="never"><el-skeleton :rows="4" /></el-card>
    <el-card v-if="!loading && !capabilities.length" shadow="never"><el-empty :description="t('account.forbidden')" /><el-button @click="load">{{t('account.retry')}}</el-button></el-card>
    <template v-if="capabilities.length">
      <el-card class="management-tabs" shadow="never"><el-tabs :model-value="tab" @tab-change="value=>changeTab(String(value))"><el-tab-pane :label="t('management.units')" name="units" :disabled="saving || !can('READ') && !can('CREATE')" /><el-tab-pane v-if="can('GRANT')" :label="t('management.membersAndGrants')" name="members" :disabled="saving" /></el-tabs></el-card>
      <div :class="tab==='members' ? 'management-split' : ''">
        <el-card v-if="tab==='members'" shadow="never">
          <h2 class="scope-title">{{t('management.organizationScope')}}</h2>
          <el-input v-model="scopeQuery" class="management-scope-search" clearable :placeholder="t('management.searchOrganization')" :aria-label="t('management.searchOrganization')" :maxlength="100" />
          <el-tree ref="treeRef" :data="scopeNodes" node-key="id" :current-node-key="scope" :filter-node-method="filterScope" highlight-current default-expand-all :expand-on-click-node="false" @node-click="node=>scope=node.id" />
        </el-card>
        <div class="management-page">
          <el-card class="management-filter" shadow="never"><el-form inline @submit.prevent="search()">
            <el-form-item :label="t(tab==='units'?'organization.name':'administration.displayName')"><el-input v-model="query" clearable :placeholder="t('management.keyword')" :maxlength="100" /></el-form-item>
            <div class="management-filter-actions"><el-button type="primary" native-type="submit" :disabled="loading"><AppIcon name="search" :size="16" class="mr-1" />{{t('administration.search')}}</el-button><el-button :disabled="loading" @click="search(true)"><AppIcon name="refresh" :size="16" class="mr-1" />{{t('management.reset')}}</el-button></div>
          </el-form></el-card>
          <ManagementTable v-if="tab==='units'" :title="t('management.units')" :columns="unitColumns" :rows="filteredUnits" :loading="loading" :busy="saving" :empty-text="t(submittedQuery?'management.noResults':'organization.empty')" @refresh="load">
            <template #actions><el-button v-if="can('CREATE')" type="primary" :disabled="loading || saving" @click="edit()"><AppIcon name="add" :size="16" class="mr-1" />{{t('organization.create')}}</el-button></template>
            <template #operations="{row}">
              <el-button v-if="can('UPDATE')" link type="primary" :disabled="loading || saving" @click="edit(row)">{{t('organization.rename')}}</el-button>
              <el-popconfirm v-if="can('DELETE')" :title="t('organization.confirmDelete',{name:row.name})" :confirm-button-text="t('organization.delete')" :cancel-button-text="t('organization.cancel')" @confirm="write(()=>api.remove(row.id),load)"><template #reference><el-button link type="danger" :disabled="loading || saving">{{t('organization.delete')}}</el-button></template></el-popconfirm>
            </template>
          </ManagementTable>
          <ManagementTable v-else :title="t('management.membersAndGrants')" :columns="memberColumns" :rows="filteredMembers" :loading="loading" :busy="saving" :empty-text="t('management.noResults')" @refresh="load">
            <template #organization="{row}">{{row.organizationName || t('organization.unassigned')}}</template>
            <template #operations="{row}"><el-button link type="primary" :disabled="loading || saving" @click="manageMember(row)">{{t('management.manageGrants')}}</el-button></template>
          </ManagementTable>
        </div>
      </div>
    </template>
    <el-dialog v-model="dialog" :title="t(editingId?'organization.rename':'organization.create')" width="min(480px, calc(100vw - 32px))" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-4" />
      <el-form ref="formRef" :model="{name}" label-position="top" @submit.prevent="save(formRef)">
        <el-form-item prop="name" :label="t('organization.name')" :rules="[{required:true,whitespace:true,max:100,message:t('organization.invalidName'),trigger:'blur'}]"><el-input v-model="name" :maxlength="100" :disabled="saving" /></el-form-item>
        <div class="management-form-actions"><el-button :disabled="saving" @click="dialog=false">{{t('organization.cancel')}}</el-button><el-button native-type="submit" type="primary" :loading="saving">{{t('organization.save')}}</el-button></div>
      </el-form>
    </el-dialog>
    <el-drawer v-model="memberDrawer" class="organization-member-drawer" :title="t('management.memberSettings',{name:selectedMember?.displayName || ''})" size="min(760px, 100vw)" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close @closed="clearMember">
      <template v-if="memberId && can('GRANT')">
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-4"><el-button link type="primary" :disabled="loading || saving" @click="loadGrants">{{t('account.retry')}}</el-button></el-alert>
        <p class="management-note">{{t('organization.grantHint')}}</p>
        <section v-if="can('READ')" class="assignment-section">
          <h2>{{t('organization.primaryOrganization')}}</h2>
          <el-form label-position="top" @submit.prevent="write(()=>api.assignOrganization(memberId,organizationId||null),load)"><el-form-item :label="t('organization.primaryOrganization')"><el-select v-model="organizationId" clearable :disabled="saving || loading || grantLoading" :placeholder="t('organization.unassigned')" @clear="organizationId=null"><el-option v-for="unit in units" :key="unit.id" :label="unit.name" :value="unit.id" /></el-select></el-form-item><div class="management-form-actions"><el-button native-type="submit" :loading="saving" :disabled="loading || grantLoading">{{t('organization.saveAssignment')}}</el-button></div></el-form>
        </section>
        <section class="assignment-section">
          <h2>{{t('organization.grant')}}</h2>
          <el-form label-position="top" @submit.prevent="grant">
            <div class="grant-fields"><el-form-item :label="t('organization.capability')"><el-select v-model="capability" :disabled="saving || loading || grantLoading"><el-option v-for="action in operations" :key="action" :label="t('organization.'+action)" :value="action" /></el-select></el-form-item><el-form-item :label="t('organization.expiresAt')" :error="expiresAt && expiresAt.getTime()<=Date.now()?t('organization.invalidExpiry'):undefined"><el-date-picker v-model="expiresAt" type="datetime" :disabled="saving || loading || grantLoading" :placeholder="t('organization.permanent')" /></el-form-item></div>
            <div class="management-form-actions"><el-button type="primary" native-type="submit" :loading="saving" :disabled="loading || grantLoading || Boolean(expiresAt && expiresAt.getTime()<=Date.now())">{{t('organization.grant')}}</el-button></div>
          </el-form>
        </section>
        <ManagementTable heading-level="h2" :title="t('management.currentGrants')" :columns="grantColumns" :rows="grants" :loading="grantLoading || loading" :busy="saving" :empty-text="t('organization.noGrants')" @refresh="loadGrants">
          <template #capability="{row}">{{t('organization.'+row.capability)}}</template>
          <template #expiry="{row}">{{expiry(row.expiresAt)}}</template>
          <template #operations="{row}"><el-popconfirm v-if="operations.includes(row.capability)" :title="t('organization.confirmRevoke')" :confirm-button-text="t('organization.revoke')" :cancel-button-text="t('organization.cancel')" @confirm="write(()=>api.revoke(memberId,row.capability),load)"><template #reference><el-button link type="danger" :disabled="saving || loading || grantLoading">{{t('organization.revoke')}}</el-button></template></el-popconfirm><span v-else>—</span></template>
        </ManagementTable>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.scope-title { margin: 0 0 16px; font-size: var(--el-font-size-base); font-weight: 600; }
.assignment-section { border-bottom: 1px solid var(--el-border-color-lighter); margin-bottom: 24px; padding-bottom: 24px; }
.assignment-section h2 { font-size: var(--el-font-size-medium); margin: 0 0 16px; }
.grant-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.grant-fields :deep(.el-date-editor) { width: 100%; }
.organization-management :deep(.el-tree-node__label) { overflow: hidden; text-overflow: ellipsis; }
@media(max-width:600px) { .grant-fields { grid-template-columns: 1fr; gap: 0; } }
</style>
