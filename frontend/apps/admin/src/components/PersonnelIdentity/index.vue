<script setup lang="ts">
import {computed,ref,watch} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessageBox} from "element-plus";
import {http,requestErrorMessage} from "@/utils/http";
import {currentTenant,beginTenantSwitch,endTenantSwitch,refreshTenantWorkspace} from "@/utils/tenantContext";
import {ManagedDialog,FloatingError,setErrorFeedback} from "@appnexus/ui";
/** 本次业务身份选择器；不保存 Token 或把客户端展示状态作为服务端授权，选择由服务器复核。 */
interface Identity {personId:string;personName:string;organizationId:string;organizationName:string}
const {t}=useI18n();const open=ref(false),loading=ref(false),busy=ref(false),items=ref<Identity[]>([]),value=ref(""),selected=ref<Identity>(),error=ref("");
/** 按人员去重；同一人员的多个组织仍须明确选择，不能自动合并业务范围。 */
const singlePerson=computed(()=>items.value.length>0&&items.value.every(item=>item.personId===items.value[0].personId));
const singleIdentity=computed(()=>items.value.length===1);
const label=computed(()=>selected.value?selected.value.personName+" / "+selected.value.organizationName:t("identityModel.none"));
/**
 * 租户变化后读取服务端身份；唯一人员与组织自动确认，多候选保留显式选择。
 * 失效回调丢弃旧租户响应，自动确认失败不发布客户端身份，后端继续默认拒绝。
 */
watch(()=>currentTenant.value?.tenantId,async(tenantId,_previous,onCleanup)=>{
 let stale=false;onCleanup(()=>{stale=true;});
 selected.value=undefined;window.dispatchEvent(new CustomEvent("appnexus:person-selected",{detail:undefined}));
 items.value=[];value.value="";open.value=false;
 if(!tenantId)return;
 loading.value=true;
 try{
  const [all,current]=await Promise.all([http.request<Identity[]>("get","/api/master-data/person/my-identities"),http.request<{personId:string;organizationId:string}|undefined>("get","/api/master-data/person/identity")]);
  if(stale)return;
  items.value=all;
  let identity=all.find(item=>item.personId===current?.personId&&item.organizationId===current?.organizationId);
  if(!identity&&all.length===1){
   let locked=false;
   try{
    beginTenantSwitch();locked=true;
    const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
    if(stale)return;
    await http.request("post","/api/master-data/person/identity",{data:{personId:all[0].personId,organizationId:all[0].organizationId},headers:{[csrf.headerName]:csrf.token}});
    identity=all[0];
   }finally{if(locked)endTenantSwitch();}
  }
  if(stale)return;
  selected.value=identity;
  window.dispatchEvent(new CustomEvent("appnexus:person-selected",{detail:identity}));
 }catch(e){if(!stale)await setErrorFeedback(error,requestErrorMessage(e));}
 finally{if(!stale)loading.value=false;}
},{immediate:true});
/** 只读加载自身有效身份；关系撤销后不能沿用旧候选项。 */
async function show(){open.value=true;value.value="";loading.value=true;const tenantId=currentTenant.value?.tenantId;try{const all=await http.request<Identity[]>("get","/api/master-data/person/my-identities");if(tenantId!==currentTenant.value?.tenantId)return;items.value=all;const index=all.findIndex(item=>item.personId===selected.value?.personId&&item.organizationId===selected.value?.organizationId);value.value=index>=0?String(index):all.length===1?"0":"";}catch(e){items.value=[];await setErrorFeedback(error,requestErrorMessage(e));}finally{loading.value=false;}}
/** 切换前提示未保存内容，等待在途写结束；服务端选定后重建内容区，不合并人员组织或权限。 */
async function select(){const item=items.value[Number(value.value)];if(!item||busy.value)return;
 try{await ElMessageBox.confirm(t("tenant.switchConfirm"),t("identityModel.select"),{confirmButtonText:t("identityModel.select"),cancelButtonText:t("organization.cancel")});}catch{return;}
 busy.value=true;let locked=false;try{beginTenantSwitch();locked=true;const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
 await http.request("post","/api/master-data/person/identity",{data:{personId:item.personId,organizationId:item.organizationId},headers:{[csrf.headerName]:csrf.token}});
 endTenantSwitch();locked=false;selected.value=item;window.dispatchEvent(new CustomEvent("appnexus:person-selected",{detail:item}));open.value=false;await refreshTenantWorkspace();}catch(e){await setErrorFeedback(error,requestErrorMessage(e));}finally{if(locked)endTenantSwitch();busy.value=false;}}
</script>
<template>
 <span v-if="currentTenant && singleIdentity" v-operation="'Person.myIdentities'" class="personnel-identity personnel-identity-name" :title="items[0].personName">{{items[0].personName}}</span>
 <el-button v-else-if="currentTenant" v-operation="'Person.myIdentities'" text :disabled="loading" :title="label" :aria-label="t('identityModel.select')" class="personnel-identity" @click="show">{{singlePerson ? items[0].personName + (selected ? " / " + selected.organizationName : "") : label}}</el-button>
 <ManagedDialog v-model="open" :title="t('identityModel.select')" :busy="busy"><FloatingError :title="error" />
  <el-empty v-if="!loading&&!items.length" :description="t('identityModel.empty')" />
  <el-form v-else label-position="top"><el-form-item :label="t('identityModel.select')"><el-select v-model="value" :loading="loading" filterable><el-option v-for="(item,index) in items" :key="item.personId+item.organizationId" :value="String(index)" :label="item.personName+' / '+item.organizationName" /></el-select></el-form-item></el-form>
  <template #footer><el-button :disabled="busy" @click="open=false">{{t('organization.cancel')}}</el-button><el-button v-operation="'Person.selectIdentity'" type="primary" :loading="busy" :disabled="!value||!items.length" @click="select">{{t('identityModel.select')}}</el-button></template>
 </ManagedDialog>
</template>
<style scoped>
/* 复用顶栏主题和点击区；长人员/组织名称省略但保留完整 title。 */
.personnel-identity-name{display:inline-flex;align-items:center;padding:0 12px;color:var(--el-text-color-regular);font-size:var(--el-font-size-base);}
.personnel-identity{height:48px;max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
@media(max-width:600px){.personnel-identity{max-width:100px;}}
</style>
