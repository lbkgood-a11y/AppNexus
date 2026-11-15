<script setup lang="ts">
import BusinessObjectManagement from '@appnexus/business-object/management';
import {businessObjectApi} from '@/api/businessObjects';
import {requestErrorMessage} from '@/utils/http';
/** 平台目录与租户授权，单据正文不进入此页面。 */
const api=businessObjectApi(true);
</script>
<template><BusinessObjectManagement :api="api" platform :error-text="requestErrorMessage"/></template>
