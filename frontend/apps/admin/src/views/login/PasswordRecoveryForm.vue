<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
import { useI18n } from "vue-i18n";
import { http, requestErrorMessage } from "@/utils/http";

/** 复用登录场景 Element Plus 表单；找回凭证和密码仅保存在本组件内存。 */
const { t } = useI18n();
const username = ref(""), recoveryCode = ref(""), token = ref(""), password = ref(""), confirm = ref("");
const stage = ref<"recover" | "complete" | "done">("recover");
const busy = ref(false), error = ref("");
const form = ref<{ validate: () => Promise<boolean> }>();
/** 两个阶段均读取当前登录会话的 CSRF，不能通过找回入口绕过防护。 */
async function submit() {
  if (busy.value || !await form.value?.validate().catch(() => false)) return;
  if (stage.value === "complete" && password.value !== confirm.value) { error.value = t("account.mismatch"); return; }
  busy.value = true; error.value = "";
  try {
    const csrf = await http.request<{ token: string; headerName: string }>("get", "/login/csrf");
    const headers = { [csrf.headerName]: csrf.token };
    if (stage.value === "recover") {
      token.value = (await http.request<{ token: string }>("post", "/login/recovery", { data: { username: username.value, recoveryCode: recoveryCode.value }, headers })).token;
      stage.value = "complete";
    } else {
      await http.request("post", "/login/recovery/completion", { data: { username: username.value, token: token.value, newPassword: password.value }, headers });
      token.value = ""; stage.value = "done";
    }
  } catch (cause) { error.value = requestErrorMessage(cause); }
  finally { busy.value = false; recoveryCode.value = ""; password.value = ""; confirm.value = ""; }
}
onBeforeUnmount(() => { recoveryCode.value = ""; token.value = ""; password.value = ""; confirm.value = ""; });
</script>
<template>
  <div>
    <el-alert :title="t(stage === 'done' ? 'account.recoveryDone' : 'account.recoveryHint')" :type="stage === 'done' ? 'success' : 'info'" :closable="false" class="mb-4" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
    <el-form v-if="stage !== 'done'" ref="form" :model="{ username, recoveryCode, token, password, confirm }" label-position="top" size="large" @submit.prevent="submit">
      <el-form-item prop="username" :label="t('platform.account')" :rules="[{ required: true, message: t('platform.account'), trigger: 'blur' }]"><el-input v-model="username" autocomplete="username" :maxlength="100" :disabled="busy" /></el-form-item>
      <el-form-item v-if="stage === 'recover'" prop="recoveryCode" :label="t('account.recoveryCode')" :rules="[{ required: true, pattern: /^[a-f0-9]{32}$/, message: t('account.recoveryCode'), trigger: 'blur' }]"><el-input v-model="recoveryCode" type="password" show-password :maxlength="32" :disabled="busy" autocomplete="off" /></el-form-item>
      <template v-else>
        <el-form-item prop="token" :label="t('account.recoveryTicket')" :rules="[{ required: true, pattern: /^[a-f0-9]{64}$/, message: t('account.recoveryTicket'), trigger: 'blur' }]"><el-input v-model="token" type="password" show-password :maxlength="64" :disabled="busy" autocomplete="off" /></el-form-item>
        <el-form-item prop="password" :label="t('account.newPassword')" :rules="[{ required: true, min: 12, max: 128, message: t('administration.invalidPassword'), trigger: 'blur' }]"><el-input v-model="password" type="password" show-password autocomplete="new-password" :maxlength="128" :disabled="busy" /></el-form-item>
        <el-form-item prop="confirm" :label="t('account.confirmPassword')" :rules="[{ required: true, message: t('account.confirmPassword'), trigger: 'blur' }]"><el-input v-model="confirm" type="password" show-password autocomplete="new-password" :maxlength="128" :disabled="busy" /></el-form-item>
      </template>
      <el-button type="primary" native-type="submit" :loading="busy">{{ t(stage === 'recover' ? 'account.recoveryStart' : 'account.recoveryComplete') }}</el-button>
      <el-button v-if="stage === 'recover'" link :disabled="busy" @click="stage = 'complete'; error = ''; recoveryCode = ''">{{ t("account.recoveryHaveTicket") }}</el-button>
    </el-form>
  </div>
</template>
