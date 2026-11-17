<script setup lang="ts">
import {IconSelect} from "@/components/ReIcon";
import MenuManagement from "@appnexus/configuration/menu-management";
import {menuManagementApi} from "@/api/menuManagement";
import {requestErrorMessage} from "@/utils/http";
</script>
<template><MenuManagement :api="menuManagementApi" :error-text="requestErrorMessage"><template #icon-picker="picker"><IconSelect :model-value="picker.modelValue" :disabled="picker.disabled" @update:model-value="picker.update" /></template></MenuManagement></template>
