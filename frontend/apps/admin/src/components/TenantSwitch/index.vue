<script setup lang="ts">
import {ref} from "vue";
import {useI18n} from "vue-i18n";
import {ElMessageBox} from "element-plus";
import {availableTenants,currentTenant} from "@/utils/tenantContext";
import {enterTenant} from "@/api/tenant";
import {requestErrorMessage} from "@/utils/http";
import {message} from "@/utils/message";
import {AppIcon} from "@appnexus/icons";
const {t}=useI18n();const switching=ref(false);
/** 多租户复用模板下拉；切换前提示未保存内容丢失，成功后重建页面。 */
async function select(id:string){
 if(id===currentTenant.value?.tenantId)return;
 try{await ElMessageBox.confirm(t('tenant.switchConfirm'),t('tenant.switch'),{confirmButtonText:t('tenant.switch'),cancelButtonText:t('organization.cancel'),type:'warning'});}catch{return;}
 switching.value=true;
 try{await enterTenant(id);}catch(cause){message(requestErrorMessage(cause),{type:'error'});}finally{switching.value=false;}
}
</script>
<template>
 <el-dropdown v-if="availableTenants.length>1" trigger="click" @command="select">
  <el-button text class="tenant-switch" :loading="switching" :title="currentTenant?.name" :aria-label="t('tenant.switch')"><AppIcon class="tenant-switch-icon" name="organization" :size="18"/><span class="tenant-name">{{currentTenant?.name??t('tenant.select')}}</span></el-button>
  <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="tenant in availableTenants" :key="tenant.tenantId" :command="tenant.tenantId" :disabled="switching||tenant.tenantId===currentTenant?.tenantId">{{tenant.name}}</el-dropdown-item></el-dropdown-menu></template>
 </el-dropdown>
</template>
<style scoped>
/* 多租户顶栏入口沿用 48px 点击区域和主题，长名称保留 title 与可访问标签。 */
.tenant-switch{height:48px;max-width:180px;}.tenant-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}.tenant-switch-icon{display:none;}
@media(max-width:600px){.tenant-switch{width:40px;max-width:40px;padding:0;}.tenant-name{display:none;}.tenant-switch-icon{display:block;}}
</style>
