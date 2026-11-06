<script setup lang="ts">
import Administration from "@appnexus/tenant/management";
import {administrationApi} from "@/api/administration";
import {requestErrorMessage} from "@/utils/http";
import {enterTenant} from "@/api/tenant";
import {ref} from "vue";
const error=ref("");
/** 平台身份不自动成为租户成员，进入组织必须经成员验证，失败留在平台页面。 */
async function enter(id:string){try{await enterTenant(id,"/organization/manage");}catch(cause){error.value=requestErrorMessage(cause);}}
</script>
<template><div><el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-4" /><Administration :api="administrationApi" :error-text="requestErrorMessage" @enter="enter" /></div></template>
