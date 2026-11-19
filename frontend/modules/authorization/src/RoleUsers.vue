<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage, ElMessageBox } from "element-plus";
import { ManagementTable, AppPagination, setErrorFeedback, type ManagementColumn } from "@appnexus/ui";
import type { AuthorizationSubject, SubjectAuthorizationApi } from "./index";

/** 当前角色的用户加入及移除入口；复用主体快照接口，不覆盖用户已有的其他角色。 */
const props=defineProps<{roleId:string;roleName:string;active:boolean;api:SubjectAuthorizationApi;errorText:(error:unknown)=>string}>();
const {t}=useI18n();
const query=ref(""),submittedQuery=ref(""),page=ref(1),memberPage=ref(1),loading=ref(false),saving=ref(false);
const rows=ref<(AuthorizationSubject & {assigned:boolean})[]>([]);
const filteredRows=computed(()=>{const keyword=submittedQuery.value.toLowerCase();return rows.value.filter(row=>[row.name,row.detail??""].some(value=>value.toLowerCase().includes(keyword)));});
const total=computed(()=>filteredRows.value.length);
const visibleRows=computed(()=>filteredRows.value.slice((page.value-1)*20,page.value*20));
/** 已加入列表基于完整授权目录，不受上方用户检索及分页影响。 */
const members=computed(()=>rows.value.filter(row=>row.assigned));
const visibleMembers=computed(()=>members.value.slice((memberPage.value-1)*20,memberPage.value*20));
let requestVersion=0;
const columns=computed<ManagementColumn[]>(()=>[
 {key:"name",prop:"name",label:t("authorizationCenter.subject"),minWidth:160},
 {key:"detail",prop:"detail",label:t("authorizationCenter.account"),minWidth:140},
 {key:"operations",label:t("tenant.operation"),minWidth:160}
]);
/**
 * 分页读取完整受控目录并按每批最多 20 人读取角色快照，保证下方已加入清单不遗漏其他页用户。
 * 任一读取失败不展示不完整总数；后到响应不能覆盖新角色，不扩大目录读取权限。
 */
async function load(){
 const request=++requestVersion;
 if(!props.roleId){rows.value=[];loading.value=false;return;}
 loading.value=true;
 try{
  const role=props.roleId,items:(AuthorizationSubject & {assigned:boolean})[]=[];
  for(let current=1;;current++){
   const result=await props.api.subjects(current,"");
   if(request!==requestVersion)return;
   if(!result.items.length&&items.length<result.total)throw new Error(t("authorizationCenter.roleUserListChanged"));
   const batch=await Promise.all(result.items.map(async item=>({...item,assigned:(await props.api.assignment(item.id)).roleIds.includes(role)})));
   if(request!==requestVersion)return;
   items.push(...batch);
   if(items.length>=result.total)break;
  }
  rows.value=items;
  page.value=Math.min(page.value,Math.max(1,Math.ceil(total.value/20)));
  memberPage.value=Math.min(memberPage.value,Math.max(1,Math.ceil(members.value.length/20)));
 }catch(cause){if(request===requestVersion){rows.value=[];await setErrorFeedback(ref(""),props.errorText(cause));}}
 finally{if(request===requestVersion)loading.value=false;}
}
/**
 * 加入或移除前重新读取全部角色和并发版本，仅变更当前角色；冲突不自动重放写入。
 * @param row 当前管理域目录中的用户；停用用户允许撤销已有角色，但不允许加入。
 * @param assigned true 表示加入，false 表示移除；移除会撤销当前角色贡献的权限，其他角色保持不变。
 */
async function changeMembership(row:AuthorizationSubject,assigned:boolean){
 if(saving.value||loading.value||!props.roleId||(assigned&&row.enabled===false))return;
 saving.value=true;
 const role=props.roleId;
 try{
  const result=await ElMessageBox.prompt(t(assigned?"authorizationCenter.addRoleUserConfirm":"authorizationCenter.removeRoleUserConfirm",{user:row.name,role:props.roleName}),t("authorizationCenter.roleUsers"),{
   type:assigned?"info":"warning",
   confirmButtonText:t(assigned?"authorizationCenter.addToRole":"authorizationCenter.removeFromRole"),cancelButtonText:t("organization.cancel"),
   inputPlaceholder:t("authorizationCenter.reason"),inputValidator:value=>Boolean(value?.trim())&&value.trim().length<=500||t("authorizationCenter.reasonRequired")
  }).catch(()=>null);
  if(!result)return;
  const assignment=await props.api.assignment(row.id);
  if(assignment.roleIds.includes(role)!==assigned){
   const ids=assigned?[...assignment.roleIds,role]:assignment.roleIds.filter(id=>id!==role);
   await props.api.replace(row.id,{ids,version:assignment.authorityVersion,reason:result.value.trim()});
  }
  ElMessage.success(t("authorizationCenter.saved"));
  await load();
 }catch(cause){await setErrorFeedback(ref(""),props.errorText(cause));await load();}
 finally{saving.value=false;}
}
function search(reset=false){if(reset)query.value="";submittedQuery.value=query.value.trim();page.value=1;}
watch([()=>props.roleId,()=>props.active],()=>{if(props.active){page.value=1;memberPage.value=1;void load();}},{immediate:true});
onBeforeUnmount(()=>{++requestVersion;});
/** 父级切换角色或离开路由时必须等待写入完成，防止确认中的角色上下文变化。 */
defineExpose({canLeave:async()=>!saving.value});
const saveOperation=computed(()=>location.hash.includes('/platform/')?'AccountRoleAssignment.replace':'MemberRoleAssignment.replace');
</script>
<template>
 <section class="role-users">
  <div class="role-users-context"><strong>{{ roleName || '—' }}</strong></div>
  <el-form inline class="role-users-search" @submit.prevent="search()">
   <el-form-item :label="t('authorizationCenter.subjectQuery')"><el-input v-model="query" clearable :maxlength="100" /></el-form-item>
   <el-form-item class="management-filter-actions"><el-button type="primary" native-type="submit" :disabled="loading||saving">{{t('authorizationCenter.search')}}</el-button><el-button :disabled="loading||saving" @click="search(true)">{{t('authorizationCenter.reset')}}</el-button></el-form-item>
  </el-form>
  <ManagementTable class="role-users-table" :title="t('authorizationCenter.allUsers')" :rows="visibleRows" :columns="columns" :total="total" :loading="loading" :busy="saving" embedded @refresh="load">
   <template #operations="{row}"><el-tag v-if="row.assigned" type="success">{{t('authorizationCenter.inRole')}}</el-tag><el-button v-else v-operation="saveOperation" link type="primary" :disabled="saving||loading||!roleId||row.enabled===false" @click="changeMembership(row,true)">{{t('authorizationCenter.addToRole')}}</el-button></template>
   <template #pagination><AppPagination v-model:current-page="page" :page-size="20" :total="total" :disabled="loading||saving" /></template>
  </ManagementTable>
  <ManagementTable class="role-users-table role-members-table" :title="t('authorizationCenter.currentRoleUsers')" :rows="visibleMembers" :columns="columns" :total="members.length" :loading="loading" :busy="saving" embedded @refresh="load">
   <template #operations="{row}"><el-button v-operation="saveOperation" link type="danger" :disabled="saving||loading||!roleId" @click="changeMembership(row,false)">{{t('authorizationCenter.removeFromRole')}}</el-button></template>
   <template #pagination><AppPagination v-model:current-page="memberPage" :page-size="20" :total="members.length" :disabled="loading||saving" /></template>
  </ManagementTable>
 </section>
</template>
<style scoped>
/* 沿用公共工作区和表格高度链，分页由 ManagementTable 固定在表体底部。 */
.role-users{display:flex;flex-direction:column;flex:1;min-height:0;min-width:0;gap:var(--app-section-gap,8px)}
.role-users-table{flex:1 1 0;min-height:220px}
.role-members-table{border-top:1px solid var(--el-border-color-lighter);padding-top:var(--app-content-padding,8px)}
.role-users-context{padding:var(--app-content-padding,8px)}
.role-users-search{display:flex;flex-wrap:wrap;gap:8px;padding:var(--app-content-padding,8px);background:var(--el-fill-color-lighter)}
.role-users-search .el-form-item{margin:0}
.role-users-search .management-filter-actions{margin-left:auto}
</style>
