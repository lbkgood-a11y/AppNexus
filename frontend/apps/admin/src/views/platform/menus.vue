<script setup lang="ts">
import MenuManagement from "@appnexus/configuration/menu-management";
import {menuManagementApi} from "@/api/menuManagement";
import {requestErrorMessage} from "@/utils/http";
</script>
<template><MenuManagement :api="menuManagementApi" :error-text="requestErrorMessage" /></template>
