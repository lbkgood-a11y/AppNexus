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
const label=computed(()=>selected.value?selected.value.personName+" / "+selected.value.organizationName:t("identityModel.none"));
watch(()=>currentTenant.value?.tenantId,async()=>{selected.value=undefined;window.dispatchEvent(new CustomEvent("appnexus:person-selected",{detail:undefined}));items.value=[];open.value=false;if(!currentTenant.value)return;try{const [all,current]=await Promise.all([http.request<Identity[]>("get","/api/master-data/person/my-identities"),http.request<{personId:string;organizationId:string}|undefined>("get","/api/master-data/person/identity")]);items.value=all;selected.value=all.find(item=>item.personId===current?.personId&&item.organizationId===current?.organizationId);window.dispatchEvent(new CustomEvent("appnexus:person-selected",{detail:selected.value}));}catch{/* 展示查询失败不扩大身份，业务请求由后端默认拒绝。 */}},{immediate:true});
/** 只读加载自身有效身份；关系撤销后不能沿用旧候选项。 */
async function show(){open.value=true;loading.value=true;try{items.value=await http.request("get","/api/master-data/person/my-identities");}catch(e){items.value=[];await setErrorFeedback(error,requestErrorMessage(e));}finally{loading.value=false;}}
/** 切换前提示未保存内容，等待在途写结束；服务端选定后重建内容区，不合并人员组织或权限。 */
async function select(){const item=items.value[Number(value.value)];if(!item||busy.value)return;
 try{await ElMessageBox.confirm(t("tenant.switchConfirm"),t("identityModel.select"),{confirmButtonText:t("identityModel.select"),cancelButtonText:t("organization.cancel")});}catch{return;}
 busy.value=true;let locked=false;try{beginTenantSwitch();locked=true;const csrf=await http.request<{headerName:string;token:string}>("get","/bff/csrf");
 await http.request("post","/api/master-data/person/identity",{data:{personId:item.personId,organizationId:item.organizationId},headers:{[csrf.headerName]:csrf.token}});
 endTenantSwitch();locked=false;selected.value=item;window.dispatchEvent(new CustomEvent("appnexus:person-selected",{detail:item}));open.value=false;await refreshTenantWorkspace();}catch(e){await setErrorFeedback(error,requestErrorMessage(e));}finally{if(locked)endTenantSwitch();busy.value=false;}}
</script>
<template>
 <el-button v-if="currentTenant" v-operation="'Person.myIdentities'" text :title="label" :aria-label="t('identityModel.select')" class="personnel-identity" @click="show">{{label}}</el-button>
 <ManagedDialog v-model="open" :title="t('identityModel.select')" :busy="busy"><FloatingError :title="error" />
  <el-empty v-if="!loading&&!items.length" :description="t('identityModel.empty')" />
  <el-form v-else label-position="top"><el-form-item :label="t('identityModel.select')"><el-select v-model="value" :loading="loading" filterable><el-option v-for="(item,index) in items" :key="item.personId+item.organizationId" :value="String(index)" :label="item.personName+' / '+item.organizationName" /></el-select></el-form-item></el-form>
  <template #footer><el-button :disabled="busy" @click="open=false">{{t('organization.cancel')}}</el-button><el-button v-operation="'Person.selectIdentity'" type="primary" :loading="busy" :disabled="!value||!items.length" @click="select">{{t('identityModel.select')}}</el-button></template>
 </ManagedDialog>
</template>
<style scoped>
/* 复用顶栏主题和点击区；长人员/组织名称省略但保留完整 title。 */
.personnel-identity{height:48px;max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
@media(max-width:600px){.personnel-identity{max-width:100px;}}
</style>
