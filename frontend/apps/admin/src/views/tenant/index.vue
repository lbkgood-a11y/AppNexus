<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { computed,onMounted,ref } from "vue";
import { BusinessWorkspace, ManagementTable, type ManagementColumn } from "@appnexus/ui";
import { useRouter } from "vue-router";
import { getMyTenants,enterTenant,type TenantMembership } from "@/api/tenant";
import {administrationApi} from "@/api/administration";
import { requestErrorMessage } from "@/utils/http";
const router=useRouter();
const {t}=useI18n();
const tenants=ref<TenantMembership[]>([]);
const loading=ref(false),selecting=ref(""),error=ref("");
const platformAdministrator=ref(false);
const query=ref(""),submittedQuery=ref("");
const filteredTenants=computed(()=>tenants.value.filter(item=>item.name.toLocaleLowerCase().includes(submittedQuery.value.toLocaleLowerCase())));
const columns=computed<ManagementColumn[]>(()=>[{key:'name',prop:'name',label:t('tenant.name'),minWidth:200},{key:'operations',label:t('tenant.operation'),width:120,fixed:'right'}]);
/** 只在本人可访问租户集合内筛选，不查询或推断全平台租户目录。 */
function search(reset=false){if(reset)query.value='';submittedQuery.value=query.value;}
/** 仅自动进入唯一有效租户；多个租户由用户明确选择，无成员时不自动授予权限。 */
async function load() {
 loading.value=true;error.value="";
 try {
  tenants.value=await getMyTenants();
  platformAdministrator.value=(await administrationApi.access()).administrator;
  if(tenants.value.length===1) await enter(tenants.value[0]);
 } catch(cause){error.value=requestErrorMessage(cause);}
 finally{loading.value=false;}
}
/** 不携带成员标识或角色，服务端决定能否进入。 */
async function enter(item:TenantMembership) {
 selecting.value=item.tenantId;
 try {await enterTenant(item.tenantId);}
 catch(cause){error.value=requestErrorMessage(cause);}
 finally{selecting.value="";}
}
onMounted(load);
</script>
<template>
 <div class="management-page">
  <BusinessWorkspace :title="t('tenant.select')" :description="t('tenant.selectDescription')">
   <template v-if="error" #feedback><el-alert :title="error" type="error" :closable="false" show-icon /></template>
   <template v-if="tenants.length" #search><div class="management-filter"><div class="management-filter-header"><h2 class="management-filter-title">{{t('tenant.search')}}</h2></div><el-form inline @submit.prevent="search()"><el-form-item :label="t('tenant.name')"><el-input v-model="query" clearable :maxlength="100" :placeholder="t('management.keyword')" /></el-form-item><div class="management-filter-actions"><el-button native-type="submit" type="primary" :disabled="loading || Boolean(selecting)">{{t('administration.search')}}</el-button><el-button :disabled="loading || Boolean(selecting)" @click="search(true)">{{t('management.reset')}}</el-button></div></el-form></div></template>
   <template #data><ManagementTable embedded :title="t('tenant.availableList')" :total="filteredTenants.length" :rows="filteredTenants" :columns="columns" :loading="loading" :busy="Boolean(selecting)" :empty-text="t(submittedQuery?'management.noResults':'tenant.noMembership')" @refresh="load">
   <template #actions><el-button v-if="!tenants.length&&platformAdministrator" type="primary" @click="router.push('/platform/tenants')">{{t('administration.tenantTitle')}}</el-button></template>
   <template #operations="{row}">
    <el-button type="primary" link :loading="selecting===row.tenantId" :disabled="Boolean(selecting)" @click="enter(row as TenantMembership)">{{ t("tenant.enter") }}</el-button>
   </template>
   </ManagementTable></template>
  </BusinessWorkspace>
 </div>
</template>
