<script setup lang="ts">
import DocumentManagement from '@appnexus/document/management';
import {documentApi} from '@/api/documents';
import {requestErrorMessage} from '@/utils/http';
/** 租户关系工作台，所有身份信息由服务端提供。 */
const api=documentApi();
</script>
<template><DocumentManagement :api="api" :error-text="requestErrorMessage"/></template>
