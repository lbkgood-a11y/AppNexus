<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import { AppIcon } from "@appnexus/icons";
import { ManagementTable, type ManagementColumn } from "@appnexus/ui";
import type { AdministrationApi, DirectoryUser, DirectoryTenant, DirectoryMember } from "./administration";

/** 平台开通流程组合 Element Plus，不构建新的表格、表单或弹层系统。 */
const props=defineProps<{ api: AdministrationApi; errorText: (error: unknown) => string }>();
const emit=defineEmits<{ enter: [tenantId: string] }>();
const {t}=useI18n();
const allowed=ref(false),loading=ref(false),saving=ref(false),error=ref("");
const tab=ref("tenants"),tenant=ref<DirectoryTenant>(),users=ref<DirectoryUser[]>([]),tenants=ref<DirectoryTenant[]>([]),members=ref<DirectoryMember[]>([]);
const page=ref(1),total=ref(0),query=ref("");
const submittedQuery=ref("");
const tableTitle=computed(()=>tab.value==='members'?t('administration.membersOf',{name:tenant.value?.name}):t('administration.'+tab.value));
const columns=computed<ManagementColumn[]>(()=>[
 ...(tab.value!=='tenants'?[{key:'username',prop:'username',label:t('administration.username'),minWidth:150}]:[]),
 {key:'name',prop:tab.value==='tenants'?'name':'displayName',label:t(tab.value==='tenants'?'tenant.name':'administration.displayName'),minWidth:160},
 {key:'status',label:t('administration.status'),width:100},
 {key:'operations',label:t('tenant.operation'),minWidth:tab.value==='tenants'?320:140,fixed:'right'}
]);
const dialog=ref<"user"|"tenant"|"member"|"">("");
const form=reactive({username:"",displayName:"",password:"",name:"",accountId:""});
const formRef=ref<{validate:()=>Promise<boolean>;clearValidate:()=>void}>();
const options=ref<DirectoryUser[]>([]),searching=ref(false),searchError=ref("");
const rows=computed<(DirectoryUser|DirectoryTenant|DirectoryMember)[]>(()=>tab.value==='users'?users.value:tab.value==='members'?members.value:tenants.value);
let request=0,searchRequest=0,creationId="";

/** 分页请求序号阻止快速切换列表后旧请求覆盖；旧内容刷新失败仍保留。 */
async function load() {
 const current=++request;loading.value=true;error.value="";
 try {
  const access=await props.api.access();if(current!==request)return;allowed.value=access.administrator;
  if(!allowed.value) {users.value=[];tenants.value=[];members.value=[];return;}
  const result=tab.value==="users"?await props.api.users(page.value,submittedQuery.value):tab.value==="members"&&tenant.value?await props.api.members(tenant.value.id,page.value,submittedQuery.value):await props.api.tenants(page.value,submittedQuery.value);
  if(current!==request)return;
  total.value=result.total;
  // 状态写入可能改变当前页数据；回到真实存在的最后一页，禁止展示超出总数的空分页。
  if(page.value>1&&result.items.length===0){page.value=Math.max(1,Math.ceil(result.total/20));await load();return;}
  if(tab.value==="users")users.value=result.items as DirectoryUser[];
  else if(tab.value==="members")members.value=result.items as DirectoryMember[];
  else tenants.value=result.items as DirectoryTenant[];
 }catch(cause){if(current===request)error.value=props.errorText(cause);}
 finally{if(current===request)loading.value=false;}
}
/** 切换实体列表时重置筛选与页码，不把租户列表筛选误用到成员目录。 */
function changeTab(value: string) {if(saving.value)return;tab.value=value;query.value="";submittedQuery.value="";page.value=1;void load();}
/** 显式查询才提交输入；刷新和翻页沿用已提交条件，重置不会离开当前租户成员上下文。 */
function search(reset=false){if(reset)query.value="";submittedQuery.value=query.value;page.value=1;void load();}
function manageMembers(row: DirectoryTenant) {tenant.value=row;changeTab("members");}
/** 账号远程搜索仅请求一页，输入登录名或显示名称，不下载全平台凭据或无界账号目录。 */
async function searchUsers(value="") {
 const current=++searchRequest;searching.value=true;searchError.value="";
 try {const result=await props.api.users(1,value);if(current===searchRequest)options.value=result.items.filter(user=>user.enabled);}
 catch(cause){if(current===searchRequest)searchError.value=props.errorText(cause);}
 finally{if(current===searchRequest)searching.value=false;}
}
/** 密码只存在表单内存，关闭弹窗或提交成功即清除。 */
function open(kind: "user"|"tenant"|"member") {
 Object.assign(form,{username:"",displayName:"",password:"",name:"",accountId:""});
 creationId=crypto.randomUUID();dialog.value=kind;searchError.value="";
 if(kind!=="user")void searchUsers();
}
function clearPassword(){form.password="";}
async function write(action:()=>Promise<unknown>) {
 saving.value=true;error.value="";
 try {await action();ElMessage.success(t('organization.saved'));await load();}
 catch(cause){error.value=props.errorText(cause);}
 finally{saving.value=false;}
}
/** 创建租户重试沿用同一 requestId；用户/成员唯一约束阻止重复创建。 */
async function save() {
 if(!await formRef.value?.validate().catch(()=>false))return;
 await write(async()=>{
  if(dialog.value==="user")await props.api.createUser(form.username,form.displayName,form.password);
  else if(dialog.value==="tenant")await props.api.createTenant(creationId,form.name,form.accountId);
  else if(tenant.value)await props.api.addMember(tenant.value.id,form.accountId);
  dialog.value="";clearPassword();
 });
}
/** 停用使用明确状态提交，失败不乐观篡改列表；恢复成员不恢复旧授权。 */
async function state(row: DirectoryUser|DirectoryTenant|DirectoryMember) {
 await write(()=>tab.value==="users"?props.api.userState((row as DirectoryUser).id,!row.enabled):tab.value==="tenants"?props.api.tenantState((row as DirectoryTenant).id,!row.enabled):props.api.memberState(tenant.value!.id,(row as DirectoryMember).memberId,!row.enabled));
}
const nameRules=[{required:true,whitespace:true,max:100,message:t("administration.invalidName"),trigger:"blur"}];
onMounted(load);
</script>
<template>
 <div class="management-page platform-administration">
  <el-alert v-if="error && !dialog" type="error" :title="error" :closable="false" show-icon><el-button link type="primary" :disabled="loading||saving" @click="load">{{t('account.retry')}}</el-button></el-alert>
  <el-card v-if="!allowed" shadow="never" v-loading="loading"><el-empty v-if="!loading&&!error" :description="t('account.forbidden')" /><el-button :disabled="loading" @click="load">{{t('account.retry')}}</el-button></el-card>
  <template v-if="allowed">
   <el-card v-if="tab!=='members'" class="management-tabs" shadow="never"><el-tabs :model-value="tab" @tab-change="value=>changeTab(String(value))">
    <el-tab-pane :label="t('administration.tenants')" name="tenants" :disabled="saving" />
    <el-tab-pane :label="t('administration.users')" name="users" :disabled="saving" />
   </el-tabs></el-card>
   <el-card v-else shadow="never"><div class="management-context"><el-button link type="primary" :disabled="saving" @click="changeTab('tenants')">{{t('management.backTenants')}}</el-button><h2>{{tenant?.name}}</h2><el-tag :type="tenant?.enabled?'success':'info'">{{t(tenant?.enabled?'administration.enabled':'administration.disabled')}}</el-tag></div></el-card>
   <el-card class="management-filter" shadow="never"><el-form inline @submit.prevent="search()">
    <el-form-item :label="t(tab==='tenants'?'tenant.name':'management.accountQuery')"><el-input v-model="query" clearable :maxlength="100" :placeholder="t('management.keyword')" /></el-form-item>
    <div class="management-filter-actions"><el-button type="primary" native-type="submit" :disabled="loading||saving"><AppIcon name="search" :size="16" class="mr-1" />{{t('administration.search')}}</el-button><el-button :disabled="loading||saving" @click="search(true)"><AppIcon name="refresh" :size="16" class="mr-1" />{{t('management.reset')}}</el-button></div>
   </el-form></el-card>
   <ManagementTable :key="tab" :title="tableTitle" :columns="columns" :rows="rows" :loading="loading" :busy="saving" :empty-text="t('administration.empty')" @refresh="load">
    <template #actions>
    <el-button type="primary" :disabled="loading||saving||(tab==='members'&&!tenant?.enabled)" @click="open(tab==='users'?'user':tab==='members'?'member':'tenant')">{{t(tab==='users'?'administration.createUser':tab==='members'?'administration.addMember':'administration.createTenant')}}</el-button>
    </template>
    <template #before><p v-if="tab==='members'" class="management-note">{{t('administration.memberHint')}}</p></template>
    <template #status="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'administration.enabled':'administration.disabled')}}</el-tag></template>
    <template #operations="{row}">
     <el-button v-if="tab==='tenants'" link type="primary" :disabled="loading||saving" @click="manageMembers(row as DirectoryTenant)">{{t('administration.members')}}</el-button>
     <el-button v-if="tab==='tenants'&&row.enabled" link type="primary" :disabled="loading||saving" @click="emit('enter',(row as DirectoryTenant).id)">{{t('administration.enterOrganization')}}</el-button>
     <el-popconfirm :title="t(row.enabled?'administration.confirmDisable':'administration.confirmEnable')" :confirm-button-text="t(row.enabled?'administration.disable':'administration.enable')" :cancel-button-text="t('organization.cancel')" @confirm="state(row as DirectoryUser)">
      <template #reference><el-button link :type="row.enabled?'danger':'primary'" :disabled="loading||saving">{{t(row.enabled?'administration.disable':'administration.enable')}}</el-button></template>
     </el-popconfirm>
    </template>
    <template #pagination><el-pagination layout="total, prev, pager, next" :page-size="20" :current-page="page" :total="total" :disabled="loading||saving" @current-change="value=>{page=value;load()}" /></template>
   </ManagementTable>
  </template>
  <el-dialog :model-value="Boolean(dialog)" :title="t(dialog==='user'?'administration.createUser':dialog==='member'?'administration.addMember':'administration.createTenant')" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close @update:model-value="value=>{if(!value)dialog=''}" @closed="clearPassword">
   <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
   <el-form ref="formRef" :model="form" label-position="top" @submit.prevent="save">
    <template v-if="dialog==='user'">
     <el-form-item prop="username" :label="t('administration.username')" :rules="[{required:true,pattern:/^[A-Za-z0-9][A-Za-z0-9._@-]{0,99}$/,message:t('administration.invalidUsername'),trigger:'blur'}]"><el-input v-model="form.username" :disabled="saving" :maxlength="100" autocomplete="off" /></el-form-item>
     <el-form-item prop="displayName" :label="t('administration.displayName')" :rules="nameRules"><el-input v-model="form.displayName" :disabled="saving" :maxlength="100" /></el-form-item>
     <el-form-item prop="password" :label="t('administration.password')" :rules="[{required:true,min:12,max:128,message:t('administration.invalidPassword'),trigger:'blur'}]"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" :disabled="saving" :maxlength="128" /></el-form-item>
    </template>
    <el-form-item v-if="dialog==='tenant'" prop="name" :label="t('tenant.name')" :rules="nameRules"><el-input v-model="form.name" :disabled="saving" :maxlength="100" /></el-form-item>
    <el-form-item v-if="dialog!=='user'" prop="accountId" :label="t(dialog==='tenant'?'administration.firstAdministrator':'administration.account')" :rules="[{required:true,message:t('administration.chooseAccount'),trigger:'change'}]">
     <el-select v-model="form.accountId" filterable remote :remote-method="searchUsers" :loading="searching" :disabled="saving" :placeholder="t('administration.chooseAccount')"><el-option v-for="user in options" :key="user.id" :value="user.id" :label="user.displayName+' ('+user.username+')'" /></el-select>
    </el-form-item>
    <el-alert v-if="searchError" :title="searchError" type="error" :closable="false" />
    <p v-if="dialog==='tenant'" class="mb-4">{{t('administration.firstAdministratorHint')}}</p>
    <div class="flex justify-end gap-2"><el-button :disabled="saving" @click="dialog=''">{{t('organization.cancel')}}</el-button><el-button type="primary" native-type="submit" :loading="saving">{{t('organization.save')}}</el-button></div>
   </el-form>
  </el-dialog>
 </div>
</template>
<style scoped>
/* 小屏保留完整管理动作，表格在自身容器滚动，按钮命中区域不小于 44px。 */
@media(max-width:600px){.platform-administration :deep(.el-button){min-height:44px;}}
</style>
