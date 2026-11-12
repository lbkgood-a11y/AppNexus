<script setup lang="ts">
import DocumentManagement from '@appnexus/document/management';
import {documentApi} from '@/api/documents';
import {requestErrorMessage} from '@/utils/http';
/** 平台目录与租户授权，单据正文不进入此页面。 */
const api=documentApi(true);
</script>
<template><DocumentManagement :api="api" platform :error-text="requestErrorMessage"/></template>
