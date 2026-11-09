<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import { AppIcon } from "@appnexus/icons";
import { BusinessWorkspace, ManagedDialog, ManagementTable, type ManagementColumn } from "@appnexus/ui";
import type { AdministrationApi, DirectoryUser, DirectoryTenant, DirectoryMember } from "./administration";

/** 平台开通流程组合 Element Plus，不构建新的表格、表单或弹层系统。 */
const props=defineProps<{
 api: AdministrationApi;
 errorText: (error: unknown) => string;
 /** 管理入口决定平台级安全对象；用户与租户不再通过页面内 Tab 混合切换。 */
 section: "users"|"tenants";
}>();
const emit=defineEmits<{ enter: [tenantId: string] }>();
const {t}=useI18n();
const allowed=ref(false),loading=ref(false),saving=ref(false),error=ref("");
const filterExpanded=ref(true);
const tab=ref<"users"|"tenants"|"members">(props.section),tenant=ref<DirectoryTenant>(),users=ref<DirectoryUser[]>([]),tenants=ref<DirectoryTenant[]>([]),members=ref<DirectoryMember[]>([]);
const page=ref(1),total=ref(0),query=ref("");
const submittedQuery=ref("");
const pageTitle=computed(()=>t(tab.value==='users'?'administration.userTitle':'administration.tenantTitle'));
const pageDescription=computed(()=>t(tab.value==='users'?'administration.userDescription':'administration.tenantDescription'));
const searchTitle=computed(()=>t(tab.value==='users'?'administration.userSearch':'administration.tenantSearch'));
const tableTitle=computed(()=>tab.value==='members'?t('administration.membersOf',{name:tenant.value?.name}):t(tab.value==='users'?'administration.userList':'administration.tenantList'));
const columns=computed<ManagementColumn[]>(()=>[
 ...(tab.value!=='tenants'?[{key:'username',prop:'username',label:t('administration.username'),minWidth:150}]:[]),
 {key:'name',prop:tab.value==='tenants'?'name':'displayName',label:t(tab.value==='tenants'?'tenant.name':'administration.displayName'),minWidth:160},
 ...(tab.value==='users'?[{key:'email',label:t('administration.email'),minWidth:220},{key:'phone',label:t('administration.phoneNumber'),minWidth:170}]:[]),
 {key:'status',label:t('administration.status'),width:100},
 {key:'operations',label:t('tenant.operation'),minWidth:tab.value==='tenants'?320:tab.value==='users'?180:140,fixed:'right'}
]);
const dialog=ref<"user"|"userEdit"|"tenant"|"tenantEdit"|"member"|"">("");
const editingUser=ref<DirectoryUser>();
const editingTenant=ref<DirectoryTenant>();
const form=reactive({username:"",displayName:"",email:"",phoneNumber:"",password:"",name:"",accountId:""});
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
/** 进入或退出成员上下文时重置筛选，不把租户列表条件误用到成员目录。 */
function changeTab(value: "tenants"|"members") {if(saving.value)return;tab.value=value;query.value="";submittedQuery.value="";page.value=1;void load();}
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
 editingUser.value=undefined;editingTenant.value=undefined;Object.assign(form,{username:"",displayName:"",email:"",phoneNumber:"",password:"",name:"",accountId:""});
 creationId=crypto.randomUUID();dialog.value=kind;searchError.value="";
 if(kind!=="user")void searchUsers();
}
/** 编辑只维护资料，登录名作为现有审计识别符不在此处变更。 */
function editUser(user: DirectoryUser) {
 editingUser.value=user;
 Object.assign(form,{username:user.username,displayName:user.displayName,email:user.restrictedFields?.includes("email")?"":user.email??"",phoneNumber:user.restrictedFields?.includes("phoneNumber")?"":user.phoneNumber??"",password:"",name:"",accountId:""});
 dialog.value="userEdit";
}
/** 租户编辑只维护展示名称，不混入状态、成员或授权变更。 */
function editTenant(row: DirectoryTenant) {
 editingTenant.value=row;Object.assign(form,{username:"",displayName:"",email:"",phoneNumber:"",password:"",name:row.name,accountId:""});
 dialog.value="tenantEdit";
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
  if(dialog.value==="user")await props.api.createUser(form.username,form.displayName,form.email,form.phoneNumber,form.password);
  else if(dialog.value==="userEdit"&&editingUser.value)await props.api.updateUser(editingUser.value.id,form.displayName,form.email,form.phoneNumber);
  else if(dialog.value==="tenant")await props.api.createTenant(creationId,form.name,form.accountId);
  else if(dialog.value==="tenantEdit"&&editingTenant.value)await props.api.updateTenant(editingTenant.value.id,form.name);
  else if(tenant.value)await props.api.addMember(tenant.value.id,form.accountId);
  dialog.value="";clearPassword();
 });
}
/** 停用使用明确状态提交，失败不乐观篡改列表；恢复成员不恢复旧授权。 */
async function state(row: DirectoryUser|DirectoryTenant|DirectoryMember) {
 await write(()=>tab.value==="users"?props.api.userState((row as DirectoryUser).id,!row.enabled):tab.value==="tenants"?props.api.tenantState((row as DirectoryTenant).id,!row.enabled):props.api.memberState(tenant.value!.id,(row as DirectoryMember).memberId,!row.enabled));
}
async function removeMember(row: DirectoryMember) {
 if(!tenant.value)return;
 await write(()=>props.api.removeMember(tenant.value!.id,row.memberId));
}
const nameRules=[{required:true,whitespace:true,max:100,message:t("administration.invalidName"),trigger:"blur"}];
onMounted(load);
</script>
<template>
 <div class="management-page platform-administration">
  <el-card v-if="!allowed" shadow="never" v-loading="loading"><el-empty v-if="!loading&&!error" :description="t('account.forbidden')" /><el-button :disabled="loading" @click="load">{{t('account.retry')}}</el-button></el-card>
  <template v-if="allowed">
   <BusinessWorkspace :title="pageTitle" :description="pageDescription">
    <template v-if="tab==='members'" #context><div class="management-context"><el-button link type="primary" :disabled="saving" @click="changeTab('tenants')">{{t('management.backTenants')}}</el-button><strong>{{tenant?.name}}</strong><el-tag :type="tenant?.enabled?'success':'info'">{{t(tenant?.enabled?'administration.enabled':'administration.disabled')}}</el-tag></div></template>
    <template v-if="error && !dialog" #feedback><el-alert type="error" :title="error" :closable="false" show-icon><el-button link type="primary" :disabled="loading||saving" @click="load">{{t('account.retry')}}</el-button></el-alert></template>
    <template #search><div class="management-filter">
    <div class="management-filter-header">
     <h2 class="management-filter-title">{{searchTitle}}</h2>
     <el-button text class="management-filter-toggle" :aria-expanded="filterExpanded" :aria-label="t(filterExpanded?'administration.collapseFilter':'administration.expandFilter')" @click="filterExpanded=!filterExpanded">
      <AppIcon name="arrowDown" :size="18" :class="{ 'filter-toggle-icon--collapsed': !filterExpanded }" />
     </el-button>
    </div>
    <el-collapse-transition><el-form v-show="filterExpanded" inline @submit.prevent="search()">
     <el-form-item :label="t(tab==='tenants'?'tenant.name':'management.accountQuery')"><el-input v-model="query" clearable :maxlength="100" :placeholder="t('management.keyword')" /></el-form-item>
     <div class="management-filter-actions"><el-button type="primary" native-type="submit" :disabled="loading||saving"><AppIcon name="search" :size="16" class="mr-1" />{{t('administration.search')}}</el-button><el-button :disabled="loading||saving" @click="search(true)"><AppIcon name="refresh" :size="16" class="mr-1" />{{t('management.reset')}}</el-button></div>
    </el-form></el-collapse-transition>
    </div></template>
    <template #data><ManagementTable :key="tab" embedded :title="tableTitle" :total="total" :columns="columns" :rows="rows" :loading="loading" :busy="saving" :empty-text="t('administration.empty')" @refresh="load">
    <template #actions>
    <el-button type="primary" :disabled="loading||saving||(tab==='members'&&!tenant?.enabled)" @click="open(tab==='users'?'user':tab==='members'?'member':'tenant')">{{t(tab==='users'?'administration.createUser':tab==='members'?'administration.addMember':'administration.createTenant')}}</el-button>
    </template>
    <template #before><p v-if="tab==='members'" class="management-note">{{t('administration.memberHint')}}</p></template>
    <template #status="{row}"><el-tag :type="row.enabled?'success':'info'">{{t(row.enabled?'administration.enabled':'administration.disabled')}}</el-tag></template>
    <template #email="{row}"><div class="contact-cell"><span>{{(row as DirectoryUser).email||'—'}}</span><el-tag v-if="(row as DirectoryUser).email" size="small" :type="(row as DirectoryUser).emailVerified?'success':'info'">{{t((row as DirectoryUser).emailVerified?'administration.verified':'administration.unverified')}}</el-tag></div></template>
    <template #phone="{row}"><div class="contact-cell"><span>{{(row as DirectoryUser).phoneNumber||'—'}}</span><el-tag v-if="(row as DirectoryUser).phoneNumber" size="small" :type="(row as DirectoryUser).phoneVerified?'success':'info'">{{t((row as DirectoryUser).phoneVerified?'administration.verified':'administration.unverified')}}</el-tag></div></template>
    <template #operations="{row}">
     <el-button v-if="tab==='tenants'" link type="primary" :disabled="loading||saving" @click="manageMembers(row as DirectoryTenant)">{{t('administration.members')}}</el-button>
     <el-button v-if="tab==='tenants'" link type="primary" :disabled="loading||saving" @click="editTenant(row as DirectoryTenant)">{{t('administration.editTenant')}}</el-button>
     <el-button v-if="tab==='tenants'&&row.enabled" link type="primary" :disabled="loading||saving" @click="emit('enter',(row as DirectoryTenant).id)">{{t('administration.enterOrganization')}}</el-button>
     <el-button v-if="tab==='users'" link type="primary" :disabled="loading||saving" @click="editUser(row as DirectoryUser)">{{t('administration.editUser')}}</el-button>
     <el-popconfirm v-if="tab==='members'&&!(row as DirectoryMember).removed" :title="t('administration.confirmRemoveMember')" :confirm-button-text="t('administration.removeMember')" :cancel-button-text="t('organization.cancel')" @confirm="removeMember(row as DirectoryMember)">
      <template #reference><el-button link type="danger" :disabled="loading||saving">{{t('administration.removeMember')}}</el-button></template>
     </el-popconfirm>
     <el-tag v-if="tab==='members'&&(row as DirectoryMember).removed" type="info">{{t('administration.removed')}}</el-tag>
     <el-popconfirm :title="t(row.enabled?'administration.confirmDisable':'administration.confirmEnable')" :confirm-button-text="t(row.enabled?'administration.disable':'administration.enable')" :cancel-button-text="t('organization.cancel')" @confirm="state(row as DirectoryUser)">
      <template #reference><el-button v-if="tab!=='members'||!(row as DirectoryMember).removed" link :type="row.enabled?'danger':'primary'" :disabled="loading||saving">{{t(row.enabled?'administration.disable':'administration.enable')}}</el-button></template>
     </el-popconfirm>
    </template>
    <template #pagination><el-pagination layout="total, prev, pager, next" :page-size="20" :current-page="page" :total="total" :disabled="loading||saving" @current-change="value=>{page=value;load()}" /></template>
    </ManagementTable></template>
   </BusinessWorkspace>
  </template>
  <ManagedDialog :model-value="Boolean(dialog)" :title="t(dialog==='user'?'administration.createUser':dialog==='userEdit'?'administration.editUser':dialog==='tenantEdit'?'administration.editTenant':dialog==='member'?'administration.addMember':'administration.createTenant')" width="min(560px, calc(100vw - 32px))" :busy="saving" @update:model-value="value=>{if(!value)dialog=''}" @closed="clearPassword">
   <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
   <el-form ref="formRef" :model="form" label-position="top" @submit.prevent="save">
    <template v-if="dialog==='user'||dialog==='userEdit'">
     <el-form-item prop="username" :label="t('administration.username')" :rules="dialog==='user'?[{required:true,pattern:/^[A-Za-z0-9][A-Za-z0-9._@-]{0,99}$/,message:t('administration.invalidUsername'),trigger:'blur'}]:[]"><el-input v-model="form.username" :disabled="saving||dialog==='userEdit'" :maxlength="100" autocomplete="off" /></el-form-item>
     <el-form-item prop="displayName" :label="t('administration.displayName')" :rules="nameRules"><el-input v-model="form.displayName" :disabled="saving" :maxlength="100" /></el-form-item>
     <el-form-item prop="email" :label="t('administration.email')" :rules="[{type:'email',message:t('administration.invalidEmail'),trigger:'blur'}]"><el-input v-model="form.email" type="email" :disabled="saving || !!editingUser?.restrictedFields?.includes('email')" :maxlength="254" autocomplete="off" /></el-form-item>
     <el-form-item prop="phoneNumber" :label="t('administration.phoneNumber')" :rules="[{pattern:/^(?:\+[1-9][0-9]{7,14})?$/,message:t('administration.invalidPhoneNumber'),trigger:'blur'}]"><el-input v-model="form.phoneNumber" :disabled="saving || !!editingUser?.restrictedFields?.includes('phoneNumber')" :maxlength="16" autocomplete="off" :placeholder="t('administration.phonePlaceholder')" /></el-form-item>
     <el-alert v-if="dialog==='userEdit'" :title="t('administration.contactVerificationHint')" type="info" :closable="false" class="mb-4" />
     <el-form-item v-if="dialog==='user'" prop="password" :label="t('administration.password')" :rules="[{required:true,min:12,max:128,message:t('administration.invalidPassword'),trigger:'blur'}]"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" :disabled="saving" :maxlength="128" /></el-form-item>
    </template>
    <el-form-item v-if="dialog==='tenant'||dialog==='tenantEdit'" prop="name" :label="t('tenant.name')" :rules="nameRules"><el-input v-model="form.name" :disabled="saving" :maxlength="100" /></el-form-item>
    <el-form-item v-if="dialog==='tenant'||dialog==='member'" prop="accountId" :label="t(dialog==='tenant'?'administration.firstAdministrator':'administration.account')" :rules="[{required:true,message:t('administration.chooseAccount'),trigger:'change'}]">
     <el-select v-model="form.accountId" filterable remote :remote-method="searchUsers" :loading="searching" :disabled="saving" :placeholder="t('administration.chooseAccount')"><el-option v-for="user in options" :key="user.id" :value="user.id" :label="user.displayName+' ('+user.username+')'" /></el-select>
    </el-form-item>
    <el-alert v-if="searchError" :title="searchError" type="error" :closable="false" />
    <p v-if="dialog==='tenant'" class="mb-4">{{t('administration.firstAdministratorHint')}}</p>
    <div class="flex justify-end gap-2"><el-button :disabled="saving" @click="dialog=''">{{t('organization.cancel')}}</el-button><el-button type="primary" native-type="submit" :loading="saving">{{t('organization.save')}}</el-button></div>
   </el-form>
  </ManagedDialog>
 </div>
</template>
<style scoped>
.filter-toggle-icon--collapsed { transform: rotate(-90deg); }
.management-filter-toggle :deep(svg) { transition: transform var(--el-transition-duration); }
.contact-cell { display: flex; align-items: center; gap: 8px; min-width: 0; }
.contact-cell span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 小屏保留完整管理动作，表格在自身容器滚动，按钮命中区域不小于 44px。 */
@media(max-width:600px){.platform-administration :deep(.el-button){min-height:44px;}}
</style>
