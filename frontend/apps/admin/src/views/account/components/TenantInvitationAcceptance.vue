<script setup lang="ts">
import { setErrorFeedback } from "@appnexus/ui";
import { FloatingError } from "@appnexus/ui";
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { http, requestErrorMessage } from "@/utils/http";
import { ElMessage } from "element-plus";
/** 本人输入已收到的邀请标识与单次凭证，不从 URL 自动消费，避免链接预取产生副作用。 */
const { t } = useI18n();
const id = ref(""), token = ref(""), busy = ref(false), error = ref("");
const form = ref<{ validate: () => Promise<boolean> }>();
async function accept() {
  if (!await form.value?.validate().catch(() => false)) return;
  busy.value = true; error.value = "";
  try {
    const csrf = await http.request<{ token: string; headerName: string }>("get", "/bff/csrf");
    await http.request("post", "/bff/account/tenant-invitations/acceptance", { data: { invitationId: id.value, token: token.value }, headers: { [csrf.headerName]: csrf.token } });
    id.value = ""; ElMessage.success(t("account.invitationAccepted"));
  } catch (cause) { await setErrorFeedback(error, requestErrorMessage(cause)); }
  finally { busy.value = false; token.value = ""; }
}
</script>
<template>
  <section class="invitation-acceptance">
    <h2>{{ t("account.acceptInvitation") }}</h2>
    <p>{{ t("account.invitationHint") }}</p>
    <FloatingError v-if="error" :title="error" scope="error" type="error" :closable="false" />
    <el-form ref="form" :model="{ id, token }" label-position="top" @submit.prevent="accept">
      <el-form-item prop="id" :label="t('account.invitationId')" :rules="[{ required: true, pattern: /^[a-fA-F0-9-]{36}$/, message: t('account.invitationId'), trigger: 'blur' }]"><el-input v-model="id" :maxlength="36" :disabled="busy" /></el-form-item>
      <el-form-item prop="token" :label="t('account.invitationToken')" :rules="[{ required: true, pattern: /^[a-f0-9]{64}$/, message: t('account.invitationToken'), trigger: 'blur' }]"><el-input v-model="token" type="password" show-password :maxlength="64" :disabled="busy" autocomplete="off" /></el-form-item>
      <el-button v-operation="'TenantInvitation.accept'" type="primary" native-type="submit" :loading="busy">{{ t("account.acceptInvitation") }}</el-button>
    </el-form>
  </section>
</template>
<style scoped>
/* 页面外围留白引用全局内容与区块 Token，局部字段间距和触控高度按原交互保留。 */
.invitation-acceptance { margin-top:var(--app-section-gap,8px); max-width: 520px; }
.invitation-acceptance h2 { font-size: 16px; font-weight: 500; margin-bottom: 12px; }
.invitation-acceptance p { color: var(--el-text-color-secondary); line-height: 1.8; margin-bottom:var(--app-section-gap,8px); }
</style>
