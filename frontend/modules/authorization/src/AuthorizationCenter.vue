<script setup lang="ts">
import {conditionDimensions,type DataConditionOptions} from "./index";
/** 多根节点包含独立弹框，应用壳的 class 等属性仅传给主工作区，确保全局页面边距及高度链生效。 */
defineOptions({ inheritAttrs: false });
import { reportError, setErrorFeedback } from "@appnexus/ui";
import { InfoHint } from "@appnexus/ui";
import {h,computed,onMounted,onBeforeUnmount,ref,watch} from "vue";
import {ElButton,ElMessage,ElMessageBox} from "element-plus";
import {useI18n} from "vue-i18n";
import {BusinessWorkspace,ManagedDialog,TreeListTemplate,AppPagination,ManagementTable,type ManagementColumn} from "@appnexus/ui";
import { AppIcon } from "@appnexus/icons";
import MenuPermissionBranch from "./MenuPermissionBranch.vue";
import {menuPermissionTree} from "./menuPermissionView";
import type {AuthorizationMenu} from "./index";
import MemberGrantEditor from "./MemberGrantEditor.vue";
import DataGrantRuleEditor from "./DataGrantRuleEditor.vue";
import {incompleteDataPolicy,objectDefaultPolicy,persistedScope,scopeChoices,shownScope,type ScopeChoice} from "./dataGrantDraft";
const scopeDraft=ref<Record<string,"RULES"|"RECORDS">>({});
import {applyFieldPolicyBatch} from "./fieldPolicyDraft";
import RoleUsers from "./RoleUsers.vue";
import SubjectAuthorization from "./SubjectAuthorization.vue";
import PolicyTemplateActions from "./PolicyTemplateActions.vue";
import type { SubjectAuthorizationApi,FieldDescriptor,FieldPolicy } from "./index";
import type {AuthorizationCenterApi,AuthorizationAssignment,DataPolicy,ObjectScope,PermissionItem,RoleItem,RoleManagementApi,ScopeSupport,AuthorizationOption} from "./index";
const conditionOptions=ref<DataConditionOptions>({});
/** initialTab 决定默认页签。memberDataOnly 固定使用成员数据权限工作区，读写均由既有接口复核。 */
const props=defineProps<{roleApi:RoleManagementApi;api:AuthorizationCenterApi;subjectApi:SubjectAuthorizationApi;initialTab?:"memberData";memberDataOnly?:boolean;pageTitle?:string;errorText:(error:unknown)=>string}>();
const activeTab=ref<string>((props.memberDataOnly||props.initialTab==="memberData")&&props.subjectApi.grants?"memberData":"permissions"), editorTab=ref("functions"), query=ref(""), onlySelected=ref(false), ready=ref(false), baseline=ref<string|null>(null);
const grantMembers=ref<import("./index").AuthorizationOption[]>([]);
const declarations=ref<import("./index").DataAccessDeclaration[]>([]);
const menus=ref<AuthorizationMenu[]>([]),functionView=ref("menus");
const fieldCatalog=ref<FieldDescriptor[]>([]), fieldPolicies=ref<FieldPolicy[]>([]);
const fieldResource=ref(""),fieldKind=ref(""),fieldQuery=ref(""),fieldSelection=ref<string[]>([]),bulkFieldMode=ref<FieldPolicy["mode"]|"NONE">("MASKED");
const fieldResources=computed(()=>[...new Set(fieldCatalog.value.map(item=>item.resource))]);
/** 敏感类型仅取所选资源的真实目录，避免跨资源类型组合产生无结果筛选。 */
const fieldKinds=computed(()=>[...new Set(fieldCatalog.value.filter(item=>!fieldResource.value||item.resource===fieldResource.value).map(item=>item.kind))]);
watch(fieldResource,()=>{if(fieldKind.value&&!fieldKinds.value.some(kind=>kind===fieldKind.value))fieldKind.value="";});
const visibleFields=computed(()=>fieldCatalog.value.filter(item=>(!fieldResource.value||item.resource===fieldResource.value)&&(!fieldKind.value||item.kind===fieldKind.value)&&(!fieldQuery.value.trim()||[item.resource,item.field,item.nameZh,item.nameEn].some(value=>value.toLowerCase().includes(fieldQuery.value.trim().toLowerCase())))));
const fieldColumns=computed<ManagementColumn[]>(()=>[
 {key:"selection",label:t("authorizationCenter.fieldSelect"),width:72},
 {key:"resource",prop:"resource",label:t("authorizationCenter.fieldResource"),minWidth:160},
 {key:"name",label:t("authorizationCenter.fieldName"),minWidth:130,showOverflow:false},
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
const subjectsVisible=ref(false);
/** 统一弹框所有关闭路径均校验主体草稿，保存期间禁止关闭。 */
async function updateSubjectsVisible(value:boolean){if(value||await subjectEditor.value?.canLeave()!==false)subjectsVisible.value=value;}
const roleUsersEditor=ref<{canLeave:()=>Promise<boolean>}>();
const subjectEditor=ref<{canLeave:()=>Promise<boolean>}>(),memberEditor=ref<{canLeave:()=>Promise<boolean>}>();
/** 应用路由与页签共享离开校验；保存过程中禁止卸载，所有已挂载草稿均须确认。 */
async function canLeave(){return !saving.value&&(await roleUsersEditor.value?.canLeave()??true)&&await confirmDiscard()&&(await subjectEditor.value?.canLeave()??true)&&(await memberEditor.value?.canLeave()??true);}
defineExpose({canLeave});
const {t,locale,te}=useI18n();
/** 已知资源显示业务名称，未知资源保留稳定标识，不猜测领域含义。 */
function resourceLabel(resource:string){const declared=declarations.value.flatMap(d=>d.resources).find(r=>r.resource===resource);if(declared)return locale.value.startsWith('zh')?declared.nameZh:declared.nameEn;const key='authorizationCenter.resourceNames.'+resource.replaceAll('.','_');return te(key)?t(key):resource;}const roles=ref<RoleItem[]>([]),permissions=ref<PermissionItem[]>([]),metadata=ref<ScopeSupport[]>([]),organizations=ref<AuthorizationOption[]>([]);const roleId=ref(""),selected=ref<string[]>([]),policies=ref<Record<string,DataPolicy>>({}),version=ref(0),reason=ref(""),loading=ref(false),saving=ref(false),error=ref("");
const objectScopes = ref<Record<string, ObjectScope>>({});
function canSetObjectScope(resource: string) {
  return declarations.value.some(object => object.scope === "TENANT" && object.resources[0]?.resource === resource);
}
/** 复制已编辑的操作范围作为对象默认，保留全部显式覆盖，继承由管理员明确选择。 */
function setObjectScope(item: PermissionItem) {
  const policy = policies.value[item.id];
  if (!policy || !canSetObjectScope(policy.resourceType)) return;
  objectScopes.value[policy.resourceType] = {
    scopeType: policy.scopeType,
    rules: JSON.parse(JSON.stringify(policy.rules ?? []))
  };
}
/** 拷贝生效的默认规则到操作覆盖，编辑时不改变其他继承操作。 */
function createOverride(item: PermissionItem) {
  const policy = defaultFor(item);
  if (policy) policies.value[item.id] = JSON.parse(JSON.stringify(policy));
}
function defaultFor(item: PermissionItem): DataPolicy | undefined {
  return objectDefaultPolicy(item.id, supports(item.id), objectScopes.value);
}
/** 恢复继承会移除显式覆盖。保存时服务端校验对象规则对目标操作仍然适用。 */
function restoreInheritance(item: PermissionItem) {
  if (!defaultFor(item)) return;
  delete policies.value[item.id];
  delete scopeDraft.value[item.id];
}
function applyAssignment(value: AuthorizationAssignment) {
  objectScopes.value = { ...(value.objectScopes ?? {}) };
  const inherited = new Set(value.inheritedPermissionIds ?? []);
  policies.value = Object.fromEntries(value.dataPolicies
    .filter(item => !inherited.has(item.permissionId))
    .map(item => [item.permissionId, { ...item, organizationIds: [...item.organizationIds] }]));
}
/** 保存确认显示读取时的继承成员人数；实际授权仍由后端复核。 */
const inheritingMemberCount=ref(0),dataAccessVersion=ref<string>();
const selectedRole=computed(()=>roles.value.find(item=>item.id===roleId.value));
/** 角色目录已逐页完整读取；检索和分页仅改变左侧展示，不重置右侧授权草稿或伪造层级。 */
const roleQuery=ref(""),rolePage=ref(1),treeCollapsed=ref(false);
/** 左侧固定每页 20 个角色；不拆分真实父子关系，角色本身为平面目录。 */
const rolePageSize=20;
const filteredRoles=computed(()=>{const keyword=roleQuery.value.trim().toLowerCase();return roles.value.filter(role=>!keyword||[role.name,role.code].some(value=>value.toLowerCase().includes(keyword)));});
const visibleRoles=computed(()=>filteredRoles.value.slice((rolePage.value-1)*rolePageSize,rolePage.value*rolePageSize));
watch(roleQuery,()=>{rolePage.value=1;});
watch(()=>filteredRoles.value.length,total=>{rolePage.value=Math.min(rolePage.value,Math.max(1,Math.ceil(total/rolePageSize)));});

/** 已选失效项独立展示，不受搜索或菜单关联过滤，移除必须显式确认并正常保存。 */
const invalidPermissions=computed(()=>selected.value.filter(id=>!permissions.value.some(item=>item.id===id&&item.enabled)).map(id=>{const item=permissions.value.find(value=>value.id===id);return {id,name:item?label(item):id,code:item?.code??id};}));
/** 只修改草稿并移除对应数据策略；保存仍受版本与审计约束。 */
async function removeInvalidPermissions(){
 try{await ElMessageBox.confirm(t('invalidPermissionCleanup.confirm'),t('invalidPermissionCleanup.title'),{type:'warning',confirmButtonText:t('invalidPermissionCleanup.remove'),cancelButtonText:t('organization.cancel')});}
 catch{return;}
 invalidPermissions.value.forEach(item=>toggle(item.id,false));
}
const grouped=computed(()=>Object.entries(permissions.value.filter(item=>(editorTab.value!=="data"||(selected.value.includes(item.id)&&supports(item.id).length>0))&&(!onlySelected.value||selected.value.includes(item.id))&&(!query.value.trim()||[label(item),item.code,item.resource].some(value=>value.toLowerCase().includes(query.value.trim().toLowerCase())))).reduce<Record<string,PermissionItem[]>>((all,item)=>{(all[item.resource]??=[]).push(item);return all;},{})));
/** 菜单与资源视图编辑同一权限草稿，搜索与仅已选过滤作用于两种视图。 */
const menuTree=computed(()=>menuPermissionTree(menus.value,grouped.value.flatMap(([,items])=>items),locale.value.startsWith('zh'),t('authorizationCenter.unlinkedPermissions')));
function toggleMenu(ids:string[],enabled:boolean){ids.forEach(id=>toggle(id,enabled));}
/** 稳定比较完整草稿；字段顺序和组织选择顺序不构成授权变化。 */
function snapshot(){return JSON.stringify({ids:[...selected.value].sort(),objectScopes:Object.entries(objectScopes.value).sort(([left],[right])=>left.localeCompare(right)),fields:[...fieldPolicies.value].sort((a,b)=>(a.resource+a.field).localeCompare(b.resource+b.field)),policies:Object.values(policies.value).map(item=>({...item,organizationIds:[...item.organizationIds].sort()})).sort((a,b)=>a.permissionId.localeCompare(b.permissionId))});}
const changed=computed(()=>baseline.value!==null&&snapshot()!==baseline.value);
const incompleteCount = computed(() => selected.value.filter(id => {
  if (!supports(id).length) return false;
  const item = permissions.value.find(entry => entry.id === id);
  const policy = policies.value[id] ?? (item ? defaultFor(item) : undefined);
  return incompleteDataPolicy(policy, conditionDimensions(declarations.value, policy?.resourceType, item?.code));
}).length);
/**
 * 资源批选只改变当前筛选结果；不自动授予 ALL，也不推断数据范围。
 * @param items 当前资源下筛选后可见的权限，禁用权限不参与批量变更。
 * @param enabled 为 true 时加入草稿，为 false 时同时移除功能和对应数据策略。
 */
function toggleGroup(items:PermissionItem[],enabled:boolean){items.filter(item=>item.enabled).forEach(item=>toggle(item.id,enabled));}
async function confirmDiscard(){if(!changed.value&&!reason.value.trim())return true;try{await ElMessageBox.confirm(t("authorizationCenter.discard"),t("authorizationCenter.title"),{confirmButtonText:t("authorizationCenter.discardAction"),cancelButtonText:t("organization.cancel"),type:"warning"});return true;}catch{return false;}}
async function selectRole(id:string){if(saving.value||id===roleId.value||!(await memberEditor.value?.canLeave()??true)||!(await roleUsersEditor.value?.canLeave()??true)||!(await confirmDiscard()))return;roleId.value=id;if(!props.memberDataOnly)await loadAssignment();}
function supports(id:string){return metadata.value.filter(item=>item.permissionId===id)}function label(item:PermissionItem){return locale.value.startsWith("zh")?item.nameZh:item.nameEn}
function toggle(id:string,enabled:boolean){if(enabled){if(!selected.value.includes(id))selected.value.push(id);}else{selected.value=selected.value.filter(value=>value!==id);delete policies.value[id];}}
function scopeChanged(permissionId:string,choice:ScopeChoice){
 const type=persistedScope(choice),support=supports(permissionId).find(item=>item.scopeType===type);if(!support)return;
 if(choice==="RECORDS"||choice==="RULES")scopeDraft.value[permissionId]=choice;
 else delete scopeDraft.value[permissionId];
 const previous=policies.value[permissionId];
 if(previous?.scopeType==="RULES"&&type==="RULES"&&(choice==="RULES"||shownScope(previous)==="RECORDS"))return;
 policies.value[permissionId]={permissionId,scopeType:type,resourceType:support.resourceType,organizationIds:[],rules:[]};
}
/** 角色切换后清除旧快照，过期响应不得写入新角色；读取失败时禁止覆盖保存。 */
/** 错误复用公共浮层；更新或离开页面时释放，重读由用户显式触发。 */
watch(error, (message, _previous, onCleanup) => {
  if (!message) return;
  const release = reportError(message, h("div", [
    h("p", message),
    h(ElButton, {
      link: true,
      type: "primary",
      onClick: () => { release(); void load(); }
    }, () => t("account.retry"))
  ]));
  onCleanup(release);
});
async function loadAssignment(){if(!roleId.value)return;const request=++requestVersion;loading.value=true;ready.value=false;error.value="";baseline.value=null;fieldSelection.value=[];fieldPolicies.value=[];selected.value=[];policies.value={};objectScopes.value={};scopeDraft.value={};reason.value="";try{const value=await props.api.assignment(roleId.value);if(request!==requestVersion)return;inheritingMemberCount.value=value.inheritingMemberCount??0;dataAccessVersion.value=value.dataAccessVersion;selected.value=[...value.permissionIds];applyAssignment(value);version.value=value.roleVersion;fieldPolicies.value=[...(value.fieldPolicies??[])];baseline.value=snapshot();ready.value=true;}catch(cause){if(request===requestVersion)await setErrorFeedback(error, props.errorText(cause));}finally{if(request===requestVersion)loading.value=false;}}
/** 读取完整角色目录，避免超过每页 100 条后无法找到目标角色。 */
async function loadRoles(){const result:RoleItem[]=[];for(let page=1;;page++){const value=await props.roleApi.list({query:"",status:"ENABLED",page,pageSize:100});result.push(...value.items);if(result.length>=value.total)return result;if(!value.items.length)throw new Error(t("authorizationCenter.roleListChanged"));}}
async function load(){if(saving.value||!(await roleUsersEditor.value?.canLeave()??true)||!(await confirmDiscard()))return;loading.value=true;ready.value=false;error.value="";try{if(props.memberDataOnly){roles.value=await loadRoles();roleId.value=roles.value.some(role=>role.id===roleId.value)?roleId.value:(roles.value[0]?.id??"");return;}const [catalog,items,support,orgs,fields,navigation,declared,values]=await Promise.all([loadRoles(),props.api.permissions(),props.api.metadata(),props.api.organizationOptions?.()??Promise.resolve([]),props.api.fields(),props.api.menus(),props.api.declarations?.()??Promise.resolve([]),props.api.conditionOptions?.()??Promise.resolve({})]);declarations.value=declared;conditionOptions.value=values;
if(declared.some(d=>d.dimensions?.length)){const all:import("./index").AuthorizationOption[]=[];for(let page=1;;page++){const batch=await props.subjectApi.subjects(page,"");all.push(...batch.items);if(all.length>=batch.total)break;if(!batch.items.length)throw new Error(t("authorizationCenter.roleListChanged"));}grantMembers.value=all;}
menus.value=navigation;fieldCatalog.value=fields;roles.value=catalog;permissions.value=items;metadata.value=support;organizations.value=orgs;roleId.value=catalog.some(item=>item.id===roleId.value)?roleId.value:(catalog[0]?.id??"");if(roleId.value)await loadAssignment();else{baseline.value=null;fieldSelection.value=[];fieldPolicies.value=[];selected.value=[];policies.value={};objectScopes.value={};reason.value="";}}catch(cause){await setErrorFeedback(error, props.errorText(cause));}finally{loading.value=false;}}
const invalid=computed(()=>!ready.value||loading.value||!changed.value||incompleteCount.value>0||!reason.value.trim());
/** 完整授权覆盖写影响全部角色成员；失败后要求重读版本，禁止盲目重试。 */
async function save(){if(!roleId.value||invalid.value||saving.value)return;saving.value=true;error.value="";try{await ElMessageBox.confirm(t("authorizationCenter.roleReplaceWarning",{name:selectedRole.value?.name,count:inheritingMemberCount.value}),t("authorizationCenter.summary"),{confirmButtonText:t("organization.save"),cancelButtonText:t("organization.cancel"),type:"warning"});}catch{saving.value=false;return;}try{const value=await props.api.replace(roleId.value,{ids:[...selected.value],dataPolicies:Object.values(policies.value),objectScopes:objectScopes.value,fieldPolicies:fieldPolicies.value,dataAccessVersion:dataAccessVersion.value,version:version.value,reason:reason.value.trim()});inheritingMemberCount.value=value.inheritingMemberCount??0;dataAccessVersion.value=value.dataAccessVersion;selected.value=[...value.permissionIds];applyAssignment(value);version.value=value.roleVersion;fieldPolicies.value=[...(value.fieldPolicies??[])];baseline.value=snapshot();reason.value="";ElMessage.success(t("authorizationCenter.saved"));}catch(cause){await setErrorFeedback(error, props.errorText(cause));ready.value=false;}finally{saving.value=false;}}
onBeforeUnmount(()=>{++requestVersion;});
onMounted(load);
</script>
<template>
  <BusinessWorkspace class="main-content" v-bind="$attrs" :title="pageTitle ?? t('authorizationCenter.title')"
    :description="memberDataOnly ? undefined : t('authorizationCenter.description')">
    <template #data>
      <TreeListTemplate v-model:collapsed="treeCollapsed" :tree-label="t('authorizationCenter.roles')" embedded class="authorization-workspace">
        <template #treeHeader>
          <!-- 主体角色入口归入角色栏标题，复用既有弹框及草稿关闭保护。 -->
          <div class="role-heading">
            <strong>{{ t('authorizationCenter.roles') }}</strong>
            <el-tooltip v-if="!memberDataOnly" :content="t('authorizationCenter.subjectRoles')">
              <el-button text :aria-label="t('authorizationCenter.subjectRoles')" @click="subjectsVisible=true"><AppIcon name="user" :size="18" /></el-button>
            </el-tooltip>
          </div>
        </template>
        <template #tree>
          <div v-loading="loading" class="role-browser">
            <el-input v-model="roleQuery" clearable :maxlength="100" :placeholder="t('authorizationCenter.roleSearch')" :aria-label="t('authorizationCenter.roleSearch')" />
            <div class="role-list-scroll">
            <el-tree :data="visibleRoles" node-key="id" :props="{label:'name'}" :current-node-key="roleId" :highlight-current="false" :expand-on-click-node="false" :aria-label="t('authorizationCenter.roles')">
              <template #default="{data}">
                <el-button text class="role-node" :class="{'is-selected':data.id===roleId}" :disabled="saving || loading" :aria-pressed="data.id===roleId" :title="data.name" @click.stop="selectRole(data.id)"><span>{{ data.name }}</span></el-button>
              </template>
            </el-tree>
            <el-empty v-if="!filteredRoles.length" :image-size="56" :description="t(roleQuery ? 'authorizationCenter.noMatchingRoles' : 'authorizationCenter.noRoles')" />
            </div>
            <div class="role-pagination"><AppPagination v-model:current-page="rolePage" :page-size="rolePageSize" :total="filteredRoles.length" layout="total, prev, next" /></div>
          </div>
        </template>
        <template #table>
          <div class="authorization-content" :class="{'is-role-users':activeTab==='roleUsers','is-role-editor':isRoleTab||activeTab==='memberData','is-field-editor':activeTab==='security'}">
      <el-tabs v-if="!memberDataOnly" v-model="activeTab">
        <el-tab-pane :label="t('authorizationCenter.permissions')" name="permissions" />
        <el-tab-pane v-if="metadata.length" :label="t('authorizationCenter.roleDefaultData')" name="data" />
        <el-tab-pane :label="t('authorizationCenter.fieldSecurity')" name="security" />
        <el-tab-pane v-if="subjectApi.grants" :label="t('authorizationCenter.memberData')" name="memberData" />
        <el-tab-pane :label="t('authorizationCenter.roleUsers')" name="roleUsers" />
      </el-tabs>
      <el-alert v-if="isRoleTab && invalidPermissions.length" type="warning" :closable="false" :title="t('invalidPermissionCleanup.title')">
        <ul><li v-for="item in invalidPermissions" :key="item.id">{{ item.name }}（{{ item.code }}）：{{ t('invalidPermissionCleanup.reason') }}</li></ul>
        <el-button :disabled="!ready || saving" @click="removeInvalidPermissions">{{ t('invalidPermissionCleanup.remove') }}</el-button>
      </el-alert>
      <RoleUsers v-if="!memberDataOnly" v-show="activeTab==='roleUsers'" ref="roleUsersEditor" :role-id="roleId" :role-name="selectedRole?.name??''" :active="activeTab==='roleUsers'" :api="subjectApi" :error-text="errorText" />

      <MemberGrantEditor :role-id="roleId" :role-name="selectedRole?.name??''" :active="activeTab==='memberData'" ref="memberEditor" v-if="subjectApi.grants" v-show="activeTab === 'memberData'" :api="subjectApi" :role-api="roleApi" :authorization-api="api" :error-text="errorText" />
      <div v-if="!memberDataOnly" v-show="isRoleTab" v-loading="loading" class="authorization-grid">
        <section class="authorization-pane permission-pane">
          <div class="editor-heading"><h2>{{ t(activeTab==='permissions'?'authorizationCenter.permissions':activeTab==='data'?'authorizationCenter.roleDefaultData':'authorizationCenter.fieldSecurity') }}<InfoHint v-if="editorTab==='security'" :content="t('authorizationCenter.fieldSecurityHint')" /><InfoHint v-if="editorTab === 'data'" :content="t('authorizationCenter.dataPolicyHint')" /><InfoHint v-if="editorTab==='functions'" :content="t('authorizationCenter.menuFunctionHint')" /></h2><el-tag class="app-status-tag" v-if="changed" type="warning">{{t('authorizationCenter.unsavedChanges')}}</el-tag></div>
          <template v-if="editorTab === 'security'">
            
            <el-empty v-if="!fieldCatalog.length" :description="t('authorizationCenter.noFieldCatalog')" />
            <el-form label-position="top" class="business-border-label-form field-filter" @submit.prevent>
              <el-form-item :label="t('authorizationCenter.fieldResource')"><el-select v-model="fieldResource" clearable filterable><el-option v-for="resource in fieldResources" :key="resource" :value="resource" :label="resourceLabel(resource)" /></el-select></el-form-item>
              <el-form-item :label="t('authorizationCenter.fieldKind')"><el-select v-model="fieldKind" clearable><el-option v-for="kind in fieldKinds" :key="kind" :value="kind" :label="t('authorizationCenter.fieldKinds.'+kind)" /></el-select></el-form-item>
              <el-form-item :label="t('authorizationCenter.fieldSearch')"><el-input v-model="fieldQuery" clearable :maxlength="100" /></el-form-item>
            </el-form>
            <ManagementTable :title="t('authorizationCenter.fieldSecurity')" :rows="visibleFields" :columns="fieldColumns" :fit-height="true" class="field-policy-table" :busy="saving" :loading="loading" embedded @refresh="load">
              <template #actions>
                <el-checkbox :model-value="visibleFields.length>0&&visibleFields.every(item=>fieldSelection.includes(fieldKey(item)))" :indeterminate="visibleFields.some(item=>fieldSelection.includes(fieldKey(item)))&&!visibleFields.every(item=>fieldSelection.includes(fieldKey(item)))" :disabled="!ready||saving" @change="value=>selectFields(Boolean(value))">{{t('authorizationCenter.selectFilteredFields')}}</el-checkbox>
                <el-select v-model="bulkFieldMode" :aria-label="t('authorizationCenter.fieldRule')" :disabled="!ready||saving" class="field-bulk-mode"><el-option v-for="mode in ['READ_ONLY','MASKED','HIDDEN','NONE']" :key="mode" :value="mode" :label="t('authorizationCenter.fieldModes.'+mode)" /></el-select>
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
          <el-form label-position="top" class="business-border-label-form permission-filter" @submit.prevent>
            <el-form-item :label="t('authorizationCenter.permissionQuery')"><el-input v-model="query" clearable :maxlength="100" /></el-form-item>
            <el-checkbox v-model="onlySelected">{{ t('authorizationCenter.onlySelected') }}</el-checkbox>
          </el-form>
          
          <el-radio-group v-if="editorTab==='functions'" v-model="functionView" class="function-view" :aria-label="t('authorizationCenter.functionView')"><el-radio-button value="menus">{{t('authorizationCenter.menuFunctions')}}</el-radio-button><el-radio-button value="resources">{{t('authorizationCenter.resourceView')}}</el-radio-button></el-radio-group>
          <template v-if="editorTab==='functions'&&functionView==='menus'"><!-- 授权说明收束为可聚焦的提示入口，不占用整行说明区域。 --><MenuPermissionBranch v-for="node in menuTree" :key="node.id" :node="node" :selected="selected" :disabled="!ready||saving" @toggle="toggleMenu" /></template>
          <el-empty v-if="!grouped.length" :description="t(permissions.length ? 'authorizationCenter.noMatchingPermissions' : 'authorizationCenter.noPermissions')" />
          <div v-for="[resource,items] in (editorTab==='functions'&&functionView==='menus'?[]:grouped)" :key="resource" class="permission-group">
            <template v-if="editorTab === 'functions' || items.some(item => selected.includes(item.id) && supports(item.id).length)">
              <div class="permission-group-heading"><h3>{{ resourceLabel(resource) }}</h3>
              <el-checkbox v-if="editorTab === 'functions'" :disabled="!ready || saving" :model-value="items.filter(item => item.enabled).length > 0 && items.filter(item => item.enabled).every(item => selected.includes(item.id))" :indeterminate="items.some(item => selected.includes(item.id)) && !items.filter(item => item.enabled).every(item => selected.includes(item.id))" @change="value => toggleGroup(items,Boolean(value))">{{ t('authorizationCenter.selectResource') }}</el-checkbox></div>
              <div v-if="editorTab === 'data' && objectScopes[resource]" class="permission-group-heading">
                <span>{{ t('authorizationCenter.objectDefault') }} · {{ t('authorizationCenter.scopes.' + objectScopes[resource].scopeType) }}</span>
                <el-button link :disabled="!ready || saving" @click="delete objectScopes[resource]">{{ t('authorizationCenter.clearObjectDefault') }}</el-button>
              </div>
              <div class="permission-items" :class="{'permission-items-functions':editorTab==='functions'}">
              <template v-for="item in items" :key="item.id">
                <div v-if="editorTab === 'functions' || (selected.includes(item.id) && supports(item.id).length)" class="permission-row" :class="{ 'function-row': editorTab === 'functions' }">
                  <el-checkbox v-if="editorTab === 'functions'" :disabled="!ready || saving || !item.enabled" :model-value="selected.includes(item.id)" @change="value => toggle(item.id,Boolean(value))"><strong>{{ label(item) }}</strong><span>{{ item.code }}</span></el-checkbox>
                  <div v-else><strong>{{ label(item) }}</strong><div class="permission-code">{{ item.code }}</div></div>
                  <template v-if="editorTab === 'data'">
                    <el-select :model-value="shownScope(policies[item.id] ?? defaultFor(item),scopeDraft[item.id])" :aria-label="label(item) + ' · ' + t('authorizationCenter.selectScope')" :disabled="!ready || saving" :placeholder="t('authorizationCenter.selectScope')" @change="value => scopeChanged(item.id,value as ScopeChoice)">
                      <el-option v-for="choice in scopeChoices(supports(item.id),item.code,!!conditionDimensions(declarations,item.resource,item.code).length)" :key="choice" :label="t('authorizationCenter.scopes.'+choice)" :value="choice" />
                    </el-select>
                    <div v-if="canSetObjectScope(item.resource)">
                      <template v-if="!policies[item.id] && defaultFor(item)">
                        <el-tag type="info">{{ t('authorizationCenter.inheritsObjectDefault') }}</el-tag>
                        <el-button link type="primary" :disabled="!ready || saving" @click="createOverride(item)">{{ t('authorizationCenter.createObjectOverride') }}</el-button>
                      </template>
                      <template v-if="policies[item.id]">
                        <el-button link type="primary" :disabled="!ready || saving || incompleteDataPolicy(policies[item.id], conditionDimensions(declarations,item.resource,item.code))" @click="setObjectScope(item)">{{ t('authorizationCenter.setObjectDefault') }}</el-button>
                        <el-button v-if="defaultFor(item)" link type="primary" :disabled="!ready || saving" @click="restoreInheritance(item)">{{ t('authorizationCenter.restoreObjectDefault') }}</el-button>
                      </template>
                    </div>
                    <DataGrantRuleEditor :record-only="shownScope(policies[item.id],scopeDraft[item.id])==='RECORDS'" :allow-records="!item.code.endsWith(':create')" :resource="item.resource" :record-search="api.recordSearch" :error-text="errorText" v-if="policies[item.id]?.scopeType==='RULES'" :model-value="policies[item.id].rules??[]" :dimensions="conditionDimensions(declarations,item.resource,item.code)" :condition-options="conditionOptions" :organizations="organizations" :members="grantMembers" :disabled="!ready||saving" @update:model-value="value=>policies[item.id].rules=value" />
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
          <div class="summary-actions">
          <PolicyTemplateActions :api="props.api" :role-id="roleId" :version="version" :busy="saving || loading || !ready" :dirty="changed" :reason="reason" :error-text="props.errorText" @applied="loadAssignment" />
            <el-button v-operation="'@role:grant'" class="save-button" type="primary" @click="save" :loading="saving" :disabled="invalid">{{ t('organization.save') }}</el-button>
          </div>
          <el-button v-if="incompleteCount" link type="warning" @click="activeTab='data';query='';onlySelected=true">{{ t('authorizationCenter.pendingScopes', { count: incompleteCount }) }}</el-button>
          <el-form class="business-border-label-form save-form" label-position="top" @submit.prevent="save">
            <el-form-item :label="t('authorizationCenter.reason')" required><el-input v-model="reason" type="textarea" :rows="2" :maxlength="500" show-word-limit :disabled="!ready || saving" /></el-form-item>
            <el-alert v-if="incompleteCount" type="warning" :title="t('authorizationCenter.incomplete')" :closable="false" show-icon />
          </el-form>
        </section>
      </div>
          </div>
        </template>
      </TreeListTemplate>
    </template>
  </BusinessWorkspace>
  <ManagedDialog :model-value="subjectsVisible" :title="t('authorizationCenter.subjectRoles')" width="min(1400px, calc(100vw - 32px))" height="min(760px, calc(100vh - 80px))" :close-on-click-modal="false" :close-on-press-escape="false" @update:model-value="updateSubjectsVisible">
    <SubjectAuthorization v-if="subjectsVisible" ref="subjectEditor" :api="subjectApi" :role-api="roleApi" :error-text="errorText" />
  </ManagedDialog>
</template>
<style scoped>
/* 页面外围留白引用全局内容与区块 Token，局部字段间距和触控高度按原交互保留。 */
/* 复用公共左树右内容模板；右侧页签与保存共享表面，角色列表不虚构父子关系。 */
.role-heading{display:flex;align-items:center;justify-content:space-between;gap:8px;min-width:0}
.authorization-content{min-width:0;overflow:auto}
/* 双表页向下传递剩余高度，各表体独立滚动，分页保留在各自底部。 */
.authorization-content.is-role-editor{display:flex;flex:1;flex-direction:column;min-height:0;overflow:hidden}
.authorization-content.is-role-editor>.el-tabs{flex:none}
/* 桌面授权区传递剩余高度，摘要固定在工作区底部，表体或权限内容独立滚动。 */
.is-role-editor .authorization-grid{flex:1;min-height:0;overflow:hidden}
.is-role-editor .permission-pane{flex:1;min-height:0;overflow:auto}
.is-role-editor .summary-pane{flex:none}
.is-field-editor .permission-pane{display:flex;flex-direction:column;overflow:hidden}
.is-field-editor .permission-pane>.editor-heading,.is-field-editor .field-filter{flex:none}
.is-field-editor .field-policy-table{flex:1;min-height:180px}
.summary-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px 16px}
.summary-actions>.template-actions{flex:1;min-width:0}
.authorization-content.is-role-users{display:flex;flex:1;flex-direction:column;min-height:0}
.authorization-content.is-role-users>.el-tabs{flex:none}
/* 左栏沿公共模板高度链填满剩余空间；仅列表滚动，检索和统一分页始终可见。 */
.authorization-workspace :deep(.tree-list-template__tree){display:flex;flex-direction:column;overflow:hidden}
.role-browser{display:flex;flex:1;flex-direction:column;gap:12px;min-width:0;min-height:0;overflow:hidden}
.role-browser>.el-input{flex:none}
.role-list-scroll{flex:1;min-height:0;overflow:auto}
.role-pagination{margin-top:auto;overflow-x:auto;flex-shrink:0;padding-top:8px;border-top:1px solid var(--el-border-color-lighter)}
.role-node{display:flex;flex-direction:column;align-items:flex-start;justify-content:center;width:100%;height:auto;min-height:48px;padding:6px 8px;margin:0;overflow:hidden}
.role-node span{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.role-node.is-selected{background:var(--el-color-primary-light-9);color:var(--el-color-primary);box-shadow:inset 2px 0 var(--el-color-primary)}
.role-browser :deep(.el-tree-node__content){height:auto;min-height:48px}
.role-browser :deep(.el-tree-node__expand-icon.is-leaf){display:none}
.role-node :deep(.el-button__text){display:flex;flex-direction:column;align-items:flex-start;min-width:0;max-width:100%}
/* 查询、视图切换和首个分组之间采用 4px 紧凑间距，不影响数据范围及字段表单。 */
.function-view{margin-bottom:4px}
.authorization-grid{display:flex;flex-direction:column;min-width:0}
.authorization-pane{min-width:0;padding:var(--app-content-padding,12px);background:var(--el-bg-color)}
.authorization-pane+.authorization-pane{border-top:1px solid var(--el-border-color-lighter)}
h2{font-size:var(--el-font-size-medium);margin:0;font-weight:600}
.role-context{display:flex;align-items:center;justify-content:space-between;gap:24px;background:var(--el-fill-color-lighter)}
.role-context-label{display:flex;flex-direction:column;gap:6px;min-width:0}
.role-context-label span{color:var(--el-text-color-secondary);font-size:var(--el-font-size-small)}
.role-context>.el-select{width:360px;max-width:100%;flex-shrink:0}
.editor-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:var(--app-section-gap,8px)}
.permission-filter{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:8px;padding:4px 8px;background:var(--el-fill-color-lighter);margin-bottom:4px;border-radius:var(--el-border-radius-base)}
.permission-filter .el-form-item{margin:0;flex:1;max-width:520px;min-width:200px}
.permission-group{margin-top:4px;border:1px solid var(--el-border-color-lighter);border-radius:var(--el-border-radius-base);overflow:hidden}
.permission-group-heading{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px 16px;padding:4px 12px;background:var(--el-fill-color-lighter)}
.permission-group h3{margin:0;font-size:14px;font-weight:600;overflow-wrap:anywhere;color:var(--el-text-color-regular)}
.permission-items{padding:0 16px}
/* 功能清单桌面端统一四列，窄屏回退两列；字段与数据范围表单不受影响。 */
.permission-items-functions{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0 16px}
.permission-row{display:grid;grid-template-columns:minmax(180px,1fr) minmax(160px,1fr) minmax(180px,1.5fr);gap:16px;align-items:center;padding:12px 0;border-bottom:1px solid var(--el-border-color-lighter)}
.permission-row:last-child{border-bottom:0}
/* 组合规则独占整行，避免在旧组织选择列中压缩多条件表单。 */
.permission-row>.grant-rules{grid-column:1/-1;min-width:0}
/* 仅压缩功能权限条目的上下留白，不改变字段和数据范围编辑表单。 */
.permission-row.function-row{grid-template-columns:1fr;padding:4px 0}
.permission-row :deep(.el-checkbox){max-width:100%;height:auto;min-height:40px;align-items:flex-start}
.permission-row :deep(.el-checkbox__input){margin-top:4px}
.permission-row :deep(.el-checkbox__label){display:flex;flex-direction:column;gap:4px;white-space:normal;overflow-wrap:anywhere}
.permission-row strong{font-weight:500}
.permission-row span,.permission-code{font-size:12px;color:var(--el-text-color-secondary);overflow-wrap:anywhere}
.field-filter{display:flex;flex-wrap:wrap;gap:12px;padding:8px;background:var(--el-fill-color-lighter);margin:8px 0;border-radius:var(--el-border-radius-base)}
.field-filter .el-form-item{min-width:160px;flex:1;margin-bottom:0}
.field-bulk-mode{width:150px}
/* 紧凑布局保留模板操作、变更原因及保存校验。 */
.summary-pane{display:flex;flex-direction:column;gap:8px}
.save-summary{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:0}
.save-summary>span{color:var(--el-text-color-secondary)}
.save-form{display:grid;grid-template-columns:minmax(0,1fr);gap:8px;align-items:end}
.save-form>.el-form-item{margin-bottom:0}
.save-form>.el-alert{grid-column:1/-1;grid-row:1}
.save-button{min-width:120px;align-self:end}
@media(min-width:701px) and (max-width:1100px){.permission-items-functions{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:1100px){.permission-row{grid-template-columns:minmax(0,1fr) minmax(160px,1fr)}.permission-row>.el-select:last-child{grid-column:2}.permission-row.function-row{grid-template-columns:1fr}}
/* 手机恢复自然纵向滚动，避免底部摘要挤压字段表体和触控区域。 */
@media(max-width:700px){.authorization-content.is-role-editor{overflow:auto}.is-role-editor .authorization-grid{flex:none;overflow:visible}.is-role-editor .permission-pane,.is-field-editor .permission-pane{flex:none;overflow:visible}.is-field-editor .field-policy-table{flex:none;min-height:260px}.summary-actions{align-items:stretch;flex-direction:column}.authorization-pane{padding:var(--app-content-padding,12px)}.role-context{align-items:stretch;flex-direction:column;gap:12px}.role-context>.el-select{width:100%}.permission-items-functions{grid-template-columns:1fr}.permission-group-heading{align-items:flex-start;flex-direction:column}.permission-filter{align-items:stretch;flex-direction:column}.permission-filter .el-form-item{max-width:none;min-width:0}.permission-row{grid-template-columns:1fr}.permission-row>.el-select:last-child{grid-column:auto}.save-form{grid-template-columns:1fr}.save-button{width:100%}.field-filter{flex-direction:column}.field-filter .el-form-item{min-width:0}:deep(.el-button){min-height:44px}}
</style>
