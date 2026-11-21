<script setup lang="ts">
import CodingRuleManagement from '@appnexus/business-object/coding-rules';
import {businessObjectApi} from '@/api/businessObjects';
import {requestErrorMessage} from '@/utils/http';
/** 独立租户规则管理入口；通过平台菜单和统一操作准入，不要求对象配置权限。 */
const api=businessObjectApi();
</script>
<template><CodingRuleManagement :api="api" :error-text="requestErrorMessage"/></template>
