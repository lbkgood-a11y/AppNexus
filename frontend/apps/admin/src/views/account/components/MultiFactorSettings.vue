<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from "vue";
import { useI18n } from "vue-i18n";
import { ElMessage } from "element-plus";
import { challengeMfa, getMfaStatus, type MfaStatus } from "@/api/mfa";
import { requestErrorMessage } from "@/utils/http";
import { clearSession } from "@/utils/sessionLifecycle";

/** 组合既有设置分区和 Element Plus 表单；密钥与恢复码仅在当前组件内存短暂显示。 */
const { t } = useI18n();
const status = ref<MfaStatus>();
const busy = ref(false), error = ref(""), password = ref(""), code = ref(""), secret = ref("");
const factorMode=ref("totp");
const recovery = ref<string[]>([]);
const form = ref<{ validate: () => Promise<boolean> }>();
/** 读取失败保留明确错误及重试，不能把失败当作未绑定。 */
async function load() {
  busy.value = true; error.value = "";
  try { status.value = await getMfaStatus(); }
  catch (cause) { error.value = requestErrorMessage(cause); }
  finally { busy.value = false; }
}
/** 每次写入复核当前密码；提交后清空密码及验证码，绑定密钥不写日志或 URL。 */
async function submit(action: "enrollment" | "activation" | "step-up" | "disable") {
  if (busy.value || !await form.value?.validate().catch(() => false)) return;
  if(action!=="enrollment"&&(action==="activation"||factorMode.value==="totp")&&!/^\d{6}$/.test(code.value.trim())){
    error.value=t("account.mfaSixDigits");return;
  }
  busy.value = true; error.value = "";
  try {
    if (action === "enrollment") secret.value = (await challengeMfa<{ secret: string }>(action, password.value)).secret;
    else if (action === "activation") {
      recovery.value = (await challengeMfa<{ codes: string[] }>(action, password.value, code.value.trim())).codes;
      secret.value = ""; await load(); ElMessage.success(t("account.mfaSaved"));
    } else {
      await challengeMfa(action, password.value, code.value.trim());
      if (action === "disable") await clearSession("expired");
      else { await load(); ElMessage.success(t("account.mfaStepUpSaved")); }
    }
  } catch (cause) { error.value = requestErrorMessage(cause); }
  finally { busy.value = false; password.value = ""; code.value = ""; }
}
onMounted(load);
onBeforeUnmount(() => { password.value = ""; code.value = ""; secret.value = ""; recovery.value = []; });
</script>
<template>
  <section class="mfa-settings" v-loading="busy">
    <h2>{{ t("account.mfaTitle") }}</h2>
    <p>{{ t("account.mfaHint") }}</p><el-alert v-if="status" :title="t(status.platformRequired?'account.mfaPlatformRequired':'account.mfaPlatformOptional')" type="info" :closable="false"/><el-descriptions :column="1" border><el-descriptions-item :label="t('account.mfaMethod')">{{t('account.mfaTotpDescription')}}</el-descriptions-item><el-descriptions-item :label="t('account.mfaRecoveryMethod')">{{t('account.mfaRecoveryDescription')}}</el-descriptions-item></el-descriptions>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-button v-if="!status" :disabled="busy" @click="load">{{ t("account.retry") }}</el-button>
    <template v-if="status">
      <el-tag :type="status.enabled ? 'success' : 'info'">{{ t(status.enabled ? "account.enabled" : "account.mfaNotEnabled") }}</el-tag>
      <el-alert v-if="!status.available" :title="t('account.mfaUnavailable')" type="warning" :closable="false" />
      <el-alert v-if="status.steppedUp" :title="t('account.mfaStepUpSaved')" type="success" :closable="false" />
      <template v-if="recovery.length">
        <el-alert :title="t('account.mfaRecoveryHint')" type="warning" :closable="false" />
        <div class="recovery-codes"><code v-for="item in recovery" :key="item">{{ item }}</code></div>
        <el-button @click="recovery = []">{{ t("account.mfaRecoverySaved") }}</el-button>
      </template>
      <el-form v-else-if="status.available" ref="form" :model="{ password, code }" label-position="top" @submit.prevent="submit(status.enabled ? 'step-up' : secret ? 'activation' : 'enrollment')">
        <el-alert v-if="secret" :title="t('account.mfaSecretHint')" type="info" :closable="false" />
        <ol v-if="secret" class="binding-help"><li>{{t('account.mfaSetupOne')}}</li><li>{{t('account.mfaSetupTwo')}}</li><li>{{t('account.mfaSetupThree')}}</li></ol><code v-if="secret" class="binding-secret">{{ secret }}</code>
        <el-form-item prop="password" :label="t('account.currentPassword')" :rules="[{ required: true, message: t('account.currentPassword'), trigger: 'blur' }]">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" :maxlength="128" :disabled="busy" />
        </el-form-item>
        <el-form-item v-if="status.enabled" :label="t('account.mfaMethod')"><el-radio-group v-model="factorMode" @change="code=''"><el-radio value="totp">{{t('account.mfaTotp')}}</el-radio><el-radio value="recovery">{{t('account.mfaRecoveryMethod')}}</el-radio></el-radio-group></el-form-item><el-form-item v-if="secret || status.enabled" prop="code" :label="t('account.mfaCode')" :rules="[{ required: true, message: t('account.mfaCode'), trigger: 'blur' }]">
          <el-input v-model="code" :inputmode="!status.enabled||factorMode==='totp'?'numeric':'text'" autocomplete="one-time-code" :maxlength="32" :disabled="busy" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy">{{ t(status.enabled ? "account.mfaStepUp" : secret ? "account.mfaActivate" : "account.mfaEnroll") }}</el-button>
        <el-button v-if="secret" :disabled="busy" @click="secret='';password='';code=''">{{t('account.cancel')}}</el-button><el-popconfirm v-if="status.enabled" :title="t('account.mfaDisableConfirm')" :confirm-button-text="t('account.mfaDisable')" :cancel-button-text="t('account.cancel')" @confirm="submit('disable')">
          <template #reference><el-button type="danger" :disabled="busy">{{ t("account.mfaDisable") }}</el-button></template>
        </el-popconfirm>
      </el-form>
    </template>
  </section>
</template>
<style scoped>
.mfa-settings { padding: 24px 0; display: grid; gap: 16px; }
.mfa-settings h2 { font-size: 16px; font-weight: 500; }
.mfa-settings p { color: var(--el-text-color-secondary); line-height: 1.8; }
.mfa-settings form { max-width: 520px; }
.binding-secret { display: block; margin: 16px 0; overflow-wrap: anywhere; }
.recovery-codes { display: grid; gap: 8px; overflow-wrap: anywhere; }
</style>
