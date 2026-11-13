<script setup lang="ts">
import {computed,onMounted,ref} from "vue";
import {ElMessage,ElMessageBox} from "element-plus";
import {useI18n} from "vue-i18n";
import type {AuthorizationCenterApi,SubjectAuthorizationApi,RoleManagementApi,AuthorizationSubject,RoleItem,MemberGrant,DataScopeType,PermissionItem,ScopeSupport,AuthorizationOption} from "./index";

/** 成员独立范围编辑复用工作台现有 Select/Form；当前主体由后端目录选择，不接收租户参数。 */
const props=defineProps<{api:SubjectAuthorizationApi;roleApi:RoleManagementApi;authorizationApi:AuthorizationCenterApi;errorText:(error:unknown)=>string}>();
const {t,locale}=useI18n();
const subjects=ref<AuthorizationSubject[]>([]),roles=ref<RoleItem[]>([]),permissions=ref<PermissionItem[]>([]),metadata=ref<ScopeSupport[]>([]),organizations=ref<AuthorizationOption[]>([]);
const member=ref(""),role=ref(""),grants=ref<MemberGrant[]>([]),draft=ref<MemberGrant>(),version=ref(0),reason=ref(""),baseline=ref<string|undefined>(undefined),error=ref(""),busy=ref(false),ready=ref(false);
const changed=computed(()=>JSON.stringify(draft.value)!==baseline.value);
/** 成员可选操作严格限定当前角色已发布上限；新增权限必须由管理员显式勾选，绝不自动复制扩权。 */
const rolePermissionIds=ref<string[]>([]);
const availablePermissions=computed(()=>permissions.value.filter(item=>rolePermissionIds.value.includes(item.id)));
/** 明确区分角色上限与成员当前授予，新增角色操作只作为可选项，不暗中扩大成员授权。 */
const permissionExplanation=computed(()=>availablePermissions.value.map(p=>({code:p.code,name:locale.value.startsWith('zh')?p.nameZh:p.nameEn,
  status:draft.value?.permissionIds.includes(p.id)?t('authorizationCenter.memberGranted'):t('authorizationCenter.memberNotGranted')})));
const scoped=computed(()=>permissions.value.filter(item=>draft.value?.permissionIds.includes(item.id)&&metadata.value.some(value=>value.permissionId===item.id)));
const invalid=computed(()=>!ready.value||!draft.value||!changed.value||!reason.value.trim()||scoped.value.some(item=>{const value=policy(item.id);return !value||((value.scopeType==="ORGANIZATION"||value.scopeType==="ORGANIZATION_SUBTREE")&&!value.organizationIds.length);}));
function policy(id:string){return draft.value?.dataPolicies.find(value=>value.permissionId===id);}
function scope(id:string,type:DataScopeType){if(!draft.value)return;const support=metadata.value.find(value=>value.permissionId===id&&value.scopeType===type);if(!support)return;draft.value.dataPolicies=draft.value.dataPolicies.filter(value=>value.permissionId!==id);draft.value.dataPolicies.push({permissionId:id,scopeType:type,resourceType:support.resourceType,organizationIds:[]});}
async function discard(){if(!changed.value&&!reason.value.trim())return true;try{await ElMessageBox.confirm(t("authorizationCenter.discard"),t("authorizationCenter.memberData"),{confirmButtonText:t("authorizationCenter.discardAction"),cancelButtonText:t("organization.cancel")});return true;}catch{return false;}}
async function selectMember(id:string){if(busy.value||!(await discard()))return;member.value=id;await loadGrants();}
async function selectRole(id:string){if(busy.value||!(await discard()))return;role.value=id;busy.value=true;try{await loadDraft();}finally{busy.value=false;}}
async function loadGrants(){if(!props.api.grants||!member.value)return;busy.value=true;ready.value=false;error.value="";try{const value=await props.api.grants.get(member.value);grants.value=value.grants;version.value=value.authorityVersion;role.value=value.grants[0]?.roleId??"";await loadDraft();}catch(cause){error.value=props.errorText(cause);}finally{busy.value=false;}}
/** 获取当前角色版本，仅用于并发与上限复核，不自动复制角色新增权限或范围。 */
async function loadDraft(){ready.value=false;draft.value=undefined;reason.value="";baseline.value=undefined;const grant=grants.value.find(value=>value.roleId===role.value);if(!grant)return;try{const current=await props.authorizationApi.assignment(role.value);rolePermissionIds.value=current.permissionIds;draft.value=JSON.parse(JSON.stringify({...grant,permissionIds:grant.permissionIds.filter(id=>current.permissionIds.includes(id)),dataPolicies:grant.dataPolicies.filter(value=>current.permissionIds.includes(value.permissionId)),roleVersion:current.roleVersion}));baseline.value=JSON.stringify(draft.value);ready.value=true;}catch(cause){error.value=props.errorText(cause);}}
/** 完整单分支更新，失败关闭写入口并保留草稿；重读必须确认丢弃修改。 */
async function save(){if(invalid.value||!draft.value||!props.api.grants||busy.value)return;busy.value=true;try{const value=await props.api.grants.replace(member.value,{grant:JSON.parse(JSON.stringify(draft.value)),version:version.value,reason:reason.value.trim()});grants.value=value.grants;version.value=value.authorityVersion;baseline.value=JSON.stringify(draft.value);reason.value="";ElMessage.success(t("authorizationCenter.saved"));}catch(cause){error.value=props.errorText(cause);ready.value=false;}finally{busy.value=false;}}
async function initialize(){busy.value=true;try{const catalog:RoleItem[]=[];for(let page=1;;page++){const value=await props.roleApi.list({query:"",page,pageSize:100});catalog.push(...value.items);if(catalog.length>=value.total)break;if(!value.items.length)throw new Error(t("authorizationCenter.roleListChanged"));}roles.value=catalog;const members:AuthorizationSubject[]=[];for(let page=1;;page++){const value=await props.api.subjects(page,"");members.push(...value.items);if(members.length>=value.total)break;if(!value.items.length)throw new Error(t("authorizationCenter.roleListChanged"));}subjects.value=members;[permissions.value,metadata.value,organizations.value]=await Promise.all([props.authorizationApi.permissions(),props.authorizationApi.metadata(),props.authorizationApi.organizationOptions?.()??Promise.resolve([])]);}catch(cause){error.value=props.errorText(cause);}finally{busy.value=false;}}
onMounted(initialize);
/** 页签切换保留组件草稿，离开整个路由由应用壳统一询问。 */
defineExpose({ canLeave:async()=>!busy.value&&await discard() });
</script>

<template>
  <div v-loading="busy" class="member-grant-editor">
    <el-alert :title="t('authorizationCenter.memberDataHint')" type="info" :closable="false" show-icon />
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link :disabled="busy" @click="async()=>{if(await discard())await loadGrants();}">{{t('authorizationCenter.reload')}}</el-button></el-alert>
    <el-form class="member-grant-form" label-position="top" @submit.prevent="save">
      <el-form-item :label="t('authorizationCenter.subject')"><el-select :model-value="member" filterable :disabled="busy" @change="selectMember"><el-option v-for="item in subjects" :key="item.id" :value="item.id" :label="item.name" /></el-select></el-form-item>
      <el-form-item v-if="member" :label="t('authorizationCenter.subjectRoles')"><el-select :model-value="role" filterable :disabled="busy" @change="selectRole"><el-option v-for="grant in grants" :key="grant.roleId" :value="grant.roleId" :label="roles.find(item=>item.id===grant.roleId)?.name??t('authorizationCenter.unavailableRole')" /></el-select></el-form-item>
      <el-empty v-if="member&&!grants.length" :description="t('authorizationCenter.assignRoleFirst')" />
      <template v-if="draft">
        <el-alert :title="t('authorizationCenter.roleCeilingHint')" type="info" :closable="false" class="member-reason" />
        <el-table :data="permissionExplanation" class="member-reason"><el-table-column prop="name" :label="t('authorizationCenter.memberOperations')"/><el-table-column prop="code" label="Code"/><el-table-column prop="status" :label="t('document.state')"/></el-table>
        <el-form-item :label="t('authorizationCenter.memberOperations')" class="member-reason"><el-checkbox-group v-model="draft.permissionIds" :disabled="!ready||busy"><el-checkbox v-for="item in availablePermissions" :key="item.id" :value="item.id">{{locale.startsWith('zh')?item.nameZh:item.nameEn}} · {{item.code}}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item class="member-scope-row" v-for="item in scoped" :key="item.id" :label="locale.startsWith('zh')?item.nameZh:item.nameEn">
          <div class="scope-inputs">
            <el-select :model-value="policy(item.id)?.scopeType" :disabled="!ready||busy" :placeholder="t('authorizationCenter.selectScope')" @change="value=>scope(item.id,value)"><el-option v-for="support in metadata.filter(value=>value.permissionId===item.id)" :key="support.scopeType" :value="support.scopeType" :label="t('authorizationCenter.scopes.'+support.scopeType)" /></el-select>
            <el-select v-if="policy(item.id)?.scopeType==='ORGANIZATION'||policy(item.id)?.scopeType==='ORGANIZATION_SUBTREE'" v-model="policy(item.id)!.organizationIds" multiple filterable :disabled="!ready||busy" :placeholder="t('authorizationCenter.selectOrganizations')"><el-option v-for="organization in organizations" :key="organization.id" :value="organization.id" :label="organization.name" /></el-select>
          </div>
        </el-form-item>
        <el-form-item :label="t('authorizationCenter.expiresAt')"><el-date-picker v-model="draft.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable :disabled="!ready||busy" /></el-form-item>
        <el-form-item class="member-reason" :label="t('authorizationCenter.reason')" required><el-input v-model="reason" type="textarea" :maxlength="500" show-word-limit :disabled="!ready||busy" /></el-form-item>
        <el-button v-operation="'@role:grant'" native-type="submit" type="primary" :loading="busy" :disabled="invalid">{{t('organization.save')}}</el-button>
      </template>
    </el-form>
  </div>
</template>
<style scoped>
/* 上方选择授权对象，下方按操作配置范围；不同任务使用轻分隔而非额外卡片。 */
.member-grant-editor{padding:20px;min-width:0}
.member-grant-editor>.el-alert{margin-bottom:20px}
.member-grant-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 24px;align-items:start}
.member-grant-editor .el-select{width:100%}
.member-scope-row,.member-reason,.member-grant-form>.el-empty{grid-column:1/-1}
.member-scope-row{padding:16px;background:var(--el-fill-color-lighter);border-radius:var(--el-border-radius-base)}
.scope-inputs{display:grid;grid-template-columns:minmax(160px,1fr) minmax(0,2fr);gap:16px;width:100%}
.member-grant-form>.el-button{justify-self:end;min-width:120px;grid-column:1/-1}
@media(max-width:700px){.member-grant-editor{padding:16px}.member-grant-form,.scope-inputs{grid-template-columns:1fr}.member-grant-form>.el-button{width:100%;min-height:44px}}
</style>
