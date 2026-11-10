<script setup lang="ts">
import { computed, nextTick, ref, toRaw, watch } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { AppIcon } from "@appnexus/icons";
import LanguageSwitch from "@/components/LanguageSwitch/index.vue";
import ThemeToggle from "@/components/ThemeToggle/index.vue";
import { useTranslationLang } from "@/layout/hooks/useTranslationLang";
import { beginLogin } from "@/api/session";
import { http } from "@/utils/http";
import { avatar, illustration } from "./utils/static";
import PasswordRecoveryForm from "./PasswordRecoveryForm.vue";

const route = useRoute();
const { t } = useI18n();
const { locale, translationCh, translationEn } = useTranslationLang();
const credentials = computed(() => route.query.credentials === "1");
const csrf = ref<{ token: string; parameterName: string }>();
const serviceUnavailable = ref(false);
const submitting = ref(false);
const username = ref("");
const password = ref("");
/** 第二因子只提交给标准登录 POST，不根据账号查询绑定状态，避免账号枚举。 */
const mfaCode = ref("");
const recovering = ref(false);

/** 路由可能复用登录组件，因此按凭据模式变化获取 CSRF；密码不写入浏览器持久存储。 */
watch(() => [credentials.value, route.query.reason], async ([required]) => {
  csrf.value = undefined;
  serviceUnavailable.value = false;
  if (!required) return;
  try { csrf.value = await http.request("get", "/login/csrf"); }
  catch { serviceUnavailable.value = true; }
}, { immediate: true });
/**
 * 提交前重新获取当前会话 CSRF，避免登录页长时间停留或另一标签注销后携带旧凭据。
 * 保留标准表单 POST；获取失败时停留在当前页面，不提交密码。
 */
async function submitLogin(event: Event) {
  if (submitting.value) return;
  const form = event.target as HTMLFormElement;
  submitting.value = true;
  serviceUnavailable.value = false;
  try {
    csrf.value = await http.request("get", "/login/csrf");
    await nextTick();
    form.submit();
  } catch {
    serviceUnavailable.value = true;
    submitting.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <header class="login-header">
      <a class="brand" href="/#/login" aria-label="AppNexus">
        <avatar class="brand-logo" /><span>AppNexus</span>
      </a>
      <div class="login-tools">
        <ThemeToggle />
        <LanguageSwitch />
      </div>
    </header>

    <main class="login-main">
      <section class="login-visual" aria-hidden="true">
        <div class="visual-orbit orbit-one" /><div class="visual-orbit orbit-two" />
        <div class="visual-content">
          <div class="visual-label">APPNEXUS WORKSPACE</div>
          <h1><span>{{ t("platform.taglineLead") }}</span><span>{{ t("platform.tagline") }}</span></h1>
          <p>{{ t("platform.description") }}</p>
          <component :is="toRaw(illustration)" class="login-illustration" />
        </div>
      </section>

      <section class="login-panel" :aria-label="t('platform.login')">
        <div class="login-form">
          <div class="form-mark"><AppIcon name="shield" :size="27" /></div>
          <h2>{{ t(recovering ? "account.recoveryTitle" : "platform.welcome") }}</h2>
          <p class="form-subtitle">{{ t("platform.subtitle") }}</p>
          <el-alert v-if="route.query.reason" :title="t(route.query.reason === 'csrfExpired' ? 'account.loginExpired' : route.query.reason === 'passwordChanged' ? 'account.passwordSaved' : 'account.sessionExpired')"
            type="info" :closable="false" show-icon class="form-alert" />
          <PasswordRecoveryForm v-if="recovering" />
          <form v-else-if="credentials" method="post" action="/login" @submit.prevent="submitLogin">
            <el-alert v-if="route.query.error" :title="t('platform.loginError')" type="error" :closable="false" show-icon class="form-alert" />
            <el-alert v-if="serviceUnavailable" :title="t('platform.serviceError')" type="error" :closable="false" show-icon class="form-alert" />
            <input v-if="csrf" type="hidden" :name="csrf.parameterName" :value="csrf.token" />
            <div class="form-field">
              <label for="username">{{ t("platform.account") }}</label>
              <el-input id="username" v-model="username" name="username" autocomplete="username"
                required maxlength="100" size="large" :placeholder="t('platform.accountPlaceholder')">
                <template #prefix><AppIcon name="user" :size="18" /></template>
              </el-input>
            </div>
            <div class="form-field">
              <label for="password">{{ t("platform.password") }}</label>
              <el-input id="password" v-model="password" name="password" type="password" autocomplete="current-password"
                required show-password size="large" :placeholder="t('platform.passwordPlaceholder')">
                <template #prefix><AppIcon name="lock" :size="18" /></template>
              </el-input>
            </div>
            <div class="form-field">
              <label for="mfa-code">{{ t("account.mfaLoginCode") }}</label>
              <el-input id="mfa-code" v-model="mfaCode" name="mfaCode" autocomplete="one-time-code" maxlength="32" size="large" />
            </div>
            <el-button native-type="submit" type="primary" size="large" class="login-submit"
              :disabled="!csrf" :loading="submitting">
              {{ t("platform.login") }}<AppIcon name="arrowRight" :size="18" class="submit-arrow" />
            </el-button>
          </form>
          <el-button v-else type="primary" size="large" class="login-submit" @click="beginLogin">
            {{ t("platform.continue") }}<AppIcon name="arrowRight" :size="18" class="submit-arrow" />
          </el-button>
          <el-button link type="primary" class="mt-4" @click="recovering = !recovering; password = ''; mfaCode = ''">{{ t(recovering ? "account.recoveryBack" : "account.recoveryTitle") }}</el-button>
          <div class="secure-caption"><AppIcon name="shield" :size="15" />{{ t("platform.secure") }}</div>
        </div>
      </section>
    </main>
    <footer class="login-footer">© {{ new Date().getFullYear() }} AppNexus</footer>
  </div>
</template>

<style scoped>
@import url("@/style/login.css");
</style>
