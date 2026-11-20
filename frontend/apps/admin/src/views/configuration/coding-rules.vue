<script setup lang="ts">
import CodingRuleManagement from '@appnexus/business-object/coding-rules';
import {BusinessWorkspace} from '@appnexus/ui';
import {useI18n} from 'vue-i18n';
import {businessObjectApi} from '@/api/businessObjects';
import {requestErrorMessage} from '@/utils/http';
/** 独立租户规则管理入口；通过平台菜单和统一操作准入，不要求对象配置权限。 */
const api=businessObjectApi();const {t}=useI18n();
</script>
<template><div class="management-page"><BusinessWorkspace :title="t('coding.rulesTitle')"><template #data><CodingRuleManagement :api="api" :error-text="requestErrorMessage"/></template></BusinessWorkspace></div></template>
