<script setup lang="ts">
import BusinessObjectManagement from '@appnexus/business-object/management';
import {businessObjectApi} from '@/api/businessObjects';
import {requestErrorMessage} from '@/utils/http';
/** 租户关系工作台，所有身份信息由服务端提供。 */
const api=businessObjectApi();
</script>
<template><BusinessObjectManagement :api="api" :error-text="requestErrorMessage"/></template>
