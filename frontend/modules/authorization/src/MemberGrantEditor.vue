<script setup lang="ts">
import {conditionDimensions,type DataConditionOptions} from "./index";
import { AppPagination, ManagementTable, type ManagementColumn } from "@appnexus/ui";
import { setErrorFeedback } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import { InfoHint } from "@appnexus/ui";
import {computed,onMounted,ref,watch} from "vue";
import {ElMessage,ElMessageBox} from "element-plus";
import DataGrantRuleEditor from "./DataGrantRuleEditor.vue";
import {incompleteDataPolicy,persistedScope,scopeChoices,shownScope,type ScopeChoice} from "./dataGrantDraft";
const scopeDraft=ref<Record<string,"RULES"|"RECORDS">>({});
import {useI18n} from "vue-i18n";
import type {AuthorizationCenterApi,SubjectAuthorizationApi,RoleManagementApi,AuthorizationSubject,RoleItem,MemberGrant,PermissionItem,ScopeSupport,AuthorizationOption} from "./index";

/** 成员独立范围编辑复用工作台现有 Select/Form；当前主体由后端目录选择，不接收租户参数。 */
const conditionOptions=ref<DataConditionOptions>({});
const props=defineProps<{roleId:string;roleName:string;active:boolean;api:SubjectAuthorizationApi;roleApi:RoleManagementApi;authorizationApi:AuthorizationCenterApi;errorText:(error:unknown)=>string}>();
const {t,locale}=useI18n();
const declarations=ref<import("./index").DataAccessDeclaration[]>([]);
const relatedSubjects=ref<AuthorizationSubject[]>([]);
const subjects=ref<AuthorizationSubject[]>([]),roles=ref<RoleItem[]>([]),permissions=ref<PermissionItem[]>([]),metadata=ref<ScopeSupport[]>([]),organizations=ref<AuthorizationOption[]>([]);
const member=ref(""),role=ref(""),grants=ref<MemberGrant[]>([]),draft=ref<MemberGrant>(),version=ref(0),reason=ref(""),baseline=ref<string|undefined>(undefined),error=ref(""),busy=ref(false),ready=ref(false);
/** 范围配置优先展示，角色功能只读清单放入独立页签，避免长清单挤压编辑区域。 */
/** 成员页签内的用户列表仅影响当前编辑对象，不改变外层角色导航。 */
const userQuery=ref(""),userPage=ref(1);
const matchedUsers=computed(()=>relatedSubjects.value.filter(item=>[item.name,item.detail??""].some(value=>value.toLowerCase().includes(userQuery.value.trim().toLowerCase()))));
const displayedUsers=computed(()=>matchedUsers.value.slice((userPage.value-1)*20,userPage.value*20));
watch([userQuery,relatedSubjects],()=>{userPage.value=1;});
const detailTab=ref("scope");
const operationColumns=computed<ManagementColumn[]>(()=>[
 {key:"status",prop:"status",label:t("businessObject.state"),minWidth:130},
 {key:"code",prop:"code",label:t("authorizationCenter.permissionCode"),minWidth:200},
 {key:"name",prop:"name",label:t("authorizationCenter.memberOperations"),minWidth:180}
]);
const changed=computed(()=>JSON.stringify(draft.value)!==baseline.value);
/** 成员可选操作严格限定当前角色已发布上限；功能权限持续跟随角色，成员页面只配置范围与有效期。 */
const rolePermissionIds=ref<string[]>([]);
const availablePermissions=computed(()=>permissions.value.filter(item=>rolePermissionIds.value.includes(item.id)));
/** 角色当前功能只读展示，成员不能独立增减角色功能。 */
const permissionExplanation=computed(()=>availablePermissions.value.map(p=>({code:p.code,name:locale.value.startsWith('zh')?p.nameZh:p.nameEn,
  status:draft.value?.permissionIds.includes(p.id)?t('authorizationCenter.memberGranted'):t('authorizationCenter.memberNotGranted')})));
const scoped=computed(()=>permissions.value.filter(item=>draft.value?.permissionIds.includes(item.id)&&metadata.value.some(value=>value.permissionId===item.id)));
const invalid=computed(()=>!ready.value||!draft.value||!changed.value||!reason.value.trim()||(!draft.value.inheritDataScope&&scoped.value.some(item=>{const value=policy(item.id);return incompleteDataPolicy(value,conditionDimensions(declarations.value,value?.resourceType,item.code));})));
/** 当前角色范围用于继承预览和首次切换为自定义；切换仅修改草稿，保存后生效。 */
const inheritedPolicies=ref<MemberGrant["dataPolicies"]>([]);
function changeScopeMode(inherit:boolean){if(!draft.value)return;draft.value.inheritDataScope=inherit;if(inherit)scopeDraft.value={};if(inherit||!draft.value.dataPolicies.length)draft.value.dataPolicies=JSON.parse(JSON.stringify(inheritedPolicies.value));}

function policy(id:string){return draft.value?.dataPolicies.find(value=>value.permissionId===id);}
function scope(id:string,choice:ScopeChoice){
 if(!draft.value)return;
 const type=persistedScope(choice),support=metadata.value.find(value=>value.permissionId===id&&value.scopeType===type);
 if(!support)return;
 if(choice==="RECORDS"||choice==="RULES")scopeDraft.value[id]=choice;
 else delete scopeDraft.value[id];
 const previous=policy(id);
 if(previous?.scopeType==="RULES"&&type==="RULES"&&(choice==="RULES"||shownScope(previous)==="RECORDS"))return;
 draft.value.dataPolicies=draft.value.dataPolicies.filter(value=>value.permissionId!==id);
 draft.value.dataPolicies.push({permissionId:id,scopeType:type,resourceType:support.resourceType,organizationIds:[],rules:[]});
}
async function discard(){if(!changed.value&&!reason.value.trim())return true;try{await ElMessageBox.confirm(t("authorizationCenter.discard"),t("authorizationCenter.memberData"),{confirmButtonText:t("authorizationCenter.discardAction"),cancelButtonText:t("organization.cancel")});return true;}catch{return false;}}
async function selectMember(id:string){if(busy.value||!(await discard()))return;member.value=id;await loadGrants();}
async function loadGrants(){if(!props.api.grants||!member.value)return;busy.value=true;ready.value=false;error.value="";try{const value=await props.api.grants.get(member.value);grants.value=value.grants;version.value=value.authorityVersion;role.value=props.roleId;await loadDraft();}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{busy.value=false;}}
/** 获取当前角色功能和范围；继承范围实时预览，自定义范围保留独立配置。 */
async function loadDraft(){scopeDraft.value={};ready.value=false;draft.value=undefined;reason.value="";baseline.value=undefined;const grant=grants.value.find(value=>value.roleId===role.value);if(!grant)return;try{const current=await props.authorizationApi.assignment(role.value);rolePermissionIds.value=current.permissionIds;inheritedPolicies.value=current.dataPolicies;draft.value=JSON.parse(JSON.stringify({...grant,permissionIds:current.permissionIds,dataPolicies:(grant.inheritDataScope?current.dataPolicies:grant.dataPolicies).filter(value=>current.permissionIds.includes(value.permissionId)),roleVersion:current.roleVersion}));baseline.value=JSON.stringify(draft.value);ready.value=true;}catch(cause){await setErrorFeedback(error, props.errorText(cause));}}
/** 完整单分支更新，失败关闭写入口并保留草稿；重读必须确认丢弃修改。 */
async function save(){if(invalid.value||!draft.value||!props.api.grants||busy.value)return;busy.value=true;try{const value=await props.api.grants.replace(member.value,{grant:JSON.parse(JSON.stringify(draft.value)),version:version.value,reason:reason.value.trim()});grants.value=value.grants;version.value=value.authorityVersion;await loadDraft();ElMessage.success(t("authorizationCenter.saved"));}catch(cause){await setErrorFeedback(error, props.errorText(cause));ready.value=false;}finally{busy.value=false;}}
async function initialize(){busy.value=true;try{const catalog:RoleItem[]=[];for(let page=1;;page++){const value=await props.roleApi.list({query:"",page,pageSize:100});catalog.push(...value.items);if(catalog.length>=value.total)break;if(!value.items.length)throw new Error(t("authorizationCenter.roleListChanged"));}roles.value=catalog;const members:AuthorizationSubject[]=[];for(let page=1;;page++){const value=await props.api.subjects(page,"");members.push(...value.items);if(members.length>=value.total)break;if(!value.items.length)throw new Error(t("authorizationCenter.roleListChanged"));}subjects.value=members;declarations.value=await props.authorizationApi.declarations?.()??[];[permissions.value,metadata.value,organizations.value,conditionOptions.value]=await Promise.all([props.authorizationApi.permissions(),props.authorizationApi.metadata(),props.authorizationApi.organizationOptions?.()??Promise.resolve([]),props.authorizationApi.conditionOptions?.()??Promise.resolve({})]);}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{busy.value=false;}}
let directoryRequest=0;
/** 仅展示可信目录中已关联左侧角色的成员；筛选不是服务端授权替代品。 */
async function loadRelatedMembers(){
 const request=++directoryRequest,target=props.roleId;
 ready.value=false;member.value="";draft.value=undefined;baseline.value=undefined;reason.value="";relatedSubjects.value=[];
 if(!props.api.grants||!target)return;
 busy.value=true;
 try{
  const matched:AuthorizationSubject[]=[];
  for(let offset=0;offset<subjects.value.length;offset+=20){
   const batch=await Promise.all(subjects.value.slice(offset,offset+20).map(async item=>({item,assignment:await props.api.assignment(item.id)})));
   if(request!==directoryRequest)return;
   matched.push(...batch.filter(value=>value.assignment.roleIds.includes(target)).map(value=>value.item));
  }
  relatedSubjects.value=matched;
  if(matched.length){member.value=matched[0].id;await loadGrants();}
 }catch(cause){if(request===directoryRequest)await setErrorFeedback(error,props.errorText(cause));}
 finally{if(request===directoryRequest)busy.value=false;}
}
onMounted(async()=>{await initialize();if(props.active)await loadRelatedMembers();});
watch([()=>props.roleId,()=>props.active],()=>{if(props.active)void loadRelatedMembers();});
/** 页签切换保留组件草稿，离开整个路由由应用壳统一询问。 */
defineExpose({ canLeave:async()=>!busy.value&&await discard() });
</script>

<template>
  <div v-loading="busy" class="member-grant-editor">
    
    <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false"><el-button link :disabled="busy" @click="async()=>{if(await discard())await loadGrants();}">{{t('authorizationCenter.reload')}}</el-button></FloatingError>
    <el-form class="business-border-label-form member-grant-form" label-position="top" @submit.prevent="save">
      <div class="member-body">
       <aside class="member-users" :aria-label="t('authorizationCenter.roleUsers')">
        <div class="member-users-heading"><strong>{{t('authorizationCenter.roleUsers')}}</strong><InfoHint :content="t('authorizationCenter.memberDataHint')" /></div>
        <el-input v-model="userQuery" clearable :maxlength="100" :aria-label="t('authorizationCenter.subjectQuery')" :placeholder="t('authorizationCenter.subjectQuery')" />
        <div class="member-users-list"><el-button v-for="item in displayedUsers" :key="item.id" text :class="{'is-selected':item.id===member}" :aria-pressed="item.id===member" :title="item.name" :disabled="busy" @click="selectMember(item.id)">{{item.name}}</el-button><el-empty v-if="!matchedUsers.length" :image-size="48" :description="t('authorizationCenter.noRoleMembers')" /></div>
        <div class="member-users-pagination"><AppPagination v-model:current-page="userPage" :page-size="20" :total="matchedUsers.length" layout="total, prev, next" /></div>
       </aside>
       <section class="member-detail">
        <div class="member-role-context"><span>{{t('authorizationCenter.subjectRoles')}}</span><strong>{{roleName || '—'}}</strong></div>
      <el-empty v-if="!member" :description="t(relatedSubjects.length?'authorizationCenter.selectSubject':'authorizationCenter.noRoleMembers')" />
      <el-empty v-if="member&&!grants.length" :description="t('authorizationCenter.assignRoleFirst')" />
      <template v-if="draft">
        
        <el-tabs v-model="detailTab" class="member-tabs"><el-tab-pane :label="t('authorizationCenter.memberOperations')" name="operations" /><el-tab-pane :label="t('authorizationCenter.memberScopeSettings')" name="scope" /></el-tabs>
        <section v-show="detailTab==='operations'" class="member-operations"><ManagementTable :title="t('authorizationCenter.memberOperations')" :rows="permissionExplanation" :columns="operationColumns" embedded /></section>
        <section v-show="detailTab==='scope'" class="member-scope-settings">
        <el-form-item class="member-reason" :label="t('authorizationCenter.scopeMode')"><el-radio-group :model-value="draft.inheritDataScope" :disabled="!ready||busy" @change="value=>changeScopeMode(Boolean(value))"><el-radio-button :value="true">{{t('authorizationCenter.inheritRole')}}</el-radio-button><el-radio-button :value="false">{{t('authorizationCenter.customScope')}}</el-radio-button></el-radio-group><InfoHint :content="t('authorizationCenter.scopeModeHint')" /></el-form-item>
        <el-form-item class="member-scope-row" v-for="item in scoped" :key="item.id" :label="locale.startsWith('zh')?item.nameZh:item.nameEn">
          <div class="scope-inputs">
            <el-select :model-value="shownScope(policy(item.id),scopeDraft[item.id])" :disabled="!ready||busy||draft.inheritDataScope" :placeholder="t('authorizationCenter.selectScope')" @change="value=>scope(item.id,value)"><el-option v-for="choice in scopeChoices(metadata.filter(value=>value.permissionId===item.id),item.code,!!conditionDimensions(declarations,item.resource,item.code).length)" :key="choice" :value="choice" :label="t('authorizationCenter.scopes.'+choice)" /></el-select>
            <el-select v-if="policy(item.id)?.scopeType==='ORGANIZATION'||policy(item.id)?.scopeType==='ORGANIZATION_SUBTREE'" v-model="policy(item.id)!.organizationIds" multiple filterable :disabled="!ready||busy||draft.inheritDataScope" :placeholder="t('authorizationCenter.selectOrganizations')"><el-option v-for="organization in organizations" :key="organization.id" :value="organization.id" :label="organization.name" /></el-select>
            <DataGrantRuleEditor :record-only="shownScope(policy(item.id),scopeDraft[item.id])==='RECORDS'" :allow-records="!item.code.endsWith(':create')" :resource="item.resource" :record-search="authorizationApi.recordSearch" :error-text="errorText" v-if="policy(item.id)?.scopeType==='RULES'" :model-value="policy(item.id)?.rules??[]" :dimensions="conditionDimensions(declarations,item.resource,item.code)" :condition-options="conditionOptions" :organizations="organizations" :members="subjects" :disabled="!ready||busy||draft.inheritDataScope" @update:model-value="value=>{const p=policy(item.id);if(p)p.rules=value}" />
          </div>
        </el-form-item>
        <el-empty v-if="!scoped.length" :image-size="48" :description="t('authorizationCenter.noMemberScopes')" />
        </section>
      </template>
       </section>
      </div>
      <template v-if="draft">
        <section class="member-submit">
        <div class="member-submit-heading"><h2>{{t('authorizationCenter.summary')}}</h2><span>{{roleName}} · {{relatedSubjects.find(item=>item.id===member)?.name}}</span><el-button v-operation="'TenantMember.authorizationReplace'" type="primary" :loading="busy" :disabled="invalid" @click="save">{{t('organization.save')}}</el-button></div>
        <el-form-item class="member-submit-expiry" :label="t('authorizationCenter.expiresAt')"><el-date-picker class="member-expiry" v-model="draft.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable :disabled="!ready||busy" /></el-form-item>
        <el-form-item class="member-reason" :label="t('authorizationCenter.reason')" required><el-input v-model="reason" type="textarea" :rows="1" :maxlength="500" show-word-limit :disabled="!ready||busy" /></el-form-item>

        </section>
      </template>
    </el-form>
  </div>
</template>
<style scoped>
/* 选择上下文、范围与只读功能、提交分层；复用公共表单和表格，不新增视觉体系。 */
.member-grant-editor{display:flex;flex-direction:column;flex:1;min-height:0;padding:var(--app-content-padding,8px);min-width:0;overflow:auto}
.member-grant-form{flex:1;min-height:0;display:flex;flex-direction:column;gap:var(--app-section-gap,8px);min-width:0}
/* 分栏严格限制在成员数据权限组件内；外层授权中心角色栏和导航保持原样。 */
.member-body{display:grid;grid-template-columns:var(--app-category-width,220px) minmax(0,1fr);flex:1;min-height:0;gap:var(--app-section-gap,8px)}
.member-users{display:flex;flex-direction:column;gap:8px;min-height:0;min-width:0;padding-right:8px;border-right:1px solid var(--el-border-color-lighter)}
.member-users-heading{display:flex;align-items:center;gap:8px;min-height:32px}
.member-users-list{flex:1;min-height:0;overflow:auto}
.member-users-list>.el-button{display:flex;justify-content:flex-start;width:100%;min-height:40px;margin:0;overflow:hidden}
.member-users-list>.el-button :deep(span){overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.member-users-list>.is-selected{color:var(--el-color-primary);background:var(--el-color-primary-light-9);box-shadow:inset 2px 0 var(--el-color-primary)}
.member-users-pagination{flex:none;overflow-x:auto;padding-top:8px;border-top:1px solid var(--el-border-color-lighter)}
.member-detail{display:flex;flex-direction:column;gap:8px;min-height:0;min-width:0;overflow:hidden}
.member-context .el-form-item,.member-submit .el-form-item{margin:0}
.member-grant-editor .el-select,.member-expiry{width:100%}
.member-tabs{flex:none}
.member-operations{flex:1;min-height:0;overflow:auto;min-width:0}
.member-scope-settings{align-content:start;flex:1;min-height:0;overflow:auto;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px 12px;min-width:0}
.member-scope-settings>.member-reason,.member-scope-settings>.el-empty{grid-column:1/-1}
.member-scope-settings>.member-reason{display:flex;align-items:center;margin:0;padding:8px;background:var(--el-fill-color-lighter)}
.member-scope-row{margin:0;padding:8px;border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base)}
.scope-inputs{display:flex;flex-direction:column;gap:8px;width:100%}
/* 桌面摘要、有效期与保存共用一行，原因占下一行；窄屏自然换行，不压缩触控区域。 */
.member-context,.member-tabs{flex:none}
.member-role-context{display:flex;align-items:center;gap:12px;color:var(--el-text-color-secondary)}
.member-role-context strong{color:var(--el-text-color-primary);font-weight:500}
.member-submit{flex:none;margin-top:auto;display:grid;grid-template-columns:minmax(0,1fr) 240px auto;gap:4px 12px;align-items:center;padding-top:4px;border-top:1px solid var(--el-border-color-lighter)}
.member-submit-heading{display:contents}
.member-submit-heading h2{grid-column:1;grid-row:1}
.member-submit-heading span{grid-column:1;grid-row:1;margin-left:80px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.member-submit-expiry{grid-column:2;grid-row:1}
.member-submit>.member-reason{grid-column:1/-1}
.member-submit>.member-reason :deep(.el-textarea__inner){min-height:36px}
.member-submit-heading h2{font-size:var(--el-font-size-medium);margin:0}
.member-submit-heading span{color:var(--el-text-color-secondary)}
.member-submit-heading>.el-button{grid-column:3;grid-row:1;min-width:100px}

@media(max-width:1000px){.member-scope-settings{grid-template-columns:1fr}}
@media(max-width:700px){.member-body{grid-template-columns:1fr;flex:none}.member-users{max-height:260px;padding-right:0;border-right:0;border-bottom:1px solid var(--el-border-color-lighter)}.member-detail{overflow:visible}.member-users-list>.el-button{min-height:44px}.member-submit{grid-template-columns:minmax(0,1fr) auto;gap:8px}.member-submit-expiry{grid-column:1;grid-row:2}.member-submit-heading>.el-button{grid-column:2;grid-row:2}.member-submit>.member-reason{grid-row:3}.member-context{grid-template-columns:1fr}.member-grant-form{min-height:auto}.member-scope-settings,.member-operations{flex:none;overflow:visible}.member-submit-heading>.el-button{min-height:44px}.member-scope-settings>.member-reason{align-items:flex-start}}
</style>
