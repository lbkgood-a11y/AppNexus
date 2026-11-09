<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref,watch} from "vue";
import {ElMessage,ElMessageBox} from "element-plus";
import {useI18n} from "vue-i18n";
import {BusinessWorkspace,ManagementTable,type ManagementColumn} from "@appnexus/ui";
import MenuPermissionBranch from "./MenuPermissionBranch.vue";
import {menuPermissionTree} from "./menuPermissionView";
import type {AuthorizationMenu} from "./index";
import MemberGrantEditor from "./MemberGrantEditor.vue";
import {applyFieldPolicyBatch} from "./fieldPolicyDraft";
import SubjectAuthorization from "./SubjectAuthorization.vue";
import type { SubjectAuthorizationApi,FieldDescriptor,FieldPolicy } from "./index";
import type {AuthorizationCenterApi,DataPolicy,DataScopeType,PermissionItem,RoleItem,RoleManagementApi,ScopeSupport,AuthorizationOption} from "./index";
const props=defineProps<{roleApi:RoleManagementApi;api:AuthorizationCenterApi;subjectApi:SubjectAuthorizationApi;errorText:(error:unknown)=>string}>();
const activeTab=ref("permissions"), editorTab=ref("functions"), query=ref(""), onlySelected=ref(false), ready=ref(false), baseline=ref<string|null>(null);
const menus=ref<AuthorizationMenu[]>([]),functionView=ref("menus");
const fieldCatalog=ref<FieldDescriptor[]>([]), fieldPolicies=ref<FieldPolicy[]>([]);
const fieldResource=ref(""),fieldKind=ref(""),fieldQuery=ref(""),fieldSelection=ref<string[]>([]),bulkFieldMode=ref<FieldPolicy["mode"]|"NONE">("MASKED");
const fieldResources=computed(()=>[...new Set(fieldCatalog.value.map(item=>item.resource))]);
const fieldKinds=computed(()=>[...new Set(fieldCatalog.value.map(item=>item.kind))]);
const visibleFields=computed(()=>fieldCatalog.value.filter(item=>(!fieldResource.value||item.resource===fieldResource.value)&&(!fieldKind.value||item.kind===fieldKind.value)&&(!fieldQuery.value.trim()||[item.resource,item.field,item.nameZh,item.nameEn].some(value=>value.toLowerCase().includes(fieldQuery.value.trim().toLowerCase())))));
const fieldColumns=computed<ManagementColumn[]>(()=>[
 {key:"selection",label:t("authorizationCenter.fieldSelect"),width:72},
 {key:"resource",prop:"resource",label:t("authorizationCenter.fieldResource"),minWidth:160},
 {key:"name",label:t("authorizationCenter.fieldName"),minWidth:130},
 {key:"kind",label:t("authorizationCenter.fieldKind"),minWidth:100},
 {key:"mode",label:t("authorizationCenter.fieldRule"),minWidth:170}
]);
function fieldKey(item:FieldDescriptor){return item.resource+":"+item.field;}
/** 批量配置仅作用于当前筛选中已选字段，不触碰隐藏字段，不自动授予原文或查询权。 */
function applyFields(){fieldPolicies.value=applyFieldPolicyBatch(fieldPolicies.value,visibleFields.value,new Set(fieldSelection.value),bulkFieldMode.value);}
function selectFields(enabled:boolean){const visible=visibleFields.value.map(fieldKey);fieldSelection.value=enabled?[...new Set([...fieldSelection.value,...visible])]:fieldSelection.value.filter(key=>!visible.includes(key));}
watch([fieldResource,fieldKind,fieldQuery],()=>{fieldSelection.value=[];});

const isRoleTab=computed(()=>["permissions","data","security"].includes(activeTab.value));
watch(activeTab,value=>{if(isRoleTab.value)editorTab.value=value==="permissions"?"functions":value;});
/** 展示模式只提供服务端目录已声明的强制限制，清空代表移除限制而非授予原文权。 */
function setField(item:FieldDescriptor,mode:FieldPolicy["mode"]|"NONE"){fieldPolicies.value=fieldPolicies.value.filter(value=>value.resource!==item.resource||value.field!==item.field);if(mode!=="NONE")fieldPolicies.value.push({resource:item.resource,field:item.field,mode});}
let requestVersion=0;
const subjectEditor=ref<{canLeave:()=>Promise<boolean>}>(),memberEditor=ref<{canLeave:()=>Promise<boolean>}>();
/** 应用路由与页签共享离开校验；保存过程中禁止卸载，所有已挂载草稿均须确认。 */
async function canLeave(){return !saving.value&&await confirmDiscard()&&(await subjectEditor.value?.canLeave()??true)&&(await memberEditor.value?.canLeave()??true);}
defineExpose({canLeave});
const {t,locale,te}=useI18n();
/** 已知资源显示业务名称，未知资源保留稳定标识，不猜测领域含义。 */
function resourceLabel(resource:string){const key='authorizationCenter.resourceNames.'+resource.replaceAll('.','_');return te(key)?t(key):resource;}const roles=ref<RoleItem[]>([]),permissions=ref<PermissionItem[]>([]),metadata=ref<ScopeSupport[]>([]),organizations=ref<AuthorizationOption[]>([]);const roleId=ref(""),selected=ref<string[]>([]),policies=ref<Record<string,DataPolicy>>({}),version=ref(0),reason=ref(""),loading=ref(false),saving=ref(false),error=ref("");
const selectedRole=computed(()=>roles.value.find(item=>item.id===roleId.value));
const grouped=computed(()=>Object.entries(permissions.value.filter(item=>(editorTab.value!=="data"||(selected.value.includes(item.id)&&supports(item.id).length>0))&&(!onlySelected.value||selected.value.includes(item.id))&&(!query.value.trim()||[label(item),item.code,item.resource].some(value=>value.toLowerCase().includes(query.value.trim().toLowerCase())))).reduce<Record<string,PermissionItem[]>>((all,item)=>{(all[item.resource]??=[]).push(item);return all;},{})));
/** 菜单与资源视图编辑同一权限草稿，搜索与仅已选过滤作用于两种视图。 */
const menuTree=computed(()=>menuPermissionTree(menus.value,grouped.value.flatMap(([,items])=>items),locale.value.startsWith('zh'),t('authorizationCenter.unlinkedPermissions')));
function toggleMenu(ids:string[],enabled:boolean){ids.forEach(id=>toggle(id,enabled));}
/** 稳定比较完整草稿；字段顺序和组织选择顺序不构成授权变化。 */
function snapshot(){return JSON.stringify({ids:[...selected.value].sort(),fields:[...fieldPolicies.value].sort((a,b)=>(a.resource+a.field).localeCompare(b.resource+b.field)),policies:Object.values(policies.value).map(item=>({...item,organizationIds:[...item.organizationIds].sort()})).sort((a,b)=>a.permissionId.localeCompare(b.permissionId))});}
const changed=computed(()=>baseline.value!==null&&snapshot()!==baseline.value);
const incompleteCount=computed(()=>selected.value.filter(id=>supports(id).length&&(!policies.value[id]||(needsOrganizations(policies.value[id].scopeType)&&!policies.value[id].organizationIds.length))).length);
/**
 * 资源批选只改变当前筛选结果；不自动授予 ALL，也不推断数据范围。
 * @param items 当前资源下筛选后可见的权限，禁用权限不参与批量变更。
 * @param enabled 为 true 时加入草稿，为 false 时同时移除功能和对应数据策略。
 */
function toggleGroup(items:PermissionItem[],enabled:boolean){items.filter(item=>item.enabled).forEach(item=>toggle(item.id,enabled));}
async function confirmDiscard(){if(!changed.value&&!reason.value.trim())return true;try{await ElMessageBox.confirm(t("authorizationCenter.discard"),t("authorizationCenter.title"),{confirmButtonText:t("authorizationCenter.discardAction"),cancelButtonText:t("organization.cancel"),type:"warning"});return true;}catch{return false;}}
async function selectRole(id:string){if(saving.value||id===roleId.value||!(await confirmDiscard()))return;roleId.value=id;await loadAssignment();}
function supports(id:string){return metadata.value.filter(item=>item.permissionId===id)}function label(item:PermissionItem){return locale.value.startsWith("zh")?item.nameZh:item.nameEn}function needsOrganizations(type?:DataScopeType){return type==="ORGANIZATION"||type==="ORGANIZATION_SUBTREE"}
function toggle(id:string,enabled:boolean){if(enabled){if(!selected.value.includes(id))selected.value.push(id);}else{selected.value=selected.value.filter(value=>value!==id);delete policies.value[id];}}
function scopeChanged(permissionId:string,type:DataScopeType){const support=supports(permissionId).find(item=>item.scopeType===type);if(!support)return;policies.value[permissionId]={permissionId,scopeType:type,resourceType:support.resourceType,organizationIds:[]};}
/** 角色切换后清除旧快照，过期响应不得写入新角色；读取失败时禁止覆盖保存。 */
async function loadAssignment(){if(!roleId.value)return;const request=++requestVersion;loading.value=true;ready.value=false;error.value="";baseline.value=null;fieldSelection.value=[];fieldPolicies.value=[];selected.value=[];policies.value={};reason.value="";try{const value=await props.api.assignment(roleId.value);if(request!==requestVersion)return;selected.value=[...value.permissionIds];policies.value=Object.fromEntries(value.dataPolicies.map(item=>[item.permissionId,{...item,organizationIds:[...item.organizationIds]}]));version.value=value.roleVersion;fieldPolicies.value=[...(value.fieldPolicies??[])];baseline.value=snapshot();ready.value=true;}catch(cause){if(request===requestVersion)error.value=props.errorText(cause);}finally{if(request===requestVersion)loading.value=false;}}
/** 读取完整角色目录，避免超过每页 100 条后无法找到目标角色。 */
async function loadRoles(){const result:RoleItem[]=[];for(let page=1;;page++){const value=await props.roleApi.list({query:"",status:"ENABLED",page,pageSize:100});result.push(...value.items);if(result.length>=value.total)return result;if(!value.items.length)throw new Error(t("authorizationCenter.roleListChanged"));}}
async function load(){if(saving.value||!(await confirmDiscard()))return;loading.value=true;ready.value=false;error.value="";try{const [catalog,items,support,orgs,fields,navigation]=await Promise.all([loadRoles(),props.api.permissions(),props.api.metadata(),props.api.organizationOptions?.()??Promise.resolve([]),props.api.fields(),props.api.menus()]);menus.value=navigation;fieldCatalog.value=fields;roles.value=catalog;permissions.value=items;metadata.value=support;organizations.value=orgs;roleId.value=catalog.some(item=>item.id===roleId.value)?roleId.value:(catalog[0]?.id??"");if(roleId.value)await loadAssignment();else{baseline.value=null;fieldSelection.value=[];fieldPolicies.value=[];selected.value=[];policies.value={};reason.value="";}}catch(cause){error.value=props.errorText(cause);}finally{loading.value=false;}}
const invalid=computed(()=>!ready.value||loading.value||!changed.value||incompleteCount.value>0||!reason.value.trim());
/** 完整授权覆盖写影响全部角色成员；失败后要求重读版本，禁止盲目重试。 */
async function save(){if(!roleId.value||invalid.value||saving.value)return;saving.value=true;error.value="";try{await ElMessageBox.confirm(t("authorizationCenter.roleReplaceWarning",{name:selectedRole.value?.name}),t("authorizationCenter.summary"),{confirmButtonText:t("organization.save"),cancelButtonText:t("organization.cancel"),type:"warning"});}catch{saving.value=false;return;}try{const value=await props.api.replace(roleId.value,{ids:[...selected.value],dataPolicies:Object.values(policies.value),fieldPolicies:fieldPolicies.value,version:version.value,reason:reason.value.trim()});selected.value=[...value.permissionIds];policies.value=Object.fromEntries(value.dataPolicies.map(item=>[item.permissionId,{...item,organizationIds:[...item.organizationIds]}]));version.value=value.roleVersion;fieldPolicies.value=[...(value.fieldPolicies??[])];baseline.value=snapshot();reason.value="";ElMessage.success(t("authorizationCenter.saved"));}catch(cause){error.value=props.errorText(cause);ready.value=false;}finally{saving.value=false;}}
onBeforeUnmount(()=>{++requestVersion;});
onMounted(load);
</script>
<template>
  <BusinessWorkspace :title="t('authorizationCenter.title')" :description="t('authorizationCenter.description')">
    <template #navigation>
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="t('authorizationCenter.permissions')" name="permissions" />
        <el-tab-pane :label="t('authorizationCenter.roleDefaultData')" name="data" />
        <el-tab-pane :label="t('authorizationCenter.fieldSecurity')" name="security" />
        <el-tab-pane v-if="subjectApi.grants" :label="t('authorizationCenter.memberData')" name="memberData" />
        <el-tab-pane :label="t('authorizationCenter.subjectRoles')" name="subjects" />
      </el-tabs>
    </template>
    <template #feedback>
      <el-alert v-if="error && isRoleTab" type="error" :title="error" :closable="false" show-icon>
        <el-button link type="primary" :disabled="saving || loading" @click="load">{{ t('account.retry') }}</el-button>
      </el-alert>
    </template>
    <template #data>
      <SubjectAuthorization ref="subjectEditor" v-show="activeTab === 'subjects'" :api="subjectApi" :role-api="roleApi" :error-text="errorText" />
      <MemberGrantEditor ref="memberEditor" v-if="subjectApi.grants" v-show="activeTab === 'memberData'" :api="subjectApi" :role-api="roleApi" :authorization-api="api" :error-text="errorText" />
      <div v-show="isRoleTab" v-loading="loading" class="authorization-grid">
        <section class="authorization-pane role-context">
          <div class="role-context-label"><h2>{{ t('authorizationCenter.roles') }}</h2><span>{{ t('authorizationCenter.roleContextHint') }}</span></div>
          <el-empty v-if="!roles.length" :description="t('authorizationCenter.noRoles')" />
          <el-select v-else :model-value="roleId" filterable :disabled="saving || loading" :aria-label="t('authorizationCenter.roles')" @change="value => selectRole(String(value))">
            <el-option v-for="role in roles" :key="role.id" :value="role.id" :label="role.name + ' (' + role.code + ')'" />
          </el-select>
        </section>
        <section class="authorization-pane permission-pane">
          <div class="editor-heading"><h2>{{ t(activeTab==='permissions'?'authorizationCenter.permissions':activeTab==='data'?'authorizationCenter.roleDefaultData':'authorizationCenter.fieldSecurity') }}</h2><el-tag v-if="changed" type="warning" effect="plain">{{t('authorizationCenter.unsavedChanges')}}</el-tag></div>
          <template v-if="editorTab === 'security'">
            <el-alert :title="t('authorizationCenter.fieldSecurityHint')" type="info" :closable="false" show-icon />
            <el-empty v-if="!fieldCatalog.length" :description="t('authorizationCenter.noFieldCatalog')" />
            <el-form label-position="top" class="field-filter" @submit.prevent>
              <el-form-item :label="t('authorizationCenter.fieldResource')"><el-select v-model="fieldResource" clearable filterable><el-option v-for="resource in fieldResources" :key="resource" :value="resource" :label="resource" /></el-select></el-form-item>
              <el-form-item :label="t('authorizationCenter.fieldKind')"><el-select v-model="fieldKind" clearable><el-option v-for="kind in fieldKinds" :key="kind" :value="kind" :label="t('authorizationCenter.fieldKinds.'+kind)" /></el-select></el-form-item>
              <el-form-item :label="t('authorizationCenter.fieldSearch')"><el-input v-model="fieldQuery" clearable :maxlength="100" /></el-form-item>
            </el-form>
            <ManagementTable :title="t('authorizationCenter.fieldSecurity')" :rows="visibleFields" :columns="fieldColumns" :busy="saving" :loading="loading" embedded @refresh="load">
              <template #actions>
                <el-checkbox :model-value="visibleFields.length>0&&visibleFields.every(item=>fieldSelection.includes(fieldKey(item)))" :indeterminate="visibleFields.some(item=>fieldSelection.includes(fieldKey(item)))&&!visibleFields.every(item=>fieldSelection.includes(fieldKey(item)))" :disabled="!ready||saving" @change="value=>selectFields(Boolean(value))">{{t('authorizationCenter.selectFilteredFields')}}</el-checkbox>
                <el-select v-model="bulkFieldMode" :aria-label="t('authorizationCenter.fieldRule')" :disabled="!ready||saving" class="field-bulk-mode"><el-option v-for="mode in ['MASKED','HIDDEN','NONE']" :key="mode" :value="mode" :label="t('authorizationCenter.fieldModes.'+mode)" /></el-select>
                <el-button :disabled="!ready||saving||!fieldSelection.length" @click="applyFields">{{t('authorizationCenter.applyFields',{count:fieldSelection.length})}}</el-button>
              </template>
              <template #selection="{row}"><el-checkbox :model-value="fieldSelection.includes(fieldKey(row))" :aria-label="row.nameZh" :disabled="!ready||saving" @change="value=>{fieldSelection=value?[...new Set([...fieldSelection,fieldKey(row)])]:fieldSelection.filter(key=>key!==fieldKey(row));}" /></template>
              <template #name="{row}">{{locale.startsWith('zh')?row.nameZh:row.nameEn}}<div class="permission-code">{{row.field}}</div></template>
              <template #kind="{row}">{{t('authorizationCenter.fieldKinds.'+row.kind)}}</template>
              <template #mode="{row}">
                <el-select :model-value="fieldPolicies.find(value=>value.resource===row.resource&&value.field===row.field)?.mode??'NONE'" :aria-label="row.nameZh+' · '+t('authorizationCenter.fieldRule')" :disabled="!ready||saving" @change="value=>setField(row,value)">
                  <el-option value="NONE" :label="t('authorizationCenter.fieldModes.NONE')" /><el-option v-for="mode in row.modes" :key="mode" :value="mode" :label="t('authorizationCenter.fieldModes.'+mode)" />
                </el-select>
              </template>
            </ManagementTable>
          </template>
          <template v-else>
          <el-form label-position="top" class="permission-filter" @submit.prevent>
            <el-form-item :label="t('authorizationCenter.permissionQuery')"><el-input v-model="query" clearable :maxlength="100" /></el-form-item>
            <el-checkbox v-model="onlySelected">{{ t('authorizationCenter.onlySelected') }}</el-checkbox>
          </el-form>
          <el-alert v-if="editorTab === 'data'" :title="t('authorizationCenter.dataPolicyHint')" type="info" :closable="false" show-icon />
          <el-radio-group v-if="editorTab==='functions'" v-model="functionView" class="function-view" :aria-label="t('authorizationCenter.functionView')"><el-radio-button value="menus">{{t('authorizationCenter.menuFunctions')}}</el-radio-button><el-radio-button value="resources">{{t('authorizationCenter.resourceView')}}</el-radio-button></el-radio-group>
          <template v-if="editorTab==='functions'&&functionView==='menus'"><el-alert :title="t('authorizationCenter.menuFunctionHint')" type="info" :closable="false" class="mb-4" /><MenuPermissionBranch v-for="node in menuTree" :key="node.id" :node="node" :selected="selected" :disabled="!ready||saving" @toggle="toggleMenu" /></template>
          <el-empty v-if="!grouped.length" :description="t(permissions.length ? 'authorizationCenter.noMatchingPermissions' : 'authorizationCenter.noPermissions')" />
          <div v-for="[resource,items] in (editorTab==='functions'&&functionView==='menus'?[]:grouped)" :key="resource" class="permission-group">
            <template v-if="editorTab === 'functions' || items.some(item => selected.includes(item.id) && supports(item.id).length)">
              <div class="permission-group-heading"><h3>{{ resourceLabel(resource) }}</h3>
              <el-checkbox v-if="editorTab === 'functions'" :disabled="!ready || saving" :model-value="items.filter(item => item.enabled).length > 0 && items.filter(item => item.enabled).every(item => selected.includes(item.id))" :indeterminate="items.some(item => selected.includes(item.id)) && !items.filter(item => item.enabled).every(item => selected.includes(item.id))" @change="value => toggleGroup(items,Boolean(value))">{{ t('authorizationCenter.selectResource') }}</el-checkbox></div>
              <div class="permission-items" :class="{'permission-items-functions':editorTab==='functions'}">
              <template v-for="item in items" :key="item.id">
                <div v-if="editorTab === 'functions' || (selected.includes(item.id) && supports(item.id).length)" class="permission-row" :class="{ 'function-row': editorTab === 'functions' }">
                  <el-checkbox v-if="editorTab === 'functions'" :disabled="!ready || saving || !item.enabled" :model-value="selected.includes(item.id)" @change="value => toggle(item.id,Boolean(value))"><strong>{{ label(item) }}</strong><span>{{ item.code }}</span></el-checkbox>
                  <div v-else><strong>{{ label(item) }}</strong><div class="permission-code">{{ item.code }}</div></div>
                  <template v-if="editorTab === 'data'">
                    <el-select :model-value="policies[item.id]?.scopeType" :aria-label="label(item) + ' · ' + t('authorizationCenter.selectScope')" :disabled="!ready || saving" :placeholder="t('authorizationCenter.selectScope')" @change="value => scopeChanged(item.id,value as DataScopeType)">
                      <el-option v-for="support in supports(item.id)" :key="support.scopeType" :label="t('authorizationCenter.scopes.' + support.scopeType)" :value="support.scopeType" />
                    </el-select>
                    <el-select v-if="needsOrganizations(policies[item.id]?.scopeType)" v-model="policies[item.id].organizationIds" :aria-label="label(item) + ' · ' + t('authorizationCenter.selectOrganizations')" :disabled="!ready || saving" multiple filterable :placeholder="t('authorizationCenter.selectOrganizations')">
                      <el-option v-for="organization in organizations" :key="organization.id" :label="organization.name" :value="organization.id" />
                    </el-select>
                  </template>
                </div>
              </template>
              </div>
            </template>
          </div>
          </template>
        </section>
        <section class="authorization-pane summary-pane">
          <div class="save-summary"><h2>{{ t('authorizationCenter.summary') }}</h2><span>{{ selectedRole?.name || '—' }}</span><el-tag effect="plain">{{ t('authorizationCenter.selectedCount') }} · {{ selected.length }}</el-tag></div>
          <el-button v-if="incompleteCount" link type="warning" @click="activeTab='data';query='';onlySelected=true">{{ t('authorizationCenter.pendingScopes', { count: incompleteCount }) }}</el-button>
          <el-form class="save-form" label-position="top" @submit.prevent="save">
            <el-form-item :label="t('authorizationCenter.reason')" required><el-input v-model="reason" type="textarea" :rows="2" :maxlength="500" show-word-limit :disabled="!ready || saving" /></el-form-item>
            <el-alert v-if="incompleteCount" type="warning" :title="t('authorizationCenter.incomplete')" :closable="false" show-icon />
            <el-button class="save-button" type="primary" native-type="submit" :loading="saving" :disabled="invalid">{{ t('organization.save') }}</el-button>
          </el-form>
        </section>
      </div>
    </template>
  </BusinessWorkspace>
</template>
<style scoped>
/* 角色上下文、配置内容和提交区纵向排列，避免三栏在模板侧栏内争抢宽度。 */
.function-view{margin-bottom:16px}
.authorization-grid{display:flex;flex-direction:column;min-width:0}
.authorization-pane{min-width:0;padding:20px;background:var(--el-bg-color)}
.authorization-pane+.authorization-pane{border-top:1px solid var(--el-border-color-lighter)}
h2{font-size:var(--el-font-size-medium);margin:0;font-weight:600}
.role-context{display:flex;align-items:center;justify-content:space-between;gap:24px;background:var(--el-fill-color-lighter)}
.role-context-label{display:flex;flex-direction:column;gap:6px;min-width:0}
.role-context-label span{color:var(--el-text-color-secondary);font-size:var(--el-font-size-small)}
.role-context>.el-select{width:360px;max-width:100%;flex-shrink:0}
.editor-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:20px}
.permission-filter{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px;padding:16px;background:var(--el-fill-color-lighter);margin-bottom:20px;border-radius:var(--el-border-radius-base)}
.permission-filter .el-form-item{margin:0;flex:1;max-width:520px;min-width:200px}
.permission-group{margin-top:16px;border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base);overflow:hidden}
.permission-group-heading{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px 16px;padding:10px 16px;background:var(--el-fill-color-lighter)}
.permission-group h3{margin:0;font-size:14px;font-weight:600;overflow-wrap:anywhere;color:var(--el-text-color-regular)}
.permission-items{padding:0 16px}
.permission-items-functions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 24px}
.permission-row{display:grid;grid-template-columns:minmax(180px,1fr) minmax(160px,1fr) minmax(180px,1.5fr);gap:16px;align-items:center;padding:12px 0;border-bottom:1px solid var(--el-border-color-lighter)}
.permission-row:last-child{border-bottom:0}
.permission-row.function-row{grid-template-columns:1fr}
.permission-row :deep(.el-checkbox){max-width:100%;height:auto;min-height:40px;align-items:flex-start}
.permission-row :deep(.el-checkbox__input){margin-top:4px}
.permission-row :deep(.el-checkbox__label){display:flex;flex-direction:column;gap:4px;white-space:normal;overflow-wrap:anywhere}
.permission-row strong{font-weight:500}
.permission-row span,.permission-code{font-size:12px;color:var(--el-text-color-secondary);overflow-wrap:anywhere}
.field-filter{display:flex;flex-wrap:wrap;gap:16px;padding:16px;background:var(--el-fill-color-lighter);margin:16px 0;border-radius:var(--el-border-radius-base)}
.field-filter .el-form-item{min-width:160px;flex:1;margin-bottom:0}
.field-bulk-mode{width:150px}
.save-summary{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:16px}
.save-summary>span{color:var(--el-text-color-secondary)}
.save-form{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12px 24px;align-items:end}
.save-form>.el-form-item{margin-bottom:0}
.save-form>.el-alert{grid-column:1/-1;grid-row:1}
.save-button{min-width:120px;align-self:end}
@media(min-width:1400px){.permission-items-functions{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(max-width:1100px){.permission-row{grid-template-columns:minmax(0,1fr) minmax(160px,1fr)}.permission-row>.el-select:last-child{grid-column:2}.permission-row.function-row{grid-template-columns:1fr}}
@media(max-width:700px){.authorization-pane{padding:16px}.role-context{align-items:stretch;flex-direction:column;gap:12px}.role-context>.el-select{width:100%}.permission-items-functions{grid-template-columns:1fr}.permission-group-heading{align-items:flex-start;flex-direction:column}.permission-filter{align-items:stretch;flex-direction:column}.permission-filter .el-form-item{max-width:none;min-width:0}.permission-row{grid-template-columns:1fr}.permission-row>.el-select:last-child{grid-column:auto}.save-form{grid-template-columns:1fr}.save-button{width:100%}.field-filter{flex-direction:column}.field-filter .el-form-item{min-width:0}:deep(.el-button){min-height:44px}}
</style>
