<script setup lang="ts">
import RoleManagement from "@appnexus/authorization/role-management";
import { platformRoleApi } from "@/api/roleManagement";
import { requestErrorMessage } from "@/utils/http";
</script>

<template>
  <RoleManagement :api="platformRoleApi" :error-text="requestErrorMessage" />
</template>
