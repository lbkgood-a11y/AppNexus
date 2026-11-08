<script setup lang="ts">
import RoleManagement from "@appnexus/authorization/role-management";
import { tenantRoleApi } from "@/api/roleManagement";
import { requestErrorMessage } from "@/utils/http";
</script>

<template>
  <RoleManagement :api="tenantRoleApi" :error-text="requestErrorMessage" />
</template>
