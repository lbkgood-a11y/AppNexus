<script setup lang="ts">
import Administration from "@appnexus/tenant/management";
import {administrationApi} from "@/api/administration";
import {requestErrorMessage} from "@/utils/http";
import {enterTenant} from "@/api/tenant";
import {ref} from "vue";
const error=ref("");
/** 平台身份不自动成为租户成员；验证成功后应用内定位组织菜单，仅重建内容区。 */
async function enter(id:string){try{await enterTenant(id,"/organization/manage");}catch(cause){error.value=requestErrorMessage(cause);}}
</script>
<template><div><el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-4" /><Administration section="tenants" :api="administrationApi" :error-text="requestErrorMessage" @enter="enter" /></div></template>
